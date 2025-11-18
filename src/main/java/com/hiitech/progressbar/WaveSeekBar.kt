package com.hiitech.progressbar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.toColorInt
import com.hiitech.playme.R
import kotlin.math.*

class WaveSeekBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // === DEFAULTS ===
    private val DEFAULT_BASE_AMPLITUDE = 4f
    private val DEFAULT_WAVELENGTH_DP = 70f
    private val DEFAULT_STROKE_WIDTH_DP = 6f
    private val DEFAULT_THUMB_WIDTH_DP = 10f
    private val DEFAULT_THUMB_HEIGHT_DP = 50f
    private val DEFAULT_WAVE_DURATION = 3000 // ms
    private val DEFAULT_WAVE_ENABLED = true
    private val DEFAULT_BG_COLOR = "#BDBDBD".toColorInt() // light gray
    private val DEFAULT_FG_COLOR = Color.BLACK
    private val DEFAULT_THUMB_COLOR = Color.BLACK
    private val DEFAULT_MAX = 100
    private val DEFAULT_PROGRESS = 0
    private val DEFAULT_WAVE_SPEED = 0.08f   // NEW: default speed

    // === CONFIG (populated from attrs) ===
    var baseAmplitude = DEFAULT_BASE_AMPLITUDE
    var wavelength = dpToPx(DEFAULT_WAVELENGTH_DP)
    var strokeWidth = dpToPx(DEFAULT_STROKE_WIDTH_DP)
    var thumbWidth = dpToPx(DEFAULT_THUMB_WIDTH_DP)
    var thumbHeight = dpToPx(DEFAULT_THUMB_HEIGHT_DP)
    var waveDuration = DEFAULT_WAVE_DURATION
    var isWaveEnabled = DEFAULT_WAVE_ENABLED
    private var waveSpeed = DEFAULT_WAVE_SPEED   // NEW: speed field

    // colors
    private var bgColor = DEFAULT_BG_COLOR
    private var fgColor = DEFAULT_FG_COLOR
    private var thumbColor = DEFAULT_THUMB_COLOR

    // === PROGRESS ===
    private var _max = DEFAULT_MAX
    private var _progress = DEFAULT_PROGRESS
    private var isTracking = false
    private var waveOffset = 0f
    private var isWaveRunning = false
    private var currentAmplitude = 0f

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
    fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener) {
        listener = l
    }

    // === ANIMATION ===
    private var waveAnimator: ValueAnimator? = null

    init {
        // read attrs
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.WaveSeekBar, defStyleAttr, 0) {

                baseAmplitude =
                    getFloat(R.styleable.WaveSeekBar_wsb_baseAmplitude, DEFAULT_BASE_AMPLITUDE)
                wavelength = getDimension(
                    R.styleable.WaveSeekBar_wsb_wavelength,
                    dpToPx(DEFAULT_WAVELENGTH_DP)
                )
                strokeWidth = getDimension(
                    R.styleable.WaveSeekBar_wsb_strokeWidth,
                    dpToPx(DEFAULT_STROKE_WIDTH_DP)
                )
                thumbWidth = getDimension(
                    R.styleable.WaveSeekBar_wsb_thumbWidth,
                    dpToPx(DEFAULT_THUMB_WIDTH_DP)
                )
                thumbHeight = getDimension(
                    R.styleable.WaveSeekBar_wsb_thumbHeight,
                    dpToPx(DEFAULT_THUMB_HEIGHT_DP)
                )
                waveDuration =
                    getInt(R.styleable.WaveSeekBar_wsb_waveDuration, DEFAULT_WAVE_DURATION)
                isWaveEnabled =
                    getBoolean(R.styleable.WaveSeekBar_wsb_waveEnabled, DEFAULT_WAVE_ENABLED)

                // NEW: read waveSpeed from XML
                waveSpeed = getFloat(
                    R.styleable.WaveSeekBar_wsb_waveSpeed,
                    DEFAULT_WAVE_SPEED
                )

                bgColor = getColor(R.styleable.WaveSeekBar_wsb_bgColor, DEFAULT_BG_COLOR)
                fgColor = getColor(R.styleable.WaveSeekBar_wsb_fgColor, DEFAULT_FG_COLOR)
                thumbColor = getColor(R.styleable.WaveSeekBar_wsb_thumbColor, DEFAULT_THUMB_COLOR)

                _max = getInt(R.styleable.WaveSeekBar_wsb_max, DEFAULT_MAX)
                _progress = getInt(R.styleable.WaveSeekBar_wsb_progress, DEFAULT_PROGRESS)
            }
        }

        // apply to paints
        bgPaint.color = bgColor
        bgPaint.strokeWidth = strokeWidth

        fgPaint.color = fgColor
        fgPaint.strokeWidth = strokeWidth

        thumbPaint.color = thumbColor

        // if wave enabled, start animation
        if (isWaveEnabled) startWaveAnimator()
        currentAmplitude = if (isWaveRunning) baseAmplitude else 0f
    }

    // === PUBLIC PROPERTIES ===
    var max: Int
        get() = _max
        set(value) {
            _max = max(1, value)
            if (_progress > _max) _progress = _max
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

    // === LAYOUT ===
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        centerY = viewHeight / 2f
    }

    // === DRAW ===
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val progressX = if (max == 0) 0f else (progress.toFloat() / max) * viewWidth

        // Draw background line from progressX to end
        canvas.drawLine(progressX, centerY, viewWidth, centerY, bgPaint)

        // Draw wave (reuse Path)
        fgPath.reset()
        createWavePath(fgPath, progressX, centerY)
        canvas.drawPath(fgPath, fgPaint)

        // Draw thumb — FIX: progress 0 pe thumb aadha na dikhe
        val halfThumbW = thumbWidth / 2
        val halfThumbH = thumbHeight / 2

        // NEW: clamp centerX so thumb fully visible
        val thumbCenterX = progressX.coerceIn(halfThumbW, viewWidth - halfThumbW)

        thumbRect.set(
            thumbCenterX - halfThumbW,
            centerY - halfThumbH,
            thumbCenterX + halfThumbW,
            centerY + halfThumbH
        )
        canvas.drawRoundRect(thumbRect, thumbWidth, thumbWidth, thumbPaint)
    }

    private fun createWavePath(path: Path, width: Float, centerY: Float) {
        val step = 2f
        path.moveTo(0f, centerY)
        var x = 0f
        while (x <= width) {
            val y = centerY + currentAmplitude * sin((x / wavelength) * (2 * Math.PI) + waveOffset)
            path.lineTo(x, y.toFloat())
            x += step
        }
    }

    // === TOUCH ===
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                isTracking = true
                listener?.onStartTrackingTouch(this)
                updateProgress(event.x, true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                updateProgress(event.x, true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                updateProgress(event.x, true)
                isTracking = false
                listener?.onStopTrackingTouch(this)
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgress(x: Float, fromUser: Boolean) {
        val fraction = (x / viewWidth).coerceIn(0f, 1f)
        val newProgress = (fraction * _max).toInt()
        if (newProgress != _progress) {
            _progress = newProgress
            listener?.onProgressChanged(this, _progress, fromUser)
            invalidate()
        }
    }

    // === WAVES ===
    private fun startWaveAnimator() {
        if (!isWaveEnabled) return
        if (waveAnimator == null) {
            waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                duration = waveDuration.toLong()
                repeatCount = ValueAnimator.INFINITE
                interpolator = null
                addUpdateListener {
                    // use waveSpeed from XML
                    waveOffset = (waveOffset + waveSpeed) % (2 * Math.PI).toFloat()
                    currentAmplitude = baseAmplitude
                    invalidate()
                }
            }
        }
        if (!(waveAnimator?.isRunning ?: false)) waveAnimator?.start()
        isWaveRunning = true
    }

    fun startWaveAnimation() {
        isWaveEnabled = true
        startWaveAnimator()
    }

    fun stopWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
        isWaveRunning = false
        currentAmplitude = 0f
        isWaveEnabled = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWaveAnimation()
    }

    // === UTIL ===
    private fun dpToPx(dp: Float): Float =
        dp * (resources.displayMetrics.densityDpi.toFloat() / 160f)
}
