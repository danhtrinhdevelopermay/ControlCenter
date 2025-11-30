package com.example.controlcenter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject

object QuickSettingsManager {
    private const val PREFS_NAME = "quick_settings_prefs"
    private const val KEY_SELECTED_TILES = "selected_tiles"
    private const val KEY_APP_SHORTCUTS = "app_shortcuts"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAvailableSystemTiles(context: Context): List<QuickSettingTile> {
        return listOf(
            QuickSettingTile(
                QuickSettingTile.TILE_BLUETOOTH,
                "Bluetooth",
                R.drawable.ic_bluetooth,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_DO_NOT_DISTURB,
                "Tắt tiếng",
                R.drawable.ic_do_not_disturb,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_FLASHLIGHT,
                "Đèn Flash",
                R.drawable.ic_flashlight,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_ROTATION_LOCK,
                "Khóa hướng màn hình",
                R.drawable.ic_orientation_lock,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_SETTINGS,
                "Cài đặt",
                R.drawable.ic_settings,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_SCREEN_RECORD,
                "Quay phim Màn hình",
                R.drawable.ic_screen_recording,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_LOCATION,
                "Dịch vụ vị trí",
                R.drawable.ic_location,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_WIFI,
                "Wi-Fi",
                R.drawable.ic_wifi,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_MOBILE_DATA,
                "Dữ liệu di động",
                R.drawable.ic_cellular,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_AIRPLANE_MODE,
                "Chế độ máy bay",
                R.drawable.ic_airplane,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_HOTSPOT,
                "Điểm truy cập",
                R.drawable.ic_hotspot,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_NFC,
                "NFC",
                R.drawable.ic_nfc,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_BATTERY_SAVER,
                "Tiết kiệm Pin",
                R.drawable.ic_battery_saver,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_AUTO_BRIGHTNESS,
                "Độ sáng tự động",
                R.drawable.ic_auto_brightness,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_DARK_MODE,
                "Chế độ tối",
                R.drawable.ic_dark_mode,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_SCREENSHOT,
                "Chụp ảnh màn hình",
                R.drawable.ic_screenshot,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_CAMERA,
                "Máy ảnh",
                R.drawable.ic_camera,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_CALCULATOR,
                "Máy tính",
                R.drawable.ic_calculator,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_WALLET,
                "Ví",
                R.drawable.ic_wallet,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_QR_SCANNER,
                "Quét QR",
                R.drawable.ic_qr_scanner,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_EYE_COMFORT,
                "Bảo vệ mắt",
                R.drawable.ic_eye_comfort,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_SYNC,
                "Đồng bộ hóa",
                R.drawable.ic_sync,
                QuickSettingTile.TileType.SYSTEM
            ),
            QuickSettingTile(
                QuickSettingTile.TILE_INVERT_COLORS,
                "Đảo màu",
                R.drawable.ic_invert_colors,
                QuickSettingTile.TileType.SYSTEM
            )
        )
    }

    fun getDefaultSelectedTileIds(): List<String> {
        return listOf(
            QuickSettingTile.TILE_BLUETOOTH,
            QuickSettingTile.TILE_DO_NOT_DISTURB,
            QuickSettingTile.TILE_FLASHLIGHT,
            QuickSettingTile.TILE_ROTATION_LOCK
        )
    }

    fun getSelectedTileIds(context: Context): List<String> {
        val prefs = getPrefs(context)
        val savedJson = prefs.getString(KEY_SELECTED_TILES, null)
        return if (savedJson != null) {
            try {
                val jsonArray = JSONArray(savedJson)
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } catch (e: Exception) {
                getDefaultSelectedTileIds()
            }
        } else {
            getDefaultSelectedTileIds()
        }
    }

    fun setSelectedTileIds(context: Context, tileIds: List<String>) {
        val jsonArray = JSONArray()
        tileIds.forEach { jsonArray.put(it) }
        getPrefs(context).edit().putString(KEY_SELECTED_TILES, jsonArray.toString()).apply()
    }

