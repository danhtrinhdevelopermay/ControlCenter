package com.example.controlcenter

import android.graphics.drawable.Drawable

data class QuickSettingTile(
    val id: String,
    val name: String,
    val iconResId: Int,
    val type: TileType,
    var isActive: Boolean = false,
    var packageName: String? = null,
    var activityName: String? = null,
    var appIcon: Drawable? = null
) {
    enum class TileType {
        SYSTEM,
        APP_SHORTCUT
    }

    companion object {
        const val TILE_WIFI = "wifi"
        const val TILE_BLUETOOTH = "bluetooth"
        const val TILE_MOBILE_DATA = "mobile_data"
        const val TILE_FLASHLIGHT = "flashlight"
        const val TILE_ROTATION_LOCK = "rotation_lock"
        const val TILE_DO_NOT_DISTURB = "do_not_disturb"
        const val TILE_AIRPLANE_MODE = "airplane_mode"
        const val TILE_LOCATION = "location"
        const val TILE_HOTSPOT = "hotspot"
        const val TILE_NFC = "nfc"
        const val TILE_BATTERY_SAVER = "battery_saver"
        const val TILE_AUTO_BRIGHTNESS = "auto_brightness"
        const val TILE_SCREEN_RECORD = "screen_record"
        const val TILE_SCREENSHOT = "screenshot"
        const val TILE_CAMERA = "camera"
        const val TILE_CALCULATOR = "calculator"
        const val TILE_SETTINGS = "settings"
        const val TILE_DARK_MODE = "dark_mode"
        const val TILE_SYNC = "sync"
        const val TILE_SCREEN_TIMEOUT = "screen_timeout"
        const val TILE_EYE_COMFORT = "eye_comfort"
        const val TILE_WALLET = "wallet"
        const val TILE_QR_SCANNER = "qr_scanner"
        const val TILE_INVERT_COLORS = "invert_colors"
    }
}
