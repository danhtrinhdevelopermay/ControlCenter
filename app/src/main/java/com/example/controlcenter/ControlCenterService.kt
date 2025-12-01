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
        
        var isHiding = false
            private set
        
        var isInteractiveDragging = false
            private set
            
        private var serviceInstance: ControlCenterService? = null
        
        fun getInstance(): ControlCenterService? = serviceInstance
        
        internal fun setHiding(hiding: Boolean) {
            isHiding = hiding
        }
    }

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
    
    private var lastBlurUpdateTime = 0L
    private var lastBlurRadius = -1
    private val blurUpdateInterval = 33L
    private val blurChangeThreshold = 10
    private var enableBlurUpdates = true

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
    
    // Cached panel height for faster opening (avoid re-measuring each time)
    private var cachedPanelHeight = 0
    private var isPanelHeightCached = false
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    
    private var audioAnalyzer: AudioAnalyzer? = null
    private var isAudioAnalyzing = false

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        getScreenDimensions()
        createNotificationChannel()
        
        // Pre-calculate panel height for faster opening
        preCachePanelHeight()
        // Pre-load system states in background
        preloadSystemStates()
    }
    
    private fun preCachePanelHeight() {
        handler.post {
            try {
                val inflater = LayoutInflater.from(this)
                val tempView = inflater.inflate(R.layout.control_center_panel, null)
                
                // Measure to get panel height
                tempView.measure(
                    View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                cachedPanelHeight = tempView.measuredHeight
                isPanelHeightCached = cachedPanelHeight > 0
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                handleDragStart()
            }
            ACTION_DRAG_UPDATE -> {
                val dragY = intent.getFloatExtra(EXTRA_DRAG_Y, 0f)
                handleDragUpdate(dragY)
            }
            ACTION_DRAG_END -> {
                val velocityY = intent.getFloatExtra(EXTRA_VELOCITY_Y, 0f)
                handleDragEnd(velocityY)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

    private fun handleDragStart() {
        if (isShowing && !isHiding) return
        
        if (isHiding) {
            currentAnimation?.cancel()
            currentAnimation = null
            setHiding(false)
        }
        
        isShowing = true
        isInteractiveDragging = true
        enableBlurUpdates = true
        vibrate()

        addBackgroundView()
        backgroundView?.alpha = 0f
        
        addControlCenterView()
        
        // Use cached panel height for immediate response
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

    private fun handleDragUpdate(dragY: Float) {
        if (!isShowing || !isInteractiveDragging) return
        if (!panelMeasured || panelHeight == 0) {
            controlCenterView?.post {
                panelHeight = controlCenterView?.height ?: 0
                if (panelHeight > 0) {
                    panelMeasured = true
                    handleDragUpdate(dragY)
                }
            }
            return
        }

        val newTranslation = (-panelHeight + dragY).coerceIn(-panelHeight.toFloat(), 0f)
        controlCenterView?.translationY = newTranslation

        val progress = 1f - (kotlin.math.abs(newTranslation) / panelHeight.toFloat())
        backgroundView?.alpha = progress.coerceIn(0f, 1f)
        updateBlurRadius(progress)
    }

    private fun handleDragEnd(velocityY: Float) {
        if (!isShowing || !isInteractiveDragging) return
        isInteractiveDragging = false
        enableBlurUpdates = true

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

    private fun showControlCenter() {
        if (isHiding) {
            currentAnimation?.cancel()
            currentAnimation = null
            setHiding(false)
        }
        
        isShowing = true
        vibrate()

        addBackgroundView()
        addControlCenterView()

        // Use cached panel height for immediate response
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

    private fun addBackgroundView() {
        if (backgroundView != null) {
            return
        }
        
        val params = WindowManager.LayoutParams(
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
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        )
        params.dimAmount = 0.0f
        params.gravity = Gravity.TOP or Gravity.START
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.blurBehindRadius = 1
        }

        backgroundView = View(this).apply {
            setBackgroundColor(0x00000000.toInt())
            alpha = 0f

            setOnTouchListener { _, event ->
                handleBackgroundTouch(event)
            }
        }

        try {
            windowManager?.addView(backgroundView, params)
            showTransparentSystemBars(backgroundView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateBlurRadius(progress: Float) {
        if (!enableBlurUpdates || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        
        backgroundView?.let { view ->
            val blurRadius = (maxBlurRadius * progress).coerceIn(0.1f, maxBlurRadius).toInt().coerceAtLeast(1)
            val currentTime = android.os.SystemClock.elapsedRealtime()
            
            val timeDiff = currentTime - lastBlurUpdateTime
            val radiusDiff = kotlin.math.abs(blurRadius - lastBlurRadius)
            
            if (timeDiff >= blurUpdateInterval || radiusDiff >= blurChangeThreshold || lastBlurRadius == -1) {
                try {
                    val params = view.layoutParams as? WindowManager.LayoutParams
                    params?.let {
                        it.blurBehindRadius = blurRadius
                        windowManager?.updateViewLayout(view, it)
                        lastBlurUpdateTime = currentTime
                        lastBlurRadius = blurRadius
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun addControlCenterView() {
        if (controlCenterView != null) {
            return
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

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
                            updateBlurRadius(progress)
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
            notificationPanelView?.let { panel ->
                val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, 0f)
                spring.spring.apply {
                    stiffness = SpringForce.STIFFNESS_MEDIUM
                    dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                }
                spring.start()
            }
            
            controlCenterView?.let { panel ->
                val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_X, -screenWidth.toFloat())
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
        if (currentPage == 0) {
            val translation = deltaX.coerceIn(-screenWidth.toFloat(), 0f)
            controlCenterView?.translationX = translation
            
            if (notificationPanelView == null) {
                addNotificationPanelView()
            }
            notificationPanelView?.translationX = screenWidth + translation
            
            val progress = kotlin.math.abs(translation) / screenWidth.toFloat()
            updatePageIndicator(progress)
        } else {
            val translation = deltaX.coerceIn(0f, screenWidth.toFloat())
            notificationPanelView?.translationX = translation
            controlCenterView?.translationX = -screenWidth + translation
            
            val progress = 1f - (translation / screenWidth.toFloat())
            updatePageIndicator(progress)
        }
    }
    
    private fun finishHorizontalSwipe(velocityX: Float) {
        val currentTransX = controlCenterView?.translationX ?: 0f
        val swipeProgress = kotlin.math.abs(currentTransX) / screenWidth.toFloat()
        
        if (currentPage == 0) {
            val shouldSwitchToNotification = swipeProgress > 0.3f || velocityX < -minHorizontalFlingVelocity
            
            if (shouldSwitchToNotification) {
                animateToNotificationPage(velocityX)
            } else {
                animateBackToControlCenter(velocityX)
            }
        } else {
            val notifTransX = notificationPanelView?.translationX ?: 0f
            val returnProgress = notifTransX / screenWidth.toFloat()
            val shouldSwitchToControl = returnProgress > 0.3f || velocityX > minHorizontalFlingVelocity
            
            if (shouldSwitchToControl) {
                animateBackToControlCenter(velocityX)
            } else {
                animateToNotificationPage(velocityX)
            }
        }
    }
    
    private fun animateToNotificationPage(velocity: Float) {
        horizontalAnimation?.cancel()
        
        if (notificationPanelView == null) {
            addNotificationPanelView()
        }
        
        controlCenterView?.let { controlPanel ->
            val controlSpring = SpringAnimation(controlPanel, DynamicAnimation.TRANSLATION_X, -screenWidth.toFloat())
            controlSpring.spring.apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            if (velocity < 0) controlSpring.setStartVelocity(velocity)
            controlSpring.start()
        }
        
        notificationPanelView?.let { notifPanel ->
            val notifSpring = SpringAnimation(notifPanel, DynamicAnimation.TRANSLATION_X, 0f)
            notifSpring.spring.apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            if (velocity < 0) notifSpring.setStartVelocity(velocity)
            notifSpring.addUpdateListener { _, value, _ ->
                val progress = 1f - (value / screenWidth.toFloat())
                updatePageIndicator(progress.coerceIn(0f, 1f))
            }
            notifSpring.addEndListener { _, _, _, _ ->
                currentPage = 1
                updatePageIndicator(1f)
            }
            horizontalAnimation = notifSpring
            notifSpring.start()
        }
    }
    
    private fun animateBackToControlCenter(velocity: Float) {
        horizontalAnimation?.cancel()
        
        controlCenterView?.let { controlPanel ->
            val controlSpring = SpringAnimation(controlPanel, DynamicAnimation.TRANSLATION_X, 0f)
            controlSpring.spring.apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            if (velocity > 0) controlSpring.setStartVelocity(velocity)
            controlSpring.addEndListener { _, _, _, _ ->
                currentPage = 0
                updatePageIndicator(0f)
                removeNotificationPanelView()
            }
            horizontalAnimation = controlSpring
            controlSpring.start()
        }
        
        notificationPanelView?.let { notifPanel ->
            val notifSpring = SpringAnimation(notifPanel, DynamicAnimation.TRANSLATION_X, screenWidth.toFloat())
            notifSpring.spring.apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            if (velocity > 0) notifSpring.setStartVelocity(velocity)
            notifSpring.addUpdateListener { _, value, _ ->
                val progress = 1f - (value / screenWidth.toFloat())
                updatePageIndicator(progress.coerceIn(0f, 1f))
            }
            notifSpring.start()
        }
    }

    private fun animateShow() {
        animateShowWithVelocity(0f)
    }
    
    private fun animateShowWithVelocity(velocity: Float) {
        if (isHiding) return
        
        currentAnimation?.cancel()
        
        controlCenterView?.let { panel ->
            val springAnimation = SpringAnimation(panel, DynamicAnimation.TRANSLATION_Y, 0f)
            springAnimation.spring.apply {
                stiffness = SpringForce.STIFFNESS_LOW
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
            
            if (velocity != 0f) {
                springAnimation.setStartVelocity(velocity)
            }
            
            springAnimation.addUpdateListener { _, value, _ ->
                val progress = 1f - (kotlin.math.abs(value) / panelHeight.toFloat())
                backgroundView?.alpha = progress.coerceIn(0f, 1f)
                updateBlurRadius(progress.coerceIn(0f, 1f))
            }
            
            currentAnimation = springAnimation
            springAnimation.start()
        }
    }

    private fun hideControlCenter() {
        hideControlCenterWithVelocity(0f)
    }
    
    private fun hideControlCenterWithVelocity(velocity: Float) {
        if (isHiding) return
        setHiding(true)
        isInteractiveDragging = false
        enableBlurUpdates = true
        
        currentAnimation?.cancel()
        
        controlCenterView?.let { panel ->
            val springAnimation = SpringAnimation(
                panel,
                DynamicAnimation.TRANSLATION_Y,
                -panelHeight.toFloat()
            )
            springAnimation.spring.apply {
                stiffness = SpringForce.STIFFNESS_LOW
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            }
            
            if (velocity != 0f && velocity < 0f) {
                springAnimation.setStartVelocity(velocity)
            }
            
            springAnimation.addUpdateListener { _, value, _ ->
                if (!isHiding) return@addUpdateListener
                val progress = 1f - (kotlin.math.abs(value) / panelHeight.toFloat())
                backgroundView?.alpha = progress.coerceIn(0f, 1f)
                updateBlurRadius(progress.coerceIn(0f, 1f))
            }
            springAnimation.addEndListener { _, _, _, _ ->
                removeViews()
            }
            
            currentAnimation = springAnimation
            springAnimation.start()
        } ?: run {
            setHiding(false)
        }
    }

    private fun removeViews() {
        isShowing = false
        isDragging = false
        setHiding(false)
        isInteractiveDragging = false
        isHorizontalSwipe = false
        panelMeasured = false
        currentPage = 0
        enableBlurUpdates = true
        
        currentAnimation?.cancel()
        currentAnimation = null
        
        horizontalAnimation?.cancel()
        horizontalAnimation = null
        
        blurAnimator?.cancel()
        blurAnimator = null
        
        velocityTracker?.recycle()
        velocityTracker = null
        
        lastBlurUpdateTime = 0L
        lastBlurRadius = -1

        controlCenterView?.let {
            it.translationX = 0f
            it.translationY = 0f
            it.visibility = View.INVISIBLE
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        controlCenterView = null
        
        removeNotificationPanelView()
        removePageIndicator()

        backgroundView?.let {
            it.visibility = View.INVISIBLE
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        backgroundView = null
        
        startX = 0f
        startY = 0f
        currentTranslationX = 0f
        currentTranslationY = 0f
    }
    
    private fun addNotificationPanelView() {
        if (notificationPanelView != null) return
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
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
        setupNotificationPanelGesture()

        try {
            windowManager?.addView(notificationPanelView, params)
            showTransparentSystemBars(notificationPanelView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        addPageIndicator()
    }
    
    private fun setupNotificationPanel() {
        MediaNotificationListener.forceRefreshNotifications()
        loadNotificationsFromListener()
        
        notificationPanelView?.findViewById<ImageView>(R.id.clearAllButton)?.setOnClickListener {
            vibrate()
            clearAllNotifications()
        }
    }
    
    private fun loadNotificationsFromListener() {
        val scrollView = notificationPanelView?.findViewById<ScrollView>(R.id.notificationsScrollView)
        val recyclerView = notificationPanelView?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.notificationsRecyclerView)
        scrollView?.visibility = View.VISIBLE
        recyclerView?.visibility = View.GONE
        
        val container = notificationPanelView?.findViewById<LinearLayout>(R.id.notificationsContainer) ?: return
        container.removeAllViews()
        
        if (!MediaNotificationListener.isNotificationAccessEnabled(this)) {
            val emptyText = TextView(this).apply {
                text = "Cần cấp quyền truy cập thông báo"
                setTextColor(0x99FFFFFF.toInt())
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 100, 0, 0)
            }
            container.addView(emptyText)
            
            val settingsButton = TextView(this).apply {
                text = "Mở cài đặt"
                setTextColor(0xFF007AFF.toInt())
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 0)
                setOnClickListener {
                    MediaNotificationListener.openNotificationAccessSettings(this@ControlCenterService)
                }
            }
            container.addView(settingsButton)
            return
        }
        
        val notifications = try {
            MediaNotificationListener.getActiveNotifications()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        
        if (notifications.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "Không có thông báo"
                setTextColor(0x99FFFFFF.toInt())
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 100, 0, 0)
            }
            container.addView(emptyText)
            return
        }
        
        for (sbn in notifications) {
            if (sbn.packageName == packageName) continue
            
            val notification = sbn.notification
            
            if (notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0 &&
                notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) {
                continue
            }
            
            val extras = notification.extras
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            
            if (title.isEmpty() && text.isEmpty()) continue
            
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_notification, container, false)
            
            try {
                val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val appIcon = packageManager.getApplicationIcon(sbn.packageName)
                
                itemView.findViewById<ImageView>(R.id.appIcon)?.setImageDrawable(appIcon)
                itemView.findViewById<TextView>(R.id.appName)?.text = appName
                
                itemView.findViewById<TextView>(R.id.notificationTitle)?.text = title
                itemView.findViewById<TextView>(R.id.notificationContent)?.text = text
                
                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val timeText = timeFormat.format(java.util.Date(sbn.postTime))
                itemView.findViewById<TextView>(R.id.notificationTime)?.text = timeText
                
                val cardColor = AppearanceSettings.getNotificationColorWithOpacity(this)
                val notificationCard = itemView.findViewById<LinearLayout>(R.id.notificationCard)
                notificationCard?.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 20 * resources.displayMetrics.density
                    setColor(cardColor)
                }
                
                itemView.setOnClickListener {
                    vibrate()
                    try {
                        notification.contentIntent?.send()
                        hideControlCenter()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                container.addView(itemView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun clearAllNotifications() {
        try {
            MediaNotificationListener.cancelAllNotifications()
            handler.postDelayed({
                MediaNotificationListener.forceRefreshNotifications()
                handler.postDelayed({
                    loadNotificationsFromListener()
                }, 100)
            }, 300)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể xóa thông báo", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupNotificationPanelGesture() {
        notificationPanelView?.setOnTouchListener { _, event ->
            handleNotificationPanelTouch(event)
        }
    }
    
    private fun handleNotificationPanelTouch(event: MotionEvent): Boolean {
        if (isInteractiveDragging) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                isHorizontalSwipe = false
                startY = event.rawY
                startX = event.rawX
                currentTranslationX = notificationPanelView?.translationX ?: 0f
                
                velocityTracker?.clear()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    velocityTracker?.addMovement(event)
                    
                    val deltaX = event.rawX - startX
                    val deltaY = event.rawY - startY
                    
                    if (!isHorizontalSwipe && kotlin.math.abs(deltaX) > horizontalSwipeThreshold && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.5f) {
                        isHorizontalSwipe = true
                    }
                    
                    if (isHorizontalSwipe) {
                        handleHorizontalSwipe(deltaX)
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
                    
                    if (isHorizontalSwipe) {
                        finishHorizontalSwipe(velocityX)
                        isHorizontalSwipe = false
                    }
                    
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
                return true
            }
        }
        return false
    }
    
    private fun removeNotificationPanelView() {
        notificationPanelView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        notificationPanelView = null
    }
    
    private fun addPageIndicator() {
        if (pageIndicatorContainer != null) return
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 80
        
        pageIndicatorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
            
            for (i in 0..1) {
                val dot = View(context).apply {
                    val size = if (i == 0) 8 else 6
                    layoutParams = LinearLayout.LayoutParams(
                        (size * resources.displayMetrics.density).toInt(),
                        (size * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = (4 * resources.displayMetrics.density).toInt()
                        marginEnd = (4 * resources.displayMetrics.density).toInt()
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(if (i == 0) 0xFFFFFFFF.toInt() else 0x66FFFFFF.toInt())
                    }
                }
                addView(dot)
            }
        }
        
        try {
            windowManager?.addView(pageIndicatorContainer, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updatePageIndicator(progress: Float) {
        pageIndicatorContainer?.let { container ->
            if (container.childCount >= 2) {
                val dot1 = container.getChildAt(0)
                val dot2 = container.getChildAt(1)
                
                val size1 = (8 - 2 * progress) * resources.displayMetrics.density
                val size2 = (6 + 2 * progress) * resources.displayMetrics.density
                
                dot1.layoutParams = (dot1.layoutParams as LinearLayout.LayoutParams).apply {
                    width = size1.toInt()
                    height = size1.toInt()
                }
                dot2.layoutParams = (dot2.layoutParams as LinearLayout.LayoutParams).apply {
                    width = size2.toInt()
                    height = size2.toInt()
                }
                
                (dot1.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                    blendColors(0xFFFFFFFF.toInt(), 0x66FFFFFF.toInt(), progress)
                )
                (dot2.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                    blendColors(0x66FFFFFF.toInt(), 0xFFFFFFFF.toInt(), progress)
                )
                
                dot1.requestLayout()
                dot2.requestLayout()
            }
        }
    }
    
    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio).toInt()
        val r = (Color.red(color1) * inverseRatio + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * inverseRatio + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }
    
    private fun removePageIndicator() {
        pageIndicatorContainer?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        pageIndicatorContainer = null
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
    
    private fun updateDateTime() {
        val dateTimeText = controlCenterView?.findViewById<TextView>(R.id.dateTimeText)
        
        val calendar = java.util.Calendar.getInstance()
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val dayOfMonth = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        
        val dayName = when (dayOfWeek) {
            java.util.Calendar.SUNDAY -> "CN"
            java.util.Calendar.MONDAY -> "Th 2"
            java.util.Calendar.TUESDAY -> "Th 3"
            java.util.Calendar.WEDNESDAY -> "Th 4"
            java.util.Calendar.THURSDAY -> "Th 5"
            java.util.Calendar.FRIDAY -> "Th 6"
            java.util.Calendar.SATURDAY -> "Th 7"
            else -> ""
        }
        
        val formattedDate = "$dayName, $dayOfMonth Thg $month"
        dateTimeText?.text = formattedDate
    }

    private fun setupControlButtons() {
        syncStateFromSystem()
        applyAppearanceSettings()
        updateDateTime()
        
        controlCenterView?.findViewById<View>(R.id.wifiButton)?.setOnClickListener { button ->
            val currentState = SystemControlHelper.isWifiEnabled(this)
            val newState = !currentState
            animateButtonPress(button)
            vibrate()
            ShizukuHelper.toggleWifi(newState) { success ->
                if (success) {
                    controlStates["wifi"] = newState
                    updateButtonState(R.id.wifiButton, newState)
                    updateWifiStatus()
                } else {
                    controlStates["wifi"] = SystemControlHelper.isWifiEnabled(this)
                    updateButtonState(R.id.wifiButton, controlStates["wifi"] ?: false)
                    updateWifiStatus()
                }
            }
        }
        
        controlCenterView?.findViewById<View>(R.id.wifiButton)?.setOnLongClickListener { button ->
            vibrate()
            showWifiListDialog()
            true
        }
        
        setupQuickSettingsGrid()
        
        controlCenterView?.findViewById<View>(R.id.playButton)?.setOnClickListener { button ->
            MediaControlHelper.playPause(this)
            animateButtonPress(button)
            vibrate()
            handler.postDelayed({
                MediaNotificationListener.refreshMediaInfo(this)
                updateMediaPlayerState()
            }, 300)
        }
        
        controlCenterView?.findViewById<View>(R.id.nextButton)?.setOnClickListener { button ->
            MediaControlHelper.next(this)
            animateButtonPress(button)
            vibrate()
            handler.postDelayed({
                MediaNotificationListener.refreshMediaInfo(this)
                updateMediaPlayerState()
            }, 300)
        }
        
        controlCenterView?.findViewById<View>(R.id.prevButton)?.setOnClickListener { button ->
            MediaControlHelper.previous(this)
            animateButtonPress(button)
            vibrate()
            handler.postDelayed({
                MediaNotificationListener.refreshMediaInfo(this)
                updateMediaPlayerState()
            }, 300)
        }
        
        setupOwnerNameAndSettings()
        
        updateAllButtonStates()
        setupMediaListener()
        updateMediaPlayerState()
        setupAppShortcuts()
        setupBrightnessSlider()
        setupVolumeSlider()
    }
    
    private var hasShownBrightnessPermissionToast = false
    
    private fun setupBrightnessSlider() {
        val brightnessSlider = controlCenterView?.findViewById<View>(R.id.brightnessSlider)
        val brightnessFill = controlCenterView?.findViewById<View>(R.id.brightnessFill)
        
        val currentBrightness = SystemControlHelper.getBrightness(this)
        val maxBrightness = SystemControlHelper.getMaxBrightness()
        val initialProgress = currentBrightness.toFloat() / maxBrightness
        
        brightnessSlider?.post {
            val sliderHeight = brightnessSlider.height
            val fillHeight = (sliderHeight * initialProgress).toInt()
            brightnessFill?.layoutParams?.height = fillHeight
            brightnessFill?.requestLayout()
        }
        
        brightnessSlider?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!SystemControlHelper.canWriteSettings(this)) {
                        if (!hasShownBrightnessPermissionToast) {
                            hasShownBrightnessPermissionToast = true
                            Toast.makeText(this, "Cần cấp quyền thay đổi cài đặt hệ thống để điều chỉnh độ sáng", Toast.LENGTH_LONG).show()
                            handler.postDelayed({
                                SystemControlHelper.openWriteSettingsPermission(this)
                            }, 500)
                        }
                        return@setOnTouchListener true
                    }
                    
                    val sliderHeight = view.height.toFloat()
                    val touchY = event.y.coerceIn(0f, sliderHeight)
                    val progress = 1f - (touchY / sliderHeight)
                    
                    val fillHeight = (sliderHeight * progress).toInt()
                    brightnessFill?.layoutParams?.height = fillHeight
                    brightnessFill?.requestLayout()
                    
                    val brightness = (progress * maxBrightness).toInt().coerceAtLeast(1)
                    SystemControlHelper.setBrightness(this, brightness)
                    
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!SystemControlHelper.canWriteSettings(this)) {
                        return@setOnTouchListener true
                    }
                    
                    val sliderHeight = view.height.toFloat()
                    val touchY = event.y.coerceIn(0f, sliderHeight)
                    val progress = 1f - (touchY / sliderHeight)
                    
                    val fillHeight = (sliderHeight * progress).toInt()
                    brightnessFill?.layoutParams?.height = fillHeight
                    brightnessFill?.requestLayout()
                    
                    val brightness = (progress * maxBrightness).toInt().coerceAtLeast(1)
                    SystemControlHelper.setBrightness(this, brightness)
                    
                    true
                }
                MotionEvent.ACTION_UP -> {
                    vibrate()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupVolumeSlider() {
        val volumeSlider = controlCenterView?.findViewById<View>(R.id.volumeSlider)
        val volumeFill = controlCenterView?.findViewById<View>(R.id.volumeFill)
        
        val currentVolume = SystemControlHelper.getVolume(this)
        val maxVolume = SystemControlHelper.getMaxVolume(this)
        val initialProgress = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f
        
        volumeSlider?.post {
            val sliderHeight = volumeSlider.height
            val fillHeight = (sliderHeight * initialProgress).toInt()
            volumeFill?.layoutParams?.height = fillHeight
            volumeFill?.requestLayout()
        }
        
        volumeSlider?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val sliderHeight = view.height.toFloat()
                    val touchY = event.y.coerceIn(0f, sliderHeight)
                    val progress = 1f - (touchY / sliderHeight)
                    
                    val fillHeight = (sliderHeight * progress).toInt()
                    volumeFill?.layoutParams?.height = fillHeight
                    volumeFill?.requestLayout()
                    
                    val volume = (progress * maxVolume).toInt()
                    SystemControlHelper.setVolume(this, volume)
                    
                    true
                }
                MotionEvent.ACTION_UP -> {
                    vibrate()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupOwnerNameAndSettings() {
        val userNameText = controlCenterView?.findViewById<TextView>(R.id.userNameText)
        val settingsButton = controlCenterView?.findViewById<ImageView>(R.id.settingsButton)
        
        val ownerName = getDeviceOwnerName()
        userNameText?.text = ownerName
        
        settingsButton?.setOnClickListener { button ->
            animateButtonPress(button)
            vibrate()
            openAndroidSettings()
        }
    }
    
    private fun getDeviceOwnerName(): String {
        return try {
            val userManager = getSystemService(Context.USER_SERVICE) as? UserManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                userManager?.userName ?: userManager?.getUserName() ?: getOwnerNameFromAccount()
            } else {
                getOwnerNameFromAccount()
            }
        } catch (e: Exception) {
            getOwnerNameFromAccount()
        }
    }
    
    private fun getOwnerNameFromAccount(): String {
        return try {
            val accounts = android.accounts.AccountManager.get(this).accounts
            if (accounts.isNotEmpty()) {
                val account = accounts.firstOrNull { it.type == "com.google" } ?: accounts.first()
                val name = account.name.split("@").firstOrNull() ?: account.name
                name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else {
                Build.MODEL
            }
        } catch (e: Exception) {
            Build.MODEL
        }
    }
    
    private fun openAndroidSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            hideControlCenter()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể mở cài đặt", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val quickSettingTileViews = mutableMapOf<String, View>()
    
    private fun setupQuickSettingsGrid() {
        val quickSettingsGrid = controlCenterView?.findViewById<LinearLayout>(R.id.quickSettingsGrid) ?: return
        val editButton = controlCenterView?.findViewById<View>(R.id.editQuickSettingsButton)
        
        quickSettingsGrid.removeAllViews()
        quickSettingTileViews.clear()
        
        editButton?.setOnClickListener {
            vibrate()
            hideControlCenter()
            val intent = Intent(this, EditQuickSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        
        val selectedTiles = QuickSettingsManager.getSelectedTiles(this)
        
        val tilesPerRow = 4
        var currentRow: LinearLayout? = null
        
        selectedTiles.forEachIndexed { index, tile ->
            if (index % tilesPerRow == 0) {
                currentRow = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = 12.dpToPx()
                    }
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                quickSettingsGrid.addView(currentRow)
            }
            
            val tileView = createQuickSettingTileView(tile)
            currentRow?.addView(tileView)
            quickSettingTileViews[tile.id] = tileView
        }
        
        syncQuickSettingStates()
        updateQuickSettingTileStates()
    }
    
    private fun createQuickSettingTileView(tile: QuickSettingTile): View {
        val buttonSize = AppearanceSettings.getCircleButtonSizePx(this)
        val containerHeight = (buttonSize * 1.133f).toInt()
        val iconSize = (buttonSize * 0.467f).toInt()
        
        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                containerHeight
            ).apply {
                weight = 1f
            }
        }
        
        val background = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                buttonSize,
                buttonSize
            ).apply {
                gravity = Gravity.CENTER
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(AppearanceSettings.getButtonColorWithOpacity(this@ControlCenterService, false))
            }
            tag = "background"
        }
        container.addView(background)
        
        val icon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize
            ).apply {
                gravity = Gravity.CENTER
            }
            
            if (tile.type == QuickSettingTile.TileType.APP_SHORTCUT && tile.appIcon != null) {
                setImageDrawable(tile.appIcon)
                imageTintList = null
            } else {
                setImageResource(tile.iconResId)
                setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            }
            tag = "icon"
        }
        container.addView(icon)
        
        container.tag = tile.id
        
        container.setOnClickListener { view ->
            animateButtonPress(view)
            vibrate()
            handleQuickSettingTileClick(tile, view)
        }
        
        container.setOnLongClickListener { view ->
            vibrate()
            handleQuickSettingTileLongClick(tile, view)
            true
        }
        
        return container
    }
    
    private fun handleQuickSettingTileClick(tile: QuickSettingTile, view: View) {
        when (tile.id) {
            QuickSettingTile.TILE_BLUETOOTH -> {
                val currentState = SystemControlHelper.isBluetoothEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleBluetooth(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_DO_NOT_DISTURB -> {
                val currentState = quickSettingStates[tile.id] ?: false
                val newState = !currentState
                quickSettingStates[tile.id] = newState
                updateQuickSettingTileState(tile.id, newState)
                SystemControlHelper.toggleDoNotDisturb(this, newState)
            }
            QuickSettingTile.TILE_FLASHLIGHT -> {
                val currentState = SystemControlHelper.isFlashlightOn()
                val newState = !currentState
                val success = SystemControlHelper.toggleFlashlight(this, newState)
                if (success) {
                    quickSettingStates[tile.id] = newState
                    updateQuickSettingTileState(tile.id, newState)
                }
            }
            QuickSettingTile.TILE_ROTATION_LOCK -> {
                val currentState = SystemControlHelper.isRotationLocked(this)
                val newState = !currentState
                ShizukuHelper.setRotationLock(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_SETTINGS -> {
                openAndroidSettings()
            }
            QuickSettingTile.TILE_SCREEN_RECORD -> {
                ShizukuHelper.startScreenRecording { success ->
                    if (!success) {
                        SystemControlHelper.openScreenRecording(this)
                    }
                }
                hideControlCenter()
            }
            QuickSettingTile.TILE_LOCATION -> {
                val currentState = SystemControlHelper.isLocationEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleLocation(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_WIFI -> {
                val currentState = SystemControlHelper.isWifiEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleWifi(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_MOBILE_DATA -> {
                val currentState = SystemControlHelper.isMobileDataEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleMobileData(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_AIRPLANE_MODE -> {
                val currentState = SystemControlHelper.isAirplaneModeEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleAirplaneMode(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_HOTSPOT -> {
                val currentState = SystemControlHelper.isHotspotEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleHotspot(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_NFC -> {
                val currentState = SystemControlHelper.isNfcEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleNfc(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_BATTERY_SAVER -> {
                val currentState = SystemControlHelper.isBatterySaverEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleBatterySaver(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_AUTO_BRIGHTNESS -> {
                val currentState = SystemControlHelper.isAutoBrightnessEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleAutoBrightness(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_DARK_MODE -> {
                val currentState = SystemControlHelper.isDarkModeEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleDarkMode(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_SCREENSHOT -> {
                hideControlCenter()
                handler.postDelayed({
                    SystemControlHelper.takeScreenshot(this)
                }, 500)
            }
            QuickSettingTile.TILE_CAMERA -> {
                SystemControlHelper.openCamera(this)
                hideControlCenter()
            }
            QuickSettingTile.TILE_CALCULATOR -> {
                SystemControlHelper.openCalculator(this)
                hideControlCenter()
            }
            QuickSettingTile.TILE_WALLET -> {
                SystemControlHelper.openWallet(this)
                hideControlCenter()
            }
            QuickSettingTile.TILE_QR_SCANNER -> {
                SystemControlHelper.openQRScanner(this)
                hideControlCenter()
            }
            QuickSettingTile.TILE_EYE_COMFORT -> {
                val currentState = SystemControlHelper.isEyeComfortEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleEyeComfort(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_SYNC -> {
                val currentState = SystemControlHelper.isSyncEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleSync(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            QuickSettingTile.TILE_INVERT_COLORS -> {
                val currentState = SystemControlHelper.isInvertColorsEnabled(this)
                val newState = !currentState
                ShizukuHelper.toggleInvertColors(newState) { success ->
                    if (success) {
                        quickSettingStates[tile.id] = newState
                        updateQuickSettingTileState(tile.id, newState)
                    }
                }
            }
            else -> {
                if (tile.type == QuickSettingTile.TileType.APP_SHORTCUT) {
                    tile.packageName?.let { pkg ->
                        hideControlCenter()
                        try {
                            val pm = packageManager
                            val launchIntent = pm.getLaunchIntentForPackage(pkg)
                            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            launchIntent?.let { startActivity(it) }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Không thể mở ứng dụng", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
    
    private fun handleQuickSettingTileLongClick(tile: QuickSettingTile, view: View) {
        when (tile.id) {
            QuickSettingTile.TILE_BLUETOOTH -> showBluetoothListDialog()
            QuickSettingTile.TILE_WIFI -> showWifiListDialog()
            else -> {}
        }
    }
    
    private val quickSettingStates = mutableMapOf<String, Boolean>()
    
    private fun syncQuickSettingStates() {
        // Run all system state queries in background to avoid UI blocking
        backgroundExecutor.execute {
            val states = mapOf(
                QuickSettingTile.TILE_WIFI to SystemControlHelper.isWifiEnabled(this),
                QuickSettingTile.TILE_BLUETOOTH to SystemControlHelper.isBluetoothEnabled(this),
                QuickSettingTile.TILE_MOBILE_DATA to SystemControlHelper.isMobileDataEnabled(this),
                QuickSettingTile.TILE_FLASHLIGHT to SystemControlHelper.isFlashlightOn(),
                QuickSettingTile.TILE_ROTATION_LOCK to SystemControlHelper.isRotationLocked(this),
                QuickSettingTile.TILE_DO_NOT_DISTURB to SystemControlHelper.isDoNotDisturbEnabled(this),
                QuickSettingTile.TILE_LOCATION to SystemControlHelper.isLocationEnabled(this),
                QuickSettingTile.TILE_AUTO_BRIGHTNESS to SystemControlHelper.isAutoBrightnessEnabled(this),
                QuickSettingTile.TILE_AIRPLANE_MODE to SystemControlHelper.isAirplaneModeEnabled(this),
                QuickSettingTile.TILE_HOTSPOT to SystemControlHelper.isHotspotEnabled(this),
                QuickSettingTile.TILE_NFC to SystemControlHelper.isNfcEnabled(this),
                QuickSettingTile.TILE_BATTERY_SAVER to SystemControlHelper.isBatterySaverEnabled(this),
                QuickSettingTile.TILE_DARK_MODE to SystemControlHelper.isDarkModeEnabled(this),
                QuickSettingTile.TILE_EYE_COMFORT to SystemControlHelper.isEyeComfortEnabled(this),
                QuickSettingTile.TILE_SYNC to SystemControlHelper.isSyncEnabled(this),
                QuickSettingTile.TILE_INVERT_COLORS to SystemControlHelper.isInvertColorsEnabled(this)
            )
            
            handler.post {
                quickSettingStates.putAll(states)
                updateQuickSettingTileStates()
            }
        }
    }
    
    private fun updateQuickSettingTileStates() {
        quickSettingTileViews.forEach { (tileId, view) ->
            val isActive = quickSettingStates[tileId] ?: false
            updateQuickSettingTileState(tileId, isActive)
        }
    }
    
    private fun updateQuickSettingTileState(tileId: String, isActive: Boolean) {
        val tileView = quickSettingTileViews[tileId] ?: return
        val background = tileView.findViewWithTag<View>("background") ?: return
        
        val buttonColor = AppearanceSettings.getButtonColorWithOpacity(this, isActive)
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(buttonColor)
        }
        background.background = drawable
    }
    
    private fun setupAppShortcuts() {
        val shortcuts = AppShortcutManager.getSavedShortcuts(this)
        val container = controlCenterView?.findViewById<LinearLayout>(R.id.appShortcutsContainer)
        val row = controlCenterView?.findViewById<LinearLayout>(R.id.appShortcutsRow)
        
        if (shortcuts.isEmpty()) {
            container?.visibility = View.GONE
            return
        }
        
        container?.visibility = View.VISIBLE
        row?.removeAllViews()
        
        for (packageName in shortcuts) {
            val appInfo = AppShortcutManager.getAppInfo(this, packageName)
            if (appInfo != null) {
                val shortcutView = createShortcutView(appInfo)
                row?.addView(shortcutView)
            }
        }
    }
    
    private fun createShortcutView(appInfo: AppInfo): View {
        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(android.R.dimen.app_icon_size).coerceAtLeast(72.dpToPx()),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8.dpToPx()
            }
        }
        
        val iconView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(52.dpToPx(), 52.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            appInfo.icon?.let { setImageDrawable(it) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        
        val textView = android.widget.TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 56.dpToPx()
            }
            text = appInfo.appName
            textSize = 11f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        
        container.addView(iconView)
        container.addView(textView)
        
        container.setOnClickListener { view ->
            animateButtonPress(view)
            vibrate()
            hideControlCenter()
            AppShortcutManager.launchApp(this, appInfo.packageName)
        }
        
        return container
    }
    
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private fun openAppPicker() {
        hideControlCenter()
        val intent = Intent(this, AppPickerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    
    private fun syncStateFromSystem() {
        // Run system state queries in background to avoid UI blocking
        backgroundExecutor.execute {
            val wifiState = SystemControlHelper.isWifiEnabled(this)
            val bluetoothState = SystemControlHelper.isBluetoothEnabled(this)
            val cellularState = SystemControlHelper.isMobileDataEnabled(this)
            val flashlightState = SystemControlHelper.isFlashlightOn()
            val rotationState = SystemControlHelper.isRotationLocked(this)
            
            handler.post {
                controlStates["wifi"] = wifiState
                controlStates["bluetooth"] = bluetoothState
                controlStates["cellular"] = cellularState
                controlStates["flashlight"] = flashlightState
                controlStates["rotation"] = rotationState
                updateAllButtonStates()
            }
        }
    }

    private fun updateAllButtonStates() {
        updateButtonState(R.id.wifiButton, controlStates["wifi"] ?: false)
        updateWifiStatus()
        updateButtonState(R.id.cellularButton, controlStates["cellular"] ?: false)
        updateCellularStatus()
        updateQuickSettingTileStates()
    }

    private fun updateButtonState(buttonId: Int, isActive: Boolean) {
        val button = controlCenterView?.findViewById<View>(buttonId)
        val iconId = buttonIconMap[buttonId]
        val icon = iconId?.let { controlCenterView?.findViewById<ImageView>(it) }
        
        when {
            circularButtons.contains(buttonId) -> {
                val circleView = (button as? android.view.ViewGroup)?.getChildAt(0)
                val buttonColor = AppearanceSettings.getButtonColorWithOpacity(this, isActive)
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                drawable.setColor(buttonColor)
                circleView?.background = drawable
                icon?.setColorFilter(
                    if (isActive) inactiveColor else inactiveColor,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            toggleButtons.contains(buttonId) -> {
                val toggleColor = AppearanceSettings.getToggleColorWithOpacity(this, isActive)
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                drawable.cornerRadius = 24f * resources.displayMetrics.density
                drawable.setColor(toggleColor)
                button?.background = drawable
                icon?.setColorFilter(
                    inactiveColor,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            else -> {
                icon?.setColorFilter(
                    if (isActive) activeColor else inactiveColor,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
        }
    }
    
    private fun applyAppearanceSettings() {
        applyPlayerAppearance()
        applySliderAppearance()
    }
    
    private fun applyPlayerAppearance() {
        val mediaPlayerWidget = controlCenterView?.findViewById<FrameLayout>(R.id.mediaPlayerWidget)
        val sliderHeightPx = AppearanceSettings.getSliderHeightPx(this)
        
        mediaPlayerWidget?.let { widget ->
            val playerColor = AppearanceSettings.getPlayerColorWithOpacity(this)
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            drawable.cornerRadius = 24f * resources.displayMetrics.density
            drawable.setColor(playerColor)
            widget.background = drawable
            
            widget.layoutParams?.let { params ->
                params.height = sliderHeightPx
                widget.layoutParams = params
            }
        }
    }
    
    private fun applySliderAppearance() {
        val sliderColor = AppearanceSettings.getSliderColorWithOpacity(this)
        val sliderFillColor = AppearanceSettings.getSliderFillColorWithOpacity(this)
        val cornerRadiusDp = 20f * resources.displayMetrics.density
        
        val sliderWidthPx = AppearanceSettings.getSliderWidthPx(this)
        val sliderHeightPx = AppearanceSettings.getSliderHeightPx(this)
        val sliderSpacingPx = AppearanceSettings.getSliderSpacingPx(this)
        
        val brightnessSlider = controlCenterView?.findViewById<FrameLayout>(R.id.brightnessSlider)
        val volumeSlider = controlCenterView?.findViewById<FrameLayout>(R.id.volumeSlider)
        val slidersContainer = brightnessSlider?.parent as? LinearLayout
        
        slidersContainer?.layoutParams?.let { containerParams ->
            containerParams.height = sliderHeightPx
            slidersContainer.layoutParams = containerParams
        }
        
        brightnessSlider?.layoutParams?.let { params ->
            if (params is LinearLayout.LayoutParams) {
                params.width = sliderWidthPx
                params.height = sliderHeightPx
                params.weight = 0f
                params.marginEnd = sliderSpacingPx
                brightnessSlider.layoutParams = params
            }
        }
        
        volumeSlider?.layoutParams?.let { params ->
            if (params is LinearLayout.LayoutParams) {
                params.width = sliderWidthPx
                params.height = sliderHeightPx
                params.weight = 0f
                volumeSlider.layoutParams = params
            }
        }
        
        slidersContainer?.gravity = android.view.Gravity.CENTER_HORIZONTAL
        
        brightnessSlider?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp
            setColor(sliderColor)
        }
        
        volumeSlider?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp
            setColor(sliderColor)
        }
        
        val brightnessFill = controlCenterView?.findViewById<View>(R.id.brightnessFill)
        val volumeFill = controlCenterView?.findViewById<View>(R.id.volumeFill)
        
        val bottomRadii = floatArrayOf(
            0f, 0f,
            0f, 0f,
            cornerRadiusDp, cornerRadiusDp,
            cornerRadiusDp, cornerRadiusDp
        )
        
        brightnessFill?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadii = bottomRadii
            setColor(sliderFillColor)
        }
        
        volumeFill?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadii = bottomRadii
            setColor(sliderFillColor)
        }
    }
    
    private fun updateWifiStatus() {
        val wifiText = controlCenterView?.findViewById<android.widget.TextView>(R.id.wifiText)
        val wifiStatusText = controlCenterView?.findViewById<android.widget.TextView>(R.id.wifiStatusText)
        val isEnabled = controlStates["wifi"] ?: false
        
        if (!isEnabled) {
            wifiText?.text = "Wi-Fi"
            wifiStatusText?.text = "Tắt"
        } else {
            val ssid = SystemControlHelper.getWifiSSID(this)
            if (ssid != null && ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                wifiText?.text = ssid
                wifiStatusText?.text = "Đã kết nối"
            } else {
                wifiText?.text = "Wi-Fi"
                wifiStatusText?.text = "Không kết nối"
            }
        }
    }
    
    private fun updateCellularStatus() {
        val cellularText = controlCenterView?.findViewById<android.widget.TextView>(R.id.cellularText)
        val cellularStatusText = controlCenterView?.findViewById<android.widget.TextView>(R.id.cellularStatusText)
        val isEnabled = controlStates["cellular"] ?: false
        
        cellularText?.text = "Dữ liệu di động"
        cellularStatusText?.text = if (isEnabled) "Đang bật" else "Tắt"
    }

    private fun animateButtonPress(button: View) {
        button.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }
    
    private var lastAlbumArt: Bitmap? = null
    private var blurredAlbumArt: Bitmap? = null
    
    private fun updateMediaPlayerState() {
        val mediaInfo = MediaNotificationListener.currentMediaInfo
        
        val musicTitle = controlCenterView?.findViewById<android.widget.TextView>(R.id.musicTitle)
        val musicTitlePlaying = controlCenterView?.findViewById<android.widget.TextView>(R.id.musicTitlePlaying)
        val musicArtist = controlCenterView?.findViewById<android.widget.TextView>(R.id.musicArtist)
        val albumArtView = controlCenterView?.findViewById<ImageView>(R.id.albumArtView)
        val albumArtBackground = controlCenterView?.findViewById<ImageView>(R.id.albumArtBackground)
        val albumArtOverlay = controlCenterView?.findViewById<View>(R.id.albumArtOverlay)
        val mediaInfoContainer = controlCenterView?.findViewById<View>(R.id.mediaInfoContainer)
        val playButton = controlCenterView?.findViewById<ImageView>(R.id.playButton)
        val audioVisualizer = controlCenterView?.findViewById<AudioVisualizerView>(R.id.audioVisualizer)
        
        if (mediaInfo != null && (mediaInfo.title.isNotEmpty() || mediaInfo.isPlaying)) {
            musicTitle?.visibility = View.GONE
            mediaInfoContainer?.visibility = View.VISIBLE
            
            musicTitlePlaying?.text = mediaInfo.title
            
            if (mediaInfo.artist.isNotEmpty()) {
                musicArtist?.text = mediaInfo.artist
                musicArtist?.visibility = View.VISIBLE
            } else {
                musicArtist?.visibility = View.GONE
            }
            
            if (mediaInfo.albumArt != null) {
                albumArtView?.setImageBitmap(mediaInfo.albumArt)
                
                if (lastAlbumArt != mediaInfo.albumArt) {
                    lastAlbumArt = mediaInfo.albumArt
                    blurredAlbumArt = blurBitmap(mediaInfo.albumArt, 25f)
                }
                
                if (blurredAlbumArt != null) {
                    albumArtBackground?.setImageBitmap(blurredAlbumArt)
                } else {
                    albumArtBackground?.setImageBitmap(mediaInfo.albumArt)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        albumArtBackground?.setRenderEffect(
                            RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
                        )
                    }
                }
                
                albumArtBackground?.visibility = View.VISIBLE
                albumArtOverlay?.visibility = View.VISIBLE
                
                val dominantColors = extractColorsFromBitmap(mediaInfo.albumArt)
                audioVisualizer?.setColors(dominantColors)
            } else {
                albumArtView?.setImageDrawable(null)
                albumArtBackground?.visibility = View.GONE
                albumArtOverlay?.visibility = View.GONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    albumArtBackground?.setRenderEffect(null)
                }
            }
            
            if (mediaInfo.isPlaying) {
                audioVisualizer?.visibility = View.VISIBLE
                audioVisualizer?.setPlaying(true)
                startAudioAnalysis(audioVisualizer)
            } else {
                audioVisualizer?.setPlaying(false)
                audioVisualizer?.visibility = View.GONE
                stopAudioAnalysis()
            }
            
            val playIcon = if (mediaInfo.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            playButton?.setImageResource(playIcon)
        } else {
            val isPlaying = MediaControlHelper.isMusicPlaying(this)
            
            musicTitle?.visibility = View.VISIBLE
            mediaInfoContainer?.visibility = View.GONE
            albumArtBackground?.visibility = View.GONE
            albumArtOverlay?.visibility = View.GONE
            audioVisualizer?.setPlaying(false)
            audioVisualizer?.visibility = View.GONE
            stopAudioAnalysis()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                albumArtBackground?.setRenderEffect(null)
            }
            
            musicTitle?.text = "Không phát"
            musicTitle?.setTextColor(Color.WHITE)
            
            val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            playButton?.setImageResource(playIcon)
        }
    }
    
    private fun blurBitmap(bitmap: Bitmap, radius: Float): Bitmap? {
        return try {
            val width = (bitmap.width * 0.4f).toInt().coerceAtLeast(1)
            val height = (bitmap.height * 0.4f).toInt().coerceAtLeast(1)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
            
            val outputBitmap = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
            
            val renderScript = RenderScript.create(this)
            val intrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
            
            val tmpIn = Allocation.createFromBitmap(renderScript, scaledBitmap)
            val tmpOut = Allocation.createFromBitmap(renderScript, outputBitmap)
            
            intrinsicBlur.setRadius(radius.coerceIn(1f, 25f))
            intrinsicBlur.setInput(tmpIn)
            intrinsicBlur.forEach(tmpOut)
            tmpOut.copyTo(outputBitmap)
            
            renderScript.destroy()
            
            outputBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun extractColorsFromBitmap(bitmap: Bitmap): IntArray {
        return try {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
            val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
            scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
            
            val colorCounts = mutableMapOf<Int, Int>()
            for (pixel in pixels) {
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                if (r + g + b < 30 || r + g + b > 720) continue
                
                val quantizedColor = Color.rgb(
                    (r / 32) * 32 + 16,
                    (g / 32) * 32 + 16,
                    (b / 32) * 32 + 16
                )
                colorCounts[quantizedColor] = (colorCounts[quantizedColor] ?: 0) + 1
            }
            
            val sortedColors = colorCounts.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }
            
            if (sortedColors.size >= 3) {
                intArrayOf(sortedColors[0], sortedColors[1], sortedColors[2])
            } else if (sortedColors.size >= 2) {
                intArrayOf(sortedColors[0], sortedColors[1], sortedColors[0])
            } else if (sortedColors.isNotEmpty()) {
                intArrayOf(sortedColors[0], sortedColors[0], sortedColors[0])
            } else {
                intArrayOf(
                    Color.parseColor("#FF6B6B"),
                    Color.parseColor("#FF8E53"),
                    Color.parseColor("#FFA726")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            intArrayOf(
                Color.parseColor("#FF6B6B"),
                Color.parseColor("#FF8E53"),
                Color.parseColor("#FFA726")
            )
        }
    }
    
    private fun startAudioAnalysis(audioVisualizer: AudioVisualizerView?) {
        if (isAudioAnalyzing || audioVisualizer == null) return
        
        if (audioAnalyzer == null) {
            audioAnalyzer = AudioAnalyzer(this)
        }
        
        if (!audioAnalyzer!!.hasRecordPermission()) {
            audioVisualizer.setRealAudioMode(false)
            return
        }
        
        audioAnalyzer?.setOnBassDetectedListener { bassLevel, subBassLevel ->
            handler.post {
                if (isShowing && controlCenterView != null) {
                    controlCenterView?.findViewById<AudioVisualizerView>(R.id.audioVisualizer)?.updateWithRealAudio(bassLevel, subBassLevel)
                }
            }
        }
        
        audioAnalyzer?.start()
        isAudioAnalyzing = true
        audioVisualizer.setRealAudioMode(true)
    }
    
    private fun stopAudioAnalysis() {
        if (!isAudioAnalyzing) return
        
        audioAnalyzer?.stop()
        isAudioAnalyzing = false
        
        handler.post {
            controlCenterView?.findViewById<AudioVisualizerView>(R.id.audioVisualizer)?.setRealAudioMode(false)
        }
    }
    
    private fun setupMediaListener() {
        MediaNotificationListener.setOnMediaChangedListener { mediaInfo ->
            handler.post {
                updateMediaPlayerState()
            }
        }
        
        if (MediaNotificationListener.isNotificationAccessEnabled(this)) {
            MediaNotificationListener.initMediaSessionManager(this)
        }
    }
    
    private var wifiScannerHelper: WiFiScannerHelper? = null
    private var wifiDialog: AlertDialog? = null
    private var passwordDialog: AlertDialog? = null
    private var bluetoothDialog: AlertDialog? = null
    private var currentBluetoothAdapter: BluetoothDeviceAdapter? = null
    private var currentBluetoothRecyclerView: RecyclerView? = null
    private var currentBluetoothLoadingProgress: ProgressBar? = null
    private var currentBluetoothEmptyText: TextView? = null
    
    private var popupBlurAnimator: ValueAnimator? = null
    private var controlCenterBlurAnimator: ValueAnimator? = null
    private var flashAnimator: ValueAnimator? = null
    private var isPopupAnimating = false
    private var baseBlurRadius = 0f
    
    private fun applyControlCenterBlur(blur: Boolean, onComplete: (() -> Unit)? = null) {
        flashAnimator?.cancel()
        controlCenterBlurAnimator?.cancel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val startBlur = if (blur) 0f else baseBlurRadius.coerceAtLeast(25f)
            val endBlur = if (blur) 25f else 0f
            
            controlCenterBlurAnimator = ValueAnimator.ofFloat(startBlur, endBlur).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                
                addUpdateListener { animator ->
                    val blurValue = animator.animatedValue as Float
                    baseBlurRadius = blurValue
                    controlCenterView?.let { view ->
                        if (blurValue > 0.1f) {
                            view.setRenderEffect(
                                RenderEffect.createBlurEffect(blurValue, blurValue, Shader.TileMode.CLAMP)
                            )
                        } else {
                            view.setRenderEffect(null)
                        }
                    }
                }
                
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        baseBlurRadius = endBlur
                        onComplete?.invoke()
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        baseBlurRadius = endBlur
                    }
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                
                start()
            }
        } else {
            controlCenterView?.animate()
                ?.alpha(if (blur) 0.5f else 1f)
                ?.setDuration(200)
                ?.withEndAction { onComplete?.invoke() }
                ?.start()
        }
    }
    
    private fun animatePopupEntrance(dialogView: View, onComplete: (() -> Unit)? = null) {
        isPopupAnimating = true
        flashAnimator?.cancel()
        
        dialogView.alpha = 0f
        dialogView.scaleX = 0.85f
        dialogView.scaleY = 0.85f
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dialogView.setRenderEffect(
                RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP)
            )
            
            flashAnimator = ValueAnimator.ofFloat(0f, 20f, 0f).apply {
                duration = 350
                startDelay = 50
                interpolator = DecelerateInterpolator()
                
                addUpdateListener { animator ->
                    val additionalBlur = animator.animatedValue as Float
                    val totalBlur = (baseBlurRadius + additionalBlur).coerceIn(0.1f, 60f)
                    controlCenterView?.setRenderEffect(
                        RenderEffect.createBlurEffect(totalBlur, totalBlur, Shader.TileMode.CLAMP)
                    )
                }
                
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        controlCenterView?.setRenderEffect(
                            RenderEffect.createBlurEffect(baseBlurRadius.coerceAtLeast(0.1f), baseBlurRadius.coerceAtLeast(0.1f), Shader.TileMode.CLAMP)
                        )
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        controlCenterView?.setRenderEffect(
                            RenderEffect.createBlurEffect(baseBlurRadius.coerceAtLeast(0.1f), baseBlurRadius.coerceAtLeast(0.1f), Shader.TileMode.CLAMP)
                        )
                    }
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                
                start()
            }
        } else {
            flashAnimator = ValueAnimator.ofFloat(0f, 0.2f, 0f).apply {
                duration = 350
                startDelay = 50
                interpolator = DecelerateInterpolator()
                
                addUpdateListener { animator ->
                    val additionalDim = animator.animatedValue as Float
                    controlCenterView?.alpha = (0.5f - additionalDim).coerceIn(0.2f, 0.5f)
                }
                
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        controlCenterView?.alpha = 0.5f
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        controlCenterView?.alpha = 0.5f
                    }
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                
                start()
            }
        }
        
        val alphaAnimator = ObjectAnimator.ofFloat(dialogView, "alpha", 0f, 1f).apply {
            duration = 300
            startDelay = 50
            interpolator = DecelerateInterpolator()
        }
        
        val scaleXAnimator = ObjectAnimator.ofFloat(dialogView, "scaleX", 0.85f, 1f).apply {
            duration = 350
            startDelay = 50
            interpolator = OvershootInterpolator(0.8f)
        }
        
        val scaleYAnimator = ObjectAnimator.ofFloat(dialogView, "scaleY", 0.85f, 1f).apply {
            duration = 350
            startDelay = 50
            interpolator = OvershootInterpolator(0.8f)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            popupBlurAnimator?.cancel()
            popupBlurAnimator = ValueAnimator.ofFloat(30f, 0f).apply {
                duration = 400
                startDelay = 100
                interpolator = DecelerateInterpolator()
                
                addUpdateListener { animator ->
                    val blurValue = animator.animatedValue as Float
                    if (blurValue > 0.1f) {
                        dialogView.setRenderEffect(
                            RenderEffect.createBlurEffect(blurValue, blurValue, Shader.TileMode.CLAMP)
                        )
                    } else {
                        dialogView.setRenderEffect(null)
                    }
                }
                
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        isPopupAnimating = false
                        onComplete?.invoke()
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        isPopupAnimating = false
                        dialogView.setRenderEffect(null)
                    }
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                
                start()
            }
        } else {
            handler.postDelayed({
                isPopupAnimating = false
                onComplete?.invoke()
            }, 400)
        }
        
        AnimatorSet().apply {
            playTogether(alphaAnimator, scaleXAnimator, scaleYAnimator)
            start()
        }
    }
    
    private fun animatePopupExit(dialogView: View, onComplete: () -> Unit) {
        isPopupAnimating = true
        
        val alphaAnimator = ObjectAnimator.ofFloat(dialogView, "alpha", 1f, 0f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        val scaleXAnimator = ObjectAnimator.ofFloat(dialogView, "scaleX", 1f, 0.9f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        val scaleYAnimator = ObjectAnimator.ofFloat(dialogView, "scaleY", 1f, 0.9f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            popupBlurAnimator?.cancel()
            popupBlurAnimator = ValueAnimator.ofFloat(0f, 15f).apply {
                duration = 200
                interpolator = AccelerateDecelerateInterpolator()
                
                addUpdateListener { animator ->
                    val blurValue = animator.animatedValue as Float
                    if (blurValue > 0.1f) {
                        dialogView.setRenderEffect(
                            RenderEffect.createBlurEffect(blurValue, blurValue, Shader.TileMode.CLAMP)
                        )
                    }
                }
                
                start()
            }
        }
        
        AnimatorSet().apply {
            playTogether(alphaAnimator, scaleXAnimator, scaleYAnimator)
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isPopupAnimating = false
                    onComplete()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    isPopupAnimating = false
                    onComplete()
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            start()
        }
    }
    
    private fun showWifiListDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wifi_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.wifiRecyclerView)
        val loadingProgress = dialogView.findViewById<ProgressBar>(R.id.loadingProgress)
        val emptyText = dialogView.findViewById<TextView>(R.id.emptyText)
        val wifiSwitch = dialogView.findViewById<android.widget.Switch>(R.id.wifiSwitch)
        val settingsButton = dialogView.findViewById<TextView>(R.id.settingsButton)
        val connectedSection = dialogView.findViewById<LinearLayout>(R.id.connectedSection)
        val connectedWifiItem = dialogView.findViewById<LinearLayout>(R.id.connectedWifiItem)
        val connectedWifiName = dialogView.findViewById<TextView>(R.id.connectedWifiName)
        val connectedWifiLock = dialogView.findViewById<ImageView>(R.id.connectedWifiLock)
        val savedNetworksLabel = dialogView.findViewById<TextView>(R.id.savedNetworksLabel)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        var adapter: WiFiNetworkAdapter? = null
        
        wifiScannerHelper = WiFiScannerHelper(this)
        
        val isWifiEnabled = SystemControlHelper.isWifiEnabled(this)
        wifiSwitch.isChecked = isWifiEnabled
        
        val updateWifiUI: (Boolean) -> Unit = { enabled ->
            if (enabled) {
                loadingProgress.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                emptyText.visibility = View.GONE
                connectedSection.visibility = View.GONE
                savedNetworksLabel.visibility = View.GONE
                
                wifiScannerHelper?.startScan { networks ->
                    loadingProgress.visibility = View.GONE
                    
                    val connectedNetwork = networks.find { it.isConnected }
                    val otherNetworks = networks.filter { !it.isConnected }
                    
                    if (connectedNetwork != null) {
                        connectedSection.visibility = View.VISIBLE
                        connectedWifiName.text = connectedNetwork.ssid
                        connectedWifiLock.visibility = if (connectedNetwork.isSecured) View.VISIBLE else View.GONE
                        
                        connectedWifiItem.setOnClickListener {
                            onWifiNetworkSelected(connectedNetwork)
                        }
                    } else {
                        connectedSection.visibility = View.GONE
                    }
                    
                    if (otherNetworks.isEmpty() && connectedNetwork == null) {
                        emptyText.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        savedNetworksLabel.visibility = View.GONE
                    } else if (otherNetworks.isNotEmpty()) {
                        emptyText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        savedNetworksLabel.visibility = View.VISIBLE
                        
                        if (adapter == null) {
                            adapter = WiFiNetworkAdapter(otherNetworks) { network ->
                                onWifiNetworkSelected(network)
                            }
                            recyclerView.adapter = adapter
                        } else {
                            adapter?.updateNetworks(otherNetworks)
                        }
                    } else {
                        recyclerView.visibility = View.GONE
                        savedNetworksLabel.visibility = View.GONE
                    }
                }
            } else {
                loadingProgress.visibility = View.GONE
                recyclerView.visibility = View.GONE
                connectedSection.visibility = View.GONE
                savedNetworksLabel.visibility = View.GONE
                emptyText.text = "Wi-Fi đã tắt"
                emptyText.visibility = View.VISIBLE
            }
        }
        
        wifiSwitch.setOnCheckedChangeListener { _, isChecked ->
            vibrate()
            ShizukuHelper.toggleWifi(isChecked) { success ->
                if (success) {
                    controlStates["wifi"] = isChecked
                    updateButtonState(R.id.wifiButton, isChecked)
                    updateWifiStatus()
                    handler.postDelayed({
                        updateWifiUI(isChecked)
                    }, 500)
                } else {
                    wifiSwitch.isChecked = !isChecked
                }
            }
        }
        
        settingsButton.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                wifiDialog?.dismiss()
            } catch (e: Exception) {
                Toast.makeText(this, "Không thể mở cài đặt Wi-Fi", Toast.LENGTH_SHORT).show()
            }
        }
        
        applyControlCenterBlur(true)
        
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
        builder.setView(dialogView)
        
        wifiDialog = builder.create()
        wifiDialog?.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            setBackgroundDrawableResource(android.R.color.transparent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setDimAmount(0.4f)
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes = attributes.also { it.blurBehindRadius = 40 }
            }
            setWindowAnimations(0)
        }
        wifiDialog?.setCanceledOnTouchOutside(true)
        
        wifiDialog?.setOnShowListener {
            dialogView.post {
                animatePopupEntrance(dialogView)
            }
        }
        
        wifiDialog?.setOnDismissListener {
            wifiScannerHelper?.cleanup()
            applyControlCenterBlur(false)
        }
        
        wifiDialog?.show()
        
        updateWifiUI(isWifiEnabled)
    }
    
    private fun onWifiNetworkSelected(network: WiFiNetwork) {
        if (network.isConnected) {
            Toast.makeText(this, "Đã kết nối với ${network.ssid}", Toast.LENGTH_SHORT).show()
            return
        }
        
        wifiDialog?.dismiss()
        
        if (network.isSecured) {
            showWifiPasswordDialog(network)
        } else {
            connectToOpenWifiNetwork(network)
        }
    }
    
    private fun connectToOpenWifiNetwork(network: WiFiNetwork) {
        Toast.makeText(this, "Đang kết nối với ${network.ssid}...", Toast.LENGTH_SHORT).show()
        
        val scanner = wifiScannerHelper ?: WiFiScannerHelper(this).also { wifiScannerHelper = it }
        
        scanner.connectToNetwork(network.ssid, null, false, network.securityType) { success, message ->
            handler.post {
                Toast.makeText(this, message, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                
                if (success) {
                    updateWifiStatus()
                    syncStateFromSystem()
                    updateAllButtonStates()
                }
            }
        }
    }
    
    private fun showWifiPasswordDialog(network: WiFiNetwork) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wifi_password, null)
        val ssidText = dialogView.findViewById<TextView>(R.id.ssidText)
        val passwordInput = dialogView.findViewById<EditText>(R.id.passwordInput)
        val showPasswordCheckbox = dialogView.findViewById<CheckBox>(R.id.showPasswordCheckbox)
        val connectButton = dialogView.findViewById<TextView>(R.id.connectButton)
        val cancelButton = dialogView.findViewById<TextView>(R.id.cancelButton)
        val connectingProgress = dialogView.findViewById<ProgressBar>(R.id.connectingProgress)
        val statusText = dialogView.findViewById<TextView>(R.id.statusText)
        val buttonsContainer = dialogView.findViewById<LinearLayout>(R.id.buttonsContainer)
        
        ssidText.text = network.ssid
        
        showPasswordCheckbox.setOnCheckedChangeListener { _, isChecked ->
            passwordInput.inputType = if (isChecked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordInput.setSelection(passwordInput.text.length)
        }
        
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
        builder.setView(dialogView)
        
        passwordDialog = builder.create()
        passwordDialog?.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        passwordDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        passwordDialog?.setCanceledOnTouchOutside(true)
        
        cancelButton.setOnClickListener {
            passwordDialog?.dismiss()
        }
        
        connectButton.setOnClickListener {
            val password = passwordInput.text.toString()
            
            if (password.length < 8) {
                statusText.text = "Mật khẩu phải có ít nhất 8 ký tự"
                statusText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            
            buttonsContainer.visibility = View.GONE
            connectingProgress.visibility = View.VISIBLE
            statusText.text = "Đang kết nối..."
            statusText.setTextColor(Color.parseColor("#AAAAAA"))
            statusText.visibility = View.VISIBLE
            
            connectToWifiNetwork(network, password, statusText, connectingProgress, buttonsContainer)
        }
        
        passwordDialog?.show()
    }
    
    private fun connectToWifiNetwork(network: WiFiNetwork, password: String?, statusText: TextView?, connectingProgress: ProgressBar?, buttonsContainer: LinearLayout?) {
        val scanner = wifiScannerHelper ?: WiFiScannerHelper(this).also { wifiScannerHelper = it }
        
        scanner.connectToNetwork(network.ssid, password, network.isSecured, network.securityType) { success, message ->
            handler.post {
                connectingProgress?.visibility = View.GONE
                
                if (success) {
                    passwordDialog?.dismiss()
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    updateWifiStatus()
                    syncStateFromSystem()
                    updateAllButtonStates()
                } else {
                    statusText?.text = message
                    statusText?.setTextColor(Color.parseColor("#FF5722"))
                    statusText?.visibility = View.VISIBLE
                    buttonsContainer?.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun showBluetoothListDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bluetooth_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.bluetoothRecyclerView)
        val loadingProgress = dialogView.findViewById<ProgressBar>(R.id.loadingProgress)
        val emptyText = dialogView.findViewById<TextView>(R.id.emptyText)
        val bluetoothSwitch = dialogView.findViewById<android.widget.Switch>(R.id.bluetoothSwitch)
        val settingsButton = dialogView.findViewById<TextView>(R.id.settingsButton)
        val pairedDevicesLabel = dialogView.findViewById<TextView>(R.id.pairedDevicesLabel)
        
        currentBluetoothRecyclerView = recyclerView
        currentBluetoothLoadingProgress = loadingProgress
        currentBluetoothEmptyText = emptyText
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val isBluetoothEnabled = SystemControlHelper.isBluetoothEnabled(this)
        bluetoothSwitch.isChecked = isBluetoothEnabled
        
        val updateBluetoothUI: (Boolean) -> Unit = { enabled ->
            if (enabled) {
                loadingProgress.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                emptyText.visibility = View.GONE
                pairedDevicesLabel.visibility = View.GONE
                
                ShizukuHelper.scanBluetoothDevices { devices ->
                    handler.post {
                        loadingProgress.visibility = View.GONE
                        
                        if (devices.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                            pairedDevicesLabel.visibility = View.GONE
                        } else {
                            emptyText.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            pairedDevicesLabel.visibility = View.VISIBLE
                            
                            if (currentBluetoothAdapter == null) {
                                currentBluetoothAdapter = BluetoothDeviceAdapter(devices) { device ->
                                    onBluetoothDeviceSelected(device)
                                }
                                recyclerView.adapter = currentBluetoothAdapter
                            } else {
                                currentBluetoothAdapter?.updateDevices(devices)
                            }
                        }
                    }
                }
            } else {
                loadingProgress.visibility = View.GONE
                recyclerView.visibility = View.GONE
                pairedDevicesLabel.visibility = View.GONE
                emptyText.text = "Bluetooth đã tắt"
                emptyText.visibility = View.VISIBLE
            }
        }
        
        bluetoothSwitch.setOnCheckedChangeListener { _, isChecked ->
            vibrate()
            ShizukuHelper.toggleBluetooth(isChecked) { success ->
                if (success) {
                    controlStates["bluetooth"] = isChecked
                    setupQuickSettingsGrid()
                    handler.postDelayed({
                        updateBluetoothUI(isChecked)
                    }, 500)
                } else {
                    bluetoothSwitch.isChecked = !isChecked
                }
            }
        }
        
        settingsButton.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                bluetoothDialog?.dismiss()
            } catch (e: Exception) {
                Toast.makeText(this, "Không thể mở cài đặt Bluetooth", Toast.LENGTH_SHORT).show()
            }
        }
        
        applyControlCenterBlur(true)
        
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
        builder.setView(dialogView)
        
        bluetoothDialog = builder.create()
        bluetoothDialog?.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            setBackgroundDrawableResource(android.R.color.transparent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setDimAmount(0.4f)
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes = attributes.also { it.blurBehindRadius = 40 }
            }
            setWindowAnimations(0)
        }
        bluetoothDialog?.setCanceledOnTouchOutside(true)
        
        bluetoothDialog?.setOnShowListener {
            dialogView.post {
                animatePopupEntrance(dialogView)
            }
        }
        
        bluetoothDialog?.setOnDismissListener {
            currentBluetoothAdapter = null
            currentBluetoothRecyclerView = null
            currentBluetoothLoadingProgress = null
            currentBluetoothEmptyText = null
            applyControlCenterBlur(false)
        }
        
        bluetoothDialog?.show()
        
        updateBluetoothUI(isBluetoothEnabled)
    }
    
    private fun refreshBluetoothList() {
        // Show loading indicator
        currentBluetoothLoadingProgress?.visibility = View.VISIBLE
        currentBluetoothRecyclerView?.visibility = View.GONE
        currentBluetoothEmptyText?.visibility = View.GONE
        
        ShizukuHelper.scanBluetoothDevices { devices ->
            handler.post {
                // Check if dialog is still showing before updating UI
                if (bluetoothDialog?.isShowing != true) {
                    return@post
                }
                
                currentBluetoothLoadingProgress?.visibility = View.GONE
                
                if (devices.isEmpty()) {
                    currentBluetoothEmptyText?.visibility = View.VISIBLE
                    currentBluetoothRecyclerView?.visibility = View.GONE
                } else {
                    currentBluetoothEmptyText?.visibility = View.GONE
                    currentBluetoothRecyclerView?.visibility = View.VISIBLE
                    currentBluetoothAdapter?.updateDevices(devices)
                }
            }
        }
    }
    
    private fun onBluetoothDeviceSelected(device: ShizukuBluetoothDevice) {
        // Prevent dismiss while operation is in progress
        bluetoothDialog?.setCanceledOnTouchOutside(false)
        
        if (device.isConnected) {
            Toast.makeText(this, "Đang ngắt kết nối ${device.name}...", Toast.LENGTH_SHORT).show()
            
            ShizukuHelper.disconnectBluetoothDevice(device.macAddress) { success, message ->
                handler.post {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    syncStateFromSystem()
                    updateAllButtonStates()
                    
                    // Re-enable dismiss and refresh the list
                    bluetoothDialog?.setCanceledOnTouchOutside(true)
                    
                    // Refresh the list if dialog is still showing
                    if (bluetoothDialog?.isShowing == true) {
                        refreshBluetoothList()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Đang kết nối với ${device.name}...", Toast.LENGTH_SHORT).show()
            
            ShizukuHelper.connectBluetoothDevice(device.macAddress) { success, message ->
                handler.post {
                    Toast.makeText(this, message, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                    syncStateFromSystem()
                    updateAllButtonStates()
                    
                    // Re-enable dismiss and refresh the list
                    bluetoothDialog?.setCanceledOnTouchOutside(true)
                    
                    // Refresh the list if dialog is still showing
                    if (bluetoothDialog?.isShowing == true) {
                        refreshBluetoothList()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        stopAudioAnalysis()
        audioAnalyzer = null
        popupBlurAnimator?.cancel()
        controlCenterBlurAnimator?.cancel()
        flashAnimator?.cancel()
        wifiScannerHelper?.cleanup()
        wifiDialog?.dismiss()
        passwordDialog?.dismiss()
        bluetoothDialog?.dismiss()
        removeViews()
    }
    
    fun directDragStart() {
        handleDragStart()
    }
    
    fun directDragUpdate(dragY: Float) {
        handleDragUpdate(dragY)
    }
    
    fun directDragEnd(velocityY: Float) {
        handleDragEnd(velocityY)
    }
}
