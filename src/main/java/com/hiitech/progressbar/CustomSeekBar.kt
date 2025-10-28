package com.hiitech.playme

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class CustomSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Default values
    private var barHeight = 8f
    private var thumbRadius = 20f
    private var showDots = true
    private var showLabels = true

    private var barColor = Color.DKGRAY
    private var progressColor = Color.parseColor("#FF4081")
    private var thumbColor = Color.WHITE
    private var textColor = Color.WHITE
    private var dotColor = Color.WHITE
    private var dotAlpha = 100
    private var textSizePx = 24f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var max = 38 // total steps
    private var progress = 8 // default position (1.0x)
    private var stepSize = (4.0f - 0.2f) / max

    private var listener: ((Int) -> Unit)? = null

    init {
        // Load XML attributes
        context.theme.obtainStyledAttributes(attrs, R.styleable.CustomSeekBar, 0, 0).apply {
            try {
                barColor = getColor(R.styleable.CustomSeekBar_barColor, barColor)
                progressColor = getColor(R.styleable.CustomSeekBar_progressColor, progressColor)
                thumbColor = getColor(R.styleable.CustomSeekBar_thumbColor, thumbColor)
                textColor = getColor(R.styleable.CustomSeekBar_textColor, textColor)
                dotColor = getColor(R.styleable.CustomSeekBar_dotColor, dotColor)
                dotAlpha = getInt(R.styleable.CustomSeekBar_dotAlpha, dotAlpha)
                barHeight = getDimension(R.styleable.CustomSeekBar_barHeight, barHeight)
                thumbRadius = getDimension(R.styleable.CustomSeekBar_thumbRadius, thumbRadius)
                textSizePx = getDimension(R.styleable.CustomSeekBar_textSize, textSizePx)
                showDots = getBoolean(R.styleable.CustomSeekBar_showDots, true)
                showLabels = getBoolean(R.styleable.CustomSeekBar_showLabels, true)
            } finally {
                recycle()
            }
        }

        barPaint.color = barColor
        progressPaint.color = progressColor
        thumbPaint.color = thumbColor
        textPaint.color = textColor
        textPaint.textSize = textSizePx
        textPaint.textAlign = Paint.Align.CENTER
        dotPaint.color = dotColor
        dotPaint.alpha = dotAlpha
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barStart = paddingStart + thumbRadius
        val barEnd = width - paddingEnd - thumbRadius
        val barWidth = barEnd - barStart
        val centerY = height / 2f
        val cornerRadius = barHeight / 2

        // Draw full bar
        val barRect = RectF(barStart, centerY - barHeight / 2, barEnd, centerY + barHeight / 2)
        canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)

        // Draw progress part
        val progressX = barStart + (progress / max.toFloat()) * barWidth
        val progressRect = RectF(barStart, centerY - barHeight / 2, progressX, centerY + barHeight / 2)
        canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, progressPaint)

        // Draw thumb
        canvas.drawCircle(progressX, centerY, thumbRadius, thumbPaint)

        // Draw labels if enabled
        if (showLabels) {
            val labelSpeeds = listOf(0.2f, 1.0f, 2.0f, 3.0f, 4.0f)
            val fontMetrics = textPaint.fontMetrics
            val labelY = centerY + thumbRadius + (fontMetrics.descent - fontMetrics.ascent)
            for (label in labelSpeeds) {
                val fraction = (label - 0.2f) / (4.0f - 0.2f)
                val labelX = barStart + fraction * barWidth
                canvas.drawText("${label}x", labelX, labelY, textPaint)
            }
        }

        // Draw dots if enabled
        if (showDots) {
            val dotSpeeds = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f)
            for (speed in dotSpeeds) {
                val fraction = (speed - 0.2f) / (4.0f - 0.2f)
                val dotX = barStart + fraction * barWidth
                canvas.drawCircle(dotX, centerY, 6f, dotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val barStart = paddingStart + thumbRadius
        val barEnd = width - paddingEnd - thumbRadius
        val barWidth = barEnd - barStart

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val clampedX = min(max(event.x, barStart), barEnd)
                val fraction = (clampedX - barStart) / barWidth
                val newProgress = (fraction * max).roundToInt()
                setProgress(newProgress)
                listener?.invoke(progress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Public API
    fun setMax(max: Int) {
        this.max = max
        stepSize = (4.0f - 0.2f) / max
        invalidate()
    }

    fun getMax() = max

    fun setProgress(progress: Int) {
        this.progress = progress.coerceIn(0, max)
        invalidate()
    }

    fun getProgress() = progress

    fun getSpeed(): Float = 0.2f + (progress * stepSize)

    fun setSpeed(speed: Float) {
        val p = ((speed - 0.2f) / stepSize).roundToInt()
        setProgress(p)
    }

    fun setOnSeekChangeListener(listener: (Int) -> Unit) {
        this.listener = listener
    }
}
