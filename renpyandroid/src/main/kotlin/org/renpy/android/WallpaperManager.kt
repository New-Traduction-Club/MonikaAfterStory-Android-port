package org.renpy.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import pl.droidsonroids.gif.GifDrawable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Manages user-wallpapers stored in external storage.
 */
object WallpaperManager {

    const val MIN_SLIDESHOW_SELECTION = 2
    const val MAX_SLIDESHOW_SELECTION = 5

    private const val PREFS_KEY = "active_wallpaper"
    private const val DEFAULT_ID = "default"
    private const val WALLPAPERS_DIR = "wallpapers"
    private const val KEY_SLIDESHOW_ENABLED = "wallpaper_slideshow_enabled"
    private const val KEY_SLIDESHOW_INTERVAL_MINUTES = "wallpaper_slideshow_interval_minutes"
    private const val KEY_SLIDESHOW_CHANGE_ON_APP_TOGGLE = "wallpaper_slideshow_change_on_app_toggle"
    private const val KEY_SLIDESHOW_SELECTION = "wallpaper_slideshow_selection"
    private const val KEY_SLIDESHOW_LAST_CHANGE = "wallpaper_slideshow_last_change"
    private const val KEY_CROP_PREFIX = "wallpaper_crop_"
    private const val VIDEO_WALLPAPER_MARKER = "video_wallpaper_overlay"

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif")
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "webm", "mkv", "mov", "avi", "3gp", "3gpp", "mpeg", "mpg", "ts", "m2ts", "mts"
    )
    private val SUPPORTED_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS

    data class WallpaperCrop(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        fun normalized(): WallpaperCrop {
            val nLeft = left.coerceIn(0f, 1f)
            val nTop = top.coerceIn(0f, 1f)
            val nRight = right.coerceIn(0f, 1f)
            val nBottom = bottom.coerceIn(0f, 1f)
            return WallpaperCrop(
                left = min(nLeft, nRight),
                top = min(nTop, nBottom),
                right = max(nLeft, nRight),
                bottom = max(nTop, nBottom)
            )
        }

        fun isFullFrame(): Boolean {
            val normalized = normalized()
            return normalized.left <= 0.001f &&
                normalized.top <= 0.001f &&
                normalized.right >= 0.999f &&
                normalized.bottom >= 0.999f
        }
    }

    data class SlideshowConfig(
        val enabled: Boolean,
        val intervalMinutes: Int?,
        val changeOnAppToggle: Boolean,
        val selectedIds: List<String>
    )

    private fun prefs(context: Context) = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private fun getWallpapersDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), WALLPAPERS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cropPrefsKey(id: String): String = KEY_CROP_PREFIX + id

    fun getActiveId(context: Context): String {
        return prefs(context).getString(PREFS_KEY, DEFAULT_ID) ?: DEFAULT_ID
    }

    fun setActive(context: Context, id: String) {
        prefs(context)
            .edit()
            .putString(PREFS_KEY, id)
            .putLong(KEY_SLIDESHOW_LAST_CHANGE, System.currentTimeMillis())
            .apply()
    }

    fun getWallpaperList(context: Context): List<String> {
        val list = mutableListOf(DEFAULT_ID)
        val dir = getWallpapersDir(context)
        dir.listFiles()?.filter {
            it.isFile && SUPPORTED_EXTENSIONS.contains(it.extension.lowercase())
        }?.sortedBy { it.lastModified() }?.forEach {
            list.add(it.name)
        }
        return list
    }

    fun isAnimatedWallpaper(context: Context, name: String): Boolean {
        if (name == DEFAULT_ID) return false
        val file = File(getWallpapersDir(context), name)
        return file.exists() && isAnimatedFile(file)
    }

    fun isVideoWallpaper(context: Context, name: String): Boolean {
        if (name == DEFAULT_ID) return false
        val file = File(getWallpapersDir(context), name)
        return file.exists() && isVideoFile(file)
    }

    @Throws(IOException::class)
    fun saveWallpaperFromUri(context: Context, sourceUri: Uri, name: String): String {
        val dir = getWallpapersDir(context)
        val file = File(dir, name)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to read selected wallpaper source")
        return name
    }

    fun saveWallpaper(context: Context, bitmap: Bitmap, name: String): String {
        val dir = getWallpapersDir(context)
        val file = File(dir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        return name
    }

    fun setWallpaperCrop(context: Context, id: String, crop: WallpaperCrop?) {
        if (id == DEFAULT_ID) return
        val editor = prefs(context).edit()
        if (crop == null) {
            editor.remove(cropPrefsKey(id)).apply()
            return
        }

        val normalized = crop.normalized()
        if (normalized.isFullFrame()) {
            editor.remove(cropPrefsKey(id)).apply()
            return
        }

        val raw = "${normalized.left},${normalized.top},${normalized.right},${normalized.bottom}"
        editor.putString(cropPrefsKey(id), raw).apply()
    }

    fun clearWallpaperCrop(context: Context, id: String) {
        if (id == DEFAULT_ID) return
        prefs(context).edit().remove(cropPrefsKey(id)).apply()
    }

    fun getWallpaperCrop(context: Context, id: String): WallpaperCrop? {
        if (id == DEFAULT_ID) return null
        val raw = prefs(context).getString(cropPrefsKey(id), null) ?: return null
        val parts = raw.split(",")
        if (parts.size != 4) return null
        val left = parts[0].toFloatOrNull() ?: return null
        val top = parts[1].toFloatOrNull() ?: return null
        val right = parts[2].toFloatOrNull() ?: return null
        val bottom = parts[3].toFloatOrNull() ?: return null
        return WallpaperCrop(left, top, right, bottom).normalized()
    }

    fun deleteWallpaper(context: Context, name: String): Boolean {
        if (name == DEFAULT_ID) return false
        val file = File(getWallpapersDir(context), name)
        val deleted = file.delete()
        if (deleted) {
            clearWallpaperCrop(context, name)
            if (getActiveId(context) == name) {
                setActive(context, DEFAULT_ID)
            }
        }
        return deleted
    }

    fun getWallpaperFile(context: Context, name: String): File {
        return File(getWallpapersDir(context), name)
    }

    fun loadThumbnail(context: Context, name: String, targetWidth: Int): Bitmap? {
        val file = File(getWallpapersDir(context), name)
        if (!file.exists()) return null

        if (isVideoFile(file)) {
            val frame = extractVideoFrame(file) ?: return null
            val crop = getWallpaperCrop(context, name) ?: return frame
            return applyCropToBitmap(frame, crop)
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val sampleSize = if (options.outWidth > 0 && targetWidth > 0) {
            max(1, options.outWidth / targetWidth)
        } else {
            1
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        val crop = getWallpaperCrop(context, name) ?: return decoded
        return applyCropToBitmap(decoded, crop)
    }

    fun applyWallpaper(context: Context, rootView: View) {
        stopDrawableAnimation(rootView.background)

        val activeId = getActiveId(context)
        if (activeId == DEFAULT_ID) {
            clearVideoWallpaper(rootView)
            rootView.setBackgroundResource(R.drawable.bg_desktop_mas)
            return
        }

        val file = File(getWallpapersDir(context), activeId)
        if (!file.exists()) {
            clearVideoWallpaper(rootView)
            clearWallpaperCrop(context, activeId)
            setActive(context, DEFAULT_ID)
            rootView.setBackgroundResource(R.drawable.bg_desktop_mas)
            return
        }

        val crop = getWallpaperCrop(context, activeId)
        if (isVideoFile(file)) {
            val applied = applyVideoWallpaper(
                context = context,
                rootView = rootView,
                file = file,
                crop = crop
            )
            if (!applied) {
                clearVideoWallpaper(rootView)
                clearWallpaperCrop(context, activeId)
                setActive(context, DEFAULT_ID)
                rootView.setBackgroundResource(R.drawable.bg_desktop_mas)
            }
            return
        }

        clearVideoWallpaper(rootView)
        val drawable = tryCreateWallpaperDrawable(
            context = context,
            file = file,
            crop = crop,
            targetWidth = if (rootView.width > 0) rootView.width else 1920,
            targetHeight = if (rootView.height > 0) rootView.height else 1080
        )

        if (drawable == null) {
            clearWallpaperCrop(context, activeId)
            setActive(context, DEFAULT_ID)
            rootView.setBackgroundResource(R.drawable.bg_desktop_mas)
            return
        }

        rootView.background = drawable
        startDrawableAnimation(drawable)
    }

    fun getSlideshowConfig(context: Context): SlideshowConfig {
        val prefs = prefs(context)
        val enabled = prefs.getBoolean(KEY_SLIDESHOW_ENABLED, false)
        val interval = if (prefs.contains(KEY_SLIDESHOW_INTERVAL_MINUTES)) {
            prefs.getInt(KEY_SLIDESHOW_INTERVAL_MINUTES, 0).takeIf { it > 0 }
        } else null
        val changeOnToggle = prefs.getBoolean(KEY_SLIDESHOW_CHANGE_ON_APP_TOGGLE, false)
        val rawSelection = prefs.getString(KEY_SLIDESHOW_SELECTION, "") ?: ""
        val selection = rawSelection.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val validSelection = sanitizeSelection(context, selection)
        val validatedEnabled = enabled && validSelection.size >= MIN_SLIDESHOW_SELECTION
        return SlideshowConfig(
            enabled = validatedEnabled,
            intervalMinutes = interval,
            changeOnAppToggle = changeOnToggle,
            selectedIds = validSelection
        )
    }

    fun saveSlideshowConfig(context: Context, config: SlideshowConfig) {
        val prefs = prefs(context)
        val selection = sanitizeSelection(context, config.selectedIds)
        val effectiveEnabled = config.enabled &&
            selection.size >= MIN_SLIDESHOW_SELECTION &&
            (config.intervalMinutes != null || config.changeOnAppToggle)
        val editor = prefs.edit()
        editor.putBoolean(KEY_SLIDESHOW_ENABLED, effectiveEnabled)
        if (config.intervalMinutes != null && config.intervalMinutes > 0) {
            editor.putInt(KEY_SLIDESHOW_INTERVAL_MINUTES, config.intervalMinutes)
        } else {
            editor.remove(KEY_SLIDESHOW_INTERVAL_MINUTES)
        }
        editor.putBoolean(KEY_SLIDESHOW_CHANGE_ON_APP_TOGGLE, config.changeOnAppToggle)
        editor.putString(KEY_SLIDESHOW_SELECTION, selection.joinToString(","))
        if (effectiveEnabled) {
            editor.putLong(KEY_SLIDESHOW_LAST_CHANGE, System.currentTimeMillis())
        } else {
            editor.remove(KEY_SLIDESHOW_LAST_CHANGE)
        }
        editor.apply()
    }

    fun disableSlideshow(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SLIDESHOW_ENABLED, false)
            .remove(KEY_SLIDESHOW_INTERVAL_MINUTES)
            .remove(KEY_SLIDESHOW_CHANGE_ON_APP_TOGGLE)
            .remove(KEY_SLIDESHOW_SELECTION)
            .remove(KEY_SLIDESHOW_LAST_CHANGE)
            .apply()
    }

    fun maybeAdvanceByTime(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val config = getSlideshowConfig(context)
        val intervalMinutes = config.intervalMinutes ?: return false
        if (!config.enabled || intervalMinutes <= 0) return false
        val lastChange = prefs(context).getLong(KEY_SLIDESHOW_LAST_CHANGE, 0L)
        val intervalMs = TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
        if (now - lastChange >= intervalMs) {
            return advanceWallpaper(context) != null
        }
        return false
    }

    fun advanceOnAppToggle(context: Context): Boolean {
        val config = getSlideshowConfig(context)
        if (!config.enabled || !config.changeOnAppToggle) return false
        return advanceWallpaper(context) != null
    }

    fun advanceWallpaper(context: Context): String? {
        val config = getSlideshowConfig(context)
        if (!config.enabled) return null
        val pool = rotationPool(context, config)
        if (pool.size < MIN_SLIDESHOW_SELECTION) return null

        val currentId = getActiveId(context)
        val currentIndex = pool.indexOf(currentId)
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % pool.size
        val nextId = pool[nextIndex]

        setActive(context, nextId)
        prefs(context).edit().putLong(KEY_SLIDESHOW_LAST_CHANGE, System.currentTimeMillis()).apply()
        return nextId
    }

    fun selectedCount(context: Context): Int {
        return sanitizeSelection(context, getSlideshowConfig(context).selectedIds).size
    }

    private fun rotationPool(context: Context, config: SlideshowConfig): List<String> {
        val selection = sanitizeSelection(context, config.selectedIds)
        return if (selection.size >= MIN_SLIDESHOW_SELECTION) selection else emptyList()
    }

    private fun sanitizeSelection(context: Context, selection: List<String>): List<String> {
        val available = getWallpaperList(context).toSet()
        return selection.filter { available.contains(it) }.take(MAX_SLIDESHOW_SELECTION)
    }

    private fun tryCreateWallpaperDrawable(
        context: Context,
        file: File,
        crop: WallpaperCrop?,
        targetWidth: Int,
        targetHeight: Int
    ): Drawable? {
        val baseDrawable = try {
            when {
                file.extension.equals("gif", ignoreCase = true) -> {
                    GifDrawable(file).apply { setLoopCount(0) }
                }
                file.extension.equals("webp", ignoreCase = true) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    isAnimatedWebp(file) -> {
                    val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
                    if (drawable is AnimatedImageDrawable) {
                        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    }
                    drawable
                }
                else -> {
                    val bitmap = decodeSampledBitmap(file, targetWidth, targetHeight) ?: return null
                    BitmapDrawable(context.resources, bitmap)
                }
            }
            } catch (_: IOException) {
            return null
        } catch (_: RuntimeException) {
            return null
        }

        return wrapWithCropDrawable(baseDrawable, crop)
    }

    private fun wrapWithCropDrawable(base: Drawable, crop: WallpaperCrop?): Drawable {
        val normalized = crop?.normalized()
        if (normalized == null || normalized.isFullFrame()) {
            return base
        }
        return CroppedDrawable(base, normalized)
    }

    private fun applyCropToBitmap(bitmap: Bitmap, crop: WallpaperCrop): Bitmap {
        val normalized = crop.normalized()
        if (normalized.isFullFrame()) return bitmap

        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) return bitmap

        val left = (normalized.left * width).toInt().coerceIn(0, width - 1)
        val top = (normalized.top * height).toInt().coerceIn(0, height - 1)
        val right = (normalized.right * width).toInt().coerceIn(left + 1, width)
        val bottom = (normalized.bottom * height).toInt().coerceIn(top + 1, height)
        val cropWidth = max(1, right - left)
        val cropHeight = max(1, bottom - top)

        val cropped = Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
        if (cropped !== bitmap) {
            bitmap.recycle()
        }
        return cropped
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }

        var sampleSize = 1
        while ((bounds.outWidth / (sampleSize * 2)) >= reqWidth && (bounds.outHeight / (sampleSize * 2)) >= reqHeight) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = max(1, sampleSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    private fun stopDrawableAnimation(drawable: Drawable?) {
        (drawable as? Animatable)?.stop()
    }

    private fun startDrawableAnimation(drawable: Drawable?) {
        (drawable as? Animatable)?.start()
    }

    private fun isVideoFile(file: File): Boolean {
        return VIDEO_EXTENSIONS.contains(file.extension.lowercase(Locale.US))
    }

    private fun extractVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    640,
                    360
                )
            } else {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: RuntimeException) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: RuntimeException) {
            }
        }
    }

    private fun applyVideoWallpaper(
        context: Context,
        rootView: View,
        file: File,
        crop: WallpaperCrop?
    ): Boolean {
        val parent = rootView as? ViewGroup ?: return false
        val normalizedCrop = crop?.normalized()
        val existing = findVideoWallpaperBundle(rootView)
        if (existing != null && sameVideoSource(existing, file, normalizedCrop)) {
            applyVideoLayout(existing)
            val existingSurface = existing.textureView.surfaceTexture
            if (existing.mediaPlayer == null && existing.textureView.isAvailable && existingSurface != null) {
                startVideoPlayback(existing, existingSurface)
            }
            return true
        }

        clearVideoWallpaper(rootView)

        val textureView = TextureView(context)
        textureView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        textureView.isOpaque = true
        textureView.contentDescription = VIDEO_WALLPAPER_MARKER

        val bundle = VideoWallpaperBundle(parent, textureView, file.absolutePath, normalizedCrop)
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyVideoLayout(bundle)
        }
        bundle.layoutChangeListener = listener
        parent.addOnLayoutChangeListener(listener)
        textureView.tag = bundle
        parent.addView(textureView, 0)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                startVideoPlayback(bundle, surface)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                applyVideoLayout(bundle)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                stopVideoPlayback(bundle)
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }

        val readySurface = textureView.surfaceTexture
        if (textureView.isAvailable && readySurface != null) {
            startVideoPlayback(bundle, readySurface)
        }

        return true
    }

    private fun findVideoWallpaperBundle(rootView: View): VideoWallpaperBundle? {
        val parent = rootView as? ViewGroup ?: return null
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child !is TextureView) continue
            if (child.contentDescription != VIDEO_WALLPAPER_MARKER) continue
            val bundle = child.tag as? VideoWallpaperBundle ?: continue
            return bundle
        }
        return null
    }

    private fun sameVideoSource(bundle: VideoWallpaperBundle, file: File, crop: WallpaperCrop?): Boolean {
        if (bundle.path != file.absolutePath) return false
        return sameCrop(bundle.crop, crop)
    }

    private fun sameCrop(a: WallpaperCrop?, b: WallpaperCrop?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        val epsilon = 0.0005f
        return kotlin.math.abs(a.left - b.left) <= epsilon &&
            kotlin.math.abs(a.top - b.top) <= epsilon &&
            kotlin.math.abs(a.right - b.right) <= epsilon &&
            kotlin.math.abs(a.bottom - b.bottom) <= epsilon
    }

    private fun startVideoPlayback(bundle: VideoWallpaperBundle, surfaceTexture: SurfaceTexture) {
        stopVideoPlayback(bundle)

        val mediaPlayer = MediaPlayer()
        bundle.mediaPlayer = mediaPlayer

        try {
            val surface = Surface(surfaceTexture)
            bundle.surface = surface
            mediaPlayer.setSurface(surface)
            mediaPlayer.setDataSource(bundle.path)
            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0f, 0f)
            mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
                bundle.videoWidth = width
                bundle.videoHeight = height
                applyVideoLayout(bundle)
            }
            mediaPlayer.setOnPreparedListener { player ->
                val width = player.videoWidth
                val height = player.videoHeight
                bundle.videoWidth = width
                bundle.videoHeight = height
                applyVideoLayout(bundle)
                player.start()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                false
            }
            mediaPlayer.prepareAsync()
        } catch (_: IOException) {
            releaseVideoBundle(bundle)
        } catch (_: SecurityException) {
            releaseVideoBundle(bundle)
        } catch (_: RuntimeException) {
            releaseVideoBundle(bundle)
        }
    }

    private fun stopVideoPlayback(bundle: VideoWallpaperBundle) {
        bundle.mediaPlayer?.let { existing ->
            try {
                existing.setSurface(null)
            } catch (_: RuntimeException) {
            }
            try {
                if (existing.isPlaying) existing.stop()
            } catch (_: RuntimeException) {
            }
            try {
                existing.reset()
            } catch (_: RuntimeException) {
            }
            try {
                existing.release()
            } catch (_: RuntimeException) {
            }
        }
        bundle.mediaPlayer = null
        bundle.surface?.release()
        bundle.surface = null
    }

    private fun applyVideoLayout(bundle: VideoWallpaperBundle) {
        val textureView = bundle.textureView
        val videoWidth = bundle.videoWidth
        val videoHeight = bundle.videoHeight
        val crop = bundle.crop
        if (videoWidth <= 0 || videoHeight <= 0) return
        if (bundle.container.width <= 0 || bundle.container.height <= 0) return

        val normalized = crop?.normalized() ?: WallpaperCrop(0f, 0f, 1f, 1f)
        val cropLeftPx = normalized.left * videoWidth
        val cropTopPx = normalized.top * videoHeight
        val cropWidthPx = max(1f, (normalized.right - normalized.left) * videoWidth)
        val cropHeightPx = max(1f, (normalized.bottom - normalized.top) * videoHeight)

        val viewWidth = bundle.container.width.toFloat()
        val viewHeight = bundle.container.height.toFloat()

        val scale = max(viewWidth / cropWidthPx, viewHeight / cropHeightPx)
        val scaledWidth = max(1, (videoWidth * scale).roundToInt())
        val scaledHeight = max(1, (videoHeight * scale).roundToInt())

        val topLeftX = ((viewWidth - cropWidthPx * scale) / 2f - cropLeftPx * scale).roundToInt()
        val topLeftY = ((viewHeight - cropHeightPx * scale) / 2f - cropTopPx * scale).roundToInt()

        val params = textureView.layoutParams
        if (params != null) {
            params.width = scaledWidth
            params.height = scaledHeight
            textureView.layoutParams = params
        } else {
            textureView.layoutParams = ViewGroup.LayoutParams(scaledWidth, scaledHeight)
        }
        textureView.translationX = topLeftX.toFloat()
        textureView.translationY = topLeftY.toFloat()
    }

    fun clearVideoWallpaper(rootView: View) {
        val parent = rootView as? ViewGroup ?: return
        val toRemove = mutableListOf<View>()
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child !is TextureView) continue
            if (child.contentDescription != VIDEO_WALLPAPER_MARKER) continue
            val bundle = child.tag as? VideoWallpaperBundle
            if (bundle != null) {
                releaseVideoBundle(bundle)
            } else {
                child.surfaceTextureListener = null
                child.tag = null
                child.contentDescription = null
            }
            toRemove.add(child)
        }
        toRemove.forEach { view ->
            parent.removeView(view)
        }
    }

    private fun releaseVideoBundle(bundle: VideoWallpaperBundle) {
        bundle.layoutChangeListener?.let { listener ->
            bundle.container.removeOnLayoutChangeListener(listener)
        }
        bundle.layoutChangeListener = null
        stopVideoPlayback(bundle)
        bundle.textureView.surfaceTextureListener = null
        bundle.textureView.tag = null
        bundle.textureView.contentDescription = null
    }

    private fun isAnimatedFile(file: File): Boolean {
        if (file.extension.equals("gif", ignoreCase = true)) return true
        if (!file.extension.equals("webp", ignoreCase = true)) return false
        return isAnimatedWebp(file)
    }

    private fun isAnimatedWebp(file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(21)
                if (input.read(header) < header.size) return false
                if (
                    header[0] != 'R'.code.toByte() ||
                    header[1] != 'I'.code.toByte() ||
                    header[2] != 'F'.code.toByte() ||
                    header[3] != 'F'.code.toByte()
                ) return false
                if (
                    header[8] != 'W'.code.toByte() ||
                    header[9] != 'E'.code.toByte() ||
                    header[10] != 'B'.code.toByte() ||
                    header[11] != 'P'.code.toByte()
                ) return false
                if (
                    header[12] != 'V'.code.toByte() ||
                    header[13] != 'P'.code.toByte() ||
                    header[14] != '8'.code.toByte() ||
                    header[15] != 'X'.code.toByte()
                ) return false
                val flags = header[20].toInt() and 0xFF
                (flags and 0x02) != 0
            }
        } catch (_: IOException) {
            false
        }
    }

    private class CroppedDrawable(
        private val base: Drawable,
        private val crop: WallpaperCrop
    ) : Drawable(), Drawable.Callback, Animatable {

        private val drawBounds = Rect()

        init {
            base.callback = this
        }

        override fun draw(canvas: Canvas) {
            val dst = bounds
            if (dst.isEmpty) return

            val iw = base.intrinsicWidth
            val ih = base.intrinsicHeight
            if (iw <= 0 || ih <= 0) {
                base.bounds = dst
                base.draw(canvas)
                return
            }

            val normalized = crop.normalized()
            val left = (normalized.left * iw).toInt().coerceIn(0, iw - 1)
            val top = (normalized.top * ih).toInt().coerceIn(0, ih - 1)
            val right = (normalized.right * iw).toInt().coerceIn(left + 1, iw)
            val bottom = (normalized.bottom * ih).toInt().coerceIn(top + 1, ih)
            val srcWidth = max(1, right - left)
            val srcHeight = max(1, bottom - top)

            val save = canvas.save()
            canvas.clipRect(dst)
            canvas.translate(dst.left.toFloat(), dst.top.toFloat())
            canvas.scale(
                dst.width().toFloat() / srcWidth.toFloat(),
                dst.height().toFloat() / srcHeight.toFloat()
            )
            canvas.translate(-left.toFloat(), -top.toFloat())
            drawBounds.set(0, 0, iw, ih)
            base.bounds = drawBounds
            base.draw(canvas)
            canvas.restoreToCount(save)
        }

        override fun onBoundsChange(bounds: Rect) {
            invalidateSelf()
        }

        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            val changed = base.setVisible(visible, restart)
            if (visible) {
                start()
            } else {
                stop()
            }
            return super.setVisible(visible, restart) || changed
        }

        override fun setAlpha(alpha: Int) {
            base.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            base.setColorFilter(colorFilter)
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = base.intrinsicWidth

        override fun getIntrinsicHeight(): Int = base.intrinsicHeight

        override fun start() {
            (base as? Animatable)?.start()
        }

        override fun stop() {
            (base as? Animatable)?.stop()
        }

        override fun isRunning(): Boolean {
            return (base as? Animatable)?.isRunning ?: false
        }

        override fun invalidateDrawable(who: Drawable) {
            invalidateSelf()
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            scheduleSelf(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            unscheduleSelf(what)
        }
    }

    private data class VideoWallpaperBundle(
        val container: ViewGroup,
        val textureView: TextureView,
        val path: String,
        val crop: WallpaperCrop?
    ) {
        var mediaPlayer: MediaPlayer? = null
        var surface: Surface? = null
        var videoWidth: Int = 0
        var videoHeight: Int = 0
        var layoutChangeListener: View.OnLayoutChangeListener? = null
    }
}
