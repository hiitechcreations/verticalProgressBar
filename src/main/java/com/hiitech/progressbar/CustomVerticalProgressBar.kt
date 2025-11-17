package com.hiitech.progressbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withClip

class CustomVerticalProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // --- Default Values ---
    private val defaultBackgroundLineWidth = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
    )
    private val defaultProgressCornerRadius = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
    )
    private val defaultZone1Color = ContextCompat.getColor(context, R.color.purple_500)
    private val defaultZone2Color = ContextCompat.getColor(context, R.color.yellow)
    private val defaultZone3Color = ContextCompat.getColor(context, R.color.red)
    private val defaultZone1Threshold = 50
    private val defaultZone2Threshold = 75

    // --- Public Properties ---
    var trackColor: Int = Color.WHITE
        set(value) {
            field = value
            backgroundPaint.color = field
            invalidate()
        }

    var backgroundLineWidth: Float = defaultBackgroundLineWidth
        set(value) {
            field = value
            invalidate()
        }

    var progressCornerRadius: Float = defaultProgressCornerRadius
        set(value) {
            field = value
            invalidate()
        }

    var zone1Color: Int = defaultZone1Color
        set(value) {
            field = value
            invalidate()
        }
    var zone2Color: Int = defaultZone2Color
        set(value) {
            field = value
            invalidate()
        }
    var zone3Color: Int = defaultZone3Color
        set(value) {
            field = value
            invalidate()
        }

    /**
     * The progress value at which the color starts grading to zone2Color.
     * (0 to zone1Threshold will be zone1Color)
     */
    var zone1Threshold: Int = defaultZone1Threshold
        set(value) {
            field = value.coerceAtLeast(0)
            // Ensure zone2 is always >= zone1
            if (zone2Threshold < field) {
                zone2Threshold = field
            }
            invalidate()
        }

    /**
     * The progress value at which the color starts grading to zone3Color.
     * (zone1Threshold to zone2Threshold will be zone1 -> zone2 gradient)
     */
    var zone2Threshold: Int = defaultZone2Threshold
        set(value) {
            // Enforce order: zone2 must be >= zone1
            field = value.coerceAtLeast(zone1Threshold)
            invalidate()
        }

    var maxValue: Int = 100
        set(value) {
            // Ensure max value is at least 1 to avoid division by zero
            field = value.coerceAtLeast(1)
            // Ensure progress value is still valid within the new max
            progressValue = progressValue.coerceIn(0, field)
            invalidate()
        }

    var progressValue: Int = 0
        set(value) {
            field = value.coerceIn(0, maxValue)
            invalidate()
        }

    // --- Private Paint & Path Objects (for Performance) ---
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = trackColor
    }

    // Re-usable objects to avoid allocation in onDraw
    private val progressRect = RectF()
    private val progressPath = Path()
    private val backgroundRect = RectF()

    // Shaders for gradients
    private var zone1to2Shader: LinearGradient? = null
    private var zone2to3Shader: LinearGradient? = null

    // Cached values to check if shaders need recalculation
    private var cacheZone1Y = -1f
    private var cacheZone2Y = -1f
    private var cacheFilledTop = -1f

    init {
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.CustomVerticalProgressBar) {

                backgroundLineWidth = getDimension(
                    R.styleable.CustomVerticalProgressBar_cvp_backgroundLineWidth,
                    defaultBackgroundLineWidth
                )

                trackColor = getColor(
                    R.styleable.CustomVerticalProgressBar_cvp_backgroundColor,
                    Color.WHITE
                )

                progressCornerRadius = getDimension(
                    R.styleable.CustomVerticalProgressBar_cvp_progressCornerRadius,
                    defaultProgressCornerRadius
                )

                // Use new zone color attributes
                zone1Color = getColor(
                    R.styleable.CustomVerticalProgressBar_cvp_zone1Color,
                    defaultZone1Color
                )

                zone2Color = getColor(
                    R.styleable.CustomVerticalProgressBar_cvp_zone2Color,
                    defaultZone2Color
                )

                zone3Color = getColor(
                    R.styleable.CustomVerticalProgressBar_cvp_zone3Color,
                    defaultZone3Color
                )

                // Use new threshold attributes (integers, not floats)
                zone1Threshold = getInt(
                    R.styleable.CustomVerticalProgressBar_cvp_zone1Threshold,
                    defaultZone1Threshold
                )

                zone2Threshold = getInt(
                    R.styleable.CustomVerticalProgressBar_cvp_zone2Threshold,
                    defaultZone2Threshold
                )

                // Set initial max and progress from XML if available
                maxValue = getInt(
                    R.styleable.CustomVerticalProgressBar_cvp_maxValue,
                    100
                )
                progressValue = getInt(
                    R.styleable.CustomVerticalProgressBar_cvp_progressValue,
                    0
                )
            }
        }

        backgroundPaint.color = trackColor
    }

    // ================= DRAW ==================
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val fullWidth = width.toFloat()
        val fullHeight = height.toFloat()

        // 1. Draw Background Track
        val trackLeft = (fullWidth - backgroundLineWidth) / 2f
        val trackRight = trackLeft + backgroundLineWidth
        backgroundRect.set(trackLeft, 0f, trackRight, fullHeight)

        canvas.drawRoundRect(
            backgroundRect,
            backgroundLineWidth / 2f,
            backgroundLineWidth / 2f,
            backgroundPaint
        )

        // 2. Stop if no progress
        if (progressValue <= 0) return

        // 3. Calculate Progress Positions
        // Calculate Y-coordinate for the top of the filled progress
        val filledTop = fullHeight * (1f - (progressValue.toFloat() / maxValue))

        // Calculate Y-coordinates for the zone thresholds
        // These are clamped between 0 and fullHeight
        val zone1Y = (fullHeight * (1f - (zone1Threshold.toFloat() / maxValue))).coerceIn(0f, fullHeight)
        val zone2Y = (fullHeight * (1f - (zone2Threshold.toFloat() / maxValue))).coerceIn(0f, fullHeight)

        // 4. Set up Clipping Path for rounded corners
        progressRect.set(0f, filledTop, fullWidth, fullHeight)
        progressPath.reset()
        progressPath.addRoundRect(progressRect, progressCornerRadius, progressCornerRadius, Path.Direction.CW)

        canvas.withClip(progressPath) {
            // Reset shader
            progressPaint.shader = null

            // 5. Draw Zones (from bottom up)

            // CASE 1: We are in Zone 1 (progress is below zone1Threshold)
            if (filledTop >= zone1Y) {
                progressPaint.color = zone1Color
                drawRect(0f, filledTop, fullWidth, fullHeight, progressPaint)
            }
            // CASE 2: We have crossed into Zone 2
            else if (filledTop >= zone2Y) {
                // Draw solid Zone 1
                progressPaint.color = zone1Color
                drawRect(0f, zone1Y, fullWidth, fullHeight, progressPaint)

                // Draw gradient Zone 2 (Zone 1 -> Zone 2)
                updateShader1(zone1Y, filledTop)
                progressPaint.shader = zone1to2Shader
                drawRect(0f, filledTop, fullWidth, zone1Y, progressPaint)
            }
            // CASE 3: We have crossed into Zone 3
            else {
                // Draw solid Zone 1
                progressPaint.color = zone1Color
                drawRect(0f, zone1Y, fullWidth, fullHeight, progressPaint)

                // Draw solid Zone 2 (Zone 1 -> Zone 2 gradient)
                updateShader1(zone1Y, zone2Y)
                progressPaint.shader = zone1to2Shader
                drawRect(0f, zone2Y, fullWidth, zone1Y, progressPaint)

                // Draw gradient Zone 3 (Zone 2 -> Zone 3)
                updateShader2(zone2Y, filledTop)
                progressPaint.shader = zone2to3Shader
                drawRect(0f, filledTop, fullWidth, zone2Y, progressPaint)
            }
        }
    }

    /** Caches the shader to avoid creating a new one if params are the same */
    private fun updateShader1(yStart: Float, yEnd: Float) {
        if (yStart != cacheZone1Y || yEnd != cacheFilledTop) {
            cacheZone1Y = yStart
            cacheFilledTop = yEnd
            zone1to2Shader = LinearGradient(
                0f, yStart, 0f, yEnd,
                zone1Color, zone2Color,
                Shader.TileMode.CLAMP
            )
        }
    }

    /** Caches the shader to avoid creating a new one if params are the same */
    private fun updateShader2(yStart: Float, yEnd: Float) {
        if (yStart != cacheZone2Y || yEnd != cacheFilledTop) {
            cacheZone2Y = yStart
            cacheFilledTop = yEnd
            zone2to3Shader = LinearGradient(
                0f, yStart, 0f, yEnd,
                zone2Color, zone3Color,
                Shader.TileMode.CLAMP
            )
        }
    }
}
