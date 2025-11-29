package com.example.controlcenter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
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
    private var currentTranslationY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var isDragging = false
    private var isHiding = false
    private var currentAnimation: SpringAnimation? = null
    
    private val maxBlurRadius = 210f
    private val minFlingVelocity = 1000f
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
        R.id.cellularButton to R.id.cellularIcon,
        R.id.bluetoothButton to R.id.bluetoothIcon,
        R.id.notificationButton to R.id.notificationIcon,
        R.id.flashlightButton to R.id.flashlightIcon,
        R.id.rotationButton to R.id.rotationIcon,
        R.id.cameraButton to R.id.cameraIcon,
        R.id.screenMirrorButton to R.id.screenMirrorIcon,
        R.id.videoButton to R.id.videoIcon,
        R.id.locationButton to R.id.locationIcon
    )
    
    private val circularButtons = setOf(
        R.id.bluetoothButton,
        R.id.notificationButton,
        R.id.flashlightButton,
        R.id.rotationButton,
        R.id.cameraButton,
        R.id.screenMirrorButton,
        R.id.videoButton,
        R.id.locationButton,
        R.id.gridButton
    )
    
    private val toggleButtons = setOf(
        R.id.wifiButton,
        R.id.cellularButton
    )
    
    private val activeColor = Color.parseColor("#007AFF")
    private val inactiveColor = Color.WHITE

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        getScreenDimensions()
        createNotificationChannel()
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
        if (isShowing || isHiding) return
        
        isShowing = true
        isInteractiveDragging = true
        panelMeasured = false
        vibrate()

        addBackgroundView()
        backgroundView?.alpha = 0f
        updateBlurRadius(0f)
        
        addControlCenterView()
        controlCenterView?.translationY = -screenHeight.toFloat()
        
        controlCenterView?.post {
            panelHeight = controlCenterView?.height ?: 0
            if (panelHeight > 0) {
                panelMeasured = true
                controlCenterView?.translationY = -panelHeight.toFloat()
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
        updateBlurRadius(progress.coerceIn(0f, 1f))
    }

    private fun handleDragEnd(velocityY: Float) {
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

    private fun showControlCenter() {
        isShowing = true
        vibrate()

        addBackgroundView()
        addControlCenterView()

        controlCenterView?.post {
            panelHeight = controlCenterView?.height ?: 0
            panelMeasured = true
            controlCenterView?.translationY = -panelHeight.toFloat()

            animateShow()
        }
    }

    private fun addBackgroundView() {
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
                    e.printStackTrace()
                }
            }
        }
    }

    private fun addControlCenterView() {
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
                startY = event.rawY
                currentTranslationY = controlCenterView?.translationY ?: 0f
                
                velocityTracker?.clear()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    velocityTracker?.addMovement(event)
                    
                    val deltaY = event.rawY - startY
                    val newTranslation = (currentTranslationY + deltaY).coerceIn(-panelHeight.toFloat(), 0f)
                    controlCenterView?.translationY = newTranslation

                    val progress = 1f - (kotlin.math.abs(newTranslation) / panelHeight.toFloat())
                    backgroundView?.alpha = progress
                    updateBlurRadius(progress)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    
                    val currentTransY = controlCenterView?.translationY ?: 0f
                    val shouldHide = currentTransY < -panelHeight / 3f || velocityY < -minFlingVelocity
                    
                    if (shouldHide) {
                        hideControlCenterWithVelocity(velocityY)
                    } else {
                        animateShowWithVelocity(velocityY)
                    }
                    
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
                return true
            }
        }
        return false
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
        isHiding = true
        isInteractiveDragging = false
        
        currentAnimation?.cancel()
        
        controlCenterView?.let { panel ->
            val springAnimation = SpringAnimation(
                panel,
                DynamicAnimation.TRANSLATION_Y,
                -panelHeight.toFloat()
            )
            springAnimation.spring.apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
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
            isHiding = false
        }
    }

    private fun removeViews() {
        isShowing = false
        isDragging = false
        isHiding = false
        isInteractiveDragging = false
        panelMeasured = false
        
        currentAnimation?.cancel()
        currentAnimation = null
        
        blurAnimator?.cancel()
        blurAnimator = null
        
        velocityTracker?.recycle()
        velocityTracker = null

        controlCenterView?.let {
            it.visibility = View.INVISIBLE
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        controlCenterView = null

        backgroundView?.let {
            it.visibility = View.INVISIBLE
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        backgroundView = null
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

    private fun setupControlButtons() {
        syncStateFromSystem()
        
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
        
        controlCenterView?.findViewById<View>(R.id.bluetoothButton)?.setOnClickListener { button ->
            val currentState = SystemControlHelper.isBluetoothEnabled(this)
            val newState = !currentState
            animateButtonPress(button)
            vibrate()
            ShizukuHelper.toggleBluetooth(newState) { success ->
                if (success) {
                    controlStates["bluetooth"] = newState
                    updateButtonState(R.id.bluetoothButton, newState)
                } else {
                    controlStates["bluetooth"] = SystemControlHelper.isBluetoothEnabled(this)
                    updateButtonState(R.id.bluetoothButton, controlStates["bluetooth"] ?: false)
                }
            }
        }
        
        controlCenterView?.findViewById<View>(R.id.bluetoothButton)?.setOnLongClickListener { button ->
            vibrate()
            showBluetoothListDialog()
            true
        }
        
        controlCenterView?.findViewById<View>(R.id.cellularButton)?.setOnClickListener { button ->
            val currentState = SystemControlHelper.isMobileDataEnabled(this)
            val newState = !currentState
            animateButtonPress(button)
            vibrate()
            ShizukuHelper.toggleMobileData(newState) { success ->
                if (success) {
                    controlStates["cellular"] = newState
                    updateButtonState(R.id.cellularButton, newState)
                    updateCellularStatus()
                } else {
                    controlStates["cellular"] = SystemControlHelper.isMobileDataEnabled(this)
                    updateButtonState(R.id.cellularButton, controlStates["cellular"] ?: false)
                    updateCellularStatus()
                }
            }
        }
        
        controlCenterView?.findViewById<View>(R.id.flashlightButton)?.setOnClickListener { button ->
            val currentState = SystemControlHelper.isFlashlightOn()
            val newState = !currentState
            animateButtonPress(button)
            vibrate()
            val success = SystemControlHelper.toggleFlashlight(this, newState)
            if (success) {
                controlStates["flashlight"] = newState
                updateButtonState(R.id.flashlightButton, newState)
            } else {
                controlStates["flashlight"] = SystemControlHelper.isFlashlightOn()
                updateButtonState(R.id.flashlightButton, controlStates["flashlight"] ?: false)
            }
        }
        
        controlCenterView?.findViewById<View>(R.id.rotationButton)?.setOnClickListener { button ->
            val currentState = SystemControlHelper.isRotationLocked(this)
            val newState = !currentState
            animateButtonPress(button)
            vibrate()
            ShizukuHelper.setRotationLock(newState) { success ->
                if (success) {
                    controlStates["rotation"] = newState
                    updateButtonState(R.id.rotationButton, newState)
                } else {
                    controlStates["rotation"] = SystemControlHelper.isRotationLocked(this)
                    updateButtonState(R.id.rotationButton, controlStates["rotation"] ?: false)
                }
            }
        }
        
        controlCenterView?.findViewById<View>(R.id.notificationButton)?.setOnClickListener { button ->
            val currentState = controlStates["notification"] ?: true
            val newState = !currentState
            animateButtonPress(button)
            vibrate()
            controlStates["notification"] = newState
            updateButtonState(R.id.notificationButton, newState)
        }
        
        controlCenterView?.findViewById<View>(R.id.cameraButton)?.setOnClickListener { button ->
            SystemControlHelper.openCamera(this)
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.screenMirrorButton)?.setOnClickListener { button ->
            SystemControlHelper.openScreenMirroring(this)
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.videoButton)?.setOnClickListener { button ->
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.locationButton)?.setOnClickListener { button ->
            val currentState = controlStates["location"] ?: false
            val newState = !currentState
            animateButtonPress(button)
            vibrate()
            controlStates["location"] = newState
            updateButtonState(R.id.locationButton, newState)
        }
        
        controlCenterView?.findViewById<View>(R.id.gridButton)?.setOnClickListener { button ->
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.editButton)?.setOnClickListener { button ->
            animateButtonPress(button)
            vibrate()
            openEditShortcuts()
        }
        
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
        
        updateAllButtonStates()
        setupMediaListener()
        updateMediaPlayerState()
        setupAppShortcuts()
        setupBrightnessSlider()
        setupVolumeSlider()
    }
    
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
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val sliderHeight = view.height.toFloat()
                    val touchY = event.y.coerceIn(0f, sliderHeight)
                    val progress = 1f - (touchY / sliderHeight)
                    
                    val fillHeight = (sliderHeight * progress).toInt()
                    brightnessFill?.layoutParams?.height = fillHeight
                    brightnessFill?.requestLayout()
                    
                    val brightness = (progress * maxBrightness).toInt()
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
    
    private fun setupAppShortcuts() {
        val shortcuts = AppShortcutManager.getSavedShortcuts(this)
        val container = controlCenterView?.findViewById<LinearLayout>(R.id.appShortcutsContainer)
        val row = controlCenterView?.findViewById<LinearLayout>(R.id.appShortcutsRow)
        
        if (shortcuts.isEmpty()) {
            container?.visibility = View.GONE
        } else {
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
        
        setupCustomShortcuts()
    }
    
    private fun setupCustomShortcuts() {
        val customShortcutsContainer = controlCenterView?.findViewById<LinearLayout>(R.id.customShortcutsContainer)
        val customShortcutsGrid = controlCenterView?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.customShortcutsGrid)
        
        val repository = com.example.controlcenter.shortcuts.ShortcutRepository(this)
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val shortcuts = repository.getActiveShortcutsList()
            
            if (shortcuts.isEmpty()) {
                customShortcutsContainer?.visibility = View.GONE
                return@launch
            }
            
            customShortcutsContainer?.visibility = View.VISIBLE
            
            val adapter = com.example.controlcenter.shortcuts.CustomShortcutAdapter { shortcut ->
                vibrate()
                hideControlCenter()
                
                when (shortcut.type) {
                    com.example.controlcenter.shortcuts.ShortcutType.SYSTEM -> {
                        shortcut.action?.let { action ->
                            com.example.controlcenter.shortcuts.SystemControlHelper.executeSystemShortcut(this@ControlCenterService, action)
                        }
                    }
                    com.example.controlcenter.shortcuts.ShortcutType.APP -> {
                        shortcut.packageName?.let { packageName ->
                            com.example.controlcenter.shortcuts.SystemControlHelper.launchApp(
                                this@ControlCenterService,
                                packageName,
                                shortcut.activityName
                            )
                        }
                    }
                }
            }
            
            customShortcutsGrid?.apply {
                layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@ControlCenterService, 4)
                this.adapter = adapter
            }
            
            adapter.submitList(shortcuts)
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
    
    private fun openEditShortcuts() {
        hideControlCenter()
        val intent = Intent(this, com.example.controlcenter.shortcuts.EditShortcutsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    
    private fun syncStateFromSystem() {
        controlStates["wifi"] = SystemControlHelper.isWifiEnabled(this)
        controlStates["bluetooth"] = SystemControlHelper.isBluetoothEnabled(this)
        controlStates["cellular"] = SystemControlHelper.isMobileDataEnabled(this)
        controlStates["flashlight"] = SystemControlHelper.isFlashlightOn()
        controlStates["rotation"] = SystemControlHelper.isRotationLocked(this)
    }

    private fun updateAllButtonStates() {
        updateButtonState(R.id.wifiButton, controlStates["wifi"] ?: false)
        updateWifiStatus()
        updateButtonState(R.id.cellularButton, controlStates["cellular"] ?: false)
        updateCellularStatus()
        updateButtonState(R.id.bluetoothButton, controlStates["bluetooth"] ?: false)
        updateButtonState(R.id.flashlightButton, controlStates["flashlight"] ?: false)
        updateButtonState(R.id.rotationButton, controlStates["rotation"] ?: false)
        updateButtonState(R.id.notificationButton, controlStates["notification"] ?: true)
        updateButtonState(R.id.cameraButton, controlStates["camera"] ?: false)
        updateButtonState(R.id.screenMirrorButton, controlStates["screenMirror"] ?: false)
        updateButtonState(R.id.videoButton, controlStates["video"] ?: false)
        updateButtonState(R.id.locationButton, controlStates["location"] ?: false)
    }

    private fun updateButtonState(buttonId: Int, isActive: Boolean) {
        val button = controlCenterView?.findViewById<View>(buttonId)
        val iconId = buttonIconMap[buttonId]
        val icon = iconId?.let { controlCenterView?.findViewById<ImageView>(it) }
        
        when {
            circularButtons.contains(buttonId) -> {
                val circleView = (button as? android.view.ViewGroup)?.getChildAt(0)
                val backgroundRes = if (isActive) R.drawable.miui_circle_button_active else R.drawable.miui_circle_button
                circleView?.setBackgroundResource(backgroundRes)
            }
            toggleButtons.contains(buttonId) -> {
                val backgroundRes = if (isActive) R.drawable.miui_toggle_background_active else R.drawable.miui_toggle_background
                button?.setBackgroundResource(backgroundRes)
            }
        }
        
        icon?.setColorFilter(
            if (isActive) activeColor else inactiveColor,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
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
                albumArtBackground?.setImageBitmap(mediaInfo.albumArt)
                albumArtBackground?.visibility = View.VISIBLE
                albumArtOverlay?.visibility = View.VISIBLE
            } else {
                albumArtView?.setImageDrawable(null)
                albumArtBackground?.visibility = View.GONE
                albumArtOverlay?.visibility = View.GONE
            }
            
            val playIcon = if (mediaInfo.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            playButton?.setImageResource(playIcon)
        } else {
            val isPlaying = MediaControlHelper.isMusicPlaying(this)
            
            musicTitle?.visibility = View.VISIBLE
            mediaInfoContainer?.visibility = View.GONE
            albumArtBackground?.visibility = View.GONE
            albumArtOverlay?.visibility = View.GONE
            
            musicTitle?.text = "Không phát"
            musicTitle?.setTextColor(Color.WHITE)
            
            val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            playButton?.setImageResource(playIcon)
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
    
    private fun showWifiListDialog() {
        if (!SystemControlHelper.isWifiEnabled(this)) {
            Toast.makeText(this, "Vui lòng bật Wi-Fi trước", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wifi_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.wifiRecyclerView)
        val loadingProgress = dialogView.findViewById<ProgressBar>(R.id.loadingProgress)
        val emptyText = dialogView.findViewById<TextView>(R.id.emptyText)
        val refreshButton = dialogView.findViewById<ImageView>(R.id.refreshButton)
        val cancelButton = dialogView.findViewById<TextView>(R.id.cancelButton)
        
        refreshButton.setImageResource(R.drawable.ic_refresh)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        var adapter: WiFiNetworkAdapter? = null
        
        wifiScannerHelper = WiFiScannerHelper(this)
        
        val scanWifi = {
            loadingProgress.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.GONE
            
            refreshButton.animate()
                .rotationBy(360f)
                .setDuration(1000)
                .start()
            
            wifiScannerHelper?.startScan { networks ->
                loadingProgress.visibility = View.GONE
                
                if (networks.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    
                    if (adapter == null) {
                        adapter = WiFiNetworkAdapter(networks) { network ->
                            onWifiNetworkSelected(network)
                        }
                        recyclerView.adapter = adapter
                    } else {
                        adapter?.updateNetworks(networks)
                    }
                }
            }
        }
        
        refreshButton.setOnClickListener {
            scanWifi()
        }
        
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
        builder.setView(dialogView)
        
        wifiDialog = builder.create()
        wifiDialog?.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        wifiDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        wifiDialog?.setCanceledOnTouchOutside(true)
        
        cancelButton.setOnClickListener {
            wifiDialog?.dismiss()
            wifiScannerHelper?.cleanup()
        }
        
        wifiDialog?.setOnDismissListener {
            wifiScannerHelper?.cleanup()
        }
        
        wifiDialog?.show()
        
        scanWifi()
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
        if (!SystemControlHelper.isBluetoothEnabled(this)) {
            Toast.makeText(this, "Vui lòng bật Bluetooth trước", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bluetooth_list, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.bluetoothRecyclerView)
        val loadingProgress = dialogView.findViewById<ProgressBar>(R.id.loadingProgress)
        val emptyText = dialogView.findViewById<TextView>(R.id.emptyText)
        val refreshButton = dialogView.findViewById<ImageView>(R.id.refreshButton)
        val cancelButton = dialogView.findViewById<TextView>(R.id.cancelButton)
        
        // Store references for refresh functionality
        currentBluetoothRecyclerView = recyclerView
        currentBluetoothLoadingProgress = loadingProgress
        currentBluetoothEmptyText = emptyText
        
        refreshButton.setImageResource(R.drawable.ic_refresh)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val scanBluetooth = {
            loadingProgress.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.GONE
            
            refreshButton.animate()
                .rotationBy(360f)
                .setDuration(1000)
                .start()
            
            ShizukuHelper.scanBluetoothDevices { devices ->
                handler.post {
                    loadingProgress.visibility = View.GONE
                    
                    if (devices.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        
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
        }
        
        refreshButton.setOnClickListener {
            scanBluetooth()
        }
        
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
        builder.setView(dialogView)
        
        bluetoothDialog = builder.create()
        bluetoothDialog?.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        bluetoothDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        bluetoothDialog?.setCanceledOnTouchOutside(true)
        
        cancelButton.setOnClickListener {
            bluetoothDialog?.dismiss()
        }
        
        bluetoothDialog?.setOnDismissListener {
            // Clear references when dialog is dismissed
            currentBluetoothAdapter = null
            currentBluetoothRecyclerView = null
            currentBluetoothLoadingProgress = null
            currentBluetoothEmptyText = null
        }
        
        bluetoothDialog?.show()
        
        scanBluetooth()
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
        wifiScannerHelper?.cleanup()
        wifiDialog?.dismiss()
        passwordDialog?.dismiss()
        bluetoothDialog?.dismiss()
        removeViews()
    }
}
