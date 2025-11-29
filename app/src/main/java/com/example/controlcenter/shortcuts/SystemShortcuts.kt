package com.example.controlcenter.shortcuts

import com.example.controlcenter.R

object SystemShortcuts {
    
    const val ACTION_AIRPLANE_MODE = "airplane_mode"
    const val ACTION_SCREEN_LOCK = "screen_lock"
    const val ACTION_SCREENSHOT = "screenshot"
    const val ACTION_BATTERY_SAVER = "battery_saver"
    const val ACTION_DND = "do_not_disturb"
    const val ACTION_DARK_MODE = "dark_mode"
    const val ACTION_READING_MODE = "reading_mode"
    const val ACTION_VIBRATE = "vibrate_mode"
    const val ACTION_HOTSPOT = "hotspot"
    const val ACTION_FLOATING_WINDOW = "floating_window"
    const val ACTION_SCREEN_RECORDER = "screen_recorder"
    const val ACTION_NFC = "nfc"
    const val ACTION_AUTO_BRIGHTNESS = "auto_brightness"
    const val ACTION_SYNC = "sync"
    const val ACTION_POWER_MENU = "power_menu"
    
    fun getSystemShortcuts(): List<ShortcutItem> {
        return listOf(
            ShortcutItem(
                id = ACTION_AIRPLANE_MODE,
                type = ShortcutType.SYSTEM,
                label = "Chế độ máy bay",
                iconResId = R.drawable.ic_airplane,
                action = ACTION_AIRPLANE_MODE
            ),
            ShortcutItem(
                id = ACTION_SCREEN_LOCK,
                type = ShortcutType.SYSTEM,
                label = "Màn hình khóa",
                iconResId = R.drawable.ic_lock,
                action = ACTION_SCREEN_LOCK
            ),
            ShortcutItem(
                id = ACTION_SCREENSHOT,
                type = ShortcutType.SYSTEM,
                label = "Chụp ảnh màn hình",
                iconResId = R.drawable.ic_screenshot,
                action = ACTION_SCREENSHOT
            ),
            ShortcutItem(
                id = ACTION_BATTERY_SAVER,
                type = ShortcutType.SYSTEM,
                label = "Tiết kiệm Pin",
                iconResId = R.drawable.ic_battery_saver,
                action = ACTION_BATTERY_SAVER
            ),
            ShortcutItem(
                id = ACTION_DND,
                type = ShortcutType.SYSTEM,
                label = "Không làm phiền",
                iconResId = R.drawable.ic_dnd,
                action = ACTION_DND
            ),
            ShortcutItem(
                id = ACTION_DARK_MODE,
                type = ShortcutType.SYSTEM,
                label = "Chế độ nền tối",
                iconResId = R.drawable.ic_dark_mode,
                action = ACTION_DARK_MODE
            ),
            ShortcutItem(
                id = ACTION_READING_MODE,
                type = ShortcutType.SYSTEM,
                label = "Chế độ đọc sách",
                iconResId = R.drawable.ic_reading_mode,
                action = ACTION_READING_MODE
            ),
            ShortcutItem(
                id = ACTION_VIBRATE,
                type = ShortcutType.SYSTEM,
                label = "Rung",
                iconResId = R.drawable.ic_vibrate,
                action = ACTION_VIBRATE
            ),
            ShortcutItem(
                id = ACTION_HOTSPOT,
                type = ShortcutType.SYSTEM,
                label = "Điểm phát sóng",
                iconResId = R.drawable.ic_hotspot,
                action = ACTION_HOTSPOT
            ),
            ShortcutItem(
                id = ACTION_FLOATING_WINDOW,
                type = ShortcutType.SYSTEM,
                label = "Cửa sổ nổi",
                iconResId = R.drawable.ic_floating_window,
                action = ACTION_FLOATING_WINDOW
            ),
            ShortcutItem(
                id = ACTION_SCREEN_RECORDER,
                type = ShortcutType.SYSTEM,
                label = "Quay màn hình",
                iconResId = R.drawable.ic_video,
                action = ACTION_SCREEN_RECORDER
            ),
            ShortcutItem(
                id = ACTION_NFC,
                type = ShortcutType.SYSTEM,
                label = "NFC",
                iconResId = R.drawable.ic_nfc,
                action = ACTION_NFC
            ),
            ShortcutItem(
                id = ACTION_AUTO_BRIGHTNESS,
                type = ShortcutType.SYSTEM,
                label = "Độ sáng tự động",
                iconResId = R.drawable.ic_auto_brightness,
                action = ACTION_AUTO_BRIGHTNESS
            ),
            ShortcutItem(
                id = ACTION_SYNC,
                type = ShortcutType.SYSTEM,
                label = "Đồng bộ",
                iconResId = R.drawable.ic_sync,
                action = ACTION_SYNC
            )
        )
    }
}
