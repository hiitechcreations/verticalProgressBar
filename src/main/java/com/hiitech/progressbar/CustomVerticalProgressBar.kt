package com.hiitech.progressbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.withClip
import androidx.core.content.withStyledAttributes

class CustomVerticalProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // Default dimensions (in pixels)
    private val defaultBackgroundLineWidth = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
    )
    private val defaultProgressCornerRadius = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
    )

    // Attributes (customizable via XML)
    var trackColor: Int = Color.WHITE  // Renamed from backgroundColor
    var backgroundLineWidth: Float = defaultBackgroundLineWidth
    var progressCornerRadius: Float = defaultProgressCornerRadius

    var purpleColor: Int = ContextCompat.getColor(context, R.color.purple_500)
    var yellowColor: Int = ContextCompat.getColor(context, R.color.yellow)
    var redColor: Int = ContextCompat.getColor(context, R.color.red)

    // Zone percentages (for max progress):
    // Default: purple zone = 50% (0-16), yellow zone = 25% (16-24)
    // red zone automatically = remaining 25% (24-32) if max is 32
    var purpleZonePercentage: Float = 0.5f
    var yellowZonePercentage: Float = 0.25f

    // Maximum progress is fixed at 32 by default, but can be set via XML.
    var max: Int = 32

    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, max)
            invalidate()
        }

    // Paint objects
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = trackColor
    }

    init {
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.CustomVerticalProgressBar) {
                backgroundLineWidth = getDimension(
                    R.styleable.CustomVerticalProgressBar_cvp_backgroundLineWidth,
                    defaultBackgroundLineWidth
                )
                trackColor =
                    getColor(R.styleable.CustomVerticalProgressBar_cvp_backgroundColor, Color.WHITE)
                progressCornerRadius = getDimension(
                    R.styleable.CustomVerticalProgressBar_cvp_progressCornerRadius,
                    defaultProgressCornerRadius
                )
                purpleColor =
                    getColor(R.styleable.CustomVerticalProgressBar_cvp_FirstColor, purpleColor)
                yellowColor =
                    getColor(R.styleable.CustomVerticalProgressBar_cvp_SecondColor, yellowColor)
                redColor = getColor(R.styleable.CustomVerticalProgressBar_cvp_ThirdColor, redColor)
                purpleZonePercentage = getFloat(
                    R.styleable.CustomVerticalProgressBar_cvp_FirstZonePercentage,
                    purpleZonePercentage
                )
                yellowZonePercentage = getFloat(
                    R.styleable.CustomVerticalProgressBar_cvp_SecondZonePercentage,
                    yellowZonePercentage
                )
                max = getInt(R.styleable.CustomVerticalProgressBar_cvp_max, 32)
            }
            backgroundPaint.color = trackColor
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fullWidth = width.toFloat()
        val fullHeight = height.toFloat()

        // ===== Draw the Background Track (White Vertical Line) =====
        val trackLeft = (fullWidth - backgroundLineWidth) / 2f
        val trackRight = trackLeft + backgroundLineWidth
        val backgroundRect = RectF(trackLeft, 0f, trackRight, fullHeight)
        canvas.drawRoundRect(backgroundRect, backgroundLineWidth / 2f, backgroundLineWidth / 2f, backgroundPaint)

        // ===== Draw the Progress Fill Over the Track =====
        if (progress > 0) {
            val progressRatio = progress.toFloat() / max
            val filledHeight = fullHeight * progressRatio
            val filledTop = fullHeight - filledHeight

            // Create a progress rectangle covering full view width with rounded corners.
            val progressRect = RectF(0f, filledTop, fullWidth, fullHeight)
            val progressPath = Path().apply {
                addRoundRect(progressRect, progressCornerRadius, progressCornerRadius, Path.Direction.CW)
            }
            canvas.withClip(progressPath) {
                if (max <= 16) {
                    // For brightness progress (max == 16), draw a pure purple fill.
                    drawRect(
                        0f,
                        filledTop,
                        fullWidth,
                        fullHeight,
                        Paint().apply { color = purpleColor })
                } else {
                    // For other cases (e.g. volume, max > 16), use segmented color fill:
                    // Calculate vertical boundaries based on zone percentages.
                    val purpleBoundaryY =
                        fullHeight * (1 - purpleZonePercentage)  // bottom zone for purple
                    val yellowBoundaryY =
                        fullHeight * (1 - (purpleZonePercentage + yellowZonePercentage))
                    when {
                        // If progress fill is entirely in the purple zone.
                        filledTop >= purpleBoundaryY -> {
                            drawRect(
                                0f,
                                filledTop,
                                fullWidth,
                                fullHeight,
                                Paint().apply { color = purpleColor })
                        }
                        // If progress extends into the yellow zone (but not into red zone)
                        filledTop in yellowBoundaryY..purpleBoundaryY -> {
                            drawRect(
                                0f,
                                purpleBoundaryY,
                                fullWidth,
                                fullHeight,
                                Paint().apply { color = purpleColor })
                            val shader = LinearGradient(
                                0f, purpleBoundaryY,
                                0f, filledTop,
                                purpleColor,
                                yellowColor,
                                Shader.TileMode.CLAMP
                            )
                            progressPaint.shader = shader
                            drawRect(0f, filledTop, fullWidth, purpleBoundaryY, progressPaint)
                        }
                        // If progress extends into the red zone.
                        else -> {
                            drawRect(
                                0f,
                                purpleBoundaryY,
                                fullWidth,
                                fullHeight,
                                Paint().apply { color = purpleColor })
                            val shaderPurpleYellow = LinearGradient(
                                0f, purpleBoundaryY,
                                0f, yellowBoundaryY,
                                purpleColor,
                                yellowColor,
                                Shader.TileMode.CLAMP
                            )
                            progressPaint.shader = shaderPurpleYellow
                            drawRect(0f, yellowBoundaryY, fullWidth, purpleBoundaryY, progressPaint)
                            if (filledTop < yellowBoundaryY) {
                                val shaderYellowRed = LinearGradient(
                                    0f, yellowBoundaryY,
                                    0f, filledTop,
                                    yellowColor,
                                    redColor,
                                    Shader.TileMode.CLAMP
                                )
                                progressPaint.shader = shaderYellowRed
                                drawRect(0f, filledTop, fullWidth, yellowBoundaryY, progressPaint)
                            }
                        }
                    }
                }
            }
        }
    }
}