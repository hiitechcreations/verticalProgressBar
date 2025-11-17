package com.hiitech.progressbar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import kotlin.math.*

class WaveSeekBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // --- Default Values ---
    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    private val defaultWaveAmplitude = dpToPx(4f)
    private val defaultWaveWavelength = dpToPx(70f)
    private val defaultWaveStrokeWidth = dpToPx(6f)
    private val defaultThumbWidth = dpToPx(10f)
    private val defaultThumbHeight = dpToPx(50f)
    private val defaultTrackColor = ContextCompat.getColor(context, R.color.dark_gray_for_seekBar)
    private val defaultWaveColor = ContextCompat.getColor(context, R.color.black)
    private val defaultThumbColor = ContextCompat.getColor(context, R.color.black)

    // === Public Properties ===
    var waveAmplitude: Float = defaultWaveAmplitude
        set(value) {
            field = value
            if (isWaveRunning) {
                currentAmplitude = field
            }
            invalidate()
        }

    var waveWavelength: Float = defaultWaveWavelength
        set(value) {
            field = value
            invalidate()
        }

    var waveStrokeWidth: Float = defaultWaveStrokeWidth
        set(value) {
            field = value
            bgPaint.strokeWidth = field
            fgPaint.strokeWidth = field
            invalidate()
        }

    var thumbWidth: Float = defaultThumbWidth
        set(value) {
            field = value
            invalidate()
        }

    var thumbHeight: Float = defaultThumbHeight
        set(value) {
            field = value
            invalidate()
        }

    var trackColor: Int = defaultTrackColor
        set(value) {
            field = value
            bgPaint.color = field
            invalidate()
        }

    var waveColor: Int = defaultWaveColor
        set(value) {
            field = value
            fgPaint.color = field
            invalidate()
        }

    var thumbColor: Int = defaultThumbColor
        set(value) {
            field = value
            thumbPaint.color = field
            invalidate()
        }

    // === PROGRESS ===
    private var _max = 100
    private var _progress = 0
    private var isTracking = false
    private var waveOffset = 0f
    private var isWaveRunning = false
    private var currentAmplitude = 0f

    // Cached layout values
    private var viewWidth = 0f
    private var viewHeight = 0f
    private var centerY = 0f

    var max: Int
        get() = _max
        set(value) {
            _max = max(1, value) // Must be at least 1
            progress = _progress.coerceIn(0, _max) // Re-coerce progress
            invalidate()
        }

    var progress: Int
        get() = _progress
        set(value) {
            val newVal = value.coerceIn(0, _max)
            if (newVal != _progress) {
                _progress = newVal
                invalidate()
            }
        }

    // === PAINTS ===
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // === REUSABLE OBJECTS (NO ALLOC IN DRAW) ===
    private val fgPath = Path()
    private val thumbRect = RectF()

    // === LISTENER ===
    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: WaveSeekBar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekBar: WaveSeekBar)
        fun onStopTrackingTouch(seekBar: WaveSeekBar)
    }

    private var listener: OnSeekBarChangeListener? = null
    fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener) {
        listener = l
    }
    
    // === INIT ===
    init {
        // Read attributes from XML
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.WaveSeekBar) {
                waveAmplitude = getDimension(R.styleable.WaveSeekBar_wsb_waveAmplitude, defaultWaveAmplitude)
                waveWavelength = getDimension(R.styleable.WaveSeekBar_wsb_waveWavelength, defaultWaveWavelength)
                waveStrokeWidth = getDimension(R.styleable.WaveSeekBar_wsb_waveStrokeWidth, defaultWaveStrokeWidth)
                thumbWidth = getDimension(R.styleable.WaveSeekBar_wsb_thumbWidth, defaultThumbWidth)
                thumbHeight = getDimension(R.styleable.WaveSeekBar_wsb_thumbHeight, defaultThumbHeight)

                trackColor = getColor(R.styleable.WaveSeekBar_wsb_trackColor, defaultTrackColor)
                waveColor = getColor(R.styleable.WaveSeekBar_wsb_waveColor, defaultWaveColor)
                thumbColor = getColor(R.styleable.WaveSeekBar_wsb_thumbColor, defaultThumbColor)

                // Use the 'max' property setter, not the private field
                max = getInt(R.styleable.WaveSeekBar_wsb_max, 100)
                // Use the 'progress' property setter
                progress = getInt(R.styleable.WaveSeekBar_wsb_progress, 0)
            }
        }

        // Apply initial values to paints
        bgPaint.color = trackColor
        bgPaint.strokeWidth = waveStrokeWidth
        fgPaint.color = waveColor
        fgPaint.strokeWidth = waveStrokeWidth
        thumbPaint.color = thumbColor

        // Set initial amplitude
        currentAmplitude = if (isWaveRunning) waveAmplitude else 0f
    }


    // === ANIMATION ===
    private var waveAnimator: ValueAnimator? = null

    private fun startWaveAnimator() {
        if (waveAnimator == null) {
            waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                duration = 3000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = null
                addUpdateListener {
                    waveOffset = (waveOffset + 0.08f) % (2 * Math.PI).toFloat()
                    invalidate()
                }
            }
        }
        if (!(waveAnimator?.isRunning ?: false)) {
            waveAnimator?.start()
        }
    }

    fun startWaveAnimation() {
        startWaveAnimator()
        // Use the public property
        currentAmplitude = waveAmplitude 
        isWaveRunning = true
        invalidate()
    }

    fun stopWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
        isWaveRunning = false
        currentAmplitude = 0f
        invalidate()
    }

    // === LAYOUT ===
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        centerY = viewHeight / 2f
    }

    // === DRAWING ===
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val progressX = (progress.toFloat() / max) * viewWidth

        // Draw background
        canvas.drawLine(progressX, centerY, viewWidth, centerY, bgPaint)

        // Draw wave (reuse Path)
        fgPath.reset()
        createWavePath(fgPath, progressX, centerY)
        canvas.drawPath(fgPath, fgPaint)

        // Draw thumb (reuse RectF)
        // Use public properties
        val halfThumbW = thumbWidth / 2 
        val halfThumbH = thumbHeight / 2
        thumbRect.set(
            progressX - halfThumbW,
            centerY - halfThumbH,
            progressX + halfThumbW,
            centerY + halfThumbH
        )
        // Use thumbWidth for corner radius to make it a perfect pill shape
        canvas.drawRoundRect(thumbRect, thumbWidth / 2f, thumbWidth / 2f, thumbPaint)
    }

    private fun createWavePath(path: Path, width: Float, centerY: Float) {
        val step = 2f
        path.moveTo(0f, centerY)
        var x = 0f
        
        // Ensure wavelength is not zero to avoid division by zero
        val effectiveWavelength = max(1f, waveWavelength)
        
        while (x <= width) {
            val y = centerY + currentAmplitude * sin((x / effectiveWavelength) * (2 * Math.PI) + waveOffset)
            path.lineTo(x, y.toFloat())
            x += step
        }
        // Ensure the path ends exactly at the thumb position
        if (x > width && width > 0) {
             val y = centerY + currentAmplitude * sin((width / effectiveWavelength) * (2 * Math.PI) + waveOffset)
             path.lineTo(width, y.toFloat())
        }
    }

    // === TOUCH HANDLING ===
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                isTracking = true
                listener?.onStartTrackingTouch(this)
                updateProgress(event.x, true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTracking) {
                    updateProgress(event.x, true)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isTracking) {
                    updateProgress(event.x, true)
                    isTracking = false
                    listener?.onStopTrackingTouch(this)
                }
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgress(x: Float, fromUser: Boolean) {
        val fraction = (x / viewWidth).coerceIn(0f, 1f)
        val newProgress = (fraction * _max).toInt()
        
        // Check against the 'progress' property, not the private field
        if (newProgress != progress) {
            // Use the property setter to update
            progress = newProgress 
            listener?.onProgressChanged(this, _progress, fromUser)
            // invalidate() is already called by the 'progress' setter
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWaveAnimation()
    }
}
