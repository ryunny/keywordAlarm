package com.haryun.keywordalarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.haryun.keywordalarm.R
import com.haryun.keywordalarm.data.AlarmRepeat
import com.haryun.keywordalarm.data.KeywordRepository
import com.haryun.keywordalarm.data.VibrationPattern

class KeywordNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "KeywordNotificationListener"
        const val CHANNEL_ID = "alarm_key_channel"
        const val CHANNEL_STATUS_ID = "alarm_key_status"
        const val NOTIFICATION_ID = 1001
        const val STATUS_NOTIFICATION_ID = 1002
        const val ACTION_STOP_ALARM = "com.haryun.keywordalarm.STOP_ALARM"
        private const val COOLDOWN_MS = 30_000L  // 알람 종료 후 재트리거 방지 쿨다운

        var instance: KeywordNotificationListener? = null
    }

    private lateinit var keywordRepository: KeywordRepository
    private var mediaPlayer: MediaPlayer? = null
    private var repeatCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private var originalAlarmVolume = -1  // 알람 전 원래 볼륨 저장용
    private var activeVibrator: Vibrator? = null  // 진동 취소용
    private var isAlarmActive = false            // 현재 알람(소리/진동) 진행 중 여부
    private var cooldownEndTime = 0L             // 쿨다운 종료 시각 (epoch ms)

    override fun onCreate() {
        super.onCreate()
        instance = this
        keywordRepository = KeywordRepository(applicationContext)
        createNotificationChannel()
        Log.d(TAG, "알림 리스너 서비스 시작됨")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 알람 채널 (소리/진동 있음)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_alarm_name), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = getString(R.string.notif_channel_alarm_desc) }
            )

            // 상태 채널 (무음, 항상 표시)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_STATUS_ID, getString(R.string.notif_channel_status_name), NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = getString(R.string.notif_channel_status_desc)
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
        }
    }

    fun updateStatusNotification() {
        val isEnabled = keywordRepository.isServiceEnabled()

        val toggleIntent = Intent(this, ServiceToggleReceiver::class.java)
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 1, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_STATUS_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (isEnabled) getString(R.string.notif_status_active) else getString(R.string.status_inactive))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                if (isEnabled) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (isEnabled) getString(R.string.btn_off) else getString(R.string.btn_on),
                togglePendingIntent
            )
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(STATUS_NOTIFICATION_ID, notification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // 자기 자신의 알림은 무시 (알람 알림이 재귀 트리거되는 것 방지)
        if (sbn.packageName == applicationContext.packageName) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val packageName = sbn.packageName
        val fullContent = "$title $text".lowercase()

        Log.d(TAG, "알림 수신: [$packageName] $title - $text")

        if (!keywordRepository.isServiceEnabled()) return

        val matchedKeyword = keywordRepository.findMatchingKeyword(packageName, fullContent)
        if (matchedKeyword != null) {
            Log.d(TAG, "키워드 매칭됨: $matchedKeyword (앱: $packageName)")

            if (!keywordRepository.isCurrentTimeInSchedule()) {
                Log.d(TAG, "스케줄 외 시간 — 알람 스킵")
                return
            }

            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (e: Exception) { packageName }

            if (triggerAlarm(matchedKeyword, appName)) {
                keywordRepository.addAlarmHistory(matchedKeyword, packageName, appName)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    // 반환값: true = 알람 실행됨(히스토리 기록), false = 스킵(히스토리 미기록)
    private fun triggerAlarm(keyword: String, appName: String, bypassCooldown: Boolean = false): Boolean {
        // 전화 중이면 알람 스킵 (통화 방해 방지)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.mode == AudioManager.MODE_IN_CALL ||
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
            Log.d(TAG, "통화 중 — 알람 스킵")
            return false
        }

        // 쿨다운 중이면 스킵 (알람 종료 후 30초)
        if (!bypassCooldown && System.currentTimeMillis() < cooldownEndTime) {
            val remaining = (cooldownEndTime - System.currentTimeMillis()) / 1000
            Log.d(TAG, "쿨다운 중 (${remaining}초 남음) — 알람 스킵")
            return false
        }

        // 이미 알람 진행 중이면 알림만 업데이트 (히스토리는 기록)
        if (isAlarmActive) {
            Log.d(TAG, "알람 이미 진행 중 — 알림 업데이트만")
            showAlarmNotification(keyword, appName)
            return true
        }

        isAlarmActive = true
        showAlarmNotification(keyword, appName)

        if (keywordRepository.isWakeScreenEnabled()) {
            wakeScreen(keyword, appName)
        }
        if (keywordRepository.isSoundEnabled()) {
            repeatCount = 0
            playOnce() // 진동은 playOnce() 안에서 소리와 함께 처리
        } else if (keywordRepository.isVibrationEnabled()) {
            // 소리 없이 진동만: 안전장치 타이머 포함
            vibrate()
            stopRunnable?.let { handler.removeCallbacks(it) }
            stopRunnable = Runnable {
                stopVibration()
                markAlarmEnded()
                cancelAlarmNotification()
            }.also { handler.postDelayed(it, 30_000L) }  // 30초 후 자동 종료
        } else {
            // 소리/진동 둘 다 꺼진 경우 — 알림만 표시하고 즉시 종료 처리
            markAlarmEnded()
        }
        return true
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = try {
            VibrationPattern.valueOf(keywordRepository.getVibrationPattern()).pattern
        } catch (e: Exception) { VibrationPattern.DEFAULT.pattern }

        vibrator.cancel() // 이전 진동 취소 후 새로 시작
        activeVibrator = vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // amplitude를 명시해야 Samsung 등에서 자체 패턴으로 대체하지 않음
            // repeat=0: 패턴 끝나면 처음부터 반복 (소리와 함께 계속 울림)
            val amplitudes = IntArray(pattern.size) { i -> if (i % 2 == 0) 0 else 255 }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        activeVibrator?.cancel()
        activeVibrator = null
    }

    private fun markAlarmEnded() {
        isAlarmActive = false
        cooldownEndTime = System.currentTimeMillis() + COOLDOWN_MS
    }

    private fun playOnce() {
        // 소리와 진동을 항상 함께 실행
        if (keywordRepository.isVibrationEnabled()) vibrate()

        try {
            mediaPlayer?.release()

            val customSoundUri = keywordRepository.getCustomSoundUri()
            val customUri = customSoundUri?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    applicationContext.contentResolver.openInputStream(uri)?.close()
                    uri // 접근 가능하면 사용
                } catch (e: Exception) { null } // 접근 불가면 기본 알람음으로 폴백
            }
            val alarmUri = customUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val repeat = try {
                AlarmRepeat.valueOf(keywordRepository.getAlarmRepeat())
            } catch (e: Exception) { AlarmRepeat.ONCE }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                // 원래 볼륨 저장 (최초 1회만)
                if (originalAlarmVolume == -1) {
                    originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                }
                val volumeLevel = keywordRepository.getVolumeLevel()
                val targetVolume = (maxVolume * volumeLevel / 100f).toInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
                prepare()
                start()
                AlarmController.register(this)

                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                    AlarmController.clear()
                    repeatCount++
                    when (repeat) {
                        AlarmRepeat.ONCE -> {
                            stopRunnable?.let { r -> handler.removeCallbacks(r) }
                            stopVibration()
                            restoreVolume()
                            markAlarmEnded()
                            cancelAlarmNotification()
                        }
                        AlarmRepeat.THREE -> {
                            if (repeatCount < 3) {
                                handler.post { playOnce() }
                            } else {
                                stopRunnable?.let { r -> handler.removeCallbacks(r) }
                                stopVibration()
                                restoreVolume()
                                markAlarmEnded()
                                cancelAlarmNotification()
                            }
                        }
                        AlarmRepeat.LOOP -> handler.post { playOnce() }
                    }
                }
            }

            // 최대 재생 시간 (안전장치)
            stopRunnable?.let { handler.removeCallbacks(it) }
            val maxMs = when (repeat) {
                AlarmRepeat.ONCE -> 30_000L
                AlarmRepeat.THREE -> 90_000L
                AlarmRepeat.LOOP -> 600_000L  // 10분 (수동 정지 전까지 계속 반복)
            }
            stopRunnable = Runnable {
                mediaPlayer?.let { if (it.isPlaying) { it.stop(); it.release() } }
                mediaPlayer = null
                stopVibration()
                restoreVolume()
                markAlarmEnded()
                if (repeat == AlarmRepeat.LOOP) {
                    showAutoStopNotification()
                }
            }.also { handler.postDelayed(it, maxMs) }

        } catch (e: Exception) {
            Log.e(TAG, "알람 소리 재생 실패: ${e.message}")
        }
    }

    private fun wakeScreen(keyword: String, appName: String) {
        try {
            // CPU 유지 (Activity가 뜨기 전까지 슬립 방지)
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "알람키:WakeLock")
            wl.acquire(10_000L)

            // 직접 Activity 시작 → setTurnScreenOn/setShowWhenLocked이 화면을 켜줌
            startActivity(AlarmFullScreenActivity.createIntent(this, keyword, appName))
        } catch (e: Exception) {
            Log.e(TAG, "화면 켜기 실패: ${e.message}")
        }
    }

    private fun showAlarmNotification(keyword: String, appName: String) {
        val stopIntent = Intent(this, AlarmStopReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 전체화면 Activity (잠금화면에서 바로 정지 가능)
        val fullScreenIntent = AlarmFullScreenActivity.createIntent(this, keyword, appName)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(getString(R.string.notif_alarm_title))
            .setContentText(getString(R.string.notif_alarm_content, keyword, appName))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setDeleteIntent(stopPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true) // 잠금화면/사용 중 전체화면으로 표시
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.btn_stop), stopPendingIntent)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    fun triggerTest() {
        triggerAlarm(getString(R.string.test_keyword), getString(R.string.app_name), bypassCooldown = true)
    }

    fun stopAlarm() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        mediaPlayer?.let {
            try { if (it.isPlaying) { it.stop(); it.release() } } catch (e: Exception) {}
        }
        mediaPlayer = null
        stopVibration()
        restoreVolume()
        markAlarmEnded()
    }

    private fun restoreVolume() {
        if (originalAlarmVolume != -1) {
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
            } catch (e: Exception) {
                Log.e(TAG, "볼륨 복구 실패: ${e.message}")
            }
            originalAlarmVolume = -1
        }
    }

    private fun cancelAlarmNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)
    }

    private fun showAutoStopNotification() {
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_STATUS_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("알람이 10분 후 자동 종료되었습니다.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(1003, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        instance = null
        Log.d(TAG, "알림 리스너 서비스 종료됨")
    }
}
