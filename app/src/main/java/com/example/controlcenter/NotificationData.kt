package com.example.controlcenter

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class NotificationData(
    val id: Int,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val time: Long,
    val icon: Drawable?,
    val largeIcon: Bitmap? = null
)
