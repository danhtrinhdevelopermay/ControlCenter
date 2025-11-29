package com.example.controlcenter

import android.content.Context
import android.content.SharedPreferences

object NotificationZoneSettings {
    private const val PREFS_NAME = "notification_zone_prefs"
    private const val KEY_ZONE_X_PERCENT = "zone_x_percent"
    private const val KEY_ZONE_WIDTH_PERCENT = "zone_width_percent"
    private const val KEY_ZONE_HEIGHT = "zone_height"
    private const val KEY_ENABLED = "notification_zone_enabled"

    private const val DEFAULT_X_PERCENT = 0
    private const val DEFAULT_WIDTH_PERCENT = 34
    private const val DEFAULT_HEIGHT = 100
    
    const val MIN_WIDTH_PERCENT = 10
    const val MIN_HEIGHT = 50

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getZoneXPercent(context: Context): Int {
        return getPrefs(context).getInt(KEY_ZONE_X_PERCENT, DEFAULT_X_PERCENT)
    }

    fun setZoneXPercent(context: Context, percent: Int) {
        getPrefs(context).edit().putInt(KEY_ZONE_X_PERCENT, percent).apply()
    }

    fun getZoneWidthPercent(context: Context): Int {
        return getPrefs(context).getInt(KEY_ZONE_WIDTH_PERCENT, DEFAULT_WIDTH_PERCENT).coerceAtLeast(MIN_WIDTH_PERCENT)
    }

    fun setZoneWidthPercent(context: Context, percent: Int) {
        getPrefs(context).edit().putInt(KEY_ZONE_WIDTH_PERCENT, percent.coerceAtLeast(MIN_WIDTH_PERCENT)).apply()
    }

    fun getZoneHeight(context: Context): Int {
        return getPrefs(context).getInt(KEY_ZONE_HEIGHT, DEFAULT_HEIGHT).coerceAtLeast(MIN_HEIGHT)
    }

    fun setZoneHeight(context: Context, height: Int) {
        getPrefs(context).edit().putInt(KEY_ZONE_HEIGHT, height.coerceAtLeast(MIN_HEIGHT)).apply()
    }

    fun resetToDefaults(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_ZONE_X_PERCENT, DEFAULT_X_PERCENT)
            .putInt(KEY_ZONE_WIDTH_PERCENT, DEFAULT_WIDTH_PERCENT)
            .putInt(KEY_ZONE_HEIGHT, DEFAULT_HEIGHT)
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }
}
