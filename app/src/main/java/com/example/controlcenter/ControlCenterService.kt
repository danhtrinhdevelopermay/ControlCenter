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
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "control_center_channel"

        var isShowing = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var controlCenterView: View? = null
    private var backgroundView: View? = null
    private var blurAnimator: ValueAnimator? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var panelHeight = 0

    private var startY = 0f
    private var currentTranslationY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var isDragging = false
    
    private val maxBlurRadius = 25f
    private val minFlingVelocity = 1000f

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
        "calculator" to false,
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
        R.id.calculatorButton to R.id.calculatorIcon,
        R.id.cameraButton to R.id.cameraIcon
    )
    
    private val bottomCircularButtons = setOf(
        R.id.flashlightButton,
        R.id.timerButton,
        R.id.calculatorButton,
        R.id.cameraButton,
        R.id.dndButton
    )
    
    private val smallOvalButtons = setOf(
        R.id.focusButton,
        R.id.screenMirrorButton,
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

    private fun showControlCenter() {
        isShowing = true
        vibrate()

        addBackgroundView()
        addControlCenterView()

        controlCenterView?.post {
            panelHeight = controlCenterView?.height ?: 0
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
        params.dimAmount = 0.3f
        params.gravity = Gravity.TOP or Gravity.START
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.blurBehindRadius = maxBlurRadius.toInt()
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
                        it.blurBehindRadius = blurRadius.toInt()
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
                hideControlCenter()
                return true
            }
        }
        return false
    }

    private fun handlePanelTouch(event: MotionEvent): Boolean {
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
            springAnimation.start()
        }
    }

    private fun hideControlCenter() {
        hideControlCenterWithVelocity(0f)
    }
    
    private fun hideControlCenterWithVelocity(velocity: Float) {
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
                val progress = 1f - (kotlin.math.abs(value) / panelHeight.toFloat())
                backgroundView?.alpha = progress.coerceIn(0f, 1f)
                updateBlurRadius(progress.coerceIn(0f, 1f))
            }
            springAnimation.addEndListener { _, _, _, _ ->
                removeViews()
            }
            springAnimation.start()
        }
    }

    private fun removeViews() {
        isShowing = false
        isDragging = false
        
        blurAnimator?.cancel()
        blurAnimator = null
        
        velocityTracker?.recycle()
        velocityTracker = null

        controlCenterView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        controlCenterView = null

        backgroundView?.let {
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
        setupControlButton(R.id.wifiButton, "wifi")
        setupControlButton(R.id.bluetoothButton, "bluetooth")
        setupControlButton(R.id.airplaneButton, "airplane")
        setupControlButton(R.id.cellularButton, "cellular")
        setupControlButton(R.id.flashlightButton, "flashlight")
        setupControlButton(R.id.dndButton, "dnd")
        setupControlButton(R.id.rotationButton, "rotation")
        setupControlButton(R.id.focusButton, "focus")
        setupControlButton(R.id.screenMirrorButton, "screenMirror")
        setupControlButton(R.id.timerButton, "timer")
        setupControlButton(R.id.calculatorButton, "calculator")
        setupControlButton(R.id.cameraButton, "camera")

        updateAllButtonStates()
    }

    private fun setupControlButton(viewId: Int, key: String) {
        val button = controlCenterView?.findViewById<View>(viewId)
        button?.setOnClickListener {
            controlStates[key] = !(controlStates[key] ?: false)
            updateButtonState(viewId, controlStates[key] ?: false)
            animateButtonPress(button)
            vibrate()
        }
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
        updateButtonState(R.id.calculatorButton, controlStates["calculator"] ?: false)
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

    override fun onDestroy() {
        super.onDestroy()
        removeViews()
    }
}
