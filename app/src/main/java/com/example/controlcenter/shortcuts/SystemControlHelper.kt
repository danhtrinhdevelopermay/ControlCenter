package com.example.controlcenter.shortcuts

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

object SystemControlHelper {

    fun executeSystemShortcut(context: Context, action: String): Boolean {
        return try {
            when (action) {
                SystemShortcuts.ACTION_AIRPLANE_MODE -> {
                    openSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                    true
                }
                SystemShortcuts.ACTION_SCREEN_LOCK -> {
                    lockScreen(context)
                    true
                }
                SystemShortcuts.ACTION_SCREENSHOT -> {
                    Toast.makeText(context, "Chức năng chụp ảnh màn hình", Toast.LENGTH_SHORT).show()
                    true
                }
                SystemShortcuts.ACTION_BATTERY_SAVER -> {
                    openSettings(context, Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    true
                }
                SystemShortcuts.ACTION_DND -> {
                    toggleDoNotDisturb(context)
                    true
                }
                SystemShortcuts.ACTION_DARK_MODE -> {
                    openSettings(context, Settings.ACTION_DISPLAY_SETTINGS)
                    true
                }
                SystemShortcuts.ACTION_READING_MODE -> {
                    openSettings(context, Settings.ACTION_DISPLAY_SETTINGS)
                    true
                }
                SystemShortcuts.ACTION_VIBRATE -> {
                    toggleVibrate(context)
                    true
                }
                SystemShortcuts.ACTION_HOTSPOT -> {
                    openHotspotSettings(context)
                    true
                }
                SystemShortcuts.ACTION_FLOATING_WINDOW -> {
                    openSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    true
                }
                SystemShortcuts.ACTION_SCREEN_RECORDER -> {
                    Toast.makeText(context, "Chức năng quay màn hình", Toast.LENGTH_SHORT).show()
                    true
                }
                SystemShortcuts.ACTION_NFC -> {
                    openSettings(context, Settings.ACTION_NFC_SETTINGS)
                    true
                }
                SystemShortcuts.ACTION_AUTO_BRIGHTNESS -> {
                    toggleAutoBrightness(context)
                    true
                }
                SystemShortcuts.ACTION_SYNC -> {
                    openSettings(context, Settings.ACTION_SYNC_SETTINGS)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun launchApp(context: Context, packageName: String, activityName: String?): Boolean {
        return try {
            val intent = if (activityName != null) {
                Intent().apply {
                    component = ComponentName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            
            intent?.let {
                context.startActivity(it)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun openSettings(context: Context, action: String) {
        try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun lockScreen(context: Context) {
        try {
            val intent = Intent("android.app.action.LOCK_DEVICE")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cần quyền Device Admin để khóa màn hình", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleDoNotDisturb(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        val currentFilter = notificationManager.currentInterruptionFilter
        val newFilter = if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        notificationManager.setInterruptionFilter(newFilter)
        
        val message = if (newFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            "Đã tắt Không làm phiền"
        } else {
            "Đã bật Không làm phiền"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleVibrate(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentMode = audioManager.ringerMode
        
        val newMode = when (currentMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        
        audioManager.ringerMode = newMode
        
        val message = when (newMode) {
            AudioManager.RINGER_MODE_NORMAL -> "Chế độ chuông"
            AudioManager.RINGER_MODE_VIBRATE -> "Chế độ rung"
            else -> "Chế độ im lặng"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun openHotspotSettings(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setClassName("com.android.settings", "com.android.settings.TetherSettings")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openSettings(context, Settings.ACTION_WIRELESS_SETTINGS)
        }
    }

    private fun toggleAutoBrightness(context: Context) {
        try {
            if (Settings.System.canWrite(context)) {
                val currentMode = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE
                )
                val newMode = if (currentMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                } else {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                }
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    newMode
                )
                
                val message = if (newMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    "Đã bật độ sáng tự động"
                } else {
                    "Đã tắt độ sáng tự động"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } else {
                openSettings(context, Settings.ACTION_MANAGE_WRITE_SETTINGS)
            }
        } catch (e: Exception) {
            openSettings(context, Settings.ACTION_DISPLAY_SETTINGS)
        }
    }
}
