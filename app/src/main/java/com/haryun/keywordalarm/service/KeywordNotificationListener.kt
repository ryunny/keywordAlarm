package com.haryun.keywordalarm.service

import android.app.Notification
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.haryun.keywordalarm.data.KeywordRepository
import com.haryun.keywordalarm.data.VibrationPattern

class KeywordNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "KeywordNotificationListener"
    }

    private lateinit var keywordRepository: KeywordRepository
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        keywordRepository = KeywordRepository(applicationContext)
        Log.d(TAG, "알림 리스너 서비스 시작됨")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // 알림 내용 추출
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val packageName = sbn.packageName

        val fullContent = "$title $text".lowercase()

        Log.d(TAG, "알림 수신: [$packageName] $title - $text")

        // 서비스가 활성화되어 있는지 확인
        if (!keywordRepository.isServiceEnabled()) {
            return
        }

        // 키워드 확인 (글로벌 + 앱별)
        val matchedKeyword = keywordRepository.findMatchingKeyword(packageName, fullContent)
        if (matchedKeyword != null) {
            Log.d(TAG, "키워드 매칭됨: $matchedKeyword (앱: $packageName)")

            // 시간대 설정 체크
            if (!keywordRepository.isCurrentTimeInSchedule()) {
                Log.d(TAG, "스케줄 외 시간 — 알람 스킵")
                return
            }

            triggerAlarm()

            // 이력 저장
            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (e: Exception) { packageName }
            keywordRepository.addAlarmHistory(matchedKeyword, packageName, appName)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 알림 제거 시 처리 (필요시 구현)
    }

    private fun triggerAlarm() {
        // 진동 설정 확인
        if (keywordRepository.isVibrationEnabled()) {
            vibrate()
        }

        // 소리 설정 확인
        if (keywordRepository.isSoundEnabled()) {
            playAlarmSound()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val patternName = keywordRepository.getVibrationPattern()
        val pattern = try {
            VibrationPattern.valueOf(patternName).pattern
        } catch (e: Exception) {
            VibrationPattern.DEFAULT.pattern
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun playAlarmSound() {
        try {
            // 기존 재생 중인 소리 중지
            mediaPlayer?.release()

            // 커스텀 알람음 또는 기본 알람음 사용
            val customSoundUri = keywordRepository.getCustomSoundUri()
            val alarmUri = if (customSoundUri != null) {
                Uri.parse(customSoundUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)

                // STREAM_ALARM 사용 - 무음 모드에서도 소리 재생
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                // 사용자 설정 볼륨 적용
                val volumeLevel = keywordRepository.getVolumeLevel()
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val targetVolume = (maxVolume * volumeLevel / 100f).toInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

                prepare()
                start()

                setOnCompletionListener {
                    release()
                }
            }

            // 3초 후 강제 중지 (긴 알람음 대비)
            android.os.Handler(mainLooper).postDelayed({
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                }
                mediaPlayer = null
            }, 3000)

        } catch (e: Exception) {
            Log.e(TAG, "알람 소리 재생 실패: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d(TAG, "알림 리스너 서비스 종료됨")
    }
}
