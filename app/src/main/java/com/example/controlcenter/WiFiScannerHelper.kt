package com.example.controlcenter

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

data class WiFiNetwork(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int,
    val isSecured: Boolean,
    val securityType: String,
    val isConnected: Boolean = false
)

class WiFiScannerHelper(private val context: Context) {
    
    private val wifiManager: WifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())
    
    private var scanResultsReceiver: BroadcastReceiver? = null
    private var onScanCompleteListener: ((List<WiFiNetwork>) -> Unit)? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isCallbackRegistered = false
    
    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled
    
    fun setWifiEnabled(enabled: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                false
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = enabled
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun startScan(onComplete: (List<WiFiNetwork>) -> Unit) {
        onScanCompleteListener = onComplete
        
        if (!hasLocationPermission()) {
            onComplete(emptyList())
            return
        }
        
        if (!wifiManager.isWifiEnabled) {
            onComplete(emptyList())
            return
        }
        
        scanResultsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (success) {
                    processScanResults()
                } else {
                    processScanResults()
                }
                unregisterReceiver()
            }
        }
        
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(scanResultsReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(scanResultsReceiver, intentFilter)
        }
        
        val scanStarted = wifiManager.startScan()
        if (!scanStarted) {
            processScanResults()
        }
        
        handler.postDelayed({
            if (scanResultsReceiver != null) {
                processScanResults()
                unregisterReceiver()
            }
        }, 10000)
    }
    
    private fun processScanResults() {
        val currentSsid = getCurrentConnectedSsid()
        val results = getScanResults()
        val networks = results
            .filter { it.SSID.isNotEmpty() }
            .distinctBy { it.SSID }
            .map { scanResult ->
                WiFiNetwork(
                    ssid = scanResult.SSID,
                    bssid = scanResult.BSSID,
                    signalLevel = WifiManager.calculateSignalLevel(scanResult.level, 5),
                    isSecured = isNetworkSecured(scanResult),
                    securityType = getSecurityType(scanResult),
                    isConnected = scanResult.SSID == currentSsid
                )
            }
            .sortedWith(compareByDescending<WiFiNetwork> { it.isConnected }.thenByDescending { it.signalLevel })
        
        handler.post {
            onScanCompleteListener?.invoke(networks)
        }
    }
    
    private fun getScanResults(): List<ScanResult> {
        return try {
            if (hasLocationPermission()) {
                wifiManager.scanResults ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    fun getCurrentConnectedSsid(): String? {
        return try {
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo != null && wifiInfo.ssid != null) {
                val ssid = wifiInfo.ssid.replace("\"", "")
                if (ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
                    ssid
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun isNetworkSecured(scanResult: ScanResult): Boolean {
        val capabilities = scanResult.capabilities
        return capabilities.contains("WEP") || 
               capabilities.contains("WPA") || 
               capabilities.contains("WPA2") ||
               capabilities.contains("WPA3") ||
               capabilities.contains("PSK") ||
               capabilities.contains("EAP")
    }
    
    private fun getSecurityType(scanResult: ScanResult): String {
        val capabilities = scanResult.capabilities
        return when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("EAP") -> "EAP"
            else -> "Open"
        }
    }
    
    fun connectToNetwork(ssid: String, password: String?, isSecured: Boolean, securityType: String = "WPA2", onResult: (Boolean, String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWithNetworkSpecifier(ssid, password, isSecured, securityType, onResult)
        } else {
            connectLegacy(ssid, password, isSecured, securityType, onResult)
        }
    }
    
    private fun connectWithNetworkSpecifier(ssid: String, password: String?, isSecured: Boolean, securityType: String, onResult: (Boolean, String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                unregisterNetworkCallback()
                
                val specifierBuilder = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                
                if (isSecured && !password.isNullOrEmpty()) {
                    when {
                        securityType == "WPA3" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            specifierBuilder.setWpa3Passphrase(password)
                        }
                        securityType == "WPA2" || securityType == "WPA" || securityType.contains("PSK") -> {
                            specifierBuilder.setWpa2Passphrase(password)
                        }
                        else -> {
                            specifierBuilder.setWpa2Passphrase(password)
                        }
                    }
                }
                
                val specifier = specifierBuilder.build()
                
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build()
                
                var callbackInvoked = false
                
                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        if (!callbackInvoked) {
                            callbackInvoked = true
                            handler.post {
                                onResult(true, "Đã kết nối với $ssid")
                            }
                        }
                    }
                    
                    override fun onUnavailable() {
                        super.onUnavailable()
                        if (!callbackInvoked) {
                            callbackInvoked = true
                            handler.post {
                                onResult(false, "Không thể kết nối với $ssid")
                            }
                            unregisterNetworkCallback()
                        }
                    }
                    
                    override fun onLost(network: Network) {
                        super.onLost(network)
                    }
                }
                
                isCallbackRegistered = true
                connectivityManager.requestNetwork(request, networkCallback!!)
                
                handler.postDelayed({
                    if (!callbackInvoked) {
                        callbackInvoked = true
                        onResult(false, "Hết thời gian kết nối")
                        unregisterNetworkCallback()
                    }
                }, 30000)
                
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Lỗi: ${e.message}")
            }
        }
    }
    
    @Suppress("DEPRECATION")
    private fun connectLegacy(ssid: String, password: String?, isSecured: Boolean, securityType: String, onResult: (Boolean, String) -> Unit) {
        try {
            val wifiConfig = WifiConfiguration()
            wifiConfig.SSID = "\"$ssid\""
            
            if (isSecured && !password.isNullOrEmpty()) {
                when (securityType) {
                    "WEP" -> {
                        wifiConfig.wepKeys[0] = "\"$password\""
                        wifiConfig.wepTxKeyIndex = 0
                        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                    }
                    "WPA", "WPA2", "WPA3" -> {
                        wifiConfig.preSharedKey = "\"$password\""
                        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                    }
                    else -> {
                        wifiConfig.preSharedKey = "\"$password\""
                        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    }
                }
            } else {
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                wifiConfig.allowedAuthAlgorithms.clear()
                wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
            }
            
            val existingConfig = wifiManager.configuredNetworks?.find { 
                it.SSID == "\"$ssid\"" 
            }
            
            val networkId = if (existingConfig != null) {
                wifiManager.removeNetwork(existingConfig.networkId)
                wifiManager.addNetwork(wifiConfig)
            } else {
                wifiManager.addNetwork(wifiConfig)
            }
            
            if (networkId == -1) {
                onResult(false, "Không thể thêm mạng. Kiểm tra quyền ứng dụng.")
                return
            }
            
            wifiManager.saveConfiguration()
            
            wifiManager.disconnect()
            val enabled = wifiManager.enableNetwork(networkId, true)
            val reconnect = wifiManager.reconnect()
            
            if (enabled && reconnect) {
                handler.postDelayed({
                    val currentSsid = getCurrentConnectedSsid()
                    if (currentSsid == ssid) {
                        onResult(true, "Đã kết nối với $ssid")
                    } else {
                        onResult(false, "Không thể kết nối - kiểm tra mật khẩu")
                    }
                }, 5000)
            } else {
                onResult(false, "Không thể kết nối. Vui lòng thử lại.")
            }
            
        } catch (e: SecurityException) {
            e.printStackTrace()
            onResult(false, "Không có quyền thay đổi cấu hình WiFi")
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(false, "Lỗi: ${e.message}")
        }
    }
    
    private fun unregisterNetworkCallback() {
        if (isCallbackRegistered && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback!!)
            } catch (e: Exception) { }
            isCallbackRegistered = false
        }
        networkCallback = null
    }
    
    fun disconnectFromNetwork() {
        unregisterNetworkCallback()
        try {
            connectivityManager.bindProcessToNetwork(null)
        } catch (e: Exception) { }
    }
    
    private fun unregisterReceiver() {
        scanResultsReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) { }
        }
        scanResultsReceiver = null
    }
    
    fun cleanup() {
        unregisterReceiver()
        unregisterNetworkCallback()
        handler.removeCallbacksAndMessages(null)
    }
}
