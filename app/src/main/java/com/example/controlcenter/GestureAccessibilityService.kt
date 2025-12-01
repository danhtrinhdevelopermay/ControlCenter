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

    private var controlCenterWindowManager: WindowManager? = null
    private var notificationWindowManager: WindowManager? = null
    private var gestureDetectorView: View? = null
    private var notificationGestureView: View? = null
    private var screenWidth = 0
    private var screenHeight = 0

    private var startY = 0f
    private var startX = 0f
    private var isDragging = false
    private var velocityTracker: VelocityTracker? = null
    private var hasSentDragStart = false

    private var notificationStartY = 0f
    private var notificationStartX = 0f
    private var isNotificationDragging = false
    private var notificationVelocityTracker: VelocityTracker? = null
    private var hasNotificationSentDragStart = false

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
        
        ensureControlCenterServiceRunning()
    }
    
    private fun ensureControlCenterServiceRunning() {
        val intent = Intent(this, ControlCenterService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
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
                controlCenterWindowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        gestureDetectorView = null
    }

    private fun removeNotificationGestureDetector() {
        notificationGestureView?.let {
            try {
                notificationWindowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        notificationGestureView = null
    }

    private fun setupGestureDetector() {
        controlCenterWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        controlCenterWindowManager?.defaultDisplay?.getMetrics(displayMetrics)
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
            controlCenterWindowManager?.addView(gestureDetectorView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupNotificationGestureDetector() {
        if (!NotificationZoneSettings.isEnabled(this)) return

        notificationWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        notificationWindowManager?.defaultDisplay?.getMetrics(displayMetrics)
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
            notificationWindowManager?.addView(notificationGestureView, params)
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
                hasSentDragStart = false
                
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    velocityTracker?.addMovement(event)
                    
                    val dragY = event.rawY - startY
                    if (dragY > 5) {
                        if (!hasSentDragStart) {
                            hasSentDragStart = true
                            sendDragStartDirect()
                        }
                        sendDragUpdateDirect(dragY)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    
                    if (hasSentDragStart) {
                        sendDragEndDirect(velocityY)
                    }
                    
                    hasSentDragStart = false
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
                hasNotificationSentDragStart = false
                
                notificationVelocityTracker?.recycle()
                notificationVelocityTracker = VelocityTracker.obtain()
                notificationVelocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isNotificationDragging) {
                    notificationVelocityTracker?.addMovement(event)
                    
                    val dragY = event.rawY - notificationStartY
                    if (dragY > 5) {
                        if (!hasNotificationSentDragStart) {
                            hasNotificationSentDragStart = true
                            sendNotificationDragStartDirect()
                        }
                        sendNotificationDragUpdateDirect(dragY)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isNotificationDragging) {
                    isNotificationDragging = false
                    
                    notificationVelocityTracker?.addMovement(event)
                    notificationVelocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = notificationVelocityTracker?.yVelocity ?: 0f
                    
                    if (hasNotificationSentDragStart) {
                        sendNotificationDragEndDirect(velocityY)
                    }
                    
                    hasNotificationSentDragStart = false
                    notificationVelocityTracker?.recycle()
                    notificationVelocityTracker = null
                }
            }
        }
        return true
    }
    
    private fun sendDragStartDirect() {
        val service = ControlCenterService.getInstance()
        if (service != null) {
            service.handleDragStartDirect()
        } else {
            sendDragStartIntent()
        }
    }
    
    private fun sendDragUpdateDirect(dragY: Float) {
        val service = ControlCenterService.getInstance()
        if (service != null) {
            service.handleDragUpdateDirect(dragY)
        }
    }
    
    private fun sendDragEndDirect(velocityY: Float) {
        val service = ControlCenterService.getInstance()
        if (service != null) {
            service.handleDragEndDirect(velocityY)
        }
    }
    
    private fun sendDragStartIntent() {
        val intent = Intent(this, ControlCenterService::class.java)
        intent.action = ControlCenterService.ACTION_DRAG_START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun sendNotificationDragStartDirect() {
        val service = NotificationCenterService.getInstance()
        if (service != null) {
            service.handleDragStartDirect()
        } else {
            sendNotificationDragStartIntent()
        }
    }
    
    private fun sendNotificationDragUpdateDirect(dragY: Float) {
        val service = NotificationCenterService.getInstance()
        if (service != null) {
            service.handleDragUpdateDirect(dragY)
        }
    }
    
    private fun sendNotificationDragEndDirect(velocityY: Float) {
        val service = NotificationCenterService.getInstance()
        if (service != null) {
            service.handleDragEndDirect(velocityY)
        }
    }
    
    private fun sendNotificationDragStartIntent() {
        val intent = Intent(this, NotificationCenterService::class.java)
        intent.action = NotificationCenterService.ACTION_DRAG_START
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
