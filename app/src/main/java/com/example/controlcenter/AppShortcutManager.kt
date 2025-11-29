package com.example.controlcenter

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

object AppShortcutManager {
    
    private const val TAG = "AppShortcutManager"
    private const val PREFS_NAME = "app_shortcuts_prefs"
    private const val KEY_SHORTCUTS = "saved_shortcuts"
    private const val MAX_SHORTCUTS = 8
    
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = mutableListOf<AppInfo>()
        
        try {
            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            
            val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            
            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == context.packageName) continue
                
                val appName = resolveInfo.loadLabel(pm).toString()
                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }
                
                apps.add(AppInfo(packageName, appName, icon))
            }
            
            apps.sortBy { it.appName.lowercase() }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps", e)
        }
        
        return apps
    }
    
    fun getSavedShortcuts(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shortcutsString = prefs.getString(KEY_SHORTCUTS, "") ?: ""
        return if (shortcutsString.isEmpty()) {
            emptyList()
        } else {
            shortcutsString.split(",").filter { it.isNotEmpty() }
        }
    }
    
    fun saveShortcuts(context: Context, shortcuts: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shortcutsString = shortcuts.take(MAX_SHORTCUTS).joinToString(",")
        prefs.edit().putString(KEY_SHORTCUTS, shortcutsString).apply()
    }
    
    fun addShortcut(context: Context, packageName: String): Boolean {
        val current = getSavedShortcuts(context).toMutableList()
        if (current.contains(packageName)) {
            return false
        }
        if (current.size >= MAX_SHORTCUTS) {
            return false
        }
        current.add(packageName)
        saveShortcuts(context, current)
        return true
    }
    
    fun removeShortcut(context: Context, packageName: String) {
        val current = getSavedShortcuts(context).toMutableList()
        current.remove(packageName)
        saveShortcuts(context, current)
    }
    
    fun isShortcutSaved(context: Context, packageName: String): Boolean {
        return getSavedShortcuts(context).contains(packageName)
    }
    
    fun getAppInfo(context: Context, packageName: String): AppInfo? {
        val pm = context.packageManager
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val appName = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            AppInfo(packageName, appName, icon)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app info for $packageName", e)
            null
        }
    }
    
    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                Log.e(TAG, "No launch intent for $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app $packageName", e)
            false
        }
    }
    
    fun getMaxShortcuts(): Int = MAX_SHORTCUTS
}
