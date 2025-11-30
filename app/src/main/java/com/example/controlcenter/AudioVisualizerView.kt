package com.example.controlcenter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    
    private val barCount = 32
    private val barHeights = FloatArray(barCount)
    private val targetHeights = FloatArray(barCount)
    private val barVelocities = FloatArray(barCount)
    
    private var isPlaying = false
    private var animationPhase = 0f
    private var bassIntensity = 0f
    private var targetBassIntensity = 0f
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                updateBars()
                invalidate()
                postDelayed(this, 16)
            }
        }
    }
    
    private var gradientColors = intArrayOf(
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#FF8E53"),
        Color.parseColor("#FFA726")
    )
    
    init {
        barPaint.style = Paint.Style.FILL
        wavePaint.style = Paint.Style.FILL
        wavePaint.alpha = 100
        
        for (i in 0 until barCount) {
            barHeights[i] = 0.1f
            targetHeights[i] = 0.1f
            barVelocities[i] = 0f
        }
    }
    
    fun setColors(colors: IntArray) {
        if (colors.isNotEmpty()) {
            gradientColors = colors
            invalidate()
        }
    }
    
    fun setPlaying(playing: Boolean) {
        val wasPlaying = isPlaying
        isPlaying = playing
        
        if (playing && !wasPlaying) {
            post(updateRunnable)
        } else if (!playing) {
            removeCallbacks(updateRunnable)
            for (i in 0 until barCount) {
                targetHeights[i] = 0.1f
            }
            invalidate()
        }
    }
    
    fun updateWithBass(intensity: Float) {
        targetBassIntensity = intensity.coerceIn(0f, 1f)
    }
    
    private fun updateBars() {
        animationPhase += 0.15f
        if (animationPhase > 2 * Math.PI.toFloat()) {
            animationPhase -= (2 * Math.PI).toFloat()
        }
        
        bassIntensity += (targetBassIntensity - bassIntensity) * 0.3f
        
        for (i in 0 until barCount) {
            val normalizedPos = i.toFloat() / barCount
            
            val bassSim = if (normalizedPos < 0.3f) {
                val bassPos = normalizedPos / 0.3f
                (sin((animationPhase * 2f + bassPos * Math.PI).toDouble()) * 0.5f + 0.5f).toFloat() *
                    (0.6f + Random.nextFloat() * 0.4f) * (0.7f + bassIntensity * 0.3f)
            } else if (normalizedPos < 0.6f) {
                val midPos = (normalizedPos - 0.3f) / 0.3f
                (sin((animationPhase * 3f + midPos * Math.PI * 2).toDouble()) * 0.5f + 0.5f).toFloat() *
                    (0.3f + Random.nextFloat() * 0.4f)
            } else {
                val highPos = (normalizedPos - 0.6f) / 0.4f
                (sin((animationPhase * 4f + highPos * Math.PI * 3).toDouble()) * 0.5f + 0.5f).toFloat() *
                    (0.2f + Random.nextFloat() * 0.3f)
            }
            
            val beatPulse = (sin((animationPhase * 4).toDouble()) * 0.5f + 0.5f).toFloat()
            val bassBoost = if (normalizedPos < 0.25f) {
                beatPulse * 0.3f * bassIntensity
            } else {
                0f
            }
            
            targetHeights[i] = (bassSim + bassBoost).coerceIn(0.1f, 1f)
            
            val springForce = (targetHeights[i] - barHeights[i]) * 0.4f
            barVelocities[i] = barVelocities[i] * 0.7f + springForce
            barHeights[i] += barVelocities[i]
            barHeights[i] = barHeights[i].coerceIn(0.05f, 1f)
        }
        
        targetBassIntensity = 0.5f + Random.nextFloat() * 0.5f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (width == 0 || height == 0) return
        
        val barWidth = width.toFloat() / barCount
        val barSpacing = barWidth * 0.2f
        val actualBarWidth = barWidth - barSpacing
        val maxBarHeight = height * 0.9f
        val cornerRadius = actualBarWidth / 2
        
        val shader = LinearGradient(
            0f, height.toFloat(), 0f, 0f,
            gradientColors,
            null,
            Shader.TileMode.CLAMP
        )
        barPaint.shader = shader
        
        for (i in 0 until barCount) {
            val left = i * barWidth + barSpacing / 2
            val barHeight = maxBarHeight * barHeights[i]
            val top = height - barHeight
            
            canvas.drawRoundRect(
                left,
                top,
                left + actualBarWidth,
                height.toFloat(),
                cornerRadius,
                cornerRadius,
                barPaint
            )
        }
        
        drawWaveOverlay(canvas)
    }
    
    private fun drawWaveOverlay(canvas: Canvas) {
        if (!isPlaying) return
        
        path.reset()
        path.moveTo(0f, height.toFloat())
        
        val waveHeight = height * 0.15f
        val points = 50
        
        for (i in 0..points) {
            val x = width.toFloat() * i / points
            val normalizedX = i.toFloat() / points
            val y = height - (sin((normalizedX * Math.PI * 4 + animationPhase * 2).toDouble()) * waveHeight * 0.5f + 
                              sin((normalizedX * Math.PI * 2 + animationPhase).toDouble()) * waveHeight * 0.5f).toFloat() - 
                    waveHeight * 0.5f
            
            if (i == 0) {
                path.lineTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        path.lineTo(width.toFloat(), height.toFloat())
        path.close()
        
        val waveShader = LinearGradient(
            0f, height.toFloat(), 0f, height - waveHeight * 2,
            intArrayOf(
                Color.argb(60, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        wavePaint.shader = waveShader
        
        canvas.drawPath(path, wavePaint)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(updateRunnable)
        isPlaying = false
    }
}
