package com.example.controlcenter

import android.content.Context
import android.graphics.Color

object AppearanceSettings {
    private const val PREFS_NAME = "appearance_settings"
    
    private const val KEY_BUTTON_COLOR = "button_color"
    private const val KEY_BUTTON_OPACITY = "button_opacity"
    private const val KEY_BUTTON_ACTIVE_COLOR = "button_active_color"
    private const val KEY_BUTTON_ACTIVE_OPACITY = "button_active_opacity"
    
    private const val KEY_TOGGLE_COLOR = "toggle_color"
    private const val KEY_TOGGLE_OPACITY = "toggle_opacity"
    private const val KEY_TOGGLE_ACTIVE_COLOR = "toggle_active_color"
    private const val KEY_TOGGLE_ACTIVE_OPACITY = "toggle_active_opacity"
    
    private const val KEY_PLAYER_COLOR = "player_color"
    private const val KEY_PLAYER_OPACITY = "player_opacity"
    
    private const val KEY_SLIDER_COLOR = "slider_color"
    private const val KEY_SLIDER_OPACITY = "slider_opacity"
    private const val KEY_SLIDER_FILL_COLOR = "slider_fill_color"
    private const val KEY_SLIDER_FILL_OPACITY = "slider_fill_opacity"
    
    private const val KEY_PANEL_COLOR = "panel_color"
    private const val KEY_PANEL_OPACITY = "panel_opacity"
    
    private const val KEY_NOTIFICATION_COLOR = "notification_color"
    private const val KEY_NOTIFICATION_OPACITY = "notification_opacity"
    private const val KEY_NOTIFICATION_HEADER_COLOR = "notification_header_color"
    private const val KEY_NOTIFICATION_HEADER_OPACITY = "notification_header_opacity"
    
    private const val DEFAULT_BUTTON_COLOR = 0x505050
    private const val DEFAULT_BUTTON_OPACITY = 80
    private const val DEFAULT_BUTTON_ACTIVE_COLOR = 0x007AFF
    private const val DEFAULT_BUTTON_ACTIVE_OPACITY = 100
    
    private const val DEFAULT_TOGGLE_COLOR = 0x535358
    private const val DEFAULT_TOGGLE_OPACITY = 65
    private const val DEFAULT_TOGGLE_ACTIVE_COLOR = 0x007AFF
    private const val DEFAULT_TOGGLE_ACTIVE_OPACITY = 100
    
    private const val DEFAULT_PLAYER_COLOR = 0x535358
    private const val DEFAULT_PLAYER_OPACITY = 65
    
    private const val DEFAULT_SLIDER_COLOR = 0x535358
    private const val DEFAULT_SLIDER_OPACITY = 65
    private const val DEFAULT_SLIDER_FILL_COLOR = 0xFFFFFF
    private const val DEFAULT_SLIDER_FILL_OPACITY = 100
    
    private const val DEFAULT_PANEL_COLOR = 0x000000
    private const val DEFAULT_PANEL_OPACITY = 0
    
    private const val DEFAULT_NOTIFICATION_COLOR = 0x2C2C2E
    private const val DEFAULT_NOTIFICATION_OPACITY = 90
    private const val DEFAULT_NOTIFICATION_HEADER_COLOR = 0xFFFFFF
    private const val DEFAULT_NOTIFICATION_HEADER_OPACITY = 100
    
