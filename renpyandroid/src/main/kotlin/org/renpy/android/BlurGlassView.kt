package org.renpy.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import kotlin.math.max

class BlurGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var targetRootRef: WeakReference<View>? = null
    private var blurredBitmap: Bitmap? = null

    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(45, 255, 255, 255)
    }

    var cornerRadiusDp: Float = 5f
        set(value) {
            field = value
            updateCornerRadiusPx()
            invalidate()
        }

    private var cornerRadiusPx: Float = 0f
    private val clipPath = Path()
    private val borderRect = RectF()
    private val dstRect = Rect()

    var overlayColor: Int = Color.argb(90, 36, 36, 42)
        set(value) {
            field = value
            invalidate()
        }

    var blurRadius: Int = 10
        set(value) {
            field = value
            refreshBlur()
        }

    var downsampleFactor: Int = 4

    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    init {
        isClickable = false
        isFocusable = false
        updateCornerRadiusPx()
        borderPaint.strokeWidth = 1f * resources.displayMetrics.density
    }

    private fun updateCornerRadiusPx() {
        cornerRadiusPx = cornerRadiusDp * resources.displayMetrics.density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)

        val w = if (wMode == MeasureSpec.EXACTLY) wSize else 0
        val h = if (hMode == MeasureSpec.EXACTLY) hSize else 0
        setMeasuredDimension(w, h)
    }

    fun setupWith(rootView: View) {
        targetRootRef = WeakReference(rootView)
        removePreDrawListener()

        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (width > 0 && height > 0) {
                    captureAndBlur()
                    removePreDrawListener()
                }
                return true
            }
        }
        preDrawListener = listener
        rootView.viewTreeObserver.addOnPreDrawListener(listener)
        post {
            if (blurredBitmap == null) {
                captureAndBlur()
            }
        }
    }

    private fun removePreDrawListener() {
        preDrawListener?.let { listener ->
            targetRootRef?.get()?.viewTreeObserver?.let { observer ->
                if (observer.isAlive) {
                    observer.removeOnPreDrawListener(listener)
                }
            }
        }
        preDrawListener = null
    }

    fun refreshBlur() {
        post {
            captureAndBlur()
        }
    }

    private fun captureAndBlur() {
        val root = targetRootRef?.get() ?: return
        if (width <= 0 || height <= 0 || root.width <= 0 || root.height <= 0) return

        val rootLoc = IntArray(2)
        val thisLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)
        getLocationInWindow(thisLoc)

        val relX = thisLoc[0] - rootLoc[0]
        val relY = thisLoc[1] - rootLoc[1]

        val sampleW = max(1, width / downsampleFactor)
        val sampleH = max(1, height / downsampleFactor)

        val snapshot = try {
            Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return
        }

        val canvas = Canvas(snapshot)
        canvas.scale(1f / downsampleFactor, 1f / downsampleFactor)
        canvas.translate(-relX.toFloat(), -relY.toFloat())

        var drewContent = false

        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is TextureView && child.contentDescription == "video_wallpaper_overlay") {
                    val videoBmp = child.bitmap
                    if (videoBmp != null) {
                        val childLoc = IntArray(2)
                        child.getLocationInWindow(childLoc)
                        val vx = (childLoc[0] - rootLoc[0]).toFloat()
                        val vy = (childLoc[1] - rootLoc[1]).toFloat()
                        canvas.drawBitmap(videoBmp, vx, vy, null)
                        videoBmp.recycle()
                        drewContent = true
                    }
                }
            }
        }

        if (!drewContent) {
            val bg = root.background
            if (bg != null) {
                val origBounds = bg.copyBounds()
                bg.setBounds(0, 0, root.width, root.height)
                bg.draw(canvas)
                bg.bounds = origBounds
                drewContent = true
            } else {
                ContextCompat.getDrawable(context, R.drawable.bg_desktop_mas)?.let { defaultBg ->
                    defaultBg.setBounds(0, 0, root.width, root.height)
                    defaultBg.draw(canvas)
                    drewContent = true
                }
            }
        }

        canvas.drawColor(0x99000000.toInt())

        val blurred = FastBlur.stackBlur(snapshot, blurRadius)
        if (blurred !== snapshot && !snapshot.isRecycled) {
            snapshot.recycle()
        }

        val old = blurredBitmap
        blurredBitmap = blurred
        if (old != null && !old.isRecycled && old !== blurred) {
            old.recycle()
        }

        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.reset()
        clipPath.addRoundRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            cornerRadiusPx, cornerRadiusPx,
            Path.Direction.CW
        )
        dstRect.set(0, 0, w, h)
        val strokeHalf = borderPaint.strokeWidth / 2f
        borderRect.set(strokeHalf, strokeHalf, w - strokeHalf, h - strokeHalf)
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
            refreshBlur()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.clipPath(clipPath)

        val bmp = blurredBitmap
        if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, null, dstRect, filterPaint)
        }

        canvas.drawColor(overlayColor)

        canvas.drawRoundRect(borderRect, cornerRadiusPx, cornerRadiusPx, borderPaint)

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removePreDrawListener()
        blurredBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        blurredBitmap = null
    }
}
