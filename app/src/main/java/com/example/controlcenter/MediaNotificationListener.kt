package com.example.controlcenter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaNotificationListener : NotificationListenerService() {
    
    companion object {
        private const val TAG = "MediaNotificationListener"
        
        var instance: MediaNotificationListener? = null
            private set
        
        var currentMediaInfo: MediaInfo? = null
            private set
        
        private var mediaSessionManager: MediaSessionManager? = null
        private var activeController: MediaController? = null
        private var mediaCallback: MediaController.Callback? = null
        private var onMediaChangedListener: ((MediaInfo?) -> Unit)? = null
        private var onNotificationChangedListener: ((List<StatusBarNotification>) -> Unit)? = null
        
        private var cachedNotifications: List<StatusBarNotification> = emptyList()
        
        fun setOnMediaChangedListener(listener: ((MediaInfo?) -> Unit)?) {
            onMediaChangedListener = listener
        }
        
        fun setOnNotificationChangedListener(listener: ((List<StatusBarNotification>) -> Unit)?) {
            onNotificationChangedListener = listener
        }
        
        fun getCachedNotifications(): List<StatusBarNotification> {
            return cachedNotifications
        }
        
        fun isNotificationAccessEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat != null && flat.contains(packageName)
        }
        
        fun openNotificationAccessSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening notification settings", e)
            }
        }
        
        fun initMediaSessionManager(context: Context) {
            if (!isNotificationAccessEnabled(context)) {
                Log.w(TAG, "Notification access not enabled")
                return
            }
            
            try {
                mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                updateActiveMediaSession(context)
                
                mediaSessionManager?.addOnActiveSessionsChangedListener(
                    { controllers ->
                        updateActiveMediaSession(context)
                    },
                    ComponentName(context, MediaNotificationListener::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing media session manager", e)
            }
        }
        
        private fun updateActiveMediaSession(context: Context) {
            try {
                val controllers = mediaSessionManager?.getActiveSessions(
                    ComponentName(context, MediaNotificationListener::class.java)
                )
                
                if (controllers.isNullOrEmpty()) {
                    currentMediaInfo = null
                    onMediaChangedListener?.invoke(null)
                    return
                }
                
                val controller = controllers.firstOrNull { ctrl ->
                    ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
                } ?: controllers.first()
                
                if (activeController?.packageName != controller.packageName) {
                    activeController?.let { oldController ->
                        mediaCallback?.let { callback ->
                            oldController.unregisterCallback(callback)
                        }
                    }
                    
                    activeController = controller
                    
                    mediaCallback = object : MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            updateMediaInfo(controller)
                        }
                        
                        override fun onMetadataChanged(metadata: MediaMetadata?) {
                            updateMediaInfo(controller)
                        }
                    }
                    
                    controller.registerCallback(mediaCallback!!)
                }
                
                updateMediaInfo(controller)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating active media session", e)
            }
        }
        
        private fun updateMediaInfo(controller: MediaController) {
            try {
                val metadata = controller.metadata
                val playbackState = controller.playbackState
                
                if (metadata == null) {
                    currentMediaInfo = null
                    onMediaChangedListener?.invoke(null)
                    return
                }
                
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: "Unknown"
                
                val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: ""
                
                val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                
                val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                
                val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
                
                val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                val position = playbackState?.position ?: 0L
                
                currentMediaInfo = MediaInfo(
                    title = title,
                    artist = artist,
                    album = album,
                    albumArt = albumArt,
                    isPlaying = isPlaying,
                    duration = duration,
                    position = position,
                    packageName = controller.packageName ?: ""
                )
                
                onMediaChangedListener?.invoke(currentMediaInfo)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating media info", e)
            }
        }
        
        fun refreshMediaInfo(context: Context) {
            if (isNotificationAccessEnabled(context)) {
                updateActiveMediaSession(context)
            }
        }
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        instance = this
        initMediaSessionManager(this)
        updateCachedNotifications()
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        instance = null
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshMediaInfo(this)
        updateCachedNotifications()
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshMediaInfo(this)
        updateCachedNotifications()
    }
    
    private fun updateCachedNotifications() {
        try {
            cachedNotifications = activeNotifications?.toList() ?: emptyList()
            onNotificationChangedListener?.invoke(cachedNotifications)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating cached notifications", e)
        }
    }
    
    fun refreshNotifications() {
        updateCachedNotifications()
    }
}

data class MediaInfo(
    val title: String,
    val artist: String,
    val album: String,
    val albumArt: Bitmap?,
    val isPlaying: Boolean,
    val duration: Long,
    val position: Long,
    val packageName: String
)
