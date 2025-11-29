package com.example.controlcenter

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: ViewGroup
    private lateinit var backgroundImage: View
    private lateinit var controlCenterPanel: View
    private lateinit var gestureArea: View

    private var isControlCenterVisible = false
    private var isPanelReady = false
    private var startY = 0f
    private var currentTranslationY = 0f
    private val maxBlurRadius = 25f
    private var panelHeight = 0

    private val controlStates = mutableMapOf(
        "wifi" to true,
        "bluetooth" to false,
        "airplane" to false,
        "cellular" to true,
        "flashlight" to false,
        "dnd" to false,
        "rotation" to false
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupGestureDetection()
        setupControlButtons()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.rootLayout)
        backgroundImage = findViewById(R.id.backgroundImage)
        controlCenterPanel = findViewById(R.id.controlCenterPanel)
        gestureArea = findViewById(R.id.gestureArea)

        controlCenterPanel.visibility = View.INVISIBLE
        controlCenterPanel.post {
            panelHeight = controlCenterPanel.height
            if (panelHeight > 0) {
                controlCenterPanel.translationY = -panelHeight.toFloat()
                controlCenterPanel.visibility = View.VISIBLE
                isPanelReady = true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetection() {
        gestureArea.setOnTouchListener { _, event ->
            if (isPanelReady && panelHeight > 0) {
                handleOpenGesture(event)
            }
            true
        }

        controlCenterPanel.setOnTouchListener { _, event ->
            if (isControlCenterVisible && isPanelReady && panelHeight > 0) {
                handleDismissGesture(event)
            }
            true
        }

        rootLayout.setOnTouchListener { _, event ->
            if (isControlCenterVisible && event.action == MotionEvent.ACTION_DOWN) {
                val panelBottom = controlCenterPanel.y + controlCenterPanel.height
                if (event.y > panelBottom) {
                    hideControlCenter()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun handleOpenGesture(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.rawY
                currentTranslationY = controlCenterPanel.translationY
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - startY
                if (deltaY > 0) {
                    val newTranslation = (currentTranslationY + deltaY).coerceIn(-panelHeight.toFloat(), 0f)
                    controlCenterPanel.translationY = newTranslation

                    val progress = 1f - (kotlin.math.abs(newTranslation) / panelHeight.toFloat())
                    applyBlurEffect(progress)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val currentTransY = controlCenterPanel.translationY
                val dragDistance = event.rawY - startY
                val threshold = panelHeight / 4f

                if (dragDistance > threshold || currentTransY > -panelHeight / 2f) {
                    showControlCenter()
                } else {
                    hideControlCenter()
                }
            }
        }
        return true
    }

    private fun handleDismissGesture(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.rawY
                currentTranslationY = controlCenterPanel.translationY
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - startY
                if (deltaY < 0) {
                    val newTranslation = (currentTranslationY + deltaY).coerceIn(-panelHeight.toFloat(), 0f)
                    controlCenterPanel.translationY = newTranslation

                    val progress = 1f - (kotlin.math.abs(newTranslation) / panelHeight.toFloat())
                    applyBlurEffect(progress)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val currentTransY = controlCenterPanel.translationY
                if (currentTransY < -panelHeight / 3f) {
                    hideControlCenter()
                } else {
                    showControlCenter()
                }
            }
        }
        return true
    }

    private fun showControlCenter() {
        if (!isPanelReady || panelHeight <= 0) return

        isControlCenterVisible = true

        val springAnimation = SpringAnimation(controlCenterPanel, DynamicAnimation.TRANSLATION_Y, 0f)
        springAnimation.spring.apply {
            stiffness = SpringForce.STIFFNESS_MEDIUM
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        springAnimation.addUpdateListener { _, value, _ ->
            val progress = 1f - (kotlin.math.abs(value) / panelHeight.toFloat())
            applyBlurEffect(progress.coerceIn(0f, 1f))
        }
        springAnimation.start()
    }

    private fun hideControlCenter() {
        if (!isPanelReady || panelHeight <= 0) return

        isControlCenterVisible = false

        val springAnimation = SpringAnimation(
            controlCenterPanel,
            DynamicAnimation.TRANSLATION_Y,
            -panelHeight.toFloat()
        )
        springAnimation.spring.apply {
            stiffness = SpringForce.STIFFNESS_MEDIUM
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
        springAnimation.addUpdateListener { _, value, _ ->
            val progress = 1f - (kotlin.math.abs(value) / panelHeight.toFloat())
            applyBlurEffect(progress.coerceIn(0f, 1f))
        }
        springAnimation.addEndListener { _, _, _, _ ->
            removeBlurEffect()
        }
        springAnimation.start()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyBlurEffect(progress: Float) {
        if (progress <= 0f) {
            removeBlurEffect()
            return
        }

        val blurRadius = (progress * maxBlurRadius).coerceIn(0.1f, maxBlurRadius)

        val blurEffect = RenderEffect.createBlurEffect(
            blurRadius,
            blurRadius,
            Shader.TileMode.CLAMP
        )
        backgroundImage.setRenderEffect(blurEffect)
    }

    private fun removeBlurEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundImage.setRenderEffect(null)
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

        updateAllButtonStates()
    }

    private fun setupControlButton(viewId: Int, key: String) {
        val button = findViewById<View>(viewId)
        button.setOnClickListener {
            controlStates[key] = !(controlStates[key] ?: false)
            updateButtonState(button, controlStates[key] ?: false)
            animateButtonPress(button)
        }
    }

    private fun updateAllButtonStates() {
        updateButtonState(findViewById(R.id.wifiButton), controlStates["wifi"] ?: false)
        updateButtonState(findViewById(R.id.bluetoothButton), controlStates["bluetooth"] ?: false)
        updateButtonState(findViewById(R.id.airplaneButton), controlStates["airplane"] ?: false)
        updateButtonState(findViewById(R.id.cellularButton), controlStates["cellular"] ?: false)
        updateButtonState(findViewById(R.id.flashlightButton), controlStates["flashlight"] ?: false)
        updateButtonState(findViewById(R.id.dndButton), controlStates["dnd"] ?: false)
        updateButtonState(findViewById(R.id.rotationButton), controlStates["rotation"] ?: false)
    }

    private fun updateButtonState(button: View, isActive: Boolean) {
        button.setBackgroundResource(
            if (isActive) R.drawable.control_item_background_active
            else R.drawable.control_item_background
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isControlCenterVisible) {
            hideControlCenter()
        } else {
            super.onBackPressed()
        }
    }
}