    private fun getPrefs(context: Context) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun getButtonColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_BUTTON_COLOR, DEFAULT_BUTTON_COLOR)
    
    fun setButtonColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_BUTTON_COLOR, color).apply()
    }
    
    fun getButtonOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_BUTTON_OPACITY, DEFAULT_BUTTON_OPACITY)
    
    fun setButtonOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_BUTTON_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getButtonActiveColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_BUTTON_ACTIVE_COLOR, DEFAULT_BUTTON_ACTIVE_COLOR)
    
    fun setButtonActiveColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_BUTTON_ACTIVE_COLOR, color).apply()
    }
    
    fun getButtonActiveOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_BUTTON_ACTIVE_OPACITY, DEFAULT_BUTTON_ACTIVE_OPACITY)
    
    fun setButtonActiveOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_BUTTON_ACTIVE_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getToggleColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_TOGGLE_COLOR, DEFAULT_TOGGLE_COLOR)
    
    fun setToggleColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_TOGGLE_COLOR, color).apply()
    }
    
    fun getToggleOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_TOGGLE_OPACITY, DEFAULT_TOGGLE_OPACITY)
    
    fun setToggleOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_TOGGLE_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getToggleActiveColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_TOGGLE_ACTIVE_COLOR, DEFAULT_TOGGLE_ACTIVE_COLOR)
    
    fun setToggleActiveColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_TOGGLE_ACTIVE_COLOR, color).apply()
    }
    
    fun getToggleActiveOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_TOGGLE_ACTIVE_OPACITY, DEFAULT_TOGGLE_ACTIVE_OPACITY)
    
    fun setToggleActiveOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_TOGGLE_ACTIVE_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getPlayerColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_PLAYER_COLOR, DEFAULT_PLAYER_COLOR)
    
    fun setPlayerColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_PLAYER_COLOR, color).apply()
    }
    
    fun getPlayerOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_PLAYER_OPACITY, DEFAULT_PLAYER_OPACITY)
    
    fun setPlayerOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_PLAYER_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getSliderColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_SLIDER_COLOR, DEFAULT_SLIDER_COLOR)
    
    fun setSliderColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_SLIDER_COLOR, color).apply()
    }
    
    fun getSliderOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_SLIDER_OPACITY, DEFAULT_SLIDER_OPACITY)
    
    fun setSliderOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_SLIDER_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getSliderFillColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_SLIDER_FILL_COLOR, DEFAULT_SLIDER_FILL_COLOR)
    
    fun setSliderFillColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_SLIDER_FILL_COLOR, color).apply()
    }
    
    fun getSliderFillOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_SLIDER_FILL_OPACITY, DEFAULT_SLIDER_FILL_OPACITY)
    
    fun setSliderFillOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_SLIDER_FILL_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getPanelColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_PANEL_COLOR, DEFAULT_PANEL_COLOR)
    
    fun setPanelColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_PANEL_COLOR, color).apply()
    }
    
    fun getPanelOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_PANEL_OPACITY, DEFAULT_PANEL_OPACITY)
    
    fun setPanelOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_PANEL_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getButtonColorWithOpacity(context: Context, isActive: Boolean = false): Int {
        return if (isActive) {
            val color = getButtonActiveColor(context)
            val opacity = getButtonActiveOpacity(context)
            colorWithOpacity(color, opacity)
        } else {
            val color = getButtonColor(context)
            val opacity = getButtonOpacity(context)
            colorWithOpacity(color, opacity)
        }
    }
    
    fun getToggleColorWithOpacity(context: Context, isActive: Boolean = false): Int {
        return if (isActive) {
            val color = getToggleActiveColor(context)
            val opacity = getToggleActiveOpacity(context)
            colorWithOpacity(color, opacity)
        } else {
            val color = getToggleColor(context)
            val opacity = getToggleOpacity(context)
            colorWithOpacity(color, opacity)
        }
    }
    
    fun getPlayerColorWithOpacity(context: Context): Int {
        val color = getPlayerColor(context)
        val opacity = getPlayerOpacity(context)
        return colorWithOpacity(color, opacity)
    }
    
    fun getSliderColorWithOpacity(context: Context): Int {
        val color = getSliderColor(context)
        val opacity = getSliderOpacity(context)
        return colorWithOpacity(color, opacity)
    }
    
    fun getSliderFillColorWithOpacity(context: Context): Int {
        val color = getSliderFillColor(context)
        val opacity = getSliderFillOpacity(context)
        return colorWithOpacity(color, opacity)
    }
    
    fun getPanelColorWithOpacity(context: Context): Int {
        val color = getPanelColor(context)
        val opacity = getPanelOpacity(context)
        return colorWithOpacity(color, opacity)
    }
    
    fun getNotificationColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_NOTIFICATION_COLOR, DEFAULT_NOTIFICATION_COLOR)
    
    fun setNotificationColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_NOTIFICATION_COLOR, color).apply()
    }
    
    fun getNotificationOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_NOTIFICATION_OPACITY, DEFAULT_NOTIFICATION_OPACITY)
    
    fun setNotificationOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_NOTIFICATION_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getNotificationHeaderColor(context: Context): Int = 
        getPrefs(context).getInt(KEY_NOTIFICATION_HEADER_COLOR, DEFAULT_NOTIFICATION_HEADER_COLOR)
    
    fun setNotificationHeaderColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_NOTIFICATION_HEADER_COLOR, color).apply()
    }
    
    fun getNotificationHeaderOpacity(context: Context): Int = 
        getPrefs(context).getInt(KEY_NOTIFICATION_HEADER_OPACITY, DEFAULT_NOTIFICATION_HEADER_OPACITY)
    
    fun setNotificationHeaderOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_NOTIFICATION_HEADER_OPACITY, opacity.coerceIn(0, 100)).apply()
    }
    
    fun getNotificationColorWithOpacity(context: Context): Int {
        val color = getNotificationColor(context)
        val opacity = getNotificationOpacity(context)
        return colorWithOpacity(color, opacity)
    }
    
    fun getNotificationHeaderColorWithOpacity(context: Context): Int {
        val color = getNotificationHeaderColor(context)
        val opacity = getNotificationHeaderOpacity(context)
        return colorWithOpacity(color, opacity)
    }
    
    private fun colorWithOpacity(color: Int, opacity: Int): Int {
        val alpha = (opacity * 255 / 100).coerceIn(0, 255)
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return Color.argb(alpha, r, g, b)
    }
    
    fun resetToDefaults(context: Context) {
        getPrefs(context).edit().apply {
            putInt(KEY_BUTTON_COLOR, DEFAULT_BUTTON_COLOR)
            putInt(KEY_BUTTON_OPACITY, DEFAULT_BUTTON_OPACITY)
            putInt(KEY_BUTTON_ACTIVE_COLOR, DEFAULT_BUTTON_ACTIVE_COLOR)
            putInt(KEY_BUTTON_ACTIVE_OPACITY, DEFAULT_BUTTON_ACTIVE_OPACITY)
            putInt(KEY_TOGGLE_COLOR, DEFAULT_TOGGLE_COLOR)
            putInt(KEY_TOGGLE_OPACITY, DEFAULT_TOGGLE_OPACITY)
            putInt(KEY_TOGGLE_ACTIVE_COLOR, DEFAULT_TOGGLE_ACTIVE_COLOR)
            putInt(KEY_TOGGLE_ACTIVE_OPACITY, DEFAULT_TOGGLE_ACTIVE_OPACITY)
            putInt(KEY_PLAYER_COLOR, DEFAULT_PLAYER_COLOR)
            putInt(KEY_PLAYER_OPACITY, DEFAULT_PLAYER_OPACITY)
            putInt(KEY_SLIDER_COLOR, DEFAULT_SLIDER_COLOR)
            putInt(KEY_SLIDER_OPACITY, DEFAULT_SLIDER_OPACITY)
            putInt(KEY_SLIDER_FILL_COLOR, DEFAULT_SLIDER_FILL_COLOR)
            putInt(KEY_SLIDER_FILL_OPACITY, DEFAULT_SLIDER_FILL_OPACITY)
            putInt(KEY_PANEL_COLOR, DEFAULT_PANEL_COLOR)
            putInt(KEY_PANEL_OPACITY, DEFAULT_PANEL_OPACITY)
            putInt(KEY_NOTIFICATION_COLOR, DEFAULT_NOTIFICATION_COLOR)
            putInt(KEY_NOTIFICATION_OPACITY, DEFAULT_NOTIFICATION_OPACITY)
            putInt(KEY_NOTIFICATION_HEADER_COLOR, DEFAULT_NOTIFICATION_HEADER_COLOR)
            putInt(KEY_NOTIFICATION_HEADER_OPACITY, DEFAULT_NOTIFICATION_HEADER_OPACITY)
            apply()
        }
    }
    
    fun colorToHex(color: Int): String {
        return String.format("#%06X", color and 0xFFFFFF)
    }
    
    fun hexToColor(hex: String): Int {
        return try {
            Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") and 0xFFFFFF
        } catch (e: Exception) {
            DEFAULT_BUTTON_COLOR
        }
    }
}
