package org.renpy.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import java.util.UUID

class WallpaperCropActivity : GameWindowActivity() {

    private lateinit var imageView: ImageView
    private lateinit var overlayView: View

    private var sourceBitmap: Bitmap? = null
    private val imageMatrix = Matrix()

    // Touch tracking
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var scaleFactor = 1f

    // Screen dimensions for the crop viewport
    private var screenWidth = 0
    private var screenHeight = 0

    // Viewport rect (the area of screen the wallpaper will cover)
    private var viewportRect = RectF()

    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_crop)
        setTitle(R.string.wallpaper_crop_title)

        SoundEffects.initialize(this)

        imageView = findViewById(R.id.cropImageView)
        overlayView = findViewById(R.id.cropOverlay)

        // Get real screen dimensions
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        scaleDetector = ScaleGestureDetector(this, ScaleListener())

        val uriStr = intent.getStringExtra("image_uri")
        if (uriStr == null) {
            finish()
            return
        }

        loadImage(Uri.parse(uriStr))

        findViewById<View>(R.id.btnCropCancel).setOnClickListener {
            SoundEffects.playClick(this)
            finish()
        }
        findViewById<View>(R.id.btnCropApply).setOnClickListener {
            SoundEffects.playClick(this)
            applyCrop()
        }

        imageView.setOnTouchListener { _, event -> handleTouch(event) }
    }

    private fun loadImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            sourceBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (sourceBitmap == null) {
                InAppNotifier.show(this, getString(R.string.viewer_error_image_decode))
                finish()
                return
            }

            // Wait for layout to be ready
            imageView.post {
                setupInitialTransform()
                drawOverlay()
            }
        } catch (e: Exception) {
            InAppNotifier.show(this, e.message ?: "", true)
            finish()
        }
    }

    private fun setupInitialTransform() {
        val bmp = sourceBitmap ?: return
        val viewW = imageView.width.toFloat()
        val viewH = imageView.height.toFloat()

        // Calculate viewport rect, centered, matching device screen aspect ratio
        val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
        val viewAspect = viewW / viewH

        val vpW: Float
        val vpH: Float
        if (screenAspect > viewAspect) {
            vpW = viewW * 0.9f
            vpH = vpW / screenAspect
        } else {
            vpH = viewH * 0.8f
            vpW = vpH * screenAspect
        }
        val vpLeft = (viewW - vpW) / 2f
        val vpTop = (viewH - vpH) / 2f
        viewportRect = RectF(vpLeft, vpTop, vpLeft + vpW, vpTop + vpH)

        // Scale image to fill the viewport initially
        val scaleX = vpW / bmp.width
        val scaleY = vpH / bmp.height
        scaleFactor = Math.max(scaleX, scaleY)

        imageMatrix.reset()
        imageMatrix.postScale(scaleFactor, scaleFactor)

        // Center image on viewport
        val scaledW = bmp.width * scaleFactor
        val scaledH = bmp.height * scaleFactor
        val tx = viewportRect.centerX() - scaledW / 2f
        val ty = viewportRect.centerY() - scaledH / 2f
        imageMatrix.postTranslate(tx, ty)

        imageView.imageMatrix = imageMatrix
        imageView.setImageBitmap(bmp)
    }

    private fun drawOverlay() {
        overlayView.post {
            val w = overlayView.width
            val h = overlayView.height
            if (w == 0 || h == 0) return@post

            val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(overlay)

            // Draw semi-transparent black
            val paint = Paint()
            paint.color = Color.parseColor("#88000000")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

            // Cut out the viewport rectangle (transparent hole)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawRect(viewportRect, paint)

            // Draw viewport border
            paint.xfermode = null
            paint.color = Color.parseColor("#CC7295")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(viewportRect, paint)

            // Draw crosshair inside the viewport
            val guidePaint = Paint()
            guidePaint.color = Color.parseColor("#80FFFFFF")
            guidePaint.style = Paint.Style.STROKE
            guidePaint.strokeWidth = 1f

            // Vertical center line
            canvas.drawLine(
                viewportRect.centerX(), viewportRect.top,
                viewportRect.centerX(), viewportRect.bottom,
                guidePaint
            )
            // Horizontal center line
            canvas.drawLine(
                viewportRect.left, viewportRect.centerY(),
                viewportRect.right, viewportRect.centerY(),
                guidePaint
            )

            overlayView.background = BitmapDrawable(resources, overlay)
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex >= 0) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        imageMatrix.postTranslate(dx, dy)
                        clampImageToViewport()
                        imageView.imageMatrix = imageMatrix
                        lastTouchX = x
                        lastTouchY = y
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    if (newPointerIndex < event.pointerCount) {
                        lastTouchX = event.getX(newPointerIndex)
                        lastTouchY = event.getY(newPointerIndex)
                        activePointerId = event.getPointerId(newPointerIndex)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    // Clamp the image so it always covers the viewport completely
    private fun clampImageToViewport() {
        val bmp = sourceBitmap ?: return

        // Get current image bounds by mapping the bitmap corners through the matrix
        val pts = floatArrayOf(
            0f, 0f,
            bmp.width.toFloat(), 0f,
            bmp.width.toFloat(), bmp.height.toFloat(),
            0f, bmp.height.toFloat()
        )
        imageMatrix.mapPoints(pts)

        val imgLeft = Math.min(Math.min(pts[0], pts[2]), Math.min(pts[4], pts[6]))
        val imgTop = Math.min(Math.min(pts[1], pts[3]), Math.min(pts[5], pts[7]))
        val imgRight = Math.max(Math.max(pts[0], pts[2]), Math.max(pts[4], pts[6]))
        val imgBottom = Math.max(Math.max(pts[1], pts[3]), Math.max(pts[5], pts[7]))

        var dx = 0f
        var dy = 0f

        if (imgLeft > viewportRect.left) dx = viewportRect.left - imgLeft
        if (imgTop > viewportRect.top) dy = viewportRect.top - imgTop
        if (imgRight < viewportRect.right) dx = viewportRect.right - imgRight
        if (imgBottom < viewportRect.bottom) dy = viewportRect.bottom - imgBottom

        if (dx != 0f || dy != 0f) {
            imageMatrix.postTranslate(dx, dy)
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            // Scale around the viewport center to prevent horizontal/vertical drift
            imageMatrix.postScale(factor, factor, viewportRect.centerX(), viewportRect.centerY())
            clampImageToViewport()
            imageView.imageMatrix = imageMatrix
            return true
        }
    }

    private fun applyCrop() {
        val bmp = sourceBitmap ?: return

        try {
            // Get the inverse of the image matrix to map viewport coords to bitmap coords
            val inverse = Matrix()
            imageMatrix.invert(inverse)

            // Map all 4 corners of the viewport to source bitmap coordinates
            val corners = floatArrayOf(
                viewportRect.left, viewportRect.top,     // top-left
                viewportRect.right, viewportRect.top,    // top-right
                viewportRect.right, viewportRect.bottom, // bottom-right
                viewportRect.left, viewportRect.bottom   // bottom-left
            )
            inverse.mapPoints(corners)

            // Derive the bounding rect from the mapped corners
            val srcLeft = Math.min(Math.min(corners[0], corners[2]), Math.min(corners[4], corners[6]))
                .coerceIn(0f, bmp.width.toFloat())
            val srcTop = Math.min(Math.min(corners[1], corners[3]), Math.min(corners[5], corners[7]))
                .coerceIn(0f, bmp.height.toFloat())
            val srcRight = Math.max(Math.max(corners[0], corners[2]), Math.max(corners[4], corners[6]))
                .coerceIn(0f, bmp.width.toFloat())
            val srcBottom = Math.max(Math.max(corners[1], corners[3]), Math.max(corners[5], corners[7]))
                .coerceIn(0f, bmp.height.toFloat())

            // Enforce viewport aspect ratio (sadly) to prevent stretching
            val vpAspect = viewportRect.width() / viewportRect.height()
            var cropW = srcRight - srcLeft
            var cropH = srcBottom - srcTop
            val cropAspect = cropW / cropH

            var finalLeft = srcLeft
            var finalTop = srcTop

            if (cropAspect > vpAspect) {
                val newW = cropH * vpAspect
                finalLeft += (cropW - newW) / 2f
                cropW = newW
            } else {
                val newH = cropW / vpAspect
                finalTop += (cropH - newH) / 2f
                cropH = newH
            }

            val iLeft = finalLeft.toInt().coerceIn(0, bmp.width - 1)
            val iTop = finalTop.toInt().coerceIn(0, bmp.height - 1)
            val iW = cropW.toInt().coerceIn(1, bmp.width - iLeft)
            val iH = cropH.toInt().coerceIn(1, bmp.height - iTop)

            // Crop from source bitmap
            val cropped = Bitmap.createBitmap(bmp, iLeft, iTop, iW, iH)

            // Scale to screen dimensions
            val scaled = Bitmap.createScaledBitmap(cropped, screenWidth, screenHeight, true)
            if (cropped != scaled) cropped.recycle()

            // Save
            val name = "wallpaper_${System.currentTimeMillis()}.png"
            WallpaperManager.saveWallpaper(this, scaled, name)
            WallpaperManager.setActive(this, name)
            scaled.recycle()

            InAppNotifier.show(this, getString(R.string.wallpaper_applied))
            finish()
        } catch (e: Exception) {
            InAppNotifier.show(this, "Error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sourceBitmap = null
    }
}
