package com.example.controlcenter

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager

object SwipeZoneOverlayManager {
    
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false

    fun show(context: Context) {
        if (isShowing) {
            update(context)
            return
        }
        
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val xPercent = SwipeZoneSettings.getZoneXPercent(context)
        val widthPercent = SwipeZoneSettings.getZoneWidthPercent(context)
        val zoneHeight = SwipeZoneSettings.getZoneHeight(context)

        val zoneX = (screenWidth * xPercent / 100f).toInt()
        val zoneWidth = (screenWidth * widthPercent / 100f).toInt().coerceAtLeast(SwipeZoneSettings.MIN_WIDTH_PERCENT)

        val params = WindowManager.LayoutParams(
            zoneWidth,
            zoneHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = zoneX
        params.y = 0

        overlayView = View(context).apply {
            setBackgroundColor(Color.parseColor("#8000FF00"))
        }

        try {
            windowManager?.addView(overlayView, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
            isShowing = false
        }
    }

    fun update(context: Context) {
        if (!isShowing || overlayView == null) return
        
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val xPercent = SwipeZoneSettings.getZoneXPercent(context)
        val widthPercent = SwipeZoneSettings.getZoneWidthPercent(context)
        val zoneHeight = SwipeZoneSettings.getZoneHeight(context)

        val zoneX = (screenWidth * xPercent / 100f).toInt()
        val zoneWidth = (screenWidth * widthPercent / 100f).toInt().coerceAtLeast(SwipeZoneSettings.MIN_WIDTH_PERCENT)

        try {
            val params = overlayView?.layoutParams as? WindowManager.LayoutParams
            params?.let {
                it.x = zoneX
                it.width = zoneWidth
                it.height = zoneHeight
                windowManager?.updateViewLayout(overlayView, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        if (!isShowing) return
        
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayView = null
        windowManager = null
        isShowing = false
    }
    
    fun isVisible(): Boolean = isShowing
}
