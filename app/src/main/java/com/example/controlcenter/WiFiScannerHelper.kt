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
import android.net.wifi.WifiInfo
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
    
    companion object {
        private const val TAG = "WiFiScannerHelper"
    }
    
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
    
    fun hasLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun startScan(onComplete: (List<WiFiNetwork>) -> Unit) {
        onScanCompleteListener = onComplete
        
        if (!wifiManager.isWifiEnabled) {
            android.util.Log.d(TAG, "WiFi is disabled")
            onComplete(emptyList())
            return
        }
        
        if (!hasLocationPermission()) {
            android.util.Log.w(TAG, "No location permission for WiFi scan")
            onComplete(emptyList())
            return
        }
        
        android.util.Log.d(TAG, "Starting WiFi scan with location permission")
        startScanWithLocationPermission(onComplete)
    }
    
    private fun startScanWithLocationPermission(onComplete: (List<WiFiNetwork>) -> Unit) {
        unregisterReceiver()
        
        scanResultsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                android.util.Log.d(TAG, "Scan broadcast received, success: $success")
                processScanResults()
                unregisterReceiver()
            }
        }
        
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(scanResultsReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(scanResultsReceiver, intentFilter)
        }
        
        @Suppress("DEPRECATION")
        val scanStarted = wifiManager.startScan()
        android.util.Log.d(TAG, "WiFi scan started: $scanStarted")
        
        if (!scanStarted) {
            android.util.Log.d(TAG, "Scan throttled, using cached results")
            processScanResults()
        }
        
        handler.postDelayed({
            if (scanResultsReceiver != null) {
                android.util.Log.d(TAG, "Scan timeout, processing cached results")
                processScanResults()
                unregisterReceiver()
            }
        }, 10000)
    }
    
    private fun processScanResults() {
        val currentSsid = getCurrentConnectedSsid()
        android.util.Log.d(TAG, "Current connected SSID: $currentSsid")
        
        val results = getScanResults()
        android.util.Log.d(TAG, "Found ${results.size} scan results")
        
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
        
        android.util.Log.d(TAG, "Processed ${networks.size} unique networks")
        
        handler.post {
            onScanCompleteListener?.invoke(networks)
        }
    }
    
    private fun getScanResults(): List<ScanResult> {
        return try {
            if (hasLocationPermission()) {
                wifiManager.scanResults ?: emptyList()
            } else {
                android.util.Log.w(TAG, "No location permission to get scan results")
                emptyList()
            }
        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "SecurityException getting scan results", e)
            emptyList()
        }
    }
    
    fun getCurrentConnectedSsid(): String? {
        return try {
            if (!hasLocationPermission()) {
                android.util.Log.w(TAG, "No location permission to get connected SSID")
                return null
            }
            
            var ssid: String? = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val network = connectivityManager.activeNetwork
                if (network != null) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val wifiInfo = capabilities.transportInfo as? WifiInfo
                        if (wifiInfo != null && wifiInfo.ssid != null) {
                            ssid = wifiInfo.ssid.replace("\"", "")
                            android.util.Log.d(TAG, "Got SSID from NetworkCapabilities: $ssid")
                        }
                    }
                }
            }
            
            if (ssid == null || ssid == "<unknown ssid>" || ssid.isEmpty()) {
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null && wifiInfo.ssid != null) {
                    ssid = wifiInfo.ssid.replace("\"", "")
                    android.util.Log.d(TAG, "Got SSID from WifiManager: $ssid")
                }
            }
            
            if (ssid != null && ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
                ssid
            } else {
                android.util.Log.d(TAG, "Could not get connected SSID")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting connected SSID", e)
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
        if (securityType == "EAP") {
            onResult(false, "Mạng doanh nghiệp (EAP) không được hỗ trợ")
            return
        }
        
        if (!hasLocationPermission()) {
            onResult(false, "Cần cấp quyền vị trí để kết nối WiFi")
            return
        }
        
        android.util.Log.d(TAG, "Connecting to WiFi: $ssid using location permission method")
        
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
                        securityType == "WPA3" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                            specifierBuilder.setWpa3Passphrase(password)
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
                                onResult(false, "Không thể kết nối với $ssid. Kiểm tra mật khẩu.")
                            }
                            unregisterNetworkCallback()
                        }
                    }
                    
                    override fun onLost(network: Network) {
                        super.onLost(network)
                        unregisterNetworkCallback()
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
            
            when {
                !isSecured || password.isNullOrEmpty() -> {
                    wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
                securityType == "WEP" -> {
                    if (password.length == 10 || password.length == 26 || password.length == 58) {
                        wifiConfig.wepKeys[0] = password
                    } else {
                        wifiConfig.wepKeys[0] = "\"$password\""
                    }
                    wifiConfig.wepTxKeyIndex = 0
                    wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                    wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                    wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                }
                else -> {
                    wifiConfig.preSharedKey = "\"$password\""
                    wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                    wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                    wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                    wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                    wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                    wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                }
            }
            
            val existingConfig = try {
                wifiManager.configuredNetworks?.find { it.SSID == "\"$ssid\"" }
            } catch (e: SecurityException) {
                null
            }
            
            val networkId = if (existingConfig != null) {
                try {
                    wifiManager.removeNetwork(existingConfig.networkId)
                } catch (e: Exception) { }
                wifiManager.addNetwork(wifiConfig)
            } else {
                wifiManager.addNetwork(wifiConfig)
            }
            
            if (networkId == -1) {
                onResult(false, "Không thể thêm mạng. Kiểm tra quyền ứng dụng.")
                return
            }
            
            try {
                wifiManager.saveConfiguration()
            } catch (e: Exception) { }
            
            wifiManager.disconnect()
            val enabled = wifiManager.enableNetwork(networkId, true)
            
            if (!enabled) {
                onResult(false, "Không thể bật mạng. Vui lòng thử lại.")
                return
            }
            
            wifiManager.reconnect()
            
            handler.postDelayed({
                val currentSsid = getCurrentConnectedSsid()
                if (currentSsid == ssid) {
                    onResult(true, "Đã kết nối với $ssid")
                } else {
                    onResult(false, "Không thể kết nối. Kiểm tra mật khẩu.")
                }
            }, 6000)
            
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
