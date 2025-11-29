package com.example.controlcenter

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {
    
    private const val TAG = "ShizukuHelper"
    private const val REQUEST_CODE = 1001
    
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
            
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = reader.readText()
            val error = errorReader.readText()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                Log.e(TAG, "Command failed with exit code $exitCode: $command\nError: $error")
                return false
            }
            
            if (output.isNotEmpty()) {
                Log.d(TAG, "Command output: $output")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: $command", e)
            false
        }
    }
    
    fun toggleWifi(enable: Boolean): Boolean {
        val command = if (enable) "svc wifi enable" else "svc wifi disable"
        return executeShellCommand(command)
    }
    
    fun toggleBluetooth(enable: Boolean): Boolean {
        val command = if (enable) "svc bluetooth enable" else "svc bluetooth disable"
        return executeShellCommand(command)
    }
    
    fun toggleAirplaneMode(enable: Boolean): Boolean {
        val value = if (enable) "1" else "0"
        val success1 = executeShellCommand("settings put global airplane_mode_on $value")
        val success2 = executeShellCommand("am broadcast -a android.intent.action.AIRPLANE_MODE")
        return success1 && success2
    }
    
    fun toggleMobileData(enable: Boolean): Boolean {
        val command = if (enable) "svc data enable" else "svc data disable"
        return executeShellCommand(command)
    }
    
    fun toggleDoNotDisturb(enable: Boolean): Boolean {
        val mode = if (enable) "1" else "0"
        return executeShellCommand("cmd notification set_dnd $mode")
    }
    
    fun setBrightness(brightness: Int): Boolean {
        val brightnessValue = brightness.coerceIn(0, 255)
        return executeShellCommand("settings put system screen_brightness $brightnessValue")
    }
    
    fun setRotationLock(locked: Boolean): Boolean {
        val value = if (locked) "0" else "1"
        return executeShellCommand("settings put system accelerometer_rotation $value")
    }
}
