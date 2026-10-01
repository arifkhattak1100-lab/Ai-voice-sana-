package com.example.tools

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.example.data.model.ToolCallResult

class SanaMediaTools(private val context: Context) {

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun sendMediaKeyEvent(keyCode: Int): Boolean {
        return try {
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager?.dispatchMediaKeyEvent(eventDown)
            audioManager?.dispatchMediaKeyEvent(eventUp)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun mediaPlay(): ToolCallResult {
        val success = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY) ||
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        return ToolCallResult("mediaPlay", "Media", success, if (success) "Resuming your music, Boss! 🎵" else "Could not play media, Boss.")
    }

    fun mediaPause(): ToolCallResult {
        val success = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE) ||
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        return ToolCallResult("mediaPause", "Media", success, if (success) "Music paused for you, Boss. ⏸️" else "Could not pause media, Boss.")
    }

    fun mediaNext(): ToolCallResult {
        val success = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
        return ToolCallResult("mediaNext", "Media", success, if (success) "Skipped to next track, Boss! ⏭️" else "Could not skip track, Boss.")
    }

    fun mediaPrevious(): ToolCallResult {
        val success = sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        return ToolCallResult("mediaPrevious", "Media", success, if (success) "Back to previous track, Boss! ⏮️" else "Could not go back, Boss.")
    }
}
