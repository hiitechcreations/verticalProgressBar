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

    // ===== DEFAULTS =====
    private val defaultBackgroundLineWidth = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
    )
    private val defaultProgressCornerRadius = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
    )

    // ===== STYLEABLE ATTRS =====
    var trackColor: Int = Color.WHITE
    var backgroundLineWidth: Float = defaultBackgroundLineWidth
    var progressCornerRadius: Float = defaultProgressCornerRadius

    var purpleColor: Int = ContextCompat.getColor(context, R.color.purple_500)
    var yellowColor: Int = ContextCompat.getColor(context, R.color.yellow)
    var redColor: Int = ContextCompat.getColor(context, R.color.red)

    var purpleZonePercentage: Float = 0.5f
    var yellowZonePercentage: Float = 0.25f

    // ===== INTERNAL VALUES (safe, conflict-free) =====
    var maxValue: Int = 32
        set(value) {
            field = value.coerceAtLeast(1)
            progressValue = progressValue.coerceIn(0, field)
            invalidate()
        }

    var progressValue: Int = 0
        set(value) {
            field = value.coerceIn(0, maxValue)
            invalidate()
        }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

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

                purpleColor = getColor(
                    R.styleable.CustomVerticalProgressBar_cvp_FirstColor,
                    purpleColor
                )

                yellowColor = getColor(
                    R.styleable.CustomVerticalProgressBar_cvp_SecondColor,
                    yellowColor
                )

                redColor = getColor(
                    R.styleable.CustomVerticalProgressBar_cvp_ThirdColor,
                    redColor
                )

                purpleZonePercentage = getFloat(
                    R.styleable.CustomVerticalProgressBar_cvp_FirstZonePercentage,
                    purpleZonePercentage
                )

                yellowZonePercentage = getFloat(
                    R.styleable.CustomVerticalProgressBar_cvp_SecondZonePercentage,
                    yellowZonePercentage
                )

                // XML MAX → only initial load
                val xmlMax = getInt(R.styleable.CustomVerticalProgressBar_cvp_max, -1)

                maxValue = if (xmlMax > 0) xmlMax else 32
            }
        }

        backgroundPaint.color = trackColor
    }

    // ===================== DRAWING ==========================
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val fullWidth = width.toFloat()
        val fullHeight = height.toFloat()

        // Draw background line
        val trackLeft = (fullWidth - backgroundLineWidth) / 2f
        val trackRight = trackLeft + backgroundLineWidth

        canvas.drawRoundRect(
            RectF(trackLeft, 0f, trackRight, fullHeight),
            backgroundLineWidth / 2f,
            backgroundLineWidth / 2f,
            backgroundPaint
        )

        if (progressValue <= 0) return

        val progressRatio = progressValue.toFloat() / maxValue
        val filledHeight = fullHeight * progressRatio
        val filledTop = fullHeight - filledHeight

        val progressRect = RectF(0f, filledTop, fullWidth, fullHeight)
        val progressPath = Path().apply {
            addRoundRect(progressRect, progressCornerRadius, progressCornerRadius, Path.Direction.CW)
        }

        canvas.withClip(progressPath) {

            if (maxValue <= 16) {
                drawRect(0f, filledTop, fullWidth, fullHeight,
                    Paint().apply { color = purpleColor })
            } else {

                val purpleY = fullHeight * (1 - purpleZonePercentage)
                val yellowY = fullHeight * (1 - (purpleZonePercentage + yellowZonePercentage))

                when {
                    filledTop >= purpleY -> {
                        drawRect(
                            0f, filledTop, fullWidth, fullHeight,
                            Paint().apply { color = purpleColor }
                        )
                    }

                    filledTop in yellowY..purpleY -> {

                        drawRect(0f, purpleY, fullWidth, fullHeight,
                            Paint().apply { color = purpleColor })

                        progressPaint.shader = LinearGradient(
                            0f, purpleY,
                            0f, filledTop,
                            purpleColor, yellowColor,
                            Shader.TileMode.CLAMP
                        )

                        drawRect(0f, filledTop, fullWidth, purpleY, progressPaint)
                    }

                    else -> {

                        drawRect(0f, purpleY, fullWidth, fullHeight,
                            Paint().apply { color = purpleColor })

                        progressPaint.shader = LinearGradient(
                            0f, purpleY,
                            0f, yellowY,
                            purpleColor, yellowColor,
                            Shader.TileMode.CLAMP
                        )
                        drawRect(0f, yellowY, fullWidth, purpleY, progressPaint)

                        if (filledTop < yellowY) {
                            progressPaint.shader = LinearGradient(
                                0f, yellowY,
                                0f, filledTop,
                                yellowColor, redColor,
                                Shader.TileMode.CLAMP
                            )
                            drawRect(0f, filledTop, fullWidth, yellowY, progressPaint)
                        }
                    }
                }
            }
        }
    }
}
