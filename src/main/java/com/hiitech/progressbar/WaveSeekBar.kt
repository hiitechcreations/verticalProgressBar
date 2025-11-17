package com.hiitech.playme

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.sin

class WaveSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ----------------- Defaults (dp/px aware where appropriate) -----------------
    private val defaultWaveAmplitude = 4f
    private val defaultWavelength = 70f
    private val defaultWaveSpeed = 0.08f
    private val defaultStrokeWidth = 6f
    private val defaultThumbWidth = 10f
    private val defaultThumbHeight = 50f
    private val defaultThumbRadius = 10f

    // ----------------- Configurable properties (backing fields) -----------------
    private var _max = 100
    private var _progress = 0

    private var baseAmplitude = defaultWaveAmplitude
    private var wavelength = defaultWavelength
    private var waveSpeed = defaultWaveSpeed // increment per frame (phase step)
    private var waveStrokeWidth = defaultStrokeWidth
    private var enableWave = true

    private var thumbWidth = defaultThumbWidth
    private var thumbHeight = defaultThumbHeight
    private var thumbRadius = defaultThumbRadius

    // Paints (configured in init)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Reusable objects (no alloc in onDraw)
    private val fgPath = Path()
    private val thumbRect = RectF()

    // Layout cache
    private var viewWidth = 0f
    private var viewHeight = 0f
    private var centerY = 0f

    // Wave runtime state
    private var wavePhase = 0f              // phase (radians)
    private var currentAmplitude = 0f

    // Sampling step (bigger = less CPU, smoother = smaller step)
    // Tune this if you want higher quality (smaller) or better perf (larger)
    private var sampleStep = 2f

    // Animator
    private var waveAnimator: ValueAnimator? = null

    // Listener
    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: WaveSeekBar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekBar: WaveSeekBar)
        fun onStopTrackingTouch(seekBar: WaveSeekBar)
    }

    private var listener: OnSeekBarChangeListener? = null
    fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        listener = l
    }

    // ----------------- Public properties -----------------
    var max: Int
        get() = _max
        set(value) {
            _max = value.coerceAtLeast(1)
            // adjust progress to new max
            progress = progress
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

    // Wave setters accessible from Kotlin
    var waveAmplitude: Float
        get() = baseAmplitude
        set(value) {
            baseAmplitude = value
            invalidate()
        }

    var waveWavelength: Float
        get() = wavelength
        set(value) {
            wavelength = value.coerceAtLeast(1f)
            invalidate()
        }

    var wavePhaseSpeed: Float
        get() = waveSpeed
        set(value) {
            waveSpeed = value
        }

    var waveColor: Int
        get() = fgPaint.color
        set(value) {
            fgPaint.color = value
            invalidate()
        }

    var trackColor: Int
        get() = bgPaint.color
        set(value) {
            bgPaint.color = value
            invalidate()
        }

    var thumbColor: Int
        get() = thumbPaint.color
        set(value) {
            thumbPaint.color = value
            invalidate()
        }

    var waveStroke: Float
        get() = waveStrokeWidth
        set(value) {
            waveStrokeWidth = value
            fgPaint.strokeWidth = value
            bgPaint.strokeWidth = value
            invalidate()
        }

    var enableWaveAnimation: Boolean
        get() = enableWave
        set(value) {
            enableWave = value
            if (enableWave) startWaveAnimator() else stopWaveAnimator()
            invalidate()
        }

    // ----------------- Init & attrs -----------------
    init {
        // default paint config
        bgPaint.apply {
            color = ContextCompat.getColor(context, R.color.dark_gray_for_seekBar)
            style = Paint.Style.STROKE
            strokeWidth = defaultStrokeWidth
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        fgPaint.apply {
            color = ContextCompat.getColor(context, R.color.black)
            style = Paint.Style.STROKE
            strokeWidth = defaultStrokeWidth
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        thumbPaint.apply {
            color = ContextCompat.getColor(context, R.color.black)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Read XML attributes if provided
        attrs?.let { a ->
            val ta = context.obtainStyledAttributes(a, R.styleable.WaveSeekBar)

            _max = ta.getInt(R.styleable.WaveSeekBar_wsb_max, _max)
            _progress = ta.getInt(R.styleable.WaveSeekBar_wsb_progress, _progress)

            baseAmplitude = ta.getFloat(R.styleable.WaveSeekBar_wsb_waveAmplitude, baseAmplitude)
            wavelength = ta.getFloat(R.styleable.WaveSeekBar_wsb_waveWavelength, wavelength)
            waveSpeed = ta.getFloat(R.styleable.WaveSeekBar_wsb_waveSpeed, waveSpeed)
            waveStrokeWidth = ta.getFloat(R.styleable.WaveSeekBar_wsb_waveStrokeWidth, waveStrokeWidth)
            fgPaint.strokeWidth = waveStrokeWidth

            fgPaint.color = ta.getColor(R.styleable.WaveSeekBar_wsb_waveColor, fgPaint.color)

            bgPaint.color = ta.getColor(R.styleable.WaveSeekBar_wsb_trackColor, bgPaint.color)
            bgPaint.strokeWidth = ta.getFloat(R.styleable.WaveSeekBar_wsb_trackStrokeWidth, bgPaint.strokeWidth)

            thumbWidth = ta.getDimension(R.styleable.WaveSeekBar_wsb_thumbWidth, thumbWidth)
            thumbHeight = ta.getDimension(R.styleable.WaveSeekBar_wsb_thumbHeight, thumbHeight)
            thumbRadius = ta.getDimension(R.styleable.WaveSeekBar_wsb_thumbRadius, thumbRadius)
            thumbPaint.color = ta.getColor(R.styleable.WaveSeekBar_wsb_thumbColor, thumbPaint.color)

            enableWave = ta.getBoolean(R.styleable.WaveSeekBar_wsb_enableWave, enableWave)

            ta.recycle()
        }

        // Ensure values valid
        _max = _max.coerceAtLeast(1)
        _progress = _progress.coerceIn(0, _max)

        // Use hardware layer for smoother, GPU-accelerated drawing
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ----------------- Animator control -----------------
    private fun startWaveAnimator() {
        if (!enableWave) return
        if (waveAnimator == null) {
            waveAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
                duration = 3000L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { anim ->
                    // drive phase using animator value and configured speed
                    // Use incremental update to avoid large jumps when speed changes
                    val v = anim.animatedValue as Float
                    wavePhase = (wavePhase + waveSpeed) % ((2 * PI).toFloat())
                    // smooth amplitude approach (can be animated further if needed)
                    currentAmplitude = baseAmplitude
                    invalidate()
                }
            }
        }
        if (waveAnimator?.isStarted != true) waveAnimator?.start()
    }

    private fun stopWaveAnimator() {
        waveAnimator?.cancel()
        waveAnimator = null
        currentAmplitude = 0f
        // keep view invalidated so it redraws without wave
        invalidate()
    }

    // ----------------- Lifecycle hooks -----------------
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (enableWave) startWaveAnimator()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWaveAnimator()
    }

    // ----------------- Layout -----------------
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        centerY = viewHeight * 0.5f
    }

    // ----------------- Drawing (no allocations) -----------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (viewWidth <= 0f || viewHeight <= 0f) return

        val progressX = (progress.toFloat() / max) * viewWidth

        // Draw background track from progressX -> end
        canvas.drawLine(progressX, centerY, viewWidth, centerY, bgPaint)

        // Draw wave from 0 -> progressX
        fgPath.reset()
        fgPath.moveTo(0f, centerY)

        if (enableWave && currentAmplitude != 0f) {
            // sample across [0, progressX] with sampleStep
            var x = 0f
            while (x <= progressX) {
                val y = centerY + currentAmplitude * sin((x / wavelength) * (2 * PI).toFloat() + wavePhase)
                fgPath.lineTo(x, y.toFloat())
                x += sampleStep
            }
            // ensure last point exactly at progressX (avoids small gap)
            if (progressX % sampleStep != 0f) {
                val yEnd = centerY + currentAmplitude * sin((progressX / wavelength) * (2 * PI).toFloat() + wavePhase)
                fgPath.lineTo(progressX, yEnd.toFloat())
            }
        } else {
            // flat line when wave disabled
            fgPath.lineTo(progressX, centerY)
        }

        canvas.drawPath(fgPath, fgPaint)

        // Draw thumb (round rect)
        val halfThumbW = thumbWidth / 2f
        val halfThumbH = thumbHeight / 2f

        thumbRect.set(
            progressX - halfThumbW,
            centerY - halfThumbH,
            progressX + halfThumbW,
            centerY + halfThumbH
        )
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint)
    }

    // ----------------- Touch handling -----------------
    private var isTracking = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isTracking = true
                listener?.onStartTrackingTouch(this)
                updateProgressFromTouch(event.x, true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateProgressFromTouch(event.x, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                updateProgressFromTouch(event.x, true)
                isTracking = false
                listener?.onStopTrackingTouch(this)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromTouch(x: Float, fromUser: Boolean) {
        if (viewWidth <= 0f) return
        val fraction = (x / viewWidth).coerceIn(0f, 1f)
        val newProgress = (fraction * _max).toInt()
        if (newProgress != _progress) {
            _progress = newProgress
            listener?.onProgressChanged(this, _progress, fromUser)
            invalidate()
        }
    }
}
