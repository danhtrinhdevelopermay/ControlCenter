package com.example.controlcenter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Binder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Toast
import android.text.InputType
import android.graphics.Color
import android.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import android.view.VelocityTracker
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.animation.OvershootInterpolator
import android.provider.Settings
import android.os.UserManager

class ControlCenterService : Service() {

    companion object {
        const val ACTION_SHOW = "com.example.controlcenter.ACTION_SHOW"
        const val ACTION_HIDE = "com.example.controlcenter.ACTION_HIDE"
        const val ACTION_DRAG_START = "com.example.controlcenter.ACTION_DRAG_START"
        const val ACTION_DRAG_UPDATE = "com.example.controlcenter.ACTION_DRAG_UPDATE"
        const val ACTION_DRAG_END = "com.example.controlcenter.ACTION_DRAG_END"
        const val EXTRA_DRAG_Y = "drag_y"
        const val EXTRA_VELOCITY_Y = "velocity_y"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "control_center_channel"

        var isShowing = false
            private set
        
        var isInteractiveDragging = false
            private set
            
        private var serviceInstance: ControlCenterService? = null
        
        fun getInstance(): ControlCenterService? = serviceInstance
        
        fun directDragStart() {
            serviceInstance?.handleDragStartDirect()
        }
        
        fun directDragUpdate(dragY: Float) {
            serviceInstance?.handleDragUpdateDirect(dragY)
        }
        
        fun directDragEnd(velocityY: Float) {
            serviceInstance?.handleDragEndDirect(velocityY)
        }
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): ControlCenterService = this@ControlCenterService
    }
    
    private val binder = LocalBinder()

    private var windowManager: WindowManager? = null
    private var controlCenterView: View? = null
    private var backgroundView: View? = null
    private var blurAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    private var screenWidth = 0
    private var screenHeight = 0
    private var panelHeight = 0
    private var panelMeasured = false

    private var startY = 0f
    private var startX = 0f
    private var currentTranslationY = 0f
    private var currentTranslationX = 0f
    private var velocityTracker: VelocityTracker? = null
    private var isDragging = false
    private var isHiding = false
    private var isHorizontalSwipe = false
    private var currentAnimation: SpringAnimation? = null
    private var horizontalAnimation: SpringAnimation? = null
    
    private var notificationPanelView: View? = null
    private var currentPage = 0
    private var pageIndicatorContainer: LinearLayout? = null
    
    private val maxBlurRadius = 165f
    private val minFlingVelocity = 1000f
    private val minHorizontalFlingVelocity = 800f
    private val horizontalSwipeThreshold = 50f
    private val openThreshold = 0.0f

    private val controlStates = mutableMapOf(
        "wifi" to true,
        "bluetooth" to true,
        "cellular" to true,
        "flashlight" to false,
        "rotation" to false,
        "notification" to true,
        "camera" to false,
        "screenMirror" to false,
        "video" to false,
        "location" to false
    )
    
    private val buttonIconMap = mapOf(
        R.id.wifiButton to R.id.wifiIcon,
        R.id.cellularButton to R.id.cellularIcon
    )
    
    private val circularButtons = setOf<Int>()
    
    private val toggleButtons = setOf(
        R.id.wifiButton,
        R.id.cellularButton
    )
    
    private val activeColor = Color.parseColor("#007AFF")
    private val inactiveColor = Color.WHITE
    
    private var cachedPanelHeight = 0
    private var isPanelHeightCached = false
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    
    private var cachedControlCenterView: View? = null
    private var cachedBackgroundView: View? = null
    private var isCacheReady = false
    private var backgroundParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    
    private var lastBlurUpdateTime = 0L
    private val blurUpdateThrottleMs = 32L
    private var pendingBlurProgress = 0f
    private var isBlurUpdatePending = false

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        getScreenDimensions()
        createNotificationChannel()
        
        handler.post {
            preCacheViews()
            preloadSystemStates()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        cleanupCachedViews()
        backgroundExecutor.shutdown()
    }
    
    private fun preCacheViews() {
        try {
            val inflater = LayoutInflater.from(this)
            
            cachedControlCenterView = inflater.inflate(R.layout.control_center_panel, null)
            cachedControlCenterView?.measure(
                View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            cachedPanelHeight = cachedControlCenterView?.measuredHeight ?: 0
            isPanelHeightCached = cachedPanelHeight > 0
            
            cachedBackgroundView = View(this).apply {
                setBackgroundColor(0x00000000.toInt())
            }
            
            backgroundParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT
            ).apply {
                dimAmount = 0.0f
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    blurBehindRadius = 1
                    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                }
            }
            
            panelParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            
            isCacheReady = true
            android.util.Log.d("ControlCenterService", "Views pre-cached, panelHeight: $cachedPanelHeight")
        } catch (e: Exception) {
            android.util.Log.e("ControlCenterService", "Failed to pre-cache views", e)
        }
    }
    
    private fun cleanupCachedViews() {
        cachedControlCenterView = null
        cachedBackgroundView = null
        isCacheReady = false
    }
    
    private fun preloadSystemStates() {
        backgroundExecutor.execute {
            val wifiState = SystemControlHelper.isWifiEnabled(this)
            val bluetoothState = SystemControlHelper.isBluetoothEnabled(this)
            val flashlightState = SystemControlHelper.isFlashlightOn()
            val rotationState = SystemControlHelper.isRotationLocked(this)
            val locationState = SystemControlHelper.isLocationEnabled(this)
            
            handler.post {
                controlStates["wifi"] = wifiState
                controlStates["bluetooth"] = bluetoothState
                controlStates["flashlight"] = flashlightState
                controlStates["rotation"] = rotationState
                controlStates["location"] = locationState
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        when (intent?.action) {
            ACTION_SHOW -> {
                if (!isShowing) {
                    showControlCenter()
                }
            }
            ACTION_HIDE -> {
                hideControlCenter()
            }
            ACTION_DRAG_START -> {
                handleDragStartDirect()
            }
            ACTION_DRAG_UPDATE -> {
                val dragY = intent.getFloatExtra(EXTRA_DRAG_Y, 0f)
                handleDragUpdateDirect(dragY)
            }
            ACTION_DRAG_END -> {
                val velocityY = intent.getFloatExtra(EXTRA_VELOCITY_Y, 0f)
                handleDragEndDirect(velocityY)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun getScreenDimensions() {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Control Center Service Notification"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    fun handleDragStartDirect() {
        if (isShowing || isHiding) return
        
        isShowing = true
        isInteractiveDragging = true
        vibrate()

        addBackgroundViewFast()
        backgroundView?.alpha = 0f
        
        addControlCenterViewFast()
        
        if (isPanelHeightCached && cachedPanelHeight > 0) {
            panelHeight = cachedPanelHeight
            panelMeasured = true
            controlCenterView?.translationY = -panelHeight.toFloat()
        } else {
            controlCenterView?.translationY = -screenHeight.toFloat()
            panelMeasured = false
            controlCenterView?.post {
                panelHeight = controlCenterView?.height ?: 0
                if (panelHeight > 0) {
                    panelMeasured = true
                    cachedPanelHeight = panelHeight
                    isPanelHeightCached = true
                    controlCenterView?.translationY = -panelHeight.toFloat()
                }
            }
        }
    }
    
    fun handleDragUpdateDirect(dragY: Float) {
        if (!isShowing || !isInteractiveDragging) return
        if (!panelMeasured || panelHeight == 0) {
            controlCenterView?.post {
                panelHeight = controlCenterView?.height ?: 0
                if (panelHeight > 0) {
                    panelMeasured = true
                    handleDragUpdateDirect(dragY)
                }
            }
            return
        }

        val newTranslation = (-panelHeight + dragY).coerceIn(-panelHeight.toFloat(), 0f)
        controlCenterView?.translationY = newTranslation

        val progress = 1f - (kotlin.math.abs(newTranslation) / panelHeight.toFloat())
        backgroundView?.alpha = progress.coerceIn(0f, 1f)
        
        updateBlurRadiusThrottled(progress.coerceIn(0f, 1f))
    }
    
    fun handleDragEndDirect(velocityY: Float) {
        if (!isShowing || !isInteractiveDragging) return
        isInteractiveDragging = false

        if (!panelMeasured || panelHeight == 0) {
            removeViews()
            return
        }

        val currentTransY = controlCenterView?.translationY ?: -panelHeight.toFloat()
        val progress = 1f - (kotlin.math.abs(currentTransY) / panelHeight.toFloat())
        
        val shouldOpen = progress > openThreshold || velocityY > minFlingVelocity
        
        if (shouldOpen) {
            animateShowWithVelocity(velocityY.coerceAtLeast(0f))
        } else {
            hideControlCenterWithVelocity(-kotlin.math.abs(velocityY))
        }
    }

    private fun handleDragStart() {
        handleDragStartDirect()
    }

    private fun handleDragUpdate(dragY: Float) {
        handleDragUpdateDirect(dragY)
    }

    private fun handleDragEnd(velocityY: Float) {
        handleDragEndDirect(velocityY)
    }

    private fun showControlCenter() {
        isShowing = true
        vibrate()

        addBackgroundViewFast()
        addControlCenterViewFast()

        if (isPanelHeightCached && cachedPanelHeight > 0) {
            panelHeight = cachedPanelHeight
            panelMeasured = true
            controlCenterView?.translationY = -panelHeight.toFloat()
            animateShow()
        } else {
            controlCenterView?.post {
                panelHeight = controlCenterView?.height ?: 0
                panelMeasured = true
                cachedPanelHeight = panelHeight
                isPanelHeightCached = panelHeight > 0
                controlCenterView?.translationY = -panelHeight.toFloat()
                animateShow()
            }
        }
    }
    
    private fun addBackgroundViewFast() {
        if (backgroundView != null) return
        
        backgroundView = View(this).apply {
            setBackgroundColor(0x00000000.toInt())
            alpha = 0f
            setOnTouchListener { _, event ->
                handleBackgroundTouch(event)
            }
        }
        
        val params = backgroundParams?.let { 
            WindowManager.LayoutParams().apply {
                copyFrom(it)
            }
        } ?: createBackgroundParams()

        try {
            windowManager?.addView(backgroundView, params)
            showTransparentSystemBars(backgroundView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun createBackgroundParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            dimAmount = 0.0f
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 1
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            }
        }
    }
    
    private fun addControlCenterViewFast() {
        if (controlCenterView != null) return
        
        val params = panelParams?.let {
            WindowManager.LayoutParams().apply {
                copyFrom(it)
            }
        } ?: createPanelParams()

        val inflater = LayoutInflater.from(this)
        controlCenterView = inflater.inflate(R.layout.control_center_panel, null)

        setupControlButtons()
        setupDismissGesture()

        try {
            windowManager?.addView(controlCenterView, params)
            showTransparentSystemBars(controlCenterView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun createPanelParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
    }

    private fun addBackgroundView() {
        addBackgroundViewFast()
    }
    
    private fun updateBlurRadiusThrottled(progress: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        
        val currentTime = System.currentTimeMillis()
        pendingBlurProgress = progress
        
        if (currentTime - lastBlurUpdateTime < blurUpdateThrottleMs) {
            if (!isBlurUpdatePending) {
                isBlurUpdatePending = true
                handler.postDelayed({
                    isBlurUpdatePending = false
                    applyBlurRadius(pendingBlurProgress)
                }, blurUpdateThrottleMs)
            }
            return
        }
        
        lastBlurUpdateTime = currentTime
        applyBlurRadius(progress)
    }
    
    private fun applyBlurRadius(progress: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundView?.let { view ->
                val blurRadius = (maxBlurRadius * progress).coerceIn(0.1f, maxBlurRadius)
                try {
                    val params = view.layoutParams as? WindowManager.LayoutParams
                    params?.let {
                        it.blurBehindRadius = blurRadius.toInt().coerceAtLeast(1)
                        windowManager?.updateViewLayout(view, it)
                    }
                } catch (e: Exception) {
                }
            }
        }
    }
    
    private fun updateBlurRadius(progress: Float) {
        applyBlurRadius(progress)
    }

    private fun addControlCenterView() {
        addControlCenterViewFast()
    }
    
    private fun showTransparentSystemBars(view: View?) {
        view?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                it.windowInsetsController?.let { controller ->
                    controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                it.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            }
        }
    }

    private fun setupDismissGesture() {
        controlCenterView?.setOnTouchListener { _, event ->
            handlePanelTouch(event)
        }
    }
    
    private fun handleBackgroundTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isInteractiveDragging) {
                    hideControlCenter()
                }
                return true
            }
        }
        return false
    }

    private fun handlePanelTouch(event: MotionEvent): Boolean {
        if (isInteractiveDragging) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                isHorizontalSwipe = false
                startY = event.rawY
                startX = event.rawX
                currentTranslationY = controlCenterView?.translationY ?: 0f
                currentTranslationX = controlCenterView?.translationX ?: 0f
                
                velocityTracker?.clear()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && currentPage == 0) {
                    velocityTracker?.addMovement(event)
                    
                    val deltaX = event.rawX - startX
                    val deltaY = event.rawY - startY
                    
                    val absX = kotlin.math.abs(deltaX)
                    val absY = kotlin.math.abs(deltaY)
                    
                    if (absX > horizontalSwipeThreshold && absX > absY * 1.5f && deltaX < 0) {
                        isHorizontalSwipe = true
                        handleHorizontalSwipe(deltaX)
                    } else if (absY > horizontalSwipeThreshold || !isHorizontalSwipe) {
                        if (isHorizontalSwipe && absY > absX) {
                            resetHorizontalSwipeInstant()
                            isHorizontalSwipe = false
                        }
                        
                        if (!isHorizontalSwipe) {
                            val newTranslation = (currentTranslationY + deltaY).coerceIn(-panelHeight.toFloat(), 0f)
                            controlCenterView?.translationY = newTranslation

                            val progress = 1f - (kotlin.math.abs(newTranslation) / panelHeight.toFloat())
                            backgroundView?.alpha = progress
                            updateBlurRadiusThrottled(progress)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    
                    val currentTransX = controlCenterView?.translationX ?: 0f
                    val horizontalProgress = kotlin.math.abs(currentTransX) / screenWidth.toFloat()
                    
                    if (isHorizontalSwipe && horizontalProgress > 0.2f) {
                        finishHorizontalSwipe(velocityX)
                    } else if (isHorizontalSwipe && kotlin.math.abs(velocityX) > minHorizontalFlingVelocity && velocityX < 0) {
                        finishHorizontalSwipe(velocityX)
                    } else if (isHorizontalSwipe) {
                        resetHorizontalSwipe()
                    } else {
                        val currentTransY = controlCenterView?.translationY ?: 0f
                        val shouldHide = currentTransY < -panelHeight / 3f || velocityY < -minFlingVelocity
                        
                        if (shouldHide) {
                            hideControlCenterWithVelocity(velocityY)
                        } else {
                            animateShowWithVelocity(velocityY)
                        }
                    }
                    
                    isHorizontalSwipe = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
                return true
            }
        }
        return false
    }
    
    private fun resetHorizontalSwipeInstant() {
        controlCenterView?.translationX = 0f
        notificationPanelView?.translationX = screenWidth.toFloat()
    }
    
    private fun resetHorizontalSwipe() {
        horizontalAnimation?.cancel()
        
        if (currentPage == 0) {
            controlCenterView?.let { panel ->
                val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, 0f)
                spring.spring.apply {
                    stiffness = SpringForce.STIFFNESS_MEDIUM
                    dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                }
                spring.start()
            }
            
            notificationPanelView?.let { panel ->
                val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, screenWidth.toFloat())
                spring.spring.apply {
                    stiffness = SpringForce.STIFFNESS_MEDIUM
                    dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                }
                spring.start()
            }
            
            updatePageIndicator(0f)
        } else {
            controlCenterView?.let { panel ->
                val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, -screenWidth.toFloat())
                spring.spring.apply {
                    stiffness = SpringForce.STIFFNESS_MEDIUM
                    dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                }
                spring.start()
            }
            
            notificationPanelView?.let { panel ->
                val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, 0f)
                spring.spring.apply {
                    stiffness = SpringForce.STIFFNESS_MEDIUM
                    dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                }
                spring.start()
            }
            
            updatePageIndicator(1f)
        }
    }
    
    private fun handleHorizontalSwipe(deltaX: Float) {
        if (notificationPanelView == null) {
            addNotificationPanel()
        }
        
        val boundedDelta = deltaX.coerceIn(-screenWidth.toFloat(), 0f)
        
        if (currentPage == 0) {
            controlCenterView?.translationX = boundedDelta
            notificationPanelView?.translationX = screenWidth + boundedDelta
            
            val progress = kotlin.math.abs(boundedDelta) / screenWidth.toFloat()
            updatePageIndicator(progress)
        }
    }
    
    private fun finishHorizontalSwipe(velocityX: Float) {
        if (currentPage == 0) {
            switchToNotificationCenter()
        }
    }
    
    private fun switchToNotificationCenter() {
        currentPage = 1
        
        controlCenterView?.let { panel ->
            val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, -screenWidth.toFloat())
            spring.spring.apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            spring.start()
        }
        
        notificationPanelView?.let { panel ->
            val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, 0f)
            spring.spring.apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            spring.start()
        }
        
        updatePageIndicator(1f)
    }
    
    private fun switchToControlCenter() {
        currentPage = 0
        
        controlCenterView?.let { panel ->
            val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, 0f)
            spring.spring.apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            spring.start()
        }
        
        notificationPanelView?.let { panel ->
            val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, screenWidth.toFloat())
            spring.spring.apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            spring.start()
        }
        
        updatePageIndicator(0f)
    }
    
    private fun addNotificationPanel() {
        if (notificationPanelView != null) return
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        
        val inflater = LayoutInflater.from(this)
        notificationPanelView = inflater.inflate(R.layout.notification_center_panel, null)
        notificationPanelView?.translationX = screenWidth.toFloat()
        
        setupNotificationPanel()
        
        try {
            windowManager?.addView(notificationPanelView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupNotificationPanel() {
        notificationPanelView?.setOnTouchListener { _, event ->
            handleNotificationPanelTouch(event)
        }
    }
    
    private fun handleNotificationPanelTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                isHorizontalSwipe = false
                startY = event.rawY
                startX = event.rawX
                currentTranslationY = controlCenterView?.translationY ?: 0f
                currentTranslationX = notificationPanelView?.translationX ?: 0f
                
                velocityTracker?.clear()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && currentPage == 1) {
                    velocityTracker?.addMovement(event)
                    
                    val deltaX = event.rawX - startX
                    val deltaY = event.rawY - startY
                    
                    val absX = kotlin.math.abs(deltaX)
                    val absY = kotlin.math.abs(deltaY)
                    
                    if (absX > horizontalSwipeThreshold && absX > absY * 1.5f && deltaX > 0) {
                        isHorizontalSwipe = true
                        handleNotificationHorizontalSwipe(deltaX)
                    } else if (absY > horizontalSwipeThreshold || !isHorizontalSwipe) {
                        if (isHorizontalSwipe && absY > absX) {
                            resetNotificationHorizontalSwipeInstant()
                            isHorizontalSwipe = false
                        }
                        
                        if (!isHorizontalSwipe) {
                            val newTranslation = (currentTranslationY + deltaY).coerceIn(-panelHeight.toFloat(), 0f)
                            controlCenterView?.translationY = newTranslation
                            notificationPanelView?.translationY = newTranslation

                            val progress = 1f - (kotlin.math.abs(newTranslation) / panelHeight.toFloat())
                            backgroundView?.alpha = progress
                            updateBlurRadiusThrottled(progress)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    
                    val currentTransX = notificationPanelView?.translationX ?: 0f
                    val horizontalProgress = currentTransX / screenWidth.toFloat()
                    
                    if (isHorizontalSwipe && horizontalProgress > 0.2f) {
                        finishNotificationHorizontalSwipe(velocityX)
                    } else if (isHorizontalSwipe && velocityX > minHorizontalFlingVelocity) {
                        finishNotificationHorizontalSwipe(velocityX)
                    } else if (isHorizontalSwipe) {
                        resetHorizontalSwipe()
                    } else {
                        val currentTransY = controlCenterView?.translationY ?: 0f
                        val shouldHide = currentTransY < -panelHeight / 3f || velocityY < -minFlingVelocity
                        
                        if (shouldHide) {
                            hideControlCenterWithVelocity(velocityY)
                        } else {
                            animateShowWithVelocity(velocityY)
                        }
                    }
                    
                    isHorizontalSwipe = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
                return true
            }
        }
        return false
    }
    
    private fun handleNotificationHorizontalSwipe(deltaX: Float) {
        val boundedDelta = deltaX.coerceIn(0f, screenWidth.toFloat())
        
        notificationPanelView?.translationX = boundedDelta
        controlCenterView?.translationX = -screenWidth + boundedDelta
        
        val progress = 1f - (boundedDelta / screenWidth.toFloat())
        updatePageIndicator(progress)
    }
    
    private fun resetNotificationHorizontalSwipeInstant() {
        notificationPanelView?.translationX = 0f
        controlCenterView?.translationX = -screenWidth.toFloat()
    }
    
    private fun finishNotificationHorizontalSwipe(velocityX: Float) {
        switchToControlCenter()
    }
    
    private fun updatePageIndicator(progress: Float) {
        pageIndicatorContainer?.let { container ->
            if (container.childCount >= 2) {
                val dot1 = container.getChildAt(0)
                val dot2 = container.getChildAt(1)
                
                val activeAlpha = 1f
                val inactiveAlpha = 0.3f
                
                dot1.alpha = activeAlpha - (progress * (activeAlpha - inactiveAlpha))
                dot2.alpha = inactiveAlpha + (progress * (activeAlpha - inactiveAlpha))
            }
        }
    }

    private fun animateShow() {
        currentAnimation?.cancel()
        
        val springAnim = SpringAnimation(controlCenterView, DynamicAnimation.TRANSLATION_Y, 0f)
        springAnim.spring.apply {
            stiffness = SpringForce.STIFFNESS_MEDIUM
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
        
        springAnim.addUpdateListener { _, value, _ ->
            val progress = 1f - (kotlin.math.abs(value) / panelHeight.toFloat())
            backgroundView?.alpha = progress.coerceIn(0f, 1f)
            updateBlurRadius(progress.coerceIn(0f, 1f))
        }
        
        currentAnimation = springAnim
        springAnim.start()
    }

    private fun animateShowWithVelocity(velocity: Float) {
        currentAnimation?.cancel()
        
        val springAnim = SpringAnimation(controlCenterView, DynamicAnimation.TRANSLATION_Y, 0f)
        springAnim.spring.apply {
            stiffness = SpringForce.STIFFNESS_MEDIUM
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
        springAnim.setStartVelocity(velocity)
        
        springAnim.addUpdateListener { _, value, _ ->
            val progress = 1f - (kotlin.math.abs(value) / panelHeight.toFloat())
            backgroundView?.alpha = progress.coerceIn(0f, 1f)
            updateBlurRadius(progress.coerceIn(0f, 1f))
        }
        
        currentAnimation = springAnim
        springAnim.start()
    }

    fun hideControlCenter() {
        hideControlCenterWithVelocity(0f)
    }

    private fun hideControlCenterWithVelocity(velocity: Float) {
        if (!isShowing || isHiding) return
        isHiding = true
        currentAnimation?.cancel()
        
        val springAnim = SpringAnimation(controlCenterView, DynamicAnimation.TRANSLATION_Y, -panelHeight.toFloat())
        springAnim.spring.apply {
            stiffness = SpringForce.STIFFNESS_MEDIUM
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
        if (velocity != 0f) {
            springAnim.setStartVelocity(velocity)
        }
        
        springAnim.addUpdateListener { _, value, _ ->
            val progress = 1f - (kotlin.math.abs(value) / panelHeight.toFloat())
            backgroundView?.alpha = progress.coerceIn(0f, 1f)
            updateBlurRadius(progress.coerceIn(0f, 1f))
            
            notificationPanelView?.translationY = value
        }
        
        springAnim.addEndListener { _, _, _, _ ->
            removeViews()
            isShowing = false
            isHiding = false
            currentPage = 0
        }
        
        currentAnimation = springAnim
        springAnim.start()
    }

    private fun removeViews() {
        controlCenterView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        controlCenterView = null

        backgroundView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        backgroundView = null
        
        notificationPanelView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        notificationPanelView = null
        
        panelMeasured = false
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(10)
                }
            }
        } catch (e: Exception) {}
    }

    private fun setupControlButtons() {
        updateControlStates()
        
        controlCenterView?.findViewById<View>(R.id.wifiButton)?.apply {
            setOnClickListener { toggleWifi() }
            setOnLongClickListener {
                showWifiDialog()
                true
            }
        }
        
        controlCenterView?.findViewById<View>(R.id.bluetoothButton)?.apply {
            setOnClickListener { toggleBluetooth() }
            setOnLongClickListener {
                showBluetoothDialog()
                true
            }
        }
        
        controlCenterView?.findViewById<View>(R.id.cellularButton)?.setOnClickListener {
            openCellularSettings()
        }
        
        controlCenterView?.findViewById<View>(R.id.airplaneButton)?.setOnClickListener {
            toggleAirplaneMode()
        }
        
        controlCenterView?.findViewById<View>(R.id.flashlightButton)?.setOnClickListener {
            toggleFlashlight()
        }
        
        controlCenterView?.findViewById<View>(R.id.rotationButton)?.setOnClickListener {
            toggleRotation()
        }
        
        controlCenterView?.findViewById<View>(R.id.locationButton)?.setOnClickListener {
            toggleLocation()
        }
        
        controlCenterView?.findViewById<View>(R.id.focusButton)?.setOnClickListener {
            toggleDND()
        }
        
        setupBrightnessSlider()
        setupVolumeSlider()
        setupMediaControls()
        setupQuickLaunchButtons()
        setupPageIndicator()
    }
    
    private fun setupPageIndicator() {
        pageIndicatorContainer = controlCenterView?.findViewById(R.id.pageIndicatorContainer)
    }

    private fun updateControlStates() {
        backgroundExecutor.execute {
            val wifiEnabled = SystemControlHelper.isWifiEnabled(this)
            val bluetoothEnabled = SystemControlHelper.isBluetoothEnabled(this)
            val flashlightOn = SystemControlHelper.isFlashlightOn()
            val rotationLocked = SystemControlHelper.isRotationLocked(this)
            val locationEnabled = SystemControlHelper.isLocationEnabled(this)
            val airplaneMode = SystemControlHelper.isAirplaneModeOn(this)
            val dndEnabled = SystemControlHelper.isDndEnabled(this)
            
            handler.post {
                controlStates["wifi"] = wifiEnabled
                controlStates["bluetooth"] = bluetoothEnabled
                controlStates["flashlight"] = flashlightOn
                controlStates["rotation"] = rotationLocked
                controlStates["location"] = locationEnabled
                
                updateButtonState(R.id.wifiButton, R.id.wifiIcon, wifiEnabled)
                updateButtonState(R.id.bluetoothButton, R.id.bluetoothIcon, bluetoothEnabled)
                updateButtonState(R.id.flashlightButton, R.id.flashlightIcon, flashlightOn)
                updateButtonState(R.id.rotationButton, R.id.rotationIcon, rotationLocked)
                updateButtonState(R.id.locationButton, R.id.locationIcon, locationEnabled)
                updateButtonState(R.id.airplaneButton, R.id.airplaneIcon, airplaneMode)
                updateButtonState(R.id.focusButton, R.id.focusIcon, dndEnabled)
            }
        }
    }
    
    private fun updateButtonState(buttonId: Int, iconId: Int, isActive: Boolean) {
        val button = controlCenterView?.findViewById<View>(buttonId)
        val icon = controlCenterView?.findViewById<ImageView>(iconId)
        
        button?.isSelected = isActive
        icon?.setColorFilter(if (isActive) activeColor else inactiveColor)
    }

    private fun toggleWifi() {
        val currentState = controlStates["wifi"] ?: false
        val newState = !currentState
        
        SystemControlHelper.toggleWifi(this)
        controlStates["wifi"] = newState
        updateButtonState(R.id.wifiButton, R.id.wifiIcon, newState)
        vibrate()
    }

    private fun toggleBluetooth() {
        val currentState = controlStates["bluetooth"] ?: false
        val newState = !currentState
        
        SystemControlHelper.toggleBluetooth(this)
        controlStates["bluetooth"] = newState
        updateButtonState(R.id.bluetoothButton, R.id.bluetoothIcon, newState)
        vibrate()
    }

    private fun toggleFlashlight() {
        val currentState = controlStates["flashlight"] ?: false
        val newState = !currentState
        
        SystemControlHelper.toggleFlashlight(this)
        controlStates["flashlight"] = newState
        updateButtonState(R.id.flashlightButton, R.id.flashlightIcon, newState)
        vibrate()
    }

    private fun toggleRotation() {
        val currentState = controlStates["rotation"] ?: false
        val newState = !currentState
        
        SystemControlHelper.toggleRotation(this)
        controlStates["rotation"] = newState
        updateButtonState(R.id.rotationButton, R.id.rotationIcon, newState)
        vibrate()
    }

    private fun toggleLocation() {
        SystemControlHelper.openLocationSettings(this)
        vibrate()
    }

    private fun openCellularSettings() {
        SystemControlHelper.openCellularSettings(this)
        vibrate()
    }

    private fun toggleAirplaneMode() {
        SystemControlHelper.openAirplaneSettings(this)
        vibrate()
    }

    private fun toggleDND() {
        SystemControlHelper.toggleDnd(this)
        val newState = SystemControlHelper.isDndEnabled(this)
        updateButtonState(R.id.focusButton, R.id.focusIcon, newState)
        vibrate()
    }

    private fun setupBrightnessSlider() {
        val brightnessContainer = controlCenterView?.findViewById<View>(R.id.brightnessSliderContainer)
        val brightnessProgress = controlCenterView?.findViewById<View>(R.id.brightnessProgress)
        
        brightnessContainer?.post {
            val currentBrightness = SystemControlHelper.getBrightness(this)
            updateBrightnessUI(currentBrightness)
        }
        
        brightnessContainer?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val progress = (event.y / view.height).coerceIn(0f, 1f)
                    val brightness = 1f - progress
                    
                    SystemControlHelper.setBrightness(this, brightness)
                    updateBrightnessUI(brightness)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun updateBrightnessUI(brightness: Float) {
        val brightnessContainer = controlCenterView?.findViewById<View>(R.id.brightnessSliderContainer)
        val brightnessProgress = controlCenterView?.findViewById<View>(R.id.brightnessProgress)
        
        brightnessContainer?.let { container ->
            brightnessProgress?.let { progress ->
                val params = progress.layoutParams
                params.height = (container.height * brightness).toInt()
                progress.layoutParams = params
            }
        }
    }

    private fun setupVolumeSlider() {
        val volumeContainer = controlCenterView?.findViewById<View>(R.id.volumeSliderContainer)
        val volumeProgress = controlCenterView?.findViewById<View>(R.id.volumeProgress)
        
        volumeContainer?.post {
            val currentVolume = SystemControlHelper.getVolume(this)
            updateVolumeUI(currentVolume)
        }
        
        volumeContainer?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val progress = (event.y / view.height).coerceIn(0f, 1f)
                    val volume = 1f - progress
                    
                    SystemControlHelper.setVolume(this, volume)
                    updateVolumeUI(volume)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun updateVolumeUI(volume: Float) {
        val volumeContainer = controlCenterView?.findViewById<View>(R.id.volumeSliderContainer)
        val volumeProgress = controlCenterView?.findViewById<View>(R.id.volumeProgress)
        
        volumeContainer?.let { container ->
            volumeProgress?.let { progress ->
                val params = progress.layoutParams
                params.height = (container.height * volume).toInt()
                progress.layoutParams = params
            }
        }
    }

    private fun setupMediaControls() {
        val previousButton = controlCenterView?.findViewById<View>(R.id.previousButton)
        val playPauseButton = controlCenterView?.findViewById<View>(R.id.playPauseButton)
        val nextButton = controlCenterView?.findViewById<View>(R.id.nextButton)
        
        previousButton?.setOnClickListener {
            MediaControlHelper.sendMediaButton(this, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        
        playPauseButton?.setOnClickListener {
            MediaControlHelper.sendMediaButton(this, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        
        nextButton?.setOnClickListener {
            MediaControlHelper.sendMediaButton(this, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        
        updateMediaInfo()
    }
    
    private fun updateMediaInfo() {
        val mediaInfo = MediaControlHelper.getCurrentMediaInfo(this)
        
        val songTitle = controlCenterView?.findViewById<TextView>(R.id.songTitle)
        val artistName = controlCenterView?.findViewById<TextView>(R.id.artistName)
        val albumArt = controlCenterView?.findViewById<ImageView>(R.id.albumArt)
        
        songTitle?.text = mediaInfo?.title ?: "Not Playing"
        artistName?.text = mediaInfo?.artist ?: ""
        
        mediaInfo?.albumArt?.let { art ->
            albumArt?.setImageBitmap(art)
        }
    }

    private fun setupQuickLaunchButtons() {
        val prefs = getSharedPreferences("quick_launch_prefs", Context.MODE_PRIVATE)
        
        setupQuickLaunchButton(R.id.quickLaunch1, prefs.getString("slot_1", null))
        setupQuickLaunchButton(R.id.quickLaunch2, prefs.getString("slot_2", null))
        setupQuickLaunchButton(R.id.quickLaunch3, prefs.getString("slot_3", null))
        setupQuickLaunchButton(R.id.quickLaunch4, prefs.getString("slot_4", null))
    }
    
    private fun setupQuickLaunchButton(buttonId: Int, packageName: String?) {
        val button = controlCenterView?.findViewById<View>(buttonId)
        val iconView = button?.findViewById<ImageView>(R.id.quickLaunchIcon)
        
        if (packageName != null) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val icon = packageManager.getApplicationIcon(appInfo)
                iconView?.setImageDrawable(icon)
                
                button?.setOnClickListener {
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    launchIntent?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                        hideControlCenter()
                    }
                }
            } catch (e: Exception) {
                iconView?.setImageResource(R.drawable.ic_add_circle)
            }
        } else {
            iconView?.setImageResource(R.drawable.ic_add_circle)
        }
    }

    private fun showWifiDialog() {
        vibrate()
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wifi_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.wifiRecyclerView)
        val loadingProgress = dialogView.findViewById<ProgressBar>(R.id.loadingProgress)
        val emptyText = dialogView.findViewById<TextView>(R.id.emptyText)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        
        var isDialogShowing = true
        
        dialogView.findViewById<View>(R.id.closeButton)?.setOnClickListener {
            isDialogShowing = false
            try {
                windowManager?.removeView(dialogView)
            } catch (e: Exception) {}
        }
        
        dialogView.setOnClickListener {
            isDialogShowing = false
            try {
                windowManager?.removeView(dialogView)
            } catch (e: Exception) {}
        }
        
        dialogView.findViewById<View>(R.id.dialogContent)?.setOnClickListener {
        }
        
        try {
            windowManager?.addView(dialogView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        
        val wifiHelper = WiFiScannerHelper(this)
        wifiHelper.startScan { networks ->
            handler.post {
                if (!isDialogShowing) return@post
                
                loadingProgress.visibility = View.GONE
                
                if (networks.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    
                    recyclerView.adapter = WiFiNetworkAdapter(networks) { network ->
                        if (network.isConnected) {
                            Toast.makeText(this, "Đã kết nối với ${network.ssid}", Toast.LENGTH_SHORT).show()
                        } else if (network.isSecured) {
                            showWifiPasswordDialog(network) { password ->
                                wifiHelper.connectToNetwork(network.ssid, password, true, network.securityType) { success, message ->
                                    handler.post {
                                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                        if (success) {
                                            try {
                                                windowManager?.removeView(dialogView)
                                            } catch (e: Exception) {}
                                        }
                                    }
                                }
                            }
                        } else {
                            wifiHelper.connectToNetwork(network.ssid, null, false) { success, message ->
                                handler.post {
                                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                    if (success) {
                                        try {
                                            windowManager?.removeView(dialogView)
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun showWifiPasswordDialog(network: WiFiNetwork, onPasswordEntered: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wifi_password, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.passwordInput)
        val networkName = dialogView.findViewById<TextView>(R.id.networkName)
        val showPasswordCheckbox = dialogView.findViewById<CheckBox>(R.id.showPasswordCheckbox)
        
        networkName.text = network.ssid
        
        showPasswordCheckbox.setOnCheckedChangeListener { _, isChecked ->
            passwordInput.inputType = if (isChecked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordInput.setSelection(passwordInput.text.length)
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        
        try {
            windowManager?.addView(dialogView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        
        dialogView.findViewById<View>(R.id.cancelButton)?.setOnClickListener {
            try {
                windowManager?.removeView(dialogView)
            } catch (e: Exception) {}
        }
        
        dialogView.findViewById<View>(R.id.connectButton)?.setOnClickListener {
            val password = passwordInput.text.toString()
            if (password.isNotEmpty()) {
                try {
                    windowManager?.removeView(dialogView)
                } catch (e: Exception) {}
                onPasswordEntered(password)
            } else {
                Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBluetoothDialog() {
        vibrate()
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bluetooth_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.bluetoothRecyclerView)
        val loadingProgress = dialogView.findViewById<ProgressBar>(R.id.loadingProgress)
        val emptyText = dialogView.findViewById<TextView>(R.id.emptyText)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        
        var isDialogShowing = true
        
        dialogView.findViewById<View>(R.id.closeButton)?.setOnClickListener {
            isDialogShowing = false
            try {
                windowManager?.removeView(dialogView)
            } catch (e: Exception) {}
        }
        
        dialogView.setOnClickListener {
            isDialogShowing = false
            try {
                windowManager?.removeView(dialogView)
            } catch (e: Exception) {}
        }
        
        dialogView.findViewById<View>(R.id.dialogContent)?.setOnClickListener {
        }
        
        try {
            windowManager?.addView(dialogView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        
        handler.postDelayed({
            if (!isDialogShowing) return@postDelayed
            
            loadingProgress.visibility = View.GONE
            
            val devices = SystemControlHelper.getPairedBluetoothDevices(this)
            
            if (devices.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                
                recyclerView.adapter = BluetoothDeviceAdapter(devices) { device ->
                    SystemControlHelper.connectBluetoothDevice(this, device)
                    Toast.makeText(this, "Đang kết nối với ${device.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }, 500)
    }
}
