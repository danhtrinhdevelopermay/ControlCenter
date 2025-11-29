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
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var gestureDetectorView: View? = null
    private var notificationGestureView: View? = null
    private var screenWidth = 0
    private var screenHeight = 0

    private var startY = 0f
    private var startX = 0f
    private var isDragging = false
    private var velocityTracker: VelocityTracker? = null

    private var notificationStartY = 0f
    private var notificationStartX = 0f
    private var isNotificationDragging = false
    private var notificationVelocityTracker: VelocityTracker? = null

    companion object {
        var instance: GestureAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        setupGestureDetector()
        setupNotificationGestureDetector()
    }

    fun refreshGestureDetector() {
        removeGestureDetector()
        removeNotificationGestureDetector()
        setupGestureDetector()
        setupNotificationGestureDetector()
    }

    private fun removeGestureDetector() {
        gestureDetectorView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        gestureDetectorView = null
    }

    private fun removeNotificationGestureDetector() {
        notificationGestureView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        notificationGestureView = null
    }

    private fun setupGestureDetector() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        val xPercent = SwipeZoneSettings.getZoneXPercent(this)
        val widthPercent = SwipeZoneSettings.getZoneWidthPercent(this)
        val zoneHeight = SwipeZoneSettings.getZoneHeight(this)

        val gestureAreaX = (screenWidth * xPercent / 100f).toInt()
        val gestureAreaWidth = (screenWidth * widthPercent / 100f).toInt().coerceAtLeast(50)

        val params = WindowManager.LayoutParams(
            gestureAreaWidth,
            zoneHeight,
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

        params.gravity = Gravity.TOP or Gravity.START
        params.x = gestureAreaX
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

    private fun setupNotificationGestureDetector() {
        if (!NotificationZoneSettings.isEnabled(this)) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        val xPercent = NotificationZoneSettings.getZoneXPercent(this)
        val widthPercent = NotificationZoneSettings.getZoneWidthPercent(this)
        val zoneHeight = NotificationZoneSettings.getZoneHeight(this)

        val gestureAreaX = (screenWidth * xPercent / 100f).toInt()
        val gestureAreaWidth = (screenWidth * widthPercent / 100f).toInt().coerceAtLeast(50)

        val params = WindowManager.LayoutParams(
            gestureAreaWidth,
            zoneHeight,
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

        params.gravity = Gravity.TOP or Gravity.START
        params.x = gestureAreaX
        params.y = 0

        notificationGestureView = View(this).apply {
            setBackgroundColor(0x00000000)

            setOnTouchListener { _, event ->
                handleNotificationTouch(event)
                true
            }
        }

        try {
            windowManager?.addView(notificationGestureView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (ControlCenterService.isShowing || NotificationCenterService.isShowing) return true
                
                startX = event.rawX
                startY = event.rawY
                isDragging = true
                
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                
                sendDragStart()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    velocityTracker?.addMovement(event)
                    
                    val dragY = event.rawY - startY
                    if (dragY > 0) {
                        sendDragUpdate(dragY)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    
                    sendDragEnd(velocityY)
                    
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
            }
        }
        return true
    }

    private fun handleNotificationTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (ControlCenterService.isShowing || NotificationCenterService.isShowing) return true
                
                notificationStartX = event.rawX
                notificationStartY = event.rawY
                isNotificationDragging = true
                
                notificationVelocityTracker?.recycle()
                notificationVelocityTracker = VelocityTracker.obtain()
                notificationVelocityTracker?.addMovement(event)
                
                sendNotificationDragStart()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isNotificationDragging) {
                    notificationVelocityTracker?.addMovement(event)
                    
                    val dragY = event.rawY - notificationStartY
                    if (dragY > 0) {
                        sendNotificationDragUpdate(dragY)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isNotificationDragging) {
                    isNotificationDragging = false
                    
                    notificationVelocityTracker?.addMovement(event)
                    notificationVelocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = notificationVelocityTracker?.yVelocity ?: 0f
                    
                    sendNotificationDragEnd(velocityY)
                    
                    notificationVelocityTracker?.recycle()
                    notificationVelocityTracker = null
                }
            }
        }
        return true
    }

    private fun sendDragStart() {
        val intent = Intent(this, ControlCenterService::class.java)
        intent.action = ControlCenterService.ACTION_DRAG_START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun sendDragUpdate(dragY: Float) {
        val intent = Intent(this, ControlCenterService::class.java)
        intent.action = ControlCenterService.ACTION_DRAG_UPDATE
        intent.putExtra(ControlCenterService.EXTRA_DRAG_Y, dragY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun sendDragEnd(velocityY: Float) {
        val intent = Intent(this, ControlCenterService::class.java)
        intent.action = ControlCenterService.ACTION_DRAG_END
        intent.putExtra(ControlCenterService.EXTRA_VELOCITY_Y, velocityY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun sendNotificationDragStart() {
        val intent = Intent(this, NotificationCenterService::class.java)
        intent.action = NotificationCenterService.ACTION_DRAG_START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun sendNotificationDragUpdate(dragY: Float) {
        val intent = Intent(this, NotificationCenterService::class.java)
        intent.action = NotificationCenterService.ACTION_DRAG_UPDATE
        intent.putExtra(NotificationCenterService.EXTRA_DRAG_Y, dragY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun sendNotificationDragEnd(velocityY: Float) {
        val intent = Intent(this, NotificationCenterService::class.java)
        intent.action = NotificationCenterService.ACTION_DRAG_END
        intent.putExtra(NotificationCenterService.EXTRA_VELOCITY_Y, velocityY)
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
        velocityTracker?.recycle()
        velocityTracker = null
        notificationVelocityTracker?.recycle()
        notificationVelocityTracker = null
        removeGestureDetector()
        removeNotificationGestureDetector()
    }
}
