package com.example.controlcenter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var gestureDetectorView: View? = null
    private var screenWidth = 0
    private var screenHeight = 0

    private var startY = 0f
    private var startX = 0f
    private val swipeThreshold = 150

    companion object {
        var instance: GestureAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        setupGestureDetector()
    }

    private fun setupGestureDetector() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        val gestureAreaWidth = screenWidth / 3
        val gestureAreaHeight = 100

        val params = WindowManager.LayoutParams(
            gestureAreaWidth,
            gestureAreaHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 0
        params.y = 0

        gestureDetectorView = View(this).apply {
            setBackgroundColor(0x00000000)

            setOnTouchListener { _, event ->
                handleTouch(event)
                true
            }
        }

        try {
            windowManager?.addView(gestureDetectorView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                val deltaY = event.rawY - startY
                val deltaX = event.rawX - startX

                if (deltaY > swipeThreshold && Math.abs(deltaX) < swipeThreshold) {
                    showControlCenter()
                }
            }
        }
        return true
    }

    private fun showControlCenter() {
        val intent = Intent(this, ControlCenterService::class.java)
        intent.action = ControlCenterService.ACTION_SHOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        gestureDetectorView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