    fun getSelectedTiles(context: Context): List<QuickSettingTile> {
        val selectedIds = getSelectedTileIds(context)
        val allSystemTiles = getAvailableSystemTiles(context)
        val appShortcuts = getAppShortcuts(context)

        val result = mutableListOf<QuickSettingTile>()
        selectedIds.forEach { id ->
            val systemTile = allSystemTiles.find { it.id == id }
            if (systemTile != null) {
                result.add(systemTile)
            } else {
                val appShortcut = appShortcuts.find { it.id == id }
                if (appShortcut != null) {
                    result.add(appShortcut)
                }
            }
        }
        return result
    }

    fun addTile(context: Context, tileId: String) {
        val currentTiles = getSelectedTileIds(context).toMutableList()
        if (!currentTiles.contains(tileId)) {
            currentTiles.add(tileId)
            setSelectedTileIds(context, currentTiles)
        }
    }

    fun removeTile(context: Context, tileId: String) {
        val currentTiles = getSelectedTileIds(context).toMutableList()
        currentTiles.remove(tileId)
        setSelectedTileIds(context, currentTiles)
    }

    fun moveTile(context: Context, fromPosition: Int, toPosition: Int) {
        val tiles = getSelectedTileIds(context).toMutableList()
        if (fromPosition < tiles.size && toPosition < tiles.size) {
            val tile = tiles.removeAt(fromPosition)
            tiles.add(toPosition, tile)
            setSelectedTileIds(context, tiles)
        }
    }

    fun saveAppShortcut(context: Context, packageName: String, activityName: String, appName: String) {
        val prefs = getPrefs(context)
        val savedJson = prefs.getString(KEY_APP_SHORTCUTS, "[]")
        val jsonArray = try {
            JSONArray(savedJson)
        } catch (e: Exception) {
            JSONArray()
        }

        val shortcutId = "app_$packageName"

        var exists = false
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("id") == shortcutId) {
                exists = true
                break
            }
        }

        if (!exists) {
            val newShortcut = JSONObject().apply {
                put("id", shortcutId)
                put("name", appName)
                put("packageName", packageName)
                put("activityName", activityName)
            }
            jsonArray.put(newShortcut)
            prefs.edit().putString(KEY_APP_SHORTCUTS, jsonArray.toString()).apply()
        }
    }

    fun removeAppShortcut(context: Context, shortcutId: String) {
        val prefs = getPrefs(context)
        val savedJson = prefs.getString(KEY_APP_SHORTCUTS, "[]")
        val jsonArray = try {
            JSONArray(savedJson)
        } catch (e: Exception) {
            return
        }

        val newArray = JSONArray()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("id") != shortcutId) {
                newArray.put(obj)
            }
        }
        prefs.edit().putString(KEY_APP_SHORTCUTS, newArray.toString()).apply()

        removeTile(context, shortcutId)
    }

    fun getAppShortcuts(context: Context): List<QuickSettingTile> {
        val prefs = getPrefs(context)
        val savedJson = prefs.getString(KEY_APP_SHORTCUTS, "[]")
        val jsonArray = try {
            JSONArray(savedJson)
        } catch (e: Exception) {
            return emptyList()
        }

        val pm = context.packageManager
        return (0 until jsonArray.length()).mapNotNull { i ->
            try {
                val obj = jsonArray.getJSONObject(i)
                val packageName = obj.getString("packageName")
                val activityName = obj.getString("activityName")
                val appName = obj.getString("name")
                val shortcutId = obj.getString("id")

                val appIcon = try {
                    pm.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    null
                }

                QuickSettingTile(
                    id = shortcutId,
                    name = appName,
                    iconResId = R.drawable.ic_grid,
                    type = QuickSettingTile.TileType.APP_SHORTCUT,
                    packageName = packageName,
                    activityName = activityName,
                    appIcon = appIcon
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun getInstalledApps(context: Context): List<ResolveInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }
}
