package com.haryun.keywordalarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haryun.keywordalarm.data.KeywordRepository

class ServiceToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = KeywordRepository(context)
        val newState = !repo.isServiceEnabled()
        repo.setServiceEnabled(newState)
        KeywordNotificationListener.instance?.updateStatusNotification()
    }
}
