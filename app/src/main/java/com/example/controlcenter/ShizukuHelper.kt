package com.example.controlcenter

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

object ShizukuHelper {
    
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private const val TAG = "ShizukuHelper"
    private const val REQUEST_CODE = 1002
    
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku not available", e)
            false
        }
    }
    
    fun checkShizukuPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku permission", e)
            false
        }
    }
    
    fun requestShizukuPermission() {
        try {
            if (isShizukuAvailable() && 
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission", e)
        }
    }
    
    private fun executeShellCommand(command: String): Boolean {
        return try {
            if (!checkShizukuPermission()) {
                Log.w(TAG, "No Shizuku permission for command: $command")
                return false
            }
            
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = reader.readText()
            val error = errorReader.readText()
            val exitCode = process.waitFor()
            
            reader.close()
            errorReader.close()
            
            if (exitCode != 0) {
                Log.e(TAG, "Command failed with exit code $exitCode: $command\nError: $error")
                return false
            }
            
            if (output.isNotEmpty()) {
                Log.d(TAG, "Command output: $output")
            }
            
            Log.d(TAG, "Command succeeded: $command")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: $command", e)
            false
        }
    }
    
    fun toggleWifi(enable: Boolean, callback: ((Boolean) -> Unit)? = null) {
        val command = if (enable) "svc wifi enable" else "svc wifi disable"
        executeShellCommandAsync(command, callback)
    }
    
    fun toggleBluetooth(enable: Boolean, callback: ((Boolean) -> Unit)? = null) {
        val command = if (enable) "svc bluetooth enable" else "svc bluetooth disable"
        executeShellCommandAsync(command, callback)
    }
    
    fun toggleAirplaneMode(enable: Boolean, callback: ((Boolean) -> Unit)? = null) {
        executor.execute {
            val value = if (enable) "1" else "0"
            val success1 = executeShellCommand("settings put global airplane_mode_on $value")
            val success2 = executeShellCommand("am broadcast -a android.intent.action.AIRPLANE_MODE")
            val result = success1 && success2
            callback?.let {
                mainHandler.post { it(result) }
            }
        }
    }
    
    fun toggleMobileData(enable: Boolean, callback: ((Boolean) -> Unit)? = null) {
        val command = if (enable) "svc data enable" else "svc data disable"
        executeShellCommandAsync(command, callback)
    }
    
    fun toggleDoNotDisturb(enable: Boolean, callback: ((Boolean) -> Unit)? = null) {
        val mode = if (enable) "1" else "0"
        executeShellCommandAsync("cmd notification set_dnd $mode", callback)
    }
    
    fun setBrightness(brightness: Int, callback: ((Boolean) -> Unit)? = null) {
        val brightnessValue = brightness.coerceIn(0, 255)
        executeShellCommandAsync("settings put system screen_brightness $brightnessValue", callback)
    }
    
    fun setRotationLock(locked: Boolean, callback: ((Boolean) -> Unit)? = null) {
        val value = if (locked) "0" else "1"
        executeShellCommandAsync("settings put system accelerometer_rotation $value", callback)
    }
    
    private fun executeShellCommandAsync(command: String, callback: ((Boolean) -> Unit)?) {
        executor.execute {
            val result = executeShellCommand(command)
            callback?.let {
                mainHandler.post { it(result) }
            }
        }
    }
}
