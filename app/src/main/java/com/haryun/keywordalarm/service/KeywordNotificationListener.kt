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

        var instance: KeywordNotificationListener? = null
    }

    private lateinit var keywordRepository: KeywordRepository
    private var mediaPlayer: MediaPlayer? = null
    private var repeatCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

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
                NotificationChannel(CHANNEL_ID, "알람키 알림", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "키워드 매칭 시 표시되는 알림" }
            )

            // 상태 채널 (무음, 항상 표시)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_STATUS_ID, "알람키 상태", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "알람키 실행 상태"
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
            .setContentTitle("알람키")
            .setContentText(if (isEnabled) "키워드 감지 중" else "비활성화됨")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                if (isEnabled) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (isEnabled) "끄기" else "켜기",
                togglePendingIntent
            )
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(STATUS_NOTIFICATION_ID, notification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

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

            triggerAlarm(matchedKeyword, appName)
            keywordRepository.addAlarmHistory(matchedKeyword, packageName, appName)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun triggerAlarm(keyword: String, appName: String) {
        // 알람 배너는 항상 표시
        showAlarmNotification(keyword, appName)

        // 화면 켜기는 설정된 경우에만
        if (keywordRepository.isWakeScreenEnabled()) {
            wakeScreen()
        }
        if (keywordRepository.isVibrationEnabled()) vibrate()
        if (keywordRepository.isSoundEnabled() && !AlarmController.isPlaying()) {
            repeatCount = 0
            playOnce()
        }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun playOnce() {
        try {
            mediaPlayer?.release()

            val customSoundUri = keywordRepository.getCustomSoundUri()
            val alarmUri = if (customSoundUri != null) Uri.parse(customSoundUri)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
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
                val volumeLevel = keywordRepository.getVolumeLevel()
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val targetVolume = (maxVolume * volumeLevel / 100f).toInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
                prepare()
                start()
                AlarmController.register(this)

                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                    repeatCount++
                    when (repeat) {
                        AlarmRepeat.ONCE -> stopRunnable?.let { r -> handler.removeCallbacks(r) }
                        AlarmRepeat.THREE -> if (repeatCount < 3) handler.post { playOnce() }
                        AlarmRepeat.LOOP -> handler.post { playOnce() }
                    }
                }
            }

            // 최대 재생 시간 (안전장치)
            stopRunnable?.let { handler.removeCallbacks(it) }
            val maxMs = when (repeat) {
                AlarmRepeat.ONCE -> 30_000L
                AlarmRepeat.THREE -> 90_000L
                AlarmRepeat.LOOP -> 120_000L
            }
            stopRunnable = Runnable {
                mediaPlayer?.let { if (it.isPlaying) { it.stop(); it.release() } }
                mediaPlayer = null
            }.also { handler.postDelayed(it, maxMs) }

        } catch (e: Exception) {
            Log.e(TAG, "알람 소리 재생 실패: ${e.message}")
        }
    }

    private fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "알람키:WakeLock"
            )
            wl.acquire(5000) // 5초간 화면 켜기
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("알람키 — 키워드 감지됨")
            .setContentText("\"$keyword\" ($appName)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "정지", stopPendingIntent)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    fun stopAlarm() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        mediaPlayer?.let { if (it.isPlaying) { it.stop(); it.release() } }
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        instance = null
        Log.d(TAG, "알림 리스너 서비스 종료됨")
    }
}
