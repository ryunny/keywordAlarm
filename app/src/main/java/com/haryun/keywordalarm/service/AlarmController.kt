package com.haryun.keywordalarm.service

import android.media.MediaPlayer

object AlarmController {
    private var currentPlayer: MediaPlayer? = null

    fun isPlaying(): Boolean = try { currentPlayer?.isPlaying == true } catch (e: Exception) { false }

    fun register(player: MediaPlayer) {
        currentPlayer = player
    }

    fun stop() {
        currentPlayer?.let {
            try { if (it.isPlaying) { it.stop(); it.release() } } catch (e: Exception) {}
        }
        currentPlayer = null
        KeywordNotificationListener.instance?.stopAlarm()
    }

    fun clear() {
        currentPlayer = null
    }
}
