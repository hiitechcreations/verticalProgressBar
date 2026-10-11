package com.hiitech.progressbar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.toColorInt
import kotlin.math.*

class WaveSeekBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // === DEFAULTS ===
    private val DEFAULT_BASE_AMPLITUDE_DP = 4f
    private val DEFAULT_WAVELENGTH_DP = 70f
    private val DEFAULT_STROKE_WIDTH_DP = 6f
    private val DEFAULT_THUMB_WIDTH_DP = 10f
    private val DEFAULT_THUMB_HEIGHT_DP = 50f
    private val DEFAULT_WAVE_DURATION = 3000 // ms
    private val DEFAULT_WAVE_ENABLED = true
    private val DEFAULT_BG_COLOR = "#BDBDBD".toColorInt()
    private val DEFAULT_FG_COLOR = Color.BLACK
    private val DEFAULT_THUMB_COLOR = Color.BLACK
    private val DEFAULT_MAX = 100
    private val DEFAULT_PROGRESS = 0
    private val DEFAULT_WAVE_SPEED = 0.08f

    // === CONFIG (populated from attrs) ===
    var baseAmplitude = dpToPx(DEFAULT_BASE_AMPLITUDE_DP)
        set(value) {
            field = value.coerceAtLeast(0f)
            if (isWaveRunning) currentAmplitude = field
            requestLayout()
            invalidate()
        }

    var wavelength = dpToPx(DEFAULT_WAVELENGTH_DP)
        set(value) {
            field = value.coerceAtLeast(1f)
            invalidate()
        }

    var strokeWidth = dpToPx(DEFAULT_STROKE_WIDTH_DP)
        set(value) {
            field = value.coerceAtLeast(1f)
            bgPaint.strokeWidth = field
            fgPaint.strokeWidth = field
            requestLayout()
            invalidate()
        }

    var thumbWidth = dpToPx(DEFAULT_THUMB_WIDTH_DP)
        set(value) {
            field = value.coerceAtLeast(1f)
            requestLayout()
            invalidate()
        }

    var thumbHeight = dpToPx(DEFAULT_THUMB_HEIGHT_DP)
        set(value) {
            field = value.coerceAtLeast(1f)
            requestLayout()
            invalidate()
        }

    var waveDuration = DEFAULT_WAVE_DURATION
        set(value) {
            field = value.coerceAtLeast(100)
            waveAnimator?.duration = field.toLong()
        }

    var isWaveEnabled = DEFAULT_WAVE_ENABLED
        set(value) {
            field = value
            if (value) startWaveAnimator() else pauseWaveAnimator(resetAmplitude = true)
        }

    var waveSpeed = DEFAULT_WAVE_SPEED
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    // Colors
    private var bgColor = DEFAULT_BG_COLOR
    private var fgColor = DEFAULT_FG_COLOR
    private var thumbColor = DEFAULT_THUMB_COLOR

    // === PROGRESS & STATE ===
    private var _max = DEFAULT_MAX
    private var _progress = DEFAULT_PROGRESS
    private var isTracking = false
    private var waveOffset = 0f
    private var isWaveRunning = false
    private var currentAmplitude = 0f
    private var lastFrameTimeMs = 0L

    // Cached layout values
    private var viewWidth = 0f
    private var viewHeight = 0f
    private var centerY = 0f

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

    // === REUSABLE OBJECTS ===
    private val fgPath = Path()
    private val thumbRect = RectF()

    // === LISTENER ===
    interface OnSeekBarChangeListener {
        fun onProgressChanged(seekBar: WaveSeekBar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekBar: WaveSeekBar)
        fun onStopTrackingTouch(seekBar: WaveSeekBar)
    }

    private var listener: OnSeekBarChangeListener? = null
    fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        listener = l
    }

    // === ANIMATION ===
    private var waveAnimator: ValueAnimator? = null

    init {
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.WaveSeekBar, defStyleAttr, 0) {
                baseAmplitude = getFloat(
                    R.styleable.WaveSeekBar_wsb_baseAmplitude,
                    DEFAULT_BASE_AMPLITUDE_DP
                )
                wavelength = getDimension(
                    R.styleable.WaveSeekBar_wsb_wavelength,
                    dpToPx(DEFAULT_WAVELENGTH_DP)
                ).coerceAtLeast(1f)
                strokeWidth = getDimension(
                    R.styleable.WaveSeekBar_wsb_strokeWidth,
                    dpToPx(DEFAULT_STROKE_WIDTH_DP)
                ).coerceAtLeast(1f)
                thumbWidth = getDimension(
                    R.styleable.WaveSeekBar_wsb_thumbWidth,
                    dpToPx(DEFAULT_THUMB_WIDTH_DP)
                ).coerceAtLeast(1f)
                thumbHeight = getDimension(
                    R.styleable.WaveSeekBar_wsb_thumbHeight,
                    dpToPx(DEFAULT_THUMB_HEIGHT_DP)
                ).coerceAtLeast(1f)
                waveDuration = getInt(
                    R.styleable.WaveSeekBar_wsb_waveDuration,
                    DEFAULT_WAVE_DURATION
                ).coerceAtLeast(100)
                isWaveEnabled = getBoolean(
                    R.styleable.WaveSeekBar_wsb_waveEnabled,
                    DEFAULT_WAVE_ENABLED
                )
                waveSpeed = getFloat(
                    R.styleable.WaveSeekBar_wsb_waveSpeed,
                    DEFAULT_WAVE_SPEED
                ).coerceAtLeast(0f)

                bgColor = getColor(R.styleable.WaveSeekBar_wsb_bgColor, DEFAULT_BG_COLOR)
                fgColor = getColor(R.styleable.WaveSeekBar_wsb_fgColor, DEFAULT_FG_COLOR)
                thumbColor = getColor(R.styleable.WaveSeekBar_wsb_thumbColor, DEFAULT_THUMB_COLOR)

                _max = max(1, getInt(R.styleable.WaveSeekBar_wsb_max, DEFAULT_MAX))
                _progress = getInt(R.styleable.WaveSeekBar_wsb_progress, DEFAULT_PROGRESS)
                    .coerceIn(0, _max)
            }
        }

        bgPaint.color = bgColor
        bgPaint.strokeWidth = strokeWidth

        fgPaint.color = fgColor
        fgPaint.strokeWidth = strokeWidth

        thumbPaint.color = thumbColor

        currentAmplitude = if (isWaveEnabled) baseAmplitude else 0f
    }

    // === PUBLIC PROPERTIES ===
    var max: Int
        get() = _max
        set(value) {
            _max = max(1, value)
            if (_progress > _max) {
                _progress = _max
                listener?.onProgressChanged(this, _progress, false)
            }
            invalidate()
        }

    var progress: Int
        get() = _progress
        set(value) {
            val newVal = value.coerceIn(0, _max)
            if (newVal != _progress) {
                _progress = newVal
                listener?.onProgressChanged(this, _progress, false)
                invalidate()
            }
        }

    // === MEASURE & LAYOUT ===
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val waveTotalHeight = (baseAmplitude * 2f) + strokeWidth
        val desiredHeight = (max(thumbHeight, waveTotalHeight) + paddingTop + paddingBottom).roundToInt()
        val desiredWidth = dpToPx(200f).roundToInt() + paddingLeft + paddingRight

        val measuredWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        centerY = paddingTop + (viewHeight - paddingTop - paddingBottom) / 2f
    }

    /**
     * Left and right track bounds accounting for both thumb width and stroke round cap
     * so nothing ever clips at the edges or sticks out past the thumb.
     */
    private fun getTrackStart(): Float {
        val edgeOffset = max(thumbWidth / 2f, strokeWidth / 2f)
        return paddingLeft + edgeOffset
    }

    private fun getTrackEnd(): Float {
        val edgeOffset = max(thumbWidth / 2f, strokeWidth / 2f)
        return (viewWidth - paddingRight - edgeOffset).coerceAtLeast(getTrackStart())
    }

    // === DRAW ===
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val trackStart = getTrackStart()
        val trackEnd = getTrackEnd()
        val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)

        val fraction = if (_max > 0) (_progress.toFloat() / _max).coerceIn(0f, 1f) else 0f
        val thumbCenterX = trackStart + (fraction * trackWidth)

        // 1. Draw Background Line (only to the right of thumbCenterX)
        if (_progress < _max && thumbCenterX < trackEnd) {
            canvas.save()
            // Clip so background round cap never sticks out behind the thumb
            canvas.clipRect(thumbCenterX, 0f, viewWidth, viewHeight)
            canvas.drawLine(thumbCenterX, centerY, trackEnd, centerY, bgPaint)
            canvas.restore()
        }

        // 2. Draw Foreground Wave (only to the left of thumbCenterX)
        if (_progress > 0 && thumbCenterX > trackStart) {
            canvas.save()
            // Clip at thumbCenterX so even a very thick strokeWidth never goes ahead of thumb
            canvas.clipRect(0f, 0f, thumbCenterX, viewHeight)
            fgPath.reset()
            createWavePath(fgPath, trackStart, thumbCenterX, centerY)
            canvas.drawPath(fgPath, fgPaint)
            canvas.restore()
        }

        // 3. Draw Thumb on top
        val halfThumbW = thumbWidth / 2f
        val halfThumbH = thumbHeight / 2f

        thumbRect.set(
            thumbCenterX - halfThumbW,
            centerY - halfThumbH,
            thumbCenterX + halfThumbW,
            centerY + halfThumbH
        )
        canvas.drawRoundRect(thumbRect, halfThumbW, halfThumbW, thumbPaint)
    }

    private fun createWavePath(path: Path, startX: Float, endX: Float, centerY: Float) {
        val safeWavelength = wavelength.coerceAtLeast(1f)
        val step = 2f
        val twoPi = (2.0 * Math.PI).toFloat()

        // Start right on the wave curve instead of pinning (startX, centerY)
        val startY = centerY + currentAmplitude * sin(-waveOffset)
        path.moveTo(startX, startY)

        var x = startX + step
        while (x < endX) {
            val phase = ((x - startX) / safeWavelength) * twoPi - waveOffset
            val y = centerY + currentAmplitude * sin(phase)
            path.lineTo(x, y)
            x += step
        }

        // Always connect the exact final point at endX so there is zero gap before the thumb
        val endPhase = ((endX - startX) / safeWavelength) * twoPi - waveOffset
        val endY = centerY + currentAmplitude * sin(endPhase)
        path.lineTo(endX, endY)
    }

    // === TOUCH ===
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isTracking = true
                listener?.onStartTrackingTouch(this)
                updateProgressFromTouch(event.x)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTracking) {
                    updateProgressFromTouch(event.x)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isTracking) {
                    updateProgressFromTouch(event.x)
                    isTracking = false
                    listener?.onStopTrackingTouch(this)
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromTouch(touchX: Float) {
        val trackStart = getTrackStart()
        val trackEnd = getTrackEnd()
        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)

        val fraction = ((touchX - trackStart) / trackWidth).coerceIn(0f, 1f)
        val newProgress = (fraction * _max).roundToInt().coerceIn(0, _max)

        if (newProgress != _progress) {
            _progress = newProgress
            listener?.onProgressChanged(this, _progress, true)
            invalidate()
        }
    }

    // === WAVES ANIMATION ===
    private fun startWaveAnimator() {
        if (!isWaveEnabled) return
        currentAmplitude = baseAmplitude

        if (waveAnimator == null) {
            val twoPi = (2.0 * Math.PI).toFloat()
            waveAnimator = ValueAnimator.ofFloat(0f, twoPi).apply {
                duration = waveDuration.toLong()
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val now = System.currentTimeMillis()
                    val deltaMs = if (lastFrameTimeMs == 0L) 16L else (now - lastFrameTimeMs).coerceIn(1L, 100L)
                    lastFrameTimeMs = now

                    // Normalized to 60fps so waveSpeed looks identical on 60Hz, 90Hz, and 120Hz screens
                    val step = waveSpeed * (deltaMs / 16.67f)
                    waveOffset = (waveOffset + step) % twoPi
                    currentAmplitude = baseAmplitude
                    invalidate()
                }
            }
        }

        if (waveAnimator?.isRunning != true) {
            lastFrameTimeMs = 0L
            waveAnimator?.start()
        }
        isWaveRunning = true
        invalidate()
    }

    private fun pauseWaveAnimator(resetAmplitude: Boolean) {
        waveAnimator?.cancel()
        waveAnimator = null
        isWaveRunning = false
        lastFrameTimeMs = 0L
        if (resetAmplitude) {
            currentAmplitude = 0f
        }
        invalidate()
    }

    fun startWaveAnimation() {
        isWaveEnabled = true
        startWaveAnimator()
    }

    fun stopWaveAnimation() {
        isWaveEnabled = false
        pauseWaveAnimator(resetAmplitude = true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isWaveEnabled) {
            startWaveAnimator()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Pause animator without setting isWaveEnabled = false so it resumes if re-attached
        pauseWaveAnimator(resetAmplitude = false)
    }

    // === UTIL ===
    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}
