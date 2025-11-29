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
import android.os.IBinder
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
import android.graphics.Color
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
    
    private val maxBlurRadius = 135f
    private val minFlingVelocity = 1000f
    private val openThreshold = 0.35f

    private val controlStates = mutableMapOf(
        "wifi" to true,
        "bluetooth" to true,
        "airplane" to false,
        "cellular" to true,
        "flashlight" to false,
        "dnd" to false,
        "rotation" to false,
        "focus" to false,
        "screenMirror" to false,
        "timer" to false,
        "camera" to false
    )
    
    private val buttonIconMap = mapOf(
        R.id.wifiButton to R.id.wifiIcon,
        R.id.bluetoothButton to R.id.bluetoothIcon,
        R.id.airplaneButton to R.id.airplaneIcon,
        R.id.cellularButton to R.id.cellularIcon,
        R.id.flashlightButton to R.id.flashlightIcon,
        R.id.dndButton to R.id.dndIcon,
        R.id.rotationButton to R.id.rotationIcon,
        R.id.focusButton to R.id.focusIcon,
        R.id.screenMirrorButton to R.id.screenMirrorIcon,
        R.id.timerButton to R.id.timerIcon,
        R.id.cameraButton to R.id.cameraIcon
    )
    
    private val bottomCircularButtons = setOf(
        R.id.flashlightButton,
        R.id.timerButton,
        R.id.cameraButton,
        R.id.screenMirrorButton
    )
    
    private val smallOvalButtons = setOf(
        R.id.focusButton,
        R.id.dndButton,
        R.id.rotationButton
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
            setBackgroundColor(0x40000000.toInt())
            alpha = 0f

            setOnTouchListener { _, event ->
                handleBackgroundTouch(event)
            }
        }

        try {
            windowManager?.addView(backgroundView, params)
            hideSystemBars(backgroundView)
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
            hideSystemBars(controlCenterView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun hideSystemBars(view: View?) {
        view?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                it.windowInsetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                it.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
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
                } else {
                    controlStates["wifi"] = SystemControlHelper.isWifiEnabled(this)
                    updateButtonState(R.id.wifiButton, controlStates["wifi"] ?: false)
                }
            }
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
        
        controlCenterView?.findViewById<View>(R.id.airplaneButton)?.setOnClickListener { button ->
            val currentState = SystemControlHelper.isAirplaneModeOn(this)
            val newState = !currentState
            animateButtonPress(button)
            vibrate()
            ShizukuHelper.toggleAirplaneMode(newState) { success ->
                if (success) {
                    controlStates["airplane"] = newState
                    updateButtonState(R.id.airplaneButton, newState)
                } else {
                    controlStates["airplane"] = SystemControlHelper.isAirplaneModeOn(this)
                    updateButtonState(R.id.airplaneButton, controlStates["airplane"] ?: false)
                }
            }
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
                } else {
                    controlStates["cellular"] = SystemControlHelper.isMobileDataEnabled(this)
                    updateButtonState(R.id.cellularButton, controlStates["cellular"] ?: false)
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
        
        controlCenterView?.findViewById<View>(R.id.dndButton)?.setOnClickListener { button ->
            val currentState = SystemControlHelper.isDoNotDisturbOn(this)
            val newState = !currentState
            animateButtonPress(button)
            vibrate()
            ShizukuHelper.toggleDoNotDisturb(newState) { success ->
                if (success) {
                    controlStates["dnd"] = newState
                    updateButtonState(R.id.dndButton, newState)
                } else {
                    controlStates["dnd"] = SystemControlHelper.isDoNotDisturbOn(this)
                    updateButtonState(R.id.dndButton, controlStates["dnd"] ?: false)
                }
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
        
        controlCenterView?.findViewById<View>(R.id.focusButton)?.setOnClickListener { button ->
            SystemControlHelper.openFocusSettings(this)
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.screenMirrorButton)?.setOnClickListener { button ->
            SystemControlHelper.openScreenMirroring(this)
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.timerButton)?.setOnClickListener { button ->
            SystemControlHelper.openTimer(this)
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.cameraButton)?.setOnClickListener { button ->
            SystemControlHelper.openCamera(this)
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.playButton)?.setOnClickListener { button ->
            MediaControlHelper.playPause(this)
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.nextButton)?.setOnClickListener { button ->
            MediaControlHelper.next(this)
            animateButtonPress(button)
            vibrate()
        }
        
        controlCenterView?.findViewById<View>(R.id.prevButton)?.setOnClickListener { button ->
            MediaControlHelper.previous(this)
            animateButtonPress(button)
            vibrate()
        }
        
        updateAllButtonStates()
        updateMediaPlayerState()
    }
    
    private fun syncStateFromSystem() {
        controlStates["wifi"] = SystemControlHelper.isWifiEnabled(this)
        controlStates["bluetooth"] = SystemControlHelper.isBluetoothEnabled(this)
        controlStates["airplane"] = SystemControlHelper.isAirplaneModeOn(this)
        controlStates["cellular"] = SystemControlHelper.isMobileDataEnabled(this)
        controlStates["flashlight"] = SystemControlHelper.isFlashlightOn()
        controlStates["dnd"] = SystemControlHelper.isDoNotDisturbOn(this)
        controlStates["rotation"] = SystemControlHelper.isRotationLocked(this)
    }

    private fun updateAllButtonStates() {
        updateButtonState(R.id.wifiButton, controlStates["wifi"] ?: false)
        updateButtonState(R.id.bluetoothButton, controlStates["bluetooth"] ?: false)
        updateButtonState(R.id.airplaneButton, controlStates["airplane"] ?: false)
        updateButtonState(R.id.cellularButton, controlStates["cellular"] ?: false)
        updateButtonState(R.id.flashlightButton, controlStates["flashlight"] ?: false)
        updateButtonState(R.id.dndButton, controlStates["dnd"] ?: false)
        updateButtonState(R.id.rotationButton, controlStates["rotation"] ?: false)
        updateButtonState(R.id.focusButton, controlStates["focus"] ?: false)
        updateButtonState(R.id.screenMirrorButton, controlStates["screenMirror"] ?: false)
        updateButtonState(R.id.timerButton, controlStates["timer"] ?: false)
        updateButtonState(R.id.cameraButton, controlStates["camera"] ?: false)
    }

    private fun updateButtonState(buttonId: Int, isActive: Boolean) {
        val button = controlCenterView?.findViewById<View>(buttonId)
        val iconId = buttonIconMap[buttonId]
        val icon = iconId?.let { controlCenterView?.findViewById<ImageView>(it) }
        
        val backgroundRes = when {
            bottomCircularButtons.contains(buttonId) -> {
                if (isActive) R.drawable.ios_circle_button_active else R.drawable.ios_circle_button
            }
            smallOvalButtons.contains(buttonId) -> {
                if (isActive) R.drawable.ios_small_button_active else R.drawable.ios_small_button
            }
            else -> {
                if (isActive) R.drawable.control_item_background_active else R.drawable.control_item_background
            }
        }
        
        button?.setBackgroundResource(backgroundRes)
        
        icon?.setColorFilter(
            if (isActive) activeColor else inactiveColor,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
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
        controlCenterView?.findViewById<android.widget.TextView>(R.id.musicTitle)?.let { textView ->
            val isPlaying = MediaControlHelper.isMusicPlaying(this)
            textView.text = if (isPlaying) "Playing" else "Not Playing"
            textView.setTextColor(if (isPlaying) Color.WHITE else Color.parseColor("#999999"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeViews()
    }
}
