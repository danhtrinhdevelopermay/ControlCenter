package com.example.controlcenter

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var overlayPermissionBtn: Button
    private lateinit var accessibilityPermissionBtn: Button
    private lateinit var startServiceBtn: Button

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        overlayPermissionBtn = findViewById(R.id.overlayPermissionBtn)
        accessibilityPermissionBtn = findViewById(R.id.accessibilityPermissionBtn)
        startServiceBtn = findViewById(R.id.startServiceBtn)
    }

    private fun setupClickListeners() {
        overlayPermissionBtn.setOnClickListener {
            requestOverlayPermission()
        }

        accessibilityPermissionBtn.setOnClickListener {
            openAccessibilitySettings()
        }

        startServiceBtn.setOnClickListener {
            startControlCenterService()
        }
    }

    private fun updateUI() {
        val hasOverlayPermission = hasOverlayPermission()
        val hasAccessibilityPermission = isAccessibilityServiceEnabled()

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

        val allPermissionsGranted = hasOverlayPermission && hasAccessibilityPermission
        startServiceBtn.apply {
            isEnabled = allPermissionsGranted
            visibility = if (allPermissionsGranted) View.VISIBLE else View.GONE
        }

        statusText.text = when {
            !hasOverlayPermission -> "Step 1: Grant overlay permission to display Control Center over other apps"
            !hasAccessibilityPermission -> "Step 2: Enable accessibility service to detect swipe gestures"
            else -> "All set! Control Center is active.\n\nSwipe down from the top-right corner of your screen to open."
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
                "Control Center is now active! Swipe down from top-right corner.",
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
