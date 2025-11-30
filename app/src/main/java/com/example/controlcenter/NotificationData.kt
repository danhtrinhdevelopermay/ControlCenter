package com.example.controlcenter

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class NotificationAction(
    val title: String,
    val actionIntent: PendingIntent?
)

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
