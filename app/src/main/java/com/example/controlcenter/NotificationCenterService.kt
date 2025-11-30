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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import java.util.concurrent.Executors
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper

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
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

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
    
    private val maxBlurRadius = 165f
    private val minFlingVelocity = 1000f
    private val openThreshold = 0.0f

    private val notifications = mutableListOf<NotificationData>()
    private var notificationAdapter: NotificationAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var emptyStateView: TextView? = null

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
        if (!MediaNotificationListener.isNotificationAccessEnabled(this)) {
            notifications.clear()
            addPermissionNotification()
            return
        }
        
        if (!MediaNotificationListener.isServiceConnected()) {
            MediaNotificationListener.requestRebind(this)
            handler.postDelayed({
                loadNotificationsAsync()
            }, 300)
            return
        }
        
        loadNotificationsAsync()
    }
    
    private fun loadNotificationsAsync() {
        backgroundExecutor.execute {
            val newNotifications = mutableListOf<NotificationData>()
            
            if (!MediaNotificationListener.isNotificationAccessEnabled(this)) {
                handler.post {
                    notifications.clear()
                    addPermissionNotification()
                    refreshNotificationList()
                }
                return@execute
            }
            
            MediaNotificationListener.forceRefreshNotifications()
            
            val activeNotifications = MediaNotificationListener.getActiveNotifications()
            if (activeNotifications.isNotEmpty()) {
                try {
                    for (sbn in activeNotifications) {
                        val notification = sbn.notification
                        val extras = notification.extras
                        
                        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                        
                        if (title.isEmpty() && text.isEmpty()) continue
                        if (sbn.packageName == packageName) continue
                        
                        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 &&
                            notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
                            continue
                        }
                        
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
                        
                        val contentIntent = notification.contentIntent
                        
                        val actions = mutableListOf<NotificationAction>()
                        notification.actions?.forEach { action ->
                            val actionTitle = action.title?.toString() ?: return@forEach
                            val remoteInputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                                action.remoteInputs
                            } else null
                            val isReplyAction = remoteInputs != null && remoteInputs.isNotEmpty()
                            actions.add(NotificationAction(
                                title = actionTitle,
                                actionIntent = action.actionIntent,
                                remoteInputs = remoteInputs,
                                isReplyAction = isReplyAction
                            ))
                        }
                        
                        newNotifications.add(
                            NotificationData(
                                id = sbn.id,
                                packageName = sbn.packageName,
                                appName = appName,
                                title = title,
                                content = text,
                                time = sbn.postTime,
                                icon = appIcon,
                                largeIcon = largeIcon,
                                key = sbn.key,
                                contentIntent = contentIntent,
                                actions = actions
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            newNotifications.sortByDescending { it.time }
            
            handler.post {
                notifications.clear()
                notifications.addAll(newNotifications)
                refreshNotificationList()
            }
        }
    }
    
    private fun addPermissionNotification() {
        val currentTime = System.currentTimeMillis()
        notifications.add(
            NotificationData(
                id = 1,
                packageName = "system",
                appName = "Hệ thống",
                title = "Cấp quyền truy cập thông báo",
                content = "Để xem thông báo từ các ứng dụng, vui lòng cấp quyền Notification Listener trong cài đặt.",
                time = currentTime,
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
        applyNotificationAppearance()

        try {
            windowManager?.addView(notificationCenterView, params)
            showTransparentSystemBars(notificationCenterView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyNotificationAppearance() {
        val timeText = notificationCenterView?.findViewById<TextView>(R.id.timeText)
        val dateText = notificationCenterView?.findViewById<TextView>(R.id.dateText)
        
        timeText?.setTextColor(Color.WHITE)
        dateText?.setTextColor(Color.WHITE)
        
        notificationAdapter?.notifyDataSetChanged()
    }
    
    private fun getContrastTextColor(backgroundColor: Int): Int {
        val red = Color.red(backgroundColor)
        val green = Color.green(backgroundColor)
        val blue = Color.blue(backgroundColor)
        
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
        
        return if (luminance > 0.5) {
            Color.parseColor("#000000")
        } else {
            Color.parseColor("#FFFFFF")
        }
    }

    private fun setupNotificationList() {
        recyclerView = notificationCenterView?.findViewById(R.id.notificationsRecyclerView)
        val scrollView = notificationCenterView?.findViewById<android.widget.ScrollView>(R.id.notificationsScrollView)
        
        recyclerView?.visibility = View.VISIBLE
        scrollView?.visibility = View.GONE
        
        val clearAllButton = notificationCenterView?.findViewById<ImageView>(R.id.clearAllButton)
        clearAllButton?.setOnClickListener {
            vibrate()
            dismissAllNotifications()
            notifications.clear()
            notificationAdapter?.submitList(emptyList())
            updateEmptyState()
        }

        notificationAdapter = NotificationAdapter(
            onItemClick = { notification -> handleNotificationClick(notification) },
            onActionClick = { action -> handleActionClick(action) },
            getCardColor = { AppearanceSettings.getNotificationColorWithOpacity(this) }
        )

        recyclerView?.apply {
            layoutManager = LinearLayoutManager(this@NotificationCenterService).apply {
                isItemPrefetchEnabled = true
                initialPrefetchItemCount = 5
            }
            adapter = notificationAdapter
            setHasFixedSize(false)
            itemAnimator = null
            setItemViewCacheSize(20)
            isNestedScrollingEnabled = true
            setRecycledViewPool(RecyclerView.RecycledViewPool().apply {
                setMaxRecycledViews(0, 25)
            })
        }

        setupSwipeToDelete()
        updateNotificationList()
    }

    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val notification = notificationAdapter?.removeItem(position)
                    if (notification != null) {
                        vibrate()
                        notifications.remove(notification)
                        dismissNotification(notification)
                        updateEmptyState()
                    }
                }
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun updateNotificationList() {
        notificationAdapter?.submitList(notifications.toList())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (notifications.isEmpty()) {
            if (emptyStateView == null) {
                emptyStateView = TextView(this).apply {
                    text = "Không có thông báo"
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER
                    val dp16 = (16 * resources.displayMetrics.density).toInt()
                    val dp100 = (100 * resources.displayMetrics.density).toInt()
                    setPadding(dp16, dp100, dp16, dp100)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    isClickable = true
                    isFocusable = true
                    
                    var startY = 0f
                    var startTime = 0L
                    
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startY = event.y
                                startTime = System.currentTimeMillis()
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                val deltaY = event.y - startY
                                val deltaTime = System.currentTimeMillis() - startTime
                                val velocity = if (deltaTime > 0) deltaY / deltaTime * 1000 else 0f
                                
                                if (deltaY < -50 && velocity < -200) {
                                    vibrate()
                                    hideNotificationCenter()
                                } else if (Math.abs(deltaY) < 20 && deltaTime < 300) {
                                    vibrate()
                                    hideNotificationCenter()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
            val parent = recyclerView?.parent as? android.view.ViewGroup
            if (emptyStateView?.parent == null) {
                parent?.addView(emptyStateView)
            }
            recyclerView?.visibility = View.GONE
            emptyStateView?.visibility = View.VISIBLE
        } else {
            recyclerView?.visibility = View.VISIBLE
            emptyStateView?.visibility = View.GONE
            (emptyStateView?.parent as? android.view.ViewGroup)?.removeView(emptyStateView)
        }
    }
    
    private fun handleNotificationClick(notification: NotificationData) {
        vibrate()
        try {
            if (notification.packageName == "system") {
                MediaNotificationListener.openNotificationAccessSettings(this)
                hideNotificationCenter()
            } else {
                if (notification.contentIntent != null) {
                    try {
                        notification.contentIntent.send()
                        hideNotificationCenter()
                        return
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                val launchIntent = packageManager.getLaunchIntentForPackage(notification.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    hideNotificationCenter()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun handleActionClick(action: NotificationAction) {
        vibrate()
        try {
            if (action.isReplyAction && action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                showReplyDialog(action)
            } else {
                action.actionIntent?.send()
                hideNotificationCenter()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun showReplyDialog(action: NotificationAction) {
        val remoteInput = action.remoteInputs?.firstOrNull() ?: return
        
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            
            val inputField = EditText(this@NotificationCenterService).apply {
                hint = remoteInput.label ?: "Nhập tin nhắn..."
                setHintTextColor(0x99FFFFFF.toInt())
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12 * resources.displayMetrics.density
                    setColor(0x33FFFFFF)
                }
                setPadding(40, 30, 40, 30)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                isSingleLine = false
                maxLines = 4
            }
            addView(inputField, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            
            val buttonContainer = LinearLayout(this@NotificationCenterService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, 30, 0, 0)
                
                val cancelButton = TextView(this@NotificationCenterService).apply {
                    text = "Hủy"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 16f
                    setPadding(40, 20, 40, 20)
                }
                addView(cancelButton)
                
                val sendButton = TextView(this@NotificationCenterService).apply {
                    text = "Gửi"
                    setTextColor(0xFF007AFF.toInt())
                    textSize = 16f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(40, 20, 40, 20)
                }
                addView(sendButton)
            }
            addView(buttonContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        
        val inputField = (dialogView.getChildAt(0) as EditText)
        val buttonContainer = (dialogView.getChildAt(1) as LinearLayout)
        val cancelButton = buttonContainer.getChildAt(0) as TextView
        val sendButton = buttonContainer.getChildAt(1) as TextView
        
        val dialogParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            width = (screenWidth * 0.85).toInt()
        }
        
        val dialogContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * resources.displayMetrics.density
                setColor(0xDD1C1C1E.toInt())
            }
            addView(dialogView)
        }
        
        var dialogAdded = false
        try {
            windowManager?.addView(dialogContainer, dialogParams)
            dialogAdded = true
            inputField.requestFocus()
            
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            handler.postDelayed({
                imm.showSoftInput(inputField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        
        cancelButton.setOnClickListener {
            if (dialogAdded) {
                try {
                    windowManager?.removeView(dialogContainer)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        sendButton.setOnClickListener {
            val replyText = inputField.text.toString().trim()
            if (replyText.isNotEmpty()) {
                sendReply(action, remoteInput, replyText)
            }
            if (dialogAdded) {
                try {
                    windowManager?.removeView(dialogContainer)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            hideNotificationCenter()
        }
        
        dialogContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (dialogAdded) {
                    try {
                        windowManager?.removeView(dialogContainer)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                true
            } else {
                false
            }
        }
    }
    
    private fun sendReply(action: NotificationAction, remoteInput: android.app.RemoteInput, replyText: String) {
        try {
            val intent = Intent()
            val bundle = android.os.Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            android.app.RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
            action.actionIntent?.send(this, 0, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun dismissNotification(notification: NotificationData) {
        try {
            if (notification.packageName != "system") {
                if (notification.key != null) {
                    MediaNotificationListener.cancelNotificationByKey(notification.key)
                } else {
                    MediaNotificationListener.cancelNotification(notification.packageName, notification.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun dismissAllNotifications() {
        try {
            MediaNotificationListener.cancelAllNotifications()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshNotificationList() {
        if (isShowing && notificationCenterView != null) {
            updateNotificationList()
            applyNotificationAppearance()
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
        val headerView = notificationCenterView?.findViewById<LinearLayout>(R.id.notificationHeader)
        headerView?.setOnTouchListener { _, event ->
            handlePanelTouch(event)
        }
        
        recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                canScrollUp = recyclerView.canScrollVertically(-1)
            }
        })
        
        recyclerView?.setOnTouchListener { _, event ->
            if (!canScrollUp && event.action == MotionEvent.ACTION_MOVE) {
                val deltaY = event.rawY - startY
                if (deltaY < -20 && !isDragging) {
                    isDragging = true
                    startY = event.rawY
                    currentTranslationY = notificationCenterView?.translationY ?: 0f
                    velocityTracker?.clear()
                    velocityTracker = VelocityTracker.obtain()
                }
            }
            
            if (isDragging) {
                handlePanelTouch(event)
                true
            } else {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startY = event.rawY
                }
                false
            }
        }
    }
    
    private var canScrollUp = false
    
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
