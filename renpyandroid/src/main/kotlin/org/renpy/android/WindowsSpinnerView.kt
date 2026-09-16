package org.renpy.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class WindowsSpinnerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var currentProgress = 0f

    init {
        if (visibility == VISIBLE) {
            startAnimation()
        }
    }

    fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                currentProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == VISIBLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (this.visibility == VISIBLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val availableSize = min(w - paddingLeft - paddingRight, h - paddingTop - paddingBottom)
        val ringRadius = (availableSize / 2f) * 0.76f
        val baseDotRadius = availableSize * 0.065f

        val numDots = 5
        val deltaT = 0.042f
        val amp = 38.0

        for (i in 0 until numDots) {
            var t = currentProgress - i * deltaT
            if (t < 0f) t += 1f
            if (t >= 1f) t -= 1f

            val angleDeg = 360.0 * t - amp * cos(2.0 * Math.PI * t)
            val rad = Math.toRadians(angleDeg)

            val x = cx + ringRadius * cos(rad).toFloat()
            val y = cy + ringRadius * sin(rad).toFloat()

            val dotRadius = baseDotRadius * (1f - i * 0.08f)
            val alpha = (255 * (1f - i * 0.14f)).toInt().coerceIn(35, 255)
            dotPaint.alpha = alpha

            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }
    }
}
