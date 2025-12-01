package com.example.controlcenter

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    private lateinit var statusText: TextView
    private lateinit var overlayPermissionBtn: Button
    private lateinit var accessibilityPermissionBtn: Button
    private lateinit var notificationPermissionBtn: Button
    private lateinit var shizukuPermissionBtn: Button
    private lateinit var locationPermissionBtn: Button
    private lateinit var writeSettingsPermissionBtn: Button
    private lateinit var startServiceBtn: Button
    private lateinit var appearanceSettingsBtn: Button
    private lateinit var swipeZoneSettingsContainer: LinearLayout
    private lateinit var zoneXSeekBar: SeekBar
    private lateinit var zoneWidthSeekBar: SeekBar
    private lateinit var zoneHeightSeekBar: SeekBar
    private lateinit var zoneXValue: TextView
    private lateinit var zoneWidthValue: TextView
    private lateinit var zoneHeightValue: TextView
    private lateinit var previewZoneBtn: Button
    private lateinit var resetZoneBtn: Button
    private lateinit var applyZoneBtn: Button
    
    private lateinit var notificationZoneSettingsContainer: LinearLayout
    private lateinit var notificationZoneSwitch: SwitchCompat
    private lateinit var notificationZoneControls: LinearLayout
    private lateinit var notifZoneXSeekBar: SeekBar
    private lateinit var notifZoneWidthSeekBar: SeekBar
    private lateinit var notifZoneHeightSeekBar: SeekBar
    private lateinit var notifZoneXValue: TextView
    private lateinit var notifZoneWidthValue: TextView
    private lateinit var notifZoneHeightValue: TextView
    private lateinit var previewNotifZoneBtn: Button
    private lateinit var resetNotifZoneBtn: Button
    private lateinit var applyNotifZoneBtn: Button

    private var isPreviewingZone = false
    private var isPreviewingNotifZone = false

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1002
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        loadSwipeZoneSettings()
        loadNotificationZoneSettings()
        
        Shizuku.addRequestPermissionResultListener(this)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        
        if (MediaNotificationListener.isNotificationAccessEnabled(this) && 
            !MediaNotificationListener.isServiceConnected()) {
            MediaNotificationListener.requestRebind(this)
        }
        
        // Auto-request location permission if not granted yet
        if (!hasLocationPermission() && hasOverlayPermission() && isAccessibilityServiceEnabled()) {
            requestLocationPermission()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(this)
    }
    
    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (granted) {
                Toast.makeText(this, "Shizuku permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            }
            updateUI()
        }
    }

    override fun onPause() {
        super.onPause()
        hideZonePreview()
        hideNotifZonePreview()
    }

    override fun onStop() {
        super.onStop()
        hideZonePreview()
        hideNotifZonePreview()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        overlayPermissionBtn = findViewById(R.id.overlayPermissionBtn)
        accessibilityPermissionBtn = findViewById(R.id.accessibilityPermissionBtn)
        notificationPermissionBtn = findViewById(R.id.notificationPermissionBtn)
        shizukuPermissionBtn = findViewById(R.id.shizukuPermissionBtn)
        locationPermissionBtn = findViewById(R.id.locationPermissionBtn)
        writeSettingsPermissionBtn = findViewById(R.id.writeSettingsPermissionBtn)
        startServiceBtn = findViewById(R.id.startServiceBtn)
        appearanceSettingsBtn = findViewById(R.id.appearanceSettingsBtn)
        swipeZoneSettingsContainer = findViewById(R.id.swipeZoneSettingsContainer)
        zoneXSeekBar = findViewById(R.id.zoneXSeekBar)
        zoneWidthSeekBar = findViewById(R.id.zoneWidthSeekBar)
        zoneHeightSeekBar = findViewById(R.id.zoneHeightSeekBar)
        zoneXValue = findViewById(R.id.zoneXValue)
        zoneWidthValue = findViewById(R.id.zoneWidthValue)
        zoneHeightValue = findViewById(R.id.zoneHeightValue)
        previewZoneBtn = findViewById(R.id.previewZoneBtn)
        resetZoneBtn = findViewById(R.id.resetZoneBtn)
        applyZoneBtn = findViewById(R.id.applyZoneBtn)
        
        notificationZoneSettingsContainer = findViewById(R.id.notificationZoneSettingsContainer)
        notificationZoneSwitch = findViewById(R.id.notificationZoneSwitch)
        notificationZoneControls = findViewById(R.id.notificationZoneControls)
        notifZoneXSeekBar = findViewById(R.id.notifZoneXSeekBar)
        notifZoneWidthSeekBar = findViewById(R.id.notifZoneWidthSeekBar)
        notifZoneHeightSeekBar = findViewById(R.id.notifZoneHeightSeekBar)
        notifZoneXValue = findViewById(R.id.notifZoneXValue)
        notifZoneWidthValue = findViewById(R.id.notifZoneWidthValue)
        notifZoneHeightValue = findViewById(R.id.notifZoneHeightValue)
        previewNotifZoneBtn = findViewById(R.id.previewNotifZoneBtn)
        resetNotifZoneBtn = findViewById(R.id.resetNotifZoneBtn)
        applyNotifZoneBtn = findViewById(R.id.applyNotifZoneBtn)
    }

    private fun setupClickListeners() {
        overlayPermissionBtn.setOnClickListener {
            requestOverlayPermission()
        }

        accessibilityPermissionBtn.setOnClickListener {
            openAccessibilitySettings()
        }
        
        notificationPermissionBtn.setOnClickListener {
            openNotificationListenerSettings()
        }
        
        shizukuPermissionBtn.setOnClickListener {
            requestShizukuPermission()
        }
        
        locationPermissionBtn.setOnClickListener {
            requestLocationPermission()
        }
        
        writeSettingsPermissionBtn.setOnClickListener {
            requestWriteSettingsPermission()
        }

        startServiceBtn.setOnClickListener {
            startControlCenterService()
        }

        appearanceSettingsBtn.setOnClickListener {
            val intent = Intent(this, AppearanceSettingsActivity::class.java)
            startActivity(intent)
        }

        setupSeekBarListeners()
        setupZoneSettingsButtons()
        setupNotificationZoneListeners()
        setupNotificationZoneButtons()
    }

    private fun setupSeekBarListeners() {
        zoneXSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                zoneXValue.text = "${progress}%"
                if (fromUser && isPreviewingZone) {
                    SwipeZoneSettings.setZoneXPercent(this@MainActivity, progress)
                    SwipeZoneOverlayManager.update(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        zoneWidthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualProgress = progress.coerceAtLeast(SwipeZoneSettings.MIN_WIDTH_PERCENT)
                zoneWidthValue.text = "${actualProgress}%"
                if (fromUser && isPreviewingZone) {
                    SwipeZoneSettings.setZoneWidthPercent(this@MainActivity, actualProgress)
                    SwipeZoneOverlayManager.update(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        zoneHeightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualProgress = progress.coerceAtLeast(SwipeZoneSettings.MIN_HEIGHT)
                zoneHeightValue.text = "${actualProgress}px"
                if (fromUser && isPreviewingZone) {
                    SwipeZoneSettings.setZoneHeight(this@MainActivity, actualProgress)
                    SwipeZoneOverlayManager.update(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupZoneSettingsButtons() {
        previewZoneBtn.setOnClickListener {
            if (isPreviewingZone) {
                hideZonePreview()
            } else {
                showZonePreview()
            }
        }

        resetZoneBtn.setOnClickListener {
            SwipeZoneSettings.resetToDefaults(this)
            loadSwipeZoneSettings()
            if (isPreviewingZone) {
                SwipeZoneOverlayManager.update(this)
            }
            Toast.makeText(this, "Reset to default settings", Toast.LENGTH_SHORT).show()
        }

        applyZoneBtn.setOnClickListener {
            saveSwipeZoneSettings()
            hideZonePreview()
            restartGestureService()
            Toast.makeText(this, "Settings applied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showZonePreview() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
            return
        }
        saveSwipeZoneSettings()
        isPreviewingZone = true
        previewZoneBtn.text = "Hide Preview"
        previewZoneBtn.setBackgroundResource(R.drawable.control_item_background_active)
        SwipeZoneOverlayManager.show(this)
    }

    private fun hideZonePreview() {
        if (isPreviewingZone) {
            isPreviewingZone = false
            previewZoneBtn.text = "Preview Zone"
            previewZoneBtn.setBackgroundResource(R.drawable.control_item_background)
            SwipeZoneOverlayManager.hide()
        }
    }
    
    private fun setupNotificationZoneListeners() {
        notificationZoneSwitch.setOnCheckedChangeListener { _, isChecked ->
            NotificationZoneSettings.setEnabled(this, isChecked)
            notificationZoneControls.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                hideNotifZonePreview()
            }
            restartGestureService()
        }
        
        notifZoneXSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                notifZoneXValue.text = "${progress}%"
                if (fromUser && isPreviewingNotifZone) {
                    NotificationZoneSettings.setZoneXPercent(this@MainActivity, progress)
                    NotificationZoneOverlayManager.update(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        notifZoneWidthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualProgress = progress.coerceAtLeast(NotificationZoneSettings.MIN_WIDTH_PERCENT)
                notifZoneWidthValue.text = "${actualProgress}%"
                if (fromUser && isPreviewingNotifZone) {
                    NotificationZoneSettings.setZoneWidthPercent(this@MainActivity, actualProgress)
                    NotificationZoneOverlayManager.update(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        notifZoneHeightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualProgress = progress.coerceAtLeast(NotificationZoneSettings.MIN_HEIGHT)
                notifZoneHeightValue.text = "${actualProgress}px"
                if (fromUser && isPreviewingNotifZone) {
                    NotificationZoneSettings.setZoneHeight(this@MainActivity, actualProgress)
                    NotificationZoneOverlayManager.update(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupNotificationZoneButtons() {
        previewNotifZoneBtn.setOnClickListener {
            if (isPreviewingNotifZone) {
                hideNotifZonePreview()
            } else {
                showNotifZonePreview()
            }
        }

        resetNotifZoneBtn.setOnClickListener {
            NotificationZoneSettings.resetToDefaults(this)
            loadNotificationZoneSettings()
            if (isPreviewingNotifZone) {
                NotificationZoneOverlayManager.update(this)
            }
            Toast.makeText(this, "Reset to default settings", Toast.LENGTH_SHORT).show()
        }

        applyNotifZoneBtn.setOnClickListener {
            saveNotificationZoneSettings()
            hideNotifZonePreview()
            restartGestureService()
            Toast.makeText(this, "Settings applied", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showNotifZonePreview() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
            return
        }
        saveNotificationZoneSettings()
        isPreviewingNotifZone = true
        previewNotifZoneBtn.text = "Hide Preview"
        previewNotifZoneBtn.setBackgroundResource(R.drawable.control_item_background_active)
        NotificationZoneOverlayManager.show(this)
    }

    private fun hideNotifZonePreview() {
        if (isPreviewingNotifZone) {
            isPreviewingNotifZone = false
            previewNotifZoneBtn.text = "Preview Zone"
            previewNotifZoneBtn.setBackgroundResource(R.drawable.control_item_background)
            NotificationZoneOverlayManager.hide()
        }
    }
    
    private fun loadNotificationZoneSettings() {
        val enabled = NotificationZoneSettings.isEnabled(this)
        val xPercent = NotificationZoneSettings.getZoneXPercent(this)
        val widthPercent = NotificationZoneSettings.getZoneWidthPercent(this)
        val height = NotificationZoneSettings.getZoneHeight(this)

        notificationZoneSwitch.isChecked = enabled
        notificationZoneControls.visibility = if (enabled) View.VISIBLE else View.GONE
        
        notifZoneXSeekBar.progress = xPercent
        notifZoneWidthSeekBar.progress = widthPercent
        notifZoneHeightSeekBar.progress = height

        notifZoneXValue.text = "${xPercent}%"
        notifZoneWidthValue.text = "${widthPercent}%"
        notifZoneHeightValue.text = "${height}px"
    }

    private fun saveNotificationZoneSettings() {
        NotificationZoneSettings.setZoneXPercent(this, notifZoneXSeekBar.progress)
        NotificationZoneSettings.setZoneWidthPercent(this, notifZoneWidthSeekBar.progress.coerceAtLeast(10))
        NotificationZoneSettings.setZoneHeight(this, notifZoneHeightSeekBar.progress.coerceAtLeast(50))
    }

    private fun loadSwipeZoneSettings() {
        val xPercent = SwipeZoneSettings.getZoneXPercent(this)
        val widthPercent = SwipeZoneSettings.getZoneWidthPercent(this)
        val height = SwipeZoneSettings.getZoneHeight(this)

        zoneXSeekBar.progress = xPercent
        zoneWidthSeekBar.progress = widthPercent
        zoneHeightSeekBar.progress = height

        zoneXValue.text = "${xPercent}%"
        zoneWidthValue.text = "${widthPercent}%"
        zoneHeightValue.text = "${height}px"
    }

    private fun saveSwipeZoneSettings() {
        SwipeZoneSettings.setZoneXPercent(this, zoneXSeekBar.progress)
        SwipeZoneSettings.setZoneWidthPercent(this, zoneWidthSeekBar.progress.coerceAtLeast(10))
        SwipeZoneSettings.setZoneHeight(this, zoneHeightSeekBar.progress.coerceAtLeast(50))
    }

    private fun restartGestureService() {
        GestureAccessibilityService.instance?.refreshGestureDetector()
    }

    private fun updateUI() {
        val hasOverlayPermission = hasOverlayPermission()
        val hasAccessibilityPermission = isAccessibilityServiceEnabled()
        val hasNotificationAccess = MediaNotificationListener.isNotificationAccessEnabled(this)
        val hasShizukuPermission = ShizukuHelper.checkShizukuPermission()
        val isShizukuAvailable = ShizukuHelper.isShizukuAvailable()
        val hasLocationPermission = hasLocationPermission()

        overlayPermissionBtn.apply {
            isEnabled = !hasOverlayPermission
            text = if (hasOverlayPermission) "✓ Overlay Permission Granted" else "Grant Overlay Permission"
            setBackgroundColor(
                if (hasOverlayPermission) ContextCompat.getColor(context, R.color.control_item_active)
                else ContextCompat.getColor(context, R.color.control_item_inactive)
            )
        }

        accessibilityPermissionBtn.apply {
            isEnabled = !hasAccessibilityPermission
            text = if (hasAccessibilityPermission) "✓ Accessibility Service Enabled" else "Enable Accessibility Service"
            setBackgroundColor(
                if (hasAccessibilityPermission) ContextCompat.getColor(context, R.color.control_item_active)
                else ContextCompat.getColor(context, R.color.control_item_inactive)
            )
        }
        
        notificationPermissionBtn.apply {
            isEnabled = !hasNotificationAccess
            text = if (hasNotificationAccess) "✓ Notification Access Granted" else "Grant Notification Access (Media Info)"
            setBackgroundColor(
                if (hasNotificationAccess) ContextCompat.getColor(context, R.color.control_item_active)
                else ContextCompat.getColor(context, R.color.control_item_inactive)
            )
        }
        
        shizukuPermissionBtn.apply {
            isEnabled = isShizukuAvailable && !hasShizukuPermission
            text = when {
                !isShizukuAvailable -> "⚠ Shizuku Not Running"
                hasShizukuPermission -> "✓ Shizuku Permission Granted"
                else -> "Grant Shizuku Permission"
            }
            setBackgroundColor(
                when {
                    !isShizukuAvailable -> ContextCompat.getColor(context, android.R.color.darker_gray)
                    hasShizukuPermission -> ContextCompat.getColor(context, R.color.control_item_active)
                    else -> ContextCompat.getColor(context, R.color.control_item_inactive)
                }
            )
        }
        
        locationPermissionBtn.apply {
            isEnabled = !hasLocationPermission
            text = if (hasLocationPermission) "✓ Location Permission Granted (WiFi SSID)" else "Grant Location Permission (WiFi SSID)"
            setBackgroundColor(
                if (hasLocationPermission) ContextCompat.getColor(context, R.color.control_item_active)
                else ContextCompat.getColor(context, R.color.control_item_inactive)
            )
        }
        
        val hasWriteSettingsPermission = SystemControlHelper.canWriteSettings(this)
        writeSettingsPermissionBtn.apply {
            isEnabled = !hasWriteSettingsPermission
            text = if (hasWriteSettingsPermission) "✓ Write Settings Permission Granted (Brightness)" else "Grant Write Settings Permission (Brightness)"
            setBackgroundColor(
                if (hasWriteSettingsPermission) ContextCompat.getColor(context, R.color.control_item_active)
                else ContextCompat.getColor(context, R.color.control_item_inactive)
            )
        }

        val allPermissionsGranted = hasOverlayPermission && hasAccessibilityPermission
        startServiceBtn.apply {
            isEnabled = allPermissionsGranted
            visibility = if (allPermissionsGranted) View.VISIBLE else View.GONE
        }

        swipeZoneSettingsContainer.visibility = if (allPermissionsGranted) View.VISIBLE else View.GONE
        notificationZoneSettingsContainer.visibility = if (allPermissionsGranted) View.VISIBLE else View.GONE
        appearanceSettingsBtn.visibility = if (allPermissionsGranted) View.VISIBLE else View.GONE

        statusText.text = when {
            !hasOverlayPermission -> "Step 1: Grant overlay permission to display Control Center over other apps"
            !hasAccessibilityPermission -> "Step 2: Enable accessibility service to detect swipe gestures"
            !hasNotificationAccess -> "Step 3: Grant notification access to display current music info (song title, artist, album art)"
            !hasLocationPermission -> "Step 4: Grant location permission to display WiFi network name (SSID)\n\nRequired on Android 10+ to show connected WiFi name."
            !isShizukuAvailable -> "Step 5 (Optional): Install and start Shizuku app for advanced features (WiFi, Bluetooth toggles)\n\nYou can still use Control Center without Shizuku, but some features will be limited."
            !hasShizukuPermission -> "Step 5 (Optional): Grant Shizuku permission for advanced system control\n\nThis enables WiFi, Bluetooth, DND, and Airplane Mode toggles."
            else -> "All set! Control Center is active.\n\nSwipe down from the configured zone to open.\n\n✓ Shizuku enabled for full system control!\n✓ Notification access enabled for media info!"
        }

        if (allPermissionsGranted) {
            startControlCenterService()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        Toast.makeText(
            this,
            "Find 'Control Center' in the list and enable it",
            Toast.LENGTH_LONG
        ).show()
    }
    
    private fun openNotificationListenerSettings() {
        MediaNotificationListener.openNotificationAccessSettings(this)
        
        Toast.makeText(
            this,
            "Find 'Control Center' in the list and enable notification access for media info",
            Toast.LENGTH_LONG
        ).show()
    }
    
    private fun requestShizukuPermission() {
        if (!ShizukuHelper.isShizukuAvailable()) {
            Toast.makeText(
                this,
                "Shizuku is not running. Please install and start Shizuku app first.\n\nShizuku enables WiFi, Bluetooth, and other system toggles.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        if (ShizukuHelper.checkShizukuPermission()) {
            Toast.makeText(this, "Shizuku permission already granted!", Toast.LENGTH_SHORT).show()
            return
        }
        
        ShizukuHelper.requestShizukuPermission()
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        if (hasLocationPermission()) {
            Toast.makeText(this, "Location permission already granted!", Toast.LENGTH_SHORT).show()
            return
        }
        
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Location permission granted! WiFi SSID will now be displayed.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Location permission denied. WiFi name will not be shown.", Toast.LENGTH_SHORT).show()
                }
                updateUI()
            }
        }
    }
    
    private fun requestWriteSettingsPermission() {
        if (SystemControlHelper.canWriteSettings(this)) {
            Toast.makeText(this, "Write settings permission already granted!", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(
            this,
            "Bật quyền 'Cho phép sửa đổi cài đặt hệ thống' để điều khiển độ sáng màn hình",
            Toast.LENGTH_LONG
        ).show()
        
        SystemControlHelper.openWriteSettingsPermission(this)
    }

    private fun startControlCenterService() {
        if (hasOverlayPermission() && isAccessibilityServiceEnabled()) {
            val intent = Intent(this, ControlCenterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            Toast.makeText(
                this,
                "Control Center is now active! Swipe down from the configured zone.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            updateUI()
        }
    }
}
