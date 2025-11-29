package com.example.controlcenter.shortcuts

import android.graphics.drawable.Drawable

enum class ShortcutType {
    SYSTEM,
    APP
}

data class ShortcutItem(
    val id: String,
    val type: ShortcutType,
    val label: String,
    val iconResId: Int? = null,
    val iconDrawable: Drawable? = null,
    val packageName: String? = null,
    val activityName: String? = null,
    val action: String? = null,
    var isActive: Boolean = false,
    var order: Int = 0
)
