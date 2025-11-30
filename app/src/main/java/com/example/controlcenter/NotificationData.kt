package com.example.controlcenter

import android.app.PendingIntent
import android.app.RemoteInput
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class NotificationAction(
    val title: String,
    val actionIntent: PendingIntent?,
    val remoteInputs: Array<RemoteInput>? = null,
    val isReplyAction: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NotificationAction

        if (title != other.title) return false
        if (actionIntent != other.actionIntent) return false
        if (remoteInputs != null) {
            if (other.remoteInputs == null) return false
            if (!remoteInputs.contentEquals(other.remoteInputs)) return false
        } else if (other.remoteInputs != null) return false
        if (isReplyAction != other.isReplyAction) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (actionIntent?.hashCode() ?: 0)
        result = 31 * result + (remoteInputs?.contentHashCode() ?: 0)
        result = 31 * result + isReplyAction.hashCode()
        return result
    }
}

data class NotificationData(
    val id: Int,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val time: Long,
    val icon: Drawable?,
    val largeIcon: Bitmap? = null,
    val key: String? = null,
    val contentIntent: PendingIntent? = null,
    val actions: List<NotificationAction> = emptyList()
)
