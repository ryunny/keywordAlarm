package com.haryun.keywordalarm.service

import android.media.MediaPlayer

object AlarmController {
    private var currentPlayer: MediaPlayer? = null

    fun isPlaying(): Boolean = currentPlayer?.isPlaying == true

    fun register(player: MediaPlayer) {
        currentPlayer = player
    }

    fun stop() {
        currentPlayer?.let {
            try { if (it.isPlaying) it.stop(); it.release() } catch (e: Exception) {}
        }
        currentPlayer = null
        KeywordNotificationListener.instance?.stopAlarm()
    }

    fun clear() {
        currentPlayer = null
    }
}
