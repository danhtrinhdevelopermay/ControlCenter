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
            
            // Use reflection to access private newProcess method
            val process = try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            } catch (e: Exception) {
                Log.e(TAG, "Failed to use Shizuku.newProcess, falling back to Runtime.exec", e)
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }
            
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
    
    fun scanBluetoothDevices(callback: (List<ShizukuBluetoothDevice>) -> Unit) {
        executor.execute {
            val devices = mutableListOf<ShizukuBluetoothDevice>()
            
            try {
                // Get full bluetooth_manager dumpsys output (without using grep which may not be available)
                val fullOutput = executeShellCommandWithOutput("dumpsys bluetooth_manager")
                
                if (fullOutput != null && fullOutput.isNotEmpty()) {
                    Log.d(TAG, "Bluetooth dumpsys output length: ${fullOutput.length}")
                    
                    // Parse all devices from full dumpsys
                    devices.addAll(parseFullBluetoothDumpsys(fullOutput))
                    
                    // Update connection status
                    val connectedMacs = parseConnectedDevicesFromFull(fullOutput)
                    for (i in devices.indices) {
                        if (connectedMacs.contains(devices[i].macAddress)) {
                            devices[i] = devices[i].copy(isConnected = true)
                        }
                    }
                }
                
                // Try to start discovery for nearby devices (quick scan, don't block long)
                executeShellCommand("cmd bluetooth enable-scan")
                Thread.sleep(500)
                
                // Get updated output after scan
                val updatedOutput = executeShellCommandWithOutput("dumpsys bluetooth_manager")
                if (updatedOutput != null && updatedOutput.isNotEmpty()) {
                    val discoveredDevices = parseDiscoveredDevicesFromFull(updatedOutput)
                    for (discovered in discoveredDevices) {
                        if (devices.none { it.macAddress == discovered.macAddress }) {
                            devices.add(discovered)
                        }
                    }
                }
                
                Log.d(TAG, "Found ${devices.size} Bluetooth devices via Shizuku")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning Bluetooth devices", e)
            }
            
            mainHandler.post {
                callback(devices.distinctBy { it.macAddress }.sortedWith(
                    compareByDescending<ShizukuBluetoothDevice> { it.isConnected }
                        .thenByDescending { it.isPaired }
                        .thenBy { it.name }
                ))
            }
        }
    }
    
    private fun parseConnectedDevicesFromFull(output: String): Set<String> {
        val connectedMacs = mutableSetOf<String>()
        
        try {
            // Look for patterns that indicate connection
            // e.g., "isActive=true", "state=connected", "Connected devices:", etc.
            val lines = output.split("\n")
            var inConnectedSection = false
            
            for (line in lines) {
                val trimmed = line.trim()
                
                if (trimmed.contains("Connected", ignoreCase = true) && 
                    (trimmed.contains("device", ignoreCase = true) || trimmed.contains(":"))) {
                    inConnectedSection = true
                }
                
                if (inConnectedSection || trimmed.contains("state=connected", ignoreCase = true) ||
                    trimmed.contains("isActive=true", ignoreCase = true)) {
                    val macMatch = Regex("([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})").find(trimmed)
                    if (macMatch != null) {
                        connectedMacs.add(macMatch.groupValues[1].uppercase())
                    }
                }
                
                // End of section detection
                if (inConnectedSection && trimmed.isBlank()) {
                    inConnectedSection = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing connected devices from full output", e)
        }
        
        return connectedMacs
    }
    
    private fun parseDiscoveredDevicesFromFull(output: String): List<ShizukuBluetoothDevice> {
        val devices = mutableListOf<ShizukuBluetoothDevice>()
        
        try {
            val lines = output.split("\n")
            var inDiscoverySection = false
            
            for (line in lines) {
                val trimmed = line.trim()
                
                if (trimmed.contains("Discovery", ignoreCase = true) || 
                    trimmed.contains("Discovered", ignoreCase = true)) {
                    inDiscoverySection = true
                    continue
                }
                
                if (inDiscoverySection) {
                    val macMatch = Regex("([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})").find(trimmed)
                    if (macMatch != null) {
                        val mac = macMatch.groupValues[1].uppercase()
                        
                        var deviceName = "Unknown Device"
                        val nameMatch = Regex("name[=:]\\s*([^,\\n\\]]+)", RegexOption.IGNORE_CASE).find(trimmed)
                        if (nameMatch != null) {
                            deviceName = nameMatch.groupValues[1].trim()
                        }
                        
                        if (deviceName != "Unknown Device" && devices.none { it.macAddress == mac }) {
                            devices.add(ShizukuBluetoothDevice(
                                name = deviceName,
                                macAddress = mac,
                                isPaired = false,
                                isConnected = false,
                                deviceType = "other"
                            ))
                        }
                    }
                    
                    // End section on blank line or new section
                    if (trimmed.isBlank() || (trimmed.contains(":") && !trimmed.contains("="))) {
                        inDiscoverySection = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing discovered devices", e)
        }
        
        return devices
    }
    
    private fun parseBluetoothDevices(output: String, isPaired: Boolean): List<ShizukuBluetoothDevice> {
        val devices = mutableListOf<ShizukuBluetoothDevice>()
        
        try {
            val lines = output.split("\n")
            
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isBlank()) continue
                
                // Skip headers and labels
                if (trimmedLine.startsWith("Bonded") || 
                    trimmedLine.startsWith("Connected") ||
                    trimmedLine.startsWith("Discovery") ||
                    trimmedLine.startsWith("---")) continue
                
                // Look for MAC address pattern
                val macMatch = Regex("([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})").find(trimmedLine)
                if (macMatch != null) {
                    val macAddress = macMatch.groupValues[1].uppercase()
                    
                    // Try to extract device name
                    var deviceName = "Unknown Device"
                    
                    // Format: "name: DeviceName" or "DeviceName (MAC)" or just after MAC
                    val nameMatch = Regex("name[=:]\\s*([^,\\n]+)", RegexOption.IGNORE_CASE).find(trimmedLine)
                    if (nameMatch != null) {
                        deviceName = nameMatch.groupValues[1].trim()
                    } else {
                        // Try format "DeviceName MAC" or "MAC DeviceName"
                        val beforeMac = trimmedLine.substringBefore(macAddress).trim()
                        val afterMac = trimmedLine.substringAfter(macAddress).trim()
                        
                        if (beforeMac.isNotEmpty() && !beforeMac.matches(Regex("^[\\s\\d:]+$"))) {
                            deviceName = beforeMac.trimEnd(':').trim()
                        } else if (afterMac.isNotEmpty() && !afterMac.startsWith("(")) {
                            deviceName = afterMac.trimStart(':').trim()
                        }
                    }
                    
                    // Extract device type if available
                    val deviceType = when {
                        trimmedLine.contains("HEADSET", ignoreCase = true) || 
                        trimmedLine.contains("AUDIO", ignoreCase = true) -> "audio"
                        trimmedLine.contains("PHONE", ignoreCase = true) -> "phone"
                        trimmedLine.contains("COMPUTER", ignoreCase = true) || 
                        trimmedLine.contains("LAPTOP", ignoreCase = true) -> "computer"
                        trimmedLine.contains("KEYBOARD", ignoreCase = true) -> "keyboard"
                        trimmedLine.contains("MOUSE", ignoreCase = true) -> "mouse"
                        trimmedLine.contains("WATCH", ignoreCase = true) || 
                        trimmedLine.contains("WEARABLE", ignoreCase = true) -> "watch"
                        else -> "other"
                    }
                    
                    if (deviceName.isNotEmpty() && deviceName != "Unknown Device") {
                        devices.add(ShizukuBluetoothDevice(
                            name = deviceName,
                            macAddress = macAddress,
                            isPaired = isPaired,
                            isConnected = false,
                            deviceType = deviceType
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Bluetooth devices", e)
        }
        
        return devices
    }
    
    private fun parseConnectedDevices(output: String): Set<String> {
        val connectedMacs = mutableSetOf<String>()
        
        try {
            val macPattern = Regex("([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})")
            macPattern.findAll(output).forEach { match ->
                connectedMacs.add(match.groupValues[1].uppercase())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing connected devices", e)
        }
        
        return connectedMacs
    }
    
    private fun parseFullBluetoothDumpsys(output: String): List<ShizukuBluetoothDevice> {
        val devices = mutableListOf<ShizukuBluetoothDevice>()
        
        try {
            // Parse bonded devices section
            val bondedSection = Regex("Bonded devices:([\\s\\S]*?)(?=\\n\\w|$)").find(output)
            if (bondedSection != null) {
                devices.addAll(parseBluetoothDevices(bondedSection.groupValues[1], isPaired = true))
            }
            
            // Parse individual device entries
            // Format: Device{...name=DeviceName, address=XX:XX:XX:XX:XX:XX...}
            val devicePattern = Regex("Device\\{[^}]*name=([^,}]+)[^}]*address=([0-9A-Fa-f:]{17})[^}]*\\}", RegexOption.IGNORE_CASE)
            devicePattern.findAll(output).forEach { match ->
                val name = match.groupValues[1].trim()
                val mac = match.groupValues[2].uppercase()
                
                if (name.isNotEmpty() && devices.none { it.macAddress == mac }) {
                    devices.add(ShizukuBluetoothDevice(
                        name = name,
                        macAddress = mac,
                        isPaired = true,
                        isConnected = false,
                        deviceType = "other"
                    ))
                }
            }
            
            // Try alternative format with reverse order
            val devicePattern2 = Regex("Device\\{[^}]*address=([0-9A-Fa-f:]{17})[^}]*name=([^,}]+)[^}]*\\}", RegexOption.IGNORE_CASE)
            devicePattern2.findAll(output).forEach { match ->
                val mac = match.groupValues[1].uppercase()
                val name = match.groupValues[2].trim()
                
                if (name.isNotEmpty() && devices.none { it.macAddress == mac }) {
                    devices.add(ShizukuBluetoothDevice(
                        name = name,
                        macAddress = mac,
                        isPaired = true,
                        isConnected = false,
                        deviceType = "other"
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing full Bluetooth dumpsys", e)
        }
        
        return devices
    }
    
    fun connectBluetoothDevice(macAddress: String, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                // Try to connect using cmd bluetooth
                var success = executeShellCommand("cmd bluetooth connect $macAddress")
                
                if (!success) {
                    // Try alternative: Use btmgmt
                    success = executeShellCommand("btmgmt connect $macAddress")
                }
                
                if (!success) {
                    // Try using settings command
                    success = executeShellCommand("am start -a android.bluetooth.device.action.PAIR -e android.bluetooth.device.extra.DEVICE $macAddress")
                }
                
                mainHandler.post {
                    if (success) {
                        callback(true, "Đang kết nối...")
                    } else {
                        callback(false, "Không thể kết nối. Thử kết nối thủ công trong Cài đặt.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting Bluetooth device", e)
                mainHandler.post {
                    callback(false, "Lỗi: ${e.message}")
                }
            }
        }
    }
    
    fun disconnectBluetoothDevice(macAddress: String, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val success = executeShellCommand("cmd bluetooth disconnect $macAddress")
                
                mainHandler.post {
                    if (success) {
                        callback(true, "Đã ngắt kết nối")
                    } else {
                        callback(false, "Không thể ngắt kết nối")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting Bluetooth device", e)
                mainHandler.post {
                    callback(false, "Lỗi: ${e.message}")
                }
            }
        }
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
    
    private fun executeShellCommandWithOutput(command: String): String? {
        return try {
            if (!checkShizukuPermission()) {
                Log.w(TAG, "No Shizuku permission for command: $command")
                return null
            }
            
            val process = try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            } catch (e: Exception) {
                Log.e(TAG, "Failed to use Shizuku.newProcess, falling back to Runtime.exec", e)
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = reader.readText()
            val error = errorReader.readText()
            val exitCode = process.waitFor()
            
            reader.close()
            errorReader.close()
            
            if (exitCode != 0) {
                Log.e(TAG, "Command failed with exit code $exitCode: $command\nError: $error")
                return null
            }
            
            Log.d(TAG, "Command output length: ${output.length}")
            output
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: $command", e)
            null
        }
    }
    
    fun scanWifiNetworks(callback: (List<ShizukuWifiNetwork>) -> Unit) {
        executor.execute {
            val networks = mutableListOf<ShizukuWifiNetwork>()
            
            try {
                // First trigger a scan
                executeShellCommand("cmd wifi start-scan")
                
                // Wait a bit for scan to complete
                Thread.sleep(2000)
                
                // Get scan results using cmd wifi
                val output = executeShellCommandWithOutput("cmd wifi list-scan-results")
                
                if (output != null && output.isNotEmpty()) {
                    Log.d(TAG, "WiFi scan output:\n$output")
                    networks.addAll(parseWifiScanResults(output))
                }
                
                // If cmd wifi doesn't work, try dumpsys
                if (networks.isEmpty()) {
                    val dumpsysOutput = executeShellCommandWithOutput("dumpsys wifi | grep -A 50 'Latest scan results'")
                    if (dumpsysOutput != null && dumpsysOutput.isNotEmpty()) {
                        Log.d(TAG, "Dumpsys WiFi output:\n$dumpsysOutput")
                        networks.addAll(parseDumpsysWifiResults(dumpsysOutput))
                    }
                }
                
                // If still empty, try wpa_cli
                if (networks.isEmpty()) {
                    val wpaOutput = executeShellCommandWithOutput("wpa_cli -i wlan0 scan_results")
                    if (wpaOutput != null && wpaOutput.isNotEmpty()) {
                        Log.d(TAG, "wpa_cli output:\n$wpaOutput")
                        networks.addAll(parseWpaCliResults(wpaOutput))
                    }
                }
                
                Log.d(TAG, "Found ${networks.size} WiFi networks via Shizuku")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning WiFi networks", e)
            }
            
            mainHandler.post {
                callback(networks.distinctBy { it.ssid }.sortedByDescending { it.signalLevel })
            }
        }
    }
    
    private fun parseWifiScanResults(output: String): List<ShizukuWifiNetwork> {
        val networks = mutableListOf<ShizukuWifiNetwork>()
        
        try {
            val lines = output.split("\n")
            
            for (line in lines) {
                if (line.isBlank()) continue
                
                // Skip header lines
                if (line.contains("BSSID") && line.contains("SSID") && line.contains("frequency")) continue
                if (line.startsWith("---") || line.startsWith("===")) continue
                
                var ssid: String? = null
                var bssid: String? = null
                var level = -100
                var capabilities = ""
                
                // Format 1: Key-value format (SSID: name, BSSID: xx:xx:xx...)
                if (line.contains("SSID:") || line.contains("ssid=")) {
                    val ssidMatch = Regex("(?:SSID:|ssid=)\\s*\"?([^\"\\n,]+)\"?").find(line)
                    ssid = ssidMatch?.groupValues?.getOrNull(1)?.trim()?.replace("\"", "")
                    
                    val bssidMatch = Regex("(?:BSSID:|bssid=)\\s*([0-9a-fA-F:]{17})").find(line)
                    bssid = bssidMatch?.groupValues?.getOrNull(1)?.trim()
                    
                    val levelMatch = Regex("(?:level:|rssi=|signal=)\\s*(-?\\d+)").find(line)
                    level = levelMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -100
                    
                    val capsMatch = Regex("\\[([^\\]]+)\\]").find(line)
                    capabilities = capsMatch?.groupValues?.getOrNull(1) ?: ""
                } else {
                    // Format 2: Table format - split by any whitespace
                    // Typical formats:
                    // a) With index: 0001 08:3a:8d:xx:xx:xx 2412 -52 [WPA2-PSK-CCMP][ESS] MySSID
                    // b) Without index: 00:11:22:33:44:55 2412 -52 [WPA2-PSK-CCMP][ESS] MySSID
                    
                    val trimmedLine = line.trim()
                    
                    // Extract BSSID (MAC address pattern) - search anywhere in line, not just at start
                    val bssidMatch = Regex("([0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}:[0-9a-fA-F]{2})").find(trimmedLine)
                    if (bssidMatch != null) {
                        bssid = bssidMatch.groupValues[1]
                        
                        // Get the part after BSSID
                        val bssidEndIndex = bssidMatch.range.last + 1
                        val afterBssid = if (bssidEndIndex < trimmedLine.length) {
                            trimmedLine.substring(bssidEndIndex).trim()
                        } else {
                            ""
                        }
                        
                        // Split remaining by whitespace
                        val parts = afterBssid.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        
                        if (parts.isNotEmpty()) {
                            // First part could be frequency, second could be level (RSSI)
                            // Level is typically negative number
                            for (part in parts) {
                                val num = part.toIntOrNull()
                                if (num != null && num < 0) {
                                    level = num
                                    break
                                }
                            }
                            
                            // Find capabilities in brackets
                            val capsMatches = Regex("\\[([^\\]]+)\\]").findAll(afterBssid)
                            capabilities = capsMatches.map { it.groupValues[1] }.joinToString("][")
                            
                            // SSID is after the last bracket
                            val lastBracket = afterBssid.lastIndexOf(']')
                            ssid = if (lastBracket >= 0 && lastBracket < afterBssid.length - 1) {
                                afterBssid.substring(lastBracket + 1).trim()
                            } else if (capabilities.isEmpty() && parts.size >= 2) {
                                // No brackets, try to find SSID after frequency and level
                                // Skip numeric parts (frequency, level)
                                parts.dropWhile { it.toIntOrNull() != null || it.startsWith("-") && it.drop(1).toIntOrNull() != null }
                                    .joinToString(" ").trim()
                            } else {
                                null
                            }
                        }
                    }
                }
                
                if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
                    val isSecured = capabilities.contains("WPA", ignoreCase = true) || 
                                   capabilities.contains("WEP", ignoreCase = true) ||
                                   capabilities.contains("PSK", ignoreCase = true) ||
                                   capabilities.contains("EAP", ignoreCase = true)
                    
                    val securityType = when {
                        capabilities.contains("WPA3", ignoreCase = true) -> "WPA3"
                        capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
                        capabilities.contains("WPA", ignoreCase = true) -> "WPA"
                        capabilities.contains("WEP", ignoreCase = true) -> "WEP"
                        capabilities.contains("EAP", ignoreCase = true) -> "EAP"
                        else -> "Open"
                    }
                    
                    networks.add(ShizukuWifiNetwork(
                        ssid = ssid,
                        bssid = bssid ?: "",
                        signalLevel = convertRssiToLevel(level),
                        isSecured = isSecured,
                        securityType = securityType
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WiFi scan results", e)
        }
        
        return networks
    }
    
    private fun parseDumpsysWifiResults(output: String): List<ShizukuWifiNetwork> {
        val networks = mutableListOf<ShizukuWifiNetwork>()
        
        try {
            // Skip header lines like "Latest scan results:"
            val contentStart = output.indexOf("Latest scan results:")
            val content = if (contentStart >= 0) {
                output.substring(contentStart + "Latest scan results:".length)
            } else {
                output
            }
            
            // Parse multiline entries - each entry typically starts with BSSID or contains ScanResult
            // Format can be:
            // ScanResult{...SSID: "name", BSSID: xx:xx..., capabilities: [...], level=-50...}
            // Or multiline with properties on separate lines
            
            val lines = content.split("\n")
            var currentSsid: String? = null
            var currentBssid: String? = null
            var currentLevel = -100
            var currentCaps = ""
            
            for (line in lines) {
                val trimmedLine = line.trim()
                
                // Skip empty lines and headers
                if (trimmedLine.isBlank()) continue
                if (trimmedLine.startsWith("Latest") || trimmedLine.startsWith("---") || trimmedLine.startsWith("===")) continue
                
                // Try to parse ScanResult{...} format (single line)
                if (trimmedLine.contains("ScanResult")) {
                    val ssidMatch = Regex("SSID:\\s*\"?([^\"\\},]+)\"?").find(trimmedLine)
                    val bssidMatch = Regex("BSSID:\\s*([0-9a-fA-F:]{17})").find(trimmedLine)
                    val levelMatch = Regex("level[=:]\\s*(-?\\d+)").find(trimmedLine)
                    val capsMatch = Regex("capabilities:\\s*\\[([^\\]]+)\\]").find(trimmedLine)
                    
                    if (ssidMatch != null && bssidMatch != null) {
                        currentSsid = ssidMatch.groupValues[1].trim()
                        currentBssid = bssidMatch.groupValues[1]
                        currentLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: -100
                        currentCaps = capsMatch?.groupValues?.get(1) ?: ""
                    }
                } else {
                    // Multiline format - accumulate values
                    when {
                        trimmedLine.contains("SSID:") || trimmedLine.contains("ssid:") -> {
                            // Save previous entry if complete
                            if (currentSsid != null && currentBssid != null && 
                                currentSsid.isNotEmpty() && currentSsid != "<unknown ssid>") {
                                addNetworkFromParsed(networks, currentSsid, currentBssid, currentLevel, currentCaps)
                            }
                            currentSsid = trimmedLine.substringAfter(":").trim().replace("\"", "").replace(",", "")
                            currentBssid = null
                            currentLevel = -100
                            currentCaps = ""
                        }
                        trimmedLine.contains("BSSID:") || trimmedLine.contains("bssid:") -> {
                            val bssidMatch = Regex("([0-9a-fA-F:]{17})").find(trimmedLine)
                            currentBssid = bssidMatch?.groupValues?.get(1)
                        }
                        trimmedLine.contains("level") && (trimmedLine.contains(":") || trimmedLine.contains("=")) -> {
                            val levelMatch = Regex("(-?\\d+)").find(trimmedLine.substringAfter("level"))
                            currentLevel = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: currentLevel
                        }
                        trimmedLine.contains("capabilities:") || trimmedLine.contains("caps:") -> {
                            val capsMatch = Regex("\\[([^\\]]+)\\]").find(trimmedLine)
                            currentCaps = capsMatch?.groupValues?.get(1) ?: ""
                        }
                    }
                }
                
                // Check if we have a complete entry to add
                if (currentSsid != null && currentBssid != null && 
                    currentSsid.isNotEmpty() && currentSsid != "<unknown ssid>" &&
                    (trimmedLine.contains("ScanResult") || trimmedLine.isBlank() || trimmedLine.startsWith("{"))) {
                    addNetworkFromParsed(networks, currentSsid, currentBssid, currentLevel, currentCaps)
                    currentSsid = null
                    currentBssid = null
                    currentLevel = -100
                    currentCaps = ""
                }
            }
            
            // Add last entry if not yet added
            if (currentSsid != null && currentBssid != null && 
                currentSsid.isNotEmpty() && currentSsid != "<unknown ssid>") {
                addNetworkFromParsed(networks, currentSsid, currentBssid, currentLevel, currentCaps)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing dumpsys WiFi results", e)
        }
        
        return networks
    }
    
    private fun addNetworkFromParsed(
        networks: MutableList<ShizukuWifiNetwork>,
        ssid: String,
        bssid: String,
        level: Int,
        capabilities: String
    ) {
        val isSecured = capabilities.contains("WPA", ignoreCase = true) || 
                       capabilities.contains("WEP", ignoreCase = true) ||
                       capabilities.contains("PSK", ignoreCase = true) ||
                       capabilities.contains("EAP", ignoreCase = true)
        
        val securityType = when {
            capabilities.contains("WPA3", ignoreCase = true) -> "WPA3"
            capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
            capabilities.contains("WPA", ignoreCase = true) -> "WPA"
            capabilities.contains("WEP", ignoreCase = true) -> "WEP"
            capabilities.contains("EAP", ignoreCase = true) -> "EAP"
            else -> "Open"
        }
        
        networks.add(ShizukuWifiNetwork(
            ssid = ssid,
            bssid = bssid,
            signalLevel = convertRssiToLevel(level),
            isSecured = isSecured,
            securityType = securityType
        ))
    }
    
    private fun parseWpaCliResults(output: String): List<ShizukuWifiNetwork> {
        val networks = mutableListOf<ShizukuWifiNetwork>()
        
        try {
            // wpa_cli format: bssid / frequency / signal level / flags / ssid
            // e.g., 00:11:22:33:44:55  2437    -50     [WPA2-PSK-CCMP][WPS][ESS]       MyNetwork
            val lines = output.split("\n").drop(1) // Skip header
            
            for (line in lines) {
                val parts = line.split("\t")
                if (parts.size >= 5) {
                    val bssid = parts[0].trim()
                    val level = parts[2].trim().toIntOrNull() ?: -100
                    val flags = parts[3].trim()
                    val ssid = parts.drop(4).joinToString("\t").trim()
                    
                    if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                        val isSecured = flags.contains("WPA", ignoreCase = true) || 
                                       flags.contains("WEP", ignoreCase = true) ||
                                       flags.contains("PSK", ignoreCase = true)
                        
                        val securityType = when {
                            flags.contains("WPA3", ignoreCase = true) -> "WPA3"
                            flags.contains("WPA2", ignoreCase = true) -> "WPA2"
                            flags.contains("WPA", ignoreCase = true) -> "WPA"
                            flags.contains("WEP", ignoreCase = true) -> "WEP"
                            flags.contains("EAP", ignoreCase = true) -> "EAP"
                            else -> "Open"
                        }
                        
                        networks.add(ShizukuWifiNetwork(
                            ssid = ssid,
                            bssid = bssid,
                            signalLevel = convertRssiToLevel(level),
                            isSecured = isSecured,
                            securityType = securityType
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing wpa_cli results", e)
        }
        
        return networks
    }
    
    private fun convertRssiToLevel(rssi: Int): Int {
        // Convert RSSI (-100 to 0) to signal level (0 to 4)
        return when {
            rssi >= -50 -> 4
            rssi >= -60 -> 3
            rssi >= -70 -> 2
            rssi >= -80 -> 1
            else -> 0
        }
    }
    
    fun connectToWifiNetwork(ssid: String, password: String?, isSecured: Boolean, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                var success = false
                
                // Method 1: Try cmd wifi add-suggestion (Android 10+)
                // This adds the network as a suggestion which may auto-connect
                if (!isSecured || password.isNullOrEmpty()) {
                    val suggestCmd = "cmd wifi add-suggestion \"$ssid\" open"
                    success = executeShellCommand(suggestCmd)
                    Log.d(TAG, "add-suggestion open result: $success")
                } else {
                    val suggestCmd = "cmd wifi add-suggestion \"$ssid\" wpa2 \"$password\""
                    success = executeShellCommand(suggestCmd)
                    Log.d(TAG, "add-suggestion wpa2 result: $success")
                }
                
                // Method 2: Try settings command for saved networks
                if (!success && !isSecured) {
                    // For open networks, try direct connection via settings
                    val settingsCmd = "cmd wifi status"
                    executeShellCommand(settingsCmd)
                }
                
                // Method 3: Try wpa_cli for older Android versions
                if (!success) {
                    // First, scan for the network to get the network id
                    executeShellCommand("wpa_cli -i wlan0 scan")
                    Thread.sleep(1000)
                    
                    if (!isSecured || password.isNullOrEmpty()) {
                        // Open network
                        val cmds = listOf(
                            "wpa_cli -i wlan0 add_network",
                            "wpa_cli -i wlan0 set_network 0 ssid '\"$ssid\"'",
                            "wpa_cli -i wlan0 set_network 0 key_mgmt NONE",
                            "wpa_cli -i wlan0 enable_network 0",
                            "wpa_cli -i wlan0 reconnect"
                        )
                        for (cmd in cmds) {
                            executeShellCommand(cmd)
                        }
                        success = true
                    } else {
                        // WPA/WPA2 network
                        val cmds = listOf(
                            "wpa_cli -i wlan0 add_network",
                            "wpa_cli -i wlan0 set_network 0 ssid '\"$ssid\"'",
                            "wpa_cli -i wlan0 set_network 0 psk '\"$password\"'",
                            "wpa_cli -i wlan0 enable_network 0",
                            "wpa_cli -i wlan0 reconnect"
                        )
                        for (cmd in cmds) {
                            executeShellCommand(cmd)
                        }
                        success = true
                    }
                }
                
                mainHandler.post {
                    if (success) {
                        // Give some time for connection to establish
                        callback(true, "Đang kết nối với $ssid...")
                    } else {
                        // Return false to trigger fallback to standard method
                        callback(false, "Shizuku connect failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to WiFi network via Shizuku", e)
                mainHandler.post {
                    callback(false, "Lỗi Shizuku: ${e.message}")
                }
            }
        }
    }
}

data class ShizukuWifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int,
    val isSecured: Boolean,
    val securityType: String
)

data class ShizukuBluetoothDevice(
    val name: String,
    val macAddress: String,
    val isPaired: Boolean,
    val isConnected: Boolean,
    val deviceType: String = "other"
)
