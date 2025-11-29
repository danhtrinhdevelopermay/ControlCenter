package com.example.controlcenter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.StatusBarNotification
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.animation.ValueAnimator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import android.view.VelocityTracker
import android.view.WindowInsets
import android.view.WindowInsetsController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationCenterService : Service() {

    companion object {
        const val ACTION_SHOW = "com.example.controlcenter.NOTIFICATION_ACTION_SHOW"
        const val ACTION_HIDE = "com.example.controlcenter.NOTIFICATION_ACTION_HIDE"
        const val ACTION_DRAG_START = "com.example.controlcenter.NOTIFICATION_ACTION_DRAG_START"
        const val ACTION_DRAG_UPDATE = "com.example.controlcenter.NOTIFICATION_ACTION_DRAG_UPDATE"
        const val ACTION_DRAG_END = "com.example.controlcenter.NOTIFICATION_ACTION_DRAG_END"
        const val ACTION_REFRESH = "com.example.controlcenter.NOTIFICATION_ACTION_REFRESH"
        const val EXTRA_DRAG_Y = "drag_y"
        const val EXTRA_VELOCITY_Y = "velocity_y"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "notification_center_channel"

        var isShowing = false
            private set
        
        var isInteractiveDragging = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var notificationCenterView: View? = null
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
    
    private val maxBlurRadius = 180f
    private val minFlingVelocity = 1000f
    private val openThreshold = 0.0f

    data class NotificationData(
        val id: Int,
        val packageName: String,
        val appName: String,
        val title: String,
        val content: String,
        val time: Long,
        val icon: Drawable?,
        val largeIcon: android.graphics.Bitmap? = null
    )

    private val notifications = mutableListOf<NotificationData>()

    private val notificationChangedListener: ((List<android.service.notification.StatusBarNotification>) -> Unit) = { _ ->
        handler.post {
            loadNotifications()
            refreshNotificationList()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        getScreenDimensions()
        createNotificationChannel()
        loadNotifications()
        MediaNotificationListener.setOnNotificationChangedListener(notificationChangedListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        when (intent?.action) {
            ACTION_SHOW -> {
                if (!isShowing) {
                    showNotificationCenter()
                }
            }
            ACTION_HIDE -> {
                hideNotificationCenter()
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
            ACTION_REFRESH -> {
                loadNotifications()
                refreshNotificationList()
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
                "Notification Center Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification Center Service Notification"
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
            .setContentTitle("Notification Center")
            .setContentText("Notification Center is running")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun loadNotifications() {
        notifications.clear()
        
        val cachedNotifications = MediaNotificationListener.getCachedNotifications()
        if (cachedNotifications.isNotEmpty()) {
            try {
                for (sbn in cachedNotifications) {
                    val notification = sbn.notification
                    val extras = notification.extras
                    
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                    
                    if (title.isEmpty() && text.isEmpty()) continue
                    if (sbn.packageName == packageName) continue
                    
                    val appName = try {
                        val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: PackageManager.NameNotFoundException) {
                        sbn.packageName
                    }
                    
                    val appIcon = try {
                        packageManager.getApplicationIcon(sbn.packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }

                    val largeIcon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        notification.getLargeIcon()?.let { icon ->
                            try {
                                val drawable = icon.loadDrawable(this)
                                if (drawable is android.graphics.drawable.BitmapDrawable) {
                                    drawable.bitmap
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        extras.getParcelable<android.graphics.Bitmap>(Notification.EXTRA_LARGE_ICON)
                    }
                    
                    notifications.add(
                        NotificationData(
                            id = sbn.id,
                            packageName = sbn.packageName,
                            appName = appName,
                            title = title,
                            content = text,
                            time = sbn.postTime,
                            icon = appIcon,
                            largeIcon = largeIcon
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (notifications.isEmpty()) {
            addSampleNotifications()
        }
        
        notifications.sortByDescending { it.time }
    }

    private fun addSampleNotifications() {
        val currentTime = System.currentTimeMillis()
        
        notifications.add(
            NotificationData(
                id = 1,
                packageName = "system",
                appName = "Hệ thống",
                title = "Chào mừng",
                content = "Vuốt xuống từ góc trái để xem thông báo. Vuốt xuống từ góc phải để mở Control Center.",
                time = currentTime,
                icon = null
            )
        )
        
        notifications.add(
            NotificationData(
                id = 2,
                packageName = "system",
                appName = "Hệ thống",
                title = "Cấp quyền Notification Listener",
                content = "Để xem thông báo từ các ứng dụng khác, vui lòng cấp quyền Notification Listener trong cài đặt.",
                time = currentTime - 60000,
                icon = null
            )
        )
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
        
        addNotificationCenterView()
        panelHeight = screenHeight
        notificationCenterView?.translationY = -panelHeight.toFloat()
        
        notificationCenterView?.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                notificationCenterView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                val measuredHeight = notificationCenterView?.height ?: 0
                if (measuredHeight > 0) {
                    panelHeight = measuredHeight
                }
                panelMeasured = true
                notificationCenterView?.translationY = -panelHeight.toFloat()
            }
        })
    }

    private fun handleDragUpdate(dragY: Float) {
        if (!isShowing || !isInteractiveDragging) return
        if (!panelMeasured || panelHeight == 0) {
            notificationCenterView?.post {
                val measuredHeight = notificationCenterView?.height ?: 0
                if (measuredHeight > 0) {
                    panelHeight = measuredHeight
                } else if (panelHeight == 0) {
                    panelHeight = screenHeight
                }
                panelMeasured = true
                handleDragUpdate(dragY)
            }
            return
        }

        val newTranslation = (-panelHeight + dragY).coerceIn(-panelHeight.toFloat(), 0f)
        notificationCenterView?.translationY = newTranslation

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

        val currentTransY = notificationCenterView?.translationY ?: -panelHeight.toFloat()
        val progress = 1f - (kotlin.math.abs(currentTransY) / panelHeight.toFloat())
        
        val shouldOpen = progress > openThreshold || velocityY > minFlingVelocity
        
        if (shouldOpen) {
            animateShowWithVelocity(velocityY.coerceAtLeast(0f))
        } else {
            hideNotificationCenterWithVelocity(-kotlin.math.abs(velocityY))
        }
    }

    private fun showNotificationCenter() {
        isShowing = true
        vibrate()
        loadNotifications()

        addBackgroundView()
        addNotificationCenterView()

        panelHeight = screenHeight
        
        notificationCenterView?.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                notificationCenterView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                val measuredHeight = notificationCenterView?.height ?: 0
                if (measuredHeight > 0) {
                    panelHeight = measuredHeight
                }
                panelMeasured = true
                notificationCenterView?.translationY = -panelHeight.toFloat()

                animateShow()
            }
        })
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

    private fun addNotificationCenterView() {
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
        notificationCenterView = inflater.inflate(R.layout.notification_center_panel, null)

        setupNotificationList()
        setupDismissGesture()

        try {
            windowManager?.addView(notificationCenterView, params)
            showTransparentSystemBars(notificationCenterView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupNotificationList() {
        val container = notificationCenterView?.findViewById<LinearLayout>(R.id.notificationsContainer)
        container?.removeAllViews()

        val clearAllButton = notificationCenterView?.findViewById<TextView>(R.id.clearAllButton)
        clearAllButton?.setOnClickListener {
            vibrate()
            notifications.clear()
            container?.removeAllViews()
            addEmptyState(container)
        }

        if (notifications.isEmpty()) {
            addEmptyState(container)
            return
        }

        val inflater = LayoutInflater.from(this)
        
        for (notification in notifications) {
            val itemView = inflater.inflate(R.layout.item_notification, container, false)
            
            val appIcon = itemView.findViewById<ImageView>(R.id.appIcon)
            val appName = itemView.findViewById<TextView>(R.id.appName)
            val notificationTime = itemView.findViewById<TextView>(R.id.notificationTime)
            val notificationTitle = itemView.findViewById<TextView>(R.id.notificationTitle)
            val notificationContent = itemView.findViewById<TextView>(R.id.notificationContent)
            val notificationImage = itemView.findViewById<ImageView>(R.id.notificationImage)
            
            if (notification.icon != null) {
                appIcon.setImageDrawable(notification.icon)
            } else {
                appIcon.setImageResource(R.drawable.ic_notification)
            }
            
            appName.text = notification.appName
            notificationTime.text = formatTime(notification.time)
            notificationTitle.text = notification.title
            
            if (notification.content.isNotEmpty()) {
                notificationContent.text = notification.content
                notificationContent.visibility = View.VISIBLE
            } else {
                notificationContent.visibility = View.GONE
            }

            if (notification.largeIcon != null) {
                notificationImage.setImageBitmap(notification.largeIcon)
                notificationImage.visibility = View.VISIBLE
            } else {
                notificationImage.visibility = View.GONE
            }

            itemView.setOnClickListener {
                vibrate()
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(notification.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        hideNotificationCenter()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            container?.addView(itemView)
        }
    }

    private fun addEmptyState(container: LinearLayout?) {
        val emptyView = TextView(this).apply {
            text = "Không có thông báo"
            setTextColor(Color.parseColor("#888888"))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 100, 0, 0)
        }
        container?.addView(emptyView)
    }

    private fun refreshNotificationList() {
        if (isShowing && notificationCenterView != null) {
            setupNotificationList()
        }
    }

    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Vừa xong"
            diff < 3600000 -> "${diff / 60000} phút trước"
            diff < 86400000 -> "${diff / 3600000} giờ trước"
            else -> {
                val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
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
        notificationCenterView?.setOnTouchListener { _, event ->
            handlePanelTouch(event)
        }
    }
    
    private fun handleBackgroundTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isInteractiveDragging) {
                    hideNotificationCenter()
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
                currentTranslationY = notificationCenterView?.translationY ?: 0f
                
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
                    notificationCenterView?.translationY = newTranslation

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
                    
                    val currentTransY = notificationCenterView?.translationY ?: 0f
                    val shouldHide = currentTransY < -panelHeight / 3f || velocityY < -minFlingVelocity
                    
                    if (shouldHide) {
                        hideNotificationCenterWithVelocity(velocityY)
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
        
        notificationCenterView?.let { panel ->
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

    private fun hideNotificationCenter() {
        hideNotificationCenterWithVelocity(0f)
    }
    
    private fun hideNotificationCenterWithVelocity(velocity: Float) {
        if (isHiding) return
        isHiding = true
        isInteractiveDragging = false
        
        currentAnimation?.cancel()
        
        notificationCenterView?.let { panel ->
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

        notificationCenterView?.let {
            it.visibility = View.INVISIBLE
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        notificationCenterView = null

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

    override fun onDestroy() {
        super.onDestroy()
        MediaNotificationListener.setOnNotificationChangedListener(null)
        removeViews()
    }
}
