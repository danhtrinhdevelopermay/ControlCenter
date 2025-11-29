package com.example.controlcenter

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
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    private lateinit var statusText: TextView
    private lateinit var overlayPermissionBtn: Button
    private lateinit var accessibilityPermissionBtn: Button
    private lateinit var notificationPermissionBtn: Button
    private lateinit var shizukuPermissionBtn: Button
    private lateinit var startServiceBtn: Button
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

    private var isPreviewingZone = false

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        loadSwipeZoneSettings()
        
        Shizuku.addRequestPermissionResultListener(this)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
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
    }

    override fun onStop() {
        super.onStop()
        hideZonePreview()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        overlayPermissionBtn = findViewById(R.id.overlayPermissionBtn)
        accessibilityPermissionBtn = findViewById(R.id.accessibilityPermissionBtn)
        notificationPermissionBtn = findViewById(R.id.notificationPermissionBtn)
        shizukuPermissionBtn = findViewById(R.id.shizukuPermissionBtn)
        startServiceBtn = findViewById(R.id.startServiceBtn)
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

        startServiceBtn.setOnClickListener {
            startControlCenterService()
        }

        setupSeekBarListeners()
        setupZoneSettingsButtons()
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

        val allPermissionsGranted = hasOverlayPermission && hasAccessibilityPermission
        startServiceBtn.apply {
            isEnabled = allPermissionsGranted
            visibility = if (allPermissionsGranted) View.VISIBLE else View.GONE
        }

        swipeZoneSettingsContainer.visibility = if (allPermissionsGranted) View.VISIBLE else View.GONE

        statusText.text = when {
            !hasOverlayPermission -> "Step 1: Grant overlay permission to display Control Center over other apps"
            !hasAccessibilityPermission -> "Step 2: Enable accessibility service to detect swipe gestures"
            !hasNotificationAccess -> "Step 3: Grant notification access to display current music info (song title, artist, album art)"
            !isShizukuAvailable -> "Step 4 (Optional): Install and start Shizuku app for advanced features (WiFi, Bluetooth toggles)\n\nYou can still use Control Center without Shizuku, but some features will be limited."
            !hasShizukuPermission -> "Step 4 (Optional): Grant Shizuku permission for advanced system control\n\nThis enables WiFi, Bluetooth, DND, and Airplane Mode toggles."
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
