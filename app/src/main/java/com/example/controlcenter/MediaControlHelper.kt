package com.example.controlcenter

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent

object MediaControlHelper {
    
    private const val TAG = "MediaControlHelper"
    
    private fun sendMediaButtonEvent(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            Log.d(TAG, "Sent media button event: $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending media button event", e)
        }
    }
    
    fun playPause(context: Context) {
        sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }
    
    fun next(context: Context) {
        sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }
    
    fun previous(context: Context) {
        sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }
    
    fun isMusicPlaying(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isMusicActive
        } catch (e: Exception) {
            Log.e(TAG, "Error checking music state", e)
            false
        }
    }
}
