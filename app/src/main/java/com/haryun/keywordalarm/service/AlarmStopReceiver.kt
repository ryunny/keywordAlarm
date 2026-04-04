package com.haryun.keywordalarm.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KeywordNotificationListener.instance?.stopAlarm()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(KeywordNotificationListener.NOTIFICATION_ID)
    }
}
