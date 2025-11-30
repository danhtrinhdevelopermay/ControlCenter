package com.example.controlcenter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast

object SystemControlHelper {
    
    private const val TAG = "SystemControlHelper"
    private var flashlightOn = false
    private var cameraId: String? = null
    
    fun toggleFlashlight(context: Context, enable: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            
            if (cameraId == null) {
                val cameraIdList = cameraManager.cameraIdList
                cameraId = cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }
            
            cameraId?.let {
                cameraManager.setTorchMode(it, enable)
                flashlightOn = enable
                true
            } ?: false
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error toggling flashlight", e)
            false
        }
    }
    
    fun isFlashlightOn(): Boolean = flashlightOn
    
    fun openCamera(context: Context) {
        try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            Toast.makeText(context, "Cannot open camera", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun openTimer(context: Context) {
        try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening timer", e)
            Toast.makeText(context, "Cannot open timer", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun openScreenMirroring(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val intent = Intent(Settings.ACTION_CAST_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening screen mirroring", e)
            Toast.makeText(context, "Cannot open screen mirroring", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun openFocusSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening focus settings", e)
            Toast.makeText(context, "Cannot open focus settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi state", e)
            false
        }
    }
    
    fun getWifiSSID(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                return null
            }
            
            var ssid: String? = null
            
            // Use ConnectivityManager for Android 10+ (more reliable)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetwork
                if (network != null) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        // Try to get SSID from WifiInfo via NetworkCapabilities (Android 12+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val wifiInfo = capabilities.transportInfo as? WifiInfo
                            ssid = wifiInfo?.ssid
                        }
                    }
                }
            }
            
            // Fallback to legacy method
            if (ssid == null || ssid == "<unknown ssid>") {
                val wifiInfo = wifiManager.connectionInfo
                ssid = wifiInfo?.ssid
            }
            
            // Remove quotes if present
            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            
            // Return null if SSID is <unknown ssid> (not connected)
            if (ssid == "<unknown ssid>" || ssid.isNullOrBlank()) {
                return null
            }
            
            ssid
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi SSID", e)
            null
        }
    }
    
    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter?.isEnabled ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth state", e)
            false
        }
    }
    
    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON, 0
            ) != 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking airplane mode", e)
            false
        }
    }
    
    fun isDoNotDisturbOn(context: Context): Boolean {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
                as android.app.NotificationManager
            notificationManager.currentInterruptionFilter != 
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Exception) {
            Log.e(TAG, "Error checking DND state", e)
            false
        }
    }
    
    fun isMobileDataEnabled(context: Context): Boolean {
        return try {
            val value = Settings.Global.getInt(
                context.contentResolver,
                "mobile_data",
                1
            )
            value == 1
        } catch (e: Exception) {
            Log.e(TAG, "Error checking mobile data state", e)
            true
        }
    }
    
    fun isRotationLocked(context: Context): Boolean {
        return try {
            val value = Settings.System.getInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            )
            value == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking rotation lock state", e)
            false
        }
    }
    
    fun openCalculator(context: Context) {
        try {
            val intent = Intent()
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_APP_CALCULATOR)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening calculator", e)
            Toast.makeText(context, "Cannot open calculator", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun openDisplaySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening display settings", e)
            Toast.makeText(context, "Cannot open display settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening battery settings", e)
            Toast.makeText(context, "Cannot open battery settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings", e)
            Toast.makeText(context, "Cannot open accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun getBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting brightness", e)
            128
        }
    }
    
    fun canWriteSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }
    
    fun openWriteSettingsPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = android.net.Uri.parse("package:${context.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening write settings permission", e)
            }
        }
    }
    
    fun isAutoBrightnessEnabled(context: Context): Boolean {
        return try {
            val mode = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            Log.e(TAG, "Error checking auto brightness", e)
            false
        }
    }
    
    fun setAutoBrightness(context: Context, enabled: Boolean): Boolean {
        return try {
            if (!canWriteSettings(context)) {
                return false
            }
            val mode = if (enabled) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                mode
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting auto brightness", e)
            false
        }
    }
    
    fun setBrightness(context: Context, brightness: Int): Boolean {
        return try {
            if (!canWriteSettings(context)) {
                Log.w(TAG, "Cannot write settings - permission not granted")
                return false
            }
            
            if (isAutoBrightnessEnabled(context)) {
                setAutoBrightness(context, false)
            }
            
            val value = brightness.coerceIn(1, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting brightness", e)
            false
        }
    }
    
    fun getMaxBrightness(): Int = 255
    
    fun getVolume(context: Context): Int {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting volume", e)
            0
        }
    }
    
    fun setVolume(context: Context, volume: Int): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val value = volume.coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
            false
        }
    }
    
    fun getMaxVolume(context: Context): Int {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting max volume", e)
            15
        }
    }
}
