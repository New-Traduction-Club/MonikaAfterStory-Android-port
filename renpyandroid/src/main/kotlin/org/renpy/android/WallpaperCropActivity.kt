package org.renpy.android

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.view.Display
import android.view.LayoutInflater
import android.webkit.MimeTypeMap
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropException
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WallpaperCropActivity : BaseActivity() {

    override val preferredOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

    private var sourceUri: Uri? = null
    private var tempOutputFile: File? = null
    private var tempVideoFrameFile: File? = null
    private var tempImageSourceFile: File? = null

    private var targetWidth = 0
    private var targetHeight = 0
    private var cropLaunched = false
    private var isVideoSource = false

    private var launchJob: Job? = null
    private var processingJob: Job? = null
    private var processingDialog: AlertDialog? = null

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cropResult = extractCropResult(result.data)
        val error = cropResult?.error

        if (result.resultCode == Activity.RESULT_OK && cropResult != null && error == null) {
            applyCroppedWallpaper(cropResult)
            return@registerForActivityResult
        }

        dismissProcessingDialog()
        if (error != null && error !is CropException.Cancellation) {
            showCropError(error)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundEffects.initialize(this)

        sourceUri = savedInstanceState?.getString(STATE_SOURCE_URI)?.let(Uri::parse)
            ?: intent.getStringExtra("image_uri")?.let(Uri::parse)
        if (sourceUri == null) {
            finish()
            return
        }

        targetWidth = savedInstanceState?.getInt(STATE_TARGET_WIDTH) ?: 0
        targetHeight = savedInstanceState?.getInt(STATE_TARGET_HEIGHT) ?: 0
        cropLaunched = savedInstanceState?.getBoolean(STATE_CROP_LAUNCHED, false) ?: false
        isVideoSource = savedInstanceState?.getBoolean(STATE_IS_VIDEO_SOURCE, false) ?: false
        savedInstanceState?.getString(STATE_TEMP_OUTPUT_PATH)?.let { tempOutputFile = File(it) }
        savedInstanceState?.getString(STATE_TEMP_VIDEO_FRAME_PATH)?.let { tempVideoFrameFile = File(it) }
        savedInstanceState?.getString(STATE_TEMP_IMAGE_SOURCE_PATH)?.let { tempImageSourceFile = File(it) }

        val useLandscapeTargets = shouldUseLandscapeTargets()
        if (!hasTargetWallpaperSizeFor(useLandscapeTargets)) {
            val (resolvedWidth, resolvedHeight) = resolveTargetWallpaperSize(useLandscapeTargets)
            targetWidth = resolvedWidth
            targetHeight = resolvedHeight
        }

        if (!cropLaunched) {
            launchCrop()
        }
    }

    private fun launchCrop() {
        val source = sourceUri ?: run {
            finish()
            return
        }

        launchJob?.cancel()
        showProcessingDialog(R.string.wallpaper_processing_opening)
        launchJob = lifecycleScope.launch(Dispatchers.Main) {
            val prepared = withContext(Dispatchers.IO) { prepareCropSource(source) }
            when (prepared) {
                is CropSourcePreparation.Ready -> {
                    val outputFile = File(
                        cacheDir,
                        "wallpaper_crop_result_${System.currentTimeMillis()}.png"
                    ).also { tempOutputFile = it }
                    dismissProcessingDialog()
                    launchCropIntent(prepared.cropSourceUri, Uri.fromFile(outputFile))
                }
                is CropSourcePreparation.UserError -> {
                    dismissProcessingDialog()
                    InAppNotifier.show(this@WallpaperCropActivity, getString(prepared.messageRes), true)
                    finish()
                }
                is CropSourcePreparation.Error -> {
                    dismissProcessingDialog()
                    showCropError(prepared.throwable)
                    finish()
                }
            }
        }
    }

    private fun prepareCropSource(source: Uri): CropSourcePreparation {
        if (!cropLaunched && !isVideoSource) {
            isVideoSource = isVideo(source)
        }

        if (!isVideoSource) {
            return try {
                CropSourcePreparation.Ready(ensureLocalImageSource(source))
            } catch (e: IOException) {
                CropSourcePreparation.Error(e)
            } catch (e: SecurityException) {
                CropSourcePreparation.Error(e)
            }
        }

        val validationError = validateVideoForWallpaper(source)
        if (validationError != null) {
            return CropSourcePreparation.UserError(validationError)
        }

        val frameFile = tempVideoFrameFile ?: File(
            cacheDir,
            "wallpaper_video_preview_${System.currentTimeMillis()}.jpg"
        ).also { tempVideoFrameFile = it }

        if (!frameFile.exists() || frameFile.length() <= 0L) {
            return try {
                extractVideoFrameToFile(source, frameFile)
                CropSourcePreparation.Ready(Uri.fromFile(frameFile))
            } catch (e: IOException) {
                CropSourcePreparation.Error(e)
            } catch (e: SecurityException) {
                CropSourcePreparation.Error(e)
            }
        }

        return CropSourcePreparation.Ready(Uri.fromFile(frameFile))
    }

    @Throws(IOException::class, SecurityException::class)
    private fun ensureLocalImageSource(source: Uri): Uri {
        if (source.scheme.equals("file", ignoreCase = true)) {
            return source
        }

        val existing = tempImageSourceFile
        if (existing != null && existing.exists() && existing.length() > 0L) {
            return Uri.fromFile(existing)
        }

        val extension = guessExtension(source).ifBlank { "png" }.lowercase(Locale.US)
        val imageFile = File(
            cacheDir,
            "wallpaper_image_source_${System.currentTimeMillis()}.$extension"
        ).also { tempImageSourceFile = it }

        contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(imageFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to open image source URI")

        return Uri.fromFile(imageFile)
    }

    private fun launchCropIntent(cropSourceUri: Uri, outputUri: Uri) {
        val cropOptions = buildCropOptions(outputUri)
        cropLaunched = true
        val cropIntent = Intent(this, FullscreenCropImageActivity::class.java).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (cropSourceUri.scheme.equals("content", ignoreCase = true)) {
                clipData = ClipData.newRawUri("crop_source", cropSourceUri)
            }
            putExtra(
                CropImage.CROP_IMAGE_EXTRA_BUNDLE,
                Bundle(2).apply {
                    putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, cropSourceUri)
                    putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, cropOptions)
                }
            )
        }
        cropLauncher.launch(cropIntent)
    }

    private fun buildCropOptions(outputUri: Uri): CropImageOptions {
        val ratio = simplifyRatio(targetWidth, targetHeight)
        return CropImageOptions(
            imageSourceIncludeGallery = false,
            imageSourceIncludeCamera = false,
            guidelines = CropImageView.Guidelines.ON,
            fixAspectRatio = true,
            aspectRatioX = ratio.first,
            aspectRatioY = ratio.second,
            autoZoomEnabled = true,
            multiTouchEnabled = true,
            centerMoveEnabled = true,
            canChangeCropWindow = true,
            initialCropWindowPaddingRatio = 0f,
            maxZoom = 8,
            outputCompressFormat = Bitmap.CompressFormat.PNG,
            outputCompressQuality = 100,
            outputRequestWidth = targetWidth,
            outputRequestHeight = targetHeight,
            outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_EXACT,
            customOutputUri = outputUri,
            activityTitle = getString(R.string.wallpaper_crop_title),
            cropMenuCropButtonTitle = getString(R.string.wallpaper_apply),
            activityBackgroundColor = ContextCompat.getColor(this, R.color.colorWindowContentBackground),
            toolbarColor = ContextCompat.getColor(this, R.color.colorWindowHeaderBackground),
            toolbarTitleColor = ContextCompat.getColor(this, R.color.colorTextPrimary),
            toolbarBackButtonColor = ContextCompat.getColor(this, R.color.colorPrimary),
            toolbarTintColor = ContextCompat.getColor(this, R.color.colorPrimary),
            activityMenuTextColor = ContextCompat.getColor(this, R.color.colorPrimary),
            activityMenuIconColor = ContextCompat.getColor(this, R.color.colorPrimary),
            backgroundColor = 0x88000000.toInt(),
            borderLineColor = ContextCompat.getColor(this, R.color.colorPrimary),
            borderCornerColor = ContextCompat.getColor(this, R.color.colorPrimary),
            guidelinesColor = ContextCompat.getColor(this, R.color.colorDivider)
        )
    }

    private fun extractCropResult(data: Intent?): CropImage.ActivityResult? {
        if (data == null) return null
        return data.parcelableCompat(CropImage.CROP_IMAGE_EXTRA_RESULT)
    }

    private inline fun <reified T : Parcelable> Intent.parcelableCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }
    }

    private fun applyCroppedWallpaper(cropResult: CropImage.ActivityResult) {
        val localSource = sourceUri
        if (localSource == null) {
            InAppNotifier.show(this, getString(R.string.viewer_error_image_decode), true)
            finish()
            return
        }

        processingJob?.cancel()
        showProcessingDialog(R.string.wallpaper_processing_applying)
        processingJob = lifecycleScope.launch(Dispatchers.Main) {
            val result = withContext(Dispatchers.IO) {
                try {
                    if (isVideoSource || isVideo(localSource)) {
                        return@withContext applyVideoWallpaper(localSource, cropResult)
                    }

                    val sourceExtension = guessExtension(localSource).lowercase(Locale.US)
                    val detectedAnimatedFormat = detectAnimatedFormat(localSource)
                    val animatedExtension = when {
                        sourceExtension == GIF_EXTENSION -> GIF_EXTENSION
                        detectedAnimatedFormat == GIF_EXTENSION -> GIF_EXTENSION
                        detectedAnimatedFormat == WEBP_EXTENSION -> WEBP_EXTENSION
                        else -> null
                    }

                    if (animatedExtension != null) {
                        return@withContext applyLiveWallpaper(localSource, animatedExtension, cropResult)
                    }

                    applyStaticWallpaper(cropResult.uriContent)
                } catch (e: IOException) {
                    WallpaperApplyResult.Error(e)
                } catch (e: SecurityException) {
                    WallpaperApplyResult.Error(e)
                } catch (e: RuntimeException) {
                    WallpaperApplyResult.Error(e)
                }
            }

            dismissProcessingDialog()
            when (result) {
                is WallpaperApplyResult.Success -> {
                    window?.decorView?.rootView?.let { WallpaperManager.applyWallpaper(this@WallpaperCropActivity, it) }
                    InAppNotifier.show(this@WallpaperCropActivity, getString(R.string.wallpaper_applied))
                }
                is WallpaperApplyResult.UserError -> {
                    InAppNotifier.show(this@WallpaperCropActivity, getString(result.messageRes), true)
                }
                is WallpaperApplyResult.Error -> {
                    showCropError(result.throwable)
                }
            }
            finish()
        }
    }

    private fun applyVideoWallpaper(sourceUri: Uri, cropResult: CropImage.ActivityResult): WallpaperApplyResult {
        val validationError = validateVideoForWallpaper(sourceUri)
        if (validationError != null) {
            return WallpaperApplyResult.UserError(validationError)
        }

        val extension = normalizeVideoExtension(guessExtension(sourceUri))
        val name = "wallpaper_${System.currentTimeMillis()}.$extension"
        WallpaperManager.saveWallpaperFromUri(this, sourceUri, name)
        WallpaperManager.setWallpaperCrop(this, name, extractNormalizedCrop(cropResult))
        WallpaperManager.setActive(this, name)
        return WallpaperApplyResult.Success
    }

    private fun detectAnimatedFormat(uri: Uri): String? {
        val header = ByteArray(21)
        val bytesRead = try {
            contentResolver.openInputStream(uri)?.use { input ->
                readAtLeast(input, header)
            } ?: -1
        } catch (_: IOException) {
            -1
        } catch (_: SecurityException) {
            -1
        }

        if (bytesRead >= 6 && isGifHeader(header)) {
            return GIF_EXTENSION
        }
        if (bytesRead >= 21 && isAnimatedWebpHeader(header)) {
            return WEBP_EXTENSION
        }
        return null
    }

    private fun readAtLeast(input: java.io.InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read <= 0) break
            offset += read
        }
        return offset
    }

    private fun isGifHeader(header: ByteArray): Boolean {
        val sig = String(header, 0, 6, Charsets.US_ASCII)
        return sig == "GIF87a" || sig == "GIF89a"
    }

    private fun isAnimatedWebpHeader(header: ByteArray): Boolean {
        val riff = header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        val webp = header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
        val vp8x = header[12] == 'V'.code.toByte() &&
            header[13] == 'P'.code.toByte() &&
            header[14] == '8'.code.toByte() &&
            header[15] == 'X'.code.toByte()
        if (!riff || !webp || !vp8x) return false
        val flags = header[20].toInt() and 0xFF
        return (flags and 0x02) != 0
    }

    private fun applyLiveWallpaper(
        sourceUri: Uri,
        extension: String,
        cropResult: CropImage.ActivityResult
    ): WallpaperApplyResult {
        val safeExtension = when (extension.lowercase(Locale.US)) {
            GIF_EXTENSION -> GIF_EXTENSION
            WEBP_EXTENSION -> WEBP_EXTENSION
            else -> GIF_EXTENSION
        }
        val name = "wallpaper_${System.currentTimeMillis()}.$safeExtension"
        WallpaperManager.saveWallpaperFromUri(this, sourceUri, name)
        WallpaperManager.setWallpaperCrop(this, name, extractNormalizedCrop(cropResult))
        WallpaperManager.setActive(this, name)
        return WallpaperApplyResult.Success
    }

    private fun applyStaticWallpaper(resultUri: Uri?): WallpaperApplyResult {
        if (resultUri == null) {
            return WallpaperApplyResult.UserError(R.string.viewer_error_image_decode)
        }

        val decoded = decodeBitmap(resultUri, targetWidth, targetHeight)
            ?: return WallpaperApplyResult.UserError(R.string.viewer_error_image_decode)

        val finalBitmap = if (decoded.width != targetWidth || decoded.height != targetHeight) {
            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also { decoded.recycle() }
        } else {
            decoded
        }

        try {
            val name = "wallpaper_${System.currentTimeMillis()}.png"
            WallpaperManager.saveWallpaper(this, finalBitmap, name)
            WallpaperManager.clearWallpaperCrop(this, name)
            WallpaperManager.setActive(this, name)
            return WallpaperApplyResult.Success
        } finally {
            finalBitmap.recycle()
        }
    }

    private fun extractNormalizedCrop(cropResult: CropImage.ActivityResult): WallpaperManager.WallpaperCrop? {
        val wholeRect = cropResult.wholeImageRect ?: return null
        if (wholeRect.width() <= 0 || wholeRect.height() <= 0) return null

        val cropRect = cropResult.cropRect ?: return null
        if (cropRect.width() <= 0 || cropRect.height() <= 0) return null

        return wallpaperCropFromRects(cropRect, wholeRect)
    }

    private fun wallpaperCropFromRects(cropRect: Rect, wholeRect: Rect): WallpaperManager.WallpaperCrop {
        val width = wholeRect.width().toFloat()
        val height = wholeRect.height().toFloat()
        return WallpaperManager.WallpaperCrop(
            left = (cropRect.left - wholeRect.left) / width,
            top = (cropRect.top - wholeRect.top) / height,
            right = (cropRect.right - wholeRect.left) / width,
            bottom = (cropRect.bottom - wholeRect.top) / height
        ).normalized()
    }

    @Throws(IOException::class)
    private fun decodeBitmap(uri: Uri, desiredWidth: Int, desiredHeight: Int): Bitmap? {
        val safeWidth = max(1, desiredWidth)
        val safeHeight = max(1, desiredHeight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                decoder.setTargetSize(safeWidth, safeHeight)
            }
        }
        return decodeSampledBitmapLegacy(uri, safeWidth, safeHeight)
    }

    @Throws(IOException::class)
    private fun decodeSampledBitmapLegacy(uri: Uri, desiredWidth: Int, desiredHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }

        val sampleSize = calculateSampleSize(
            srcWidth = bounds.outWidth,
            srcHeight = bounds.outHeight,
            reqWidth = desiredWidth,
            reqHeight = desiredHeight
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    }

    private fun calculateSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        if (srcWidth <= 0 || srcHeight <= 0) return sampleSize

        while ((srcWidth / (sampleSize * 2)) >= reqWidth && (srcHeight / (sampleSize * 2)) >= reqHeight) {
            sampleSize *= 2
        }
        return max(1, sampleSize)
    }

    private fun guessExtension(uri: Uri): String {
        val mimeType = contentResolver.getType(uri)
        if (!mimeType.isNullOrEmpty()) {
            val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (!fromMime.isNullOrBlank()) return fromMime.lowercase(Locale.US)
        }

        val segment = uri.lastPathSegment.orEmpty()
        val dot = segment.lastIndexOf('.')
        if (dot >= 0 && dot + 1 < segment.length) {
            return segment.substring(dot + 1).lowercase(Locale.US)
        }
        return "png"
    }

    private fun normalizeVideoExtension(extension: String): String {
        val normalized = extension.lowercase(Locale.US)
        return if (VIDEO_EXTENSIONS.contains(normalized)) normalized else "mp4"
    }

    private fun isVideo(uri: Uri): Boolean {
        val mime = contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()
        if (mime.startsWith("video/")) return true
        val extension = guessExtension(uri).lowercase(Locale.US)
        if (VIDEO_EXTENSIONS.contains(extension)) return true
        return isLikelyVideoByMetadata(uri)
    }

    private fun isLikelyVideoByMetadata(uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                hasVideo == "yes" || retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) != null
            } ?: false
        } catch (_: RuntimeException) {
            false
        } finally {
            try {
                retriever.release()
            } catch (_: RuntimeException) {
            }
        }
    }

    @Throws(IOException::class)
    private fun extractVideoFrameToFile(videoUri: Uri, outputFile: File) {
        val retriever = MediaMetadataRetriever()
        val frame = try {
            contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        min(max(targetWidth, 1), VIDEO_PREVIEW_MAX_WIDTH),
                        min(max(targetHeight, 1), VIDEO_PREVIEW_MAX_HEIGHT)
                    )
                } else {
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }
        } catch (e: RuntimeException) {
            throw IOException("Unable to extract video frame", e)
        } finally {
            try {
                retriever.release()
            } catch (_: RuntimeException) {
            }
        } ?: throw IOException("Unable to decode video preview frame")

        val scaled = scaleDownBitmapIfNeeded(frame, VIDEO_PREVIEW_MAX_WIDTH, VIDEO_PREVIEW_MAX_HEIGHT)
        FileOutputStream(outputFile).use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, VIDEO_PREVIEW_JPEG_QUALITY, output)
        }
        if (scaled !== frame) frame.recycle()
        scaled.recycle()
    }

    private fun scaleDownBitmapIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }
        val ratio = min(maxWidth / bitmap.width.toFloat(), maxHeight / bitmap.height.toFloat())
        val width = max(1, (bitmap.width * ratio).roundToInt())
        val height = max(1, (bitmap.height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun validateVideoForWallpaper(uri: Uri): Int? {
        val fileSize = queryFileSize(uri)
        if (fileSize != null && fileSize > MAX_VIDEO_FILE_SIZE_BYTES) {
            return R.string.wallpaper_video_too_large
        }

        val info = extractVideoInfo(uri) ?: return null
        val width = info.width
        val height = info.height
        if (width <= 0 || height <= 0) return null

        val pixels = width.toLong() * height.toLong()
        if (max(width, height) > MAX_VIDEO_DIMENSION || pixels > MAX_VIDEO_PIXELS) {
            return R.string.wallpaper_video_resolution_too_high
        }
        if (info.bitrate != null && info.bitrate > MAX_VIDEO_BITRATE) {
            return R.string.wallpaper_video_bitrate_too_high
        }
        return null
    }

    private fun queryFileSize(uri: Uri): Long? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index == -1 || cursor.isNull(index)) return null
                cursor.getLong(index)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun extractVideoInfo(uri: Uri): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                VideoInfo(width = width, height = height, bitrate = bitrate)
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

    private fun resolveTargetWallpaperSize(useLandscapeTargets: Boolean): Pair<Int, Int> {
        val displayManager = getSystemService(DisplayManager::class.java)
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.display ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

        if (display != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val mode = display.mode
                val longSide = max(mode.physicalWidth, mode.physicalHeight)
                val shortSide = min(mode.physicalWidth, mode.physicalHeight)
                return sizeForOrientation(longSide, shortSide, useLandscapeTargets)
            }

            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(realMetrics)
            val longSide = max(realMetrics.widthPixels, realMetrics.heightPixels)
            val shortSide = min(realMetrics.widthPixels, realMetrics.heightPixels)
            return sizeForOrientation(longSide, shortSide, useLandscapeTargets)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            val longSide = max(bounds.width(), bounds.height())
            val shortSide = min(bounds.width(), bounds.height())
            return sizeForOrientation(longSide, shortSide, useLandscapeTargets)
        }

        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val longSide = max(metrics.widthPixels, metrics.heightPixels)
        val shortSide = min(metrics.widthPixels, metrics.heightPixels)
        return sizeForOrientation(longSide, shortSide, useLandscapeTargets)
    }

    private fun hasTargetWallpaperSizeFor(useLandscapeTargets: Boolean): Boolean {
        if (targetWidth <= 0 || targetHeight <= 0) return false
        return if (useLandscapeTargets) {
            targetWidth >= targetHeight
        } else {
            targetHeight >= targetWidth
        }
    }

    private fun shouldUseLandscapeTargets(): Boolean {
        return when (preferredOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE -> true
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> false
            else -> resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
    }

    private fun sizeForOrientation(
        longSide: Int,
        shortSide: Int,
        useLandscapeTargets: Boolean
    ): Pair<Int, Int> {
        return if (useLandscapeTargets) {
            Pair(max(longSide, 1), max(shortSide, 1))
        } else {
            Pair(max(shortSide, 1), max(longSide, 1))
        }
    }

    private fun simplifyRatio(width: Int, height: Int): Pair<Int, Int> {
        var a = max(width, 1)
        var b = max(height, 1)
        while (b != 0) {
            val remainder = a % b
            a = b
            b = remainder
        }
        val gcd = max(a, 1)
        return Pair(max(width / gcd, 1), max(height / gcd, 1))
    }

    private fun showCropError(error: Throwable?) {
        val message = error?.localizedMessage ?: getString(R.string.viewer_error_image_decode)
        InAppNotifier.show(this, getString(R.string.setup_error, message), true)
    }

    private fun showProcessingDialog(@StringRes messageRes: Int) {
        if (isFinishing) return
        val message = getString(messageRes)
        val existing = processingDialog
        if (existing?.isShowing == true) {
            existing.findViewById<TextView>(R.id.progressText)?.text = message
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        view.findViewById<ProgressBar>(R.id.progressBar)?.isIndeterminate = true
        view.findViewById<TextView>(R.id.progressText)?.text = message

        processingDialog = GameDialogBuilder(this)
            .setView(view)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    private fun dismissProcessingDialog() {
        processingDialog?.dismiss()
        processingDialog = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SOURCE_URI, sourceUri?.toString())
        outState.putInt(STATE_TARGET_WIDTH, targetWidth)
        outState.putInt(STATE_TARGET_HEIGHT, targetHeight)
        outState.putBoolean(STATE_CROP_LAUNCHED, cropLaunched)
        outState.putBoolean(STATE_IS_VIDEO_SOURCE, isVideoSource)
        outState.putString(STATE_TEMP_OUTPUT_PATH, tempOutputFile?.absolutePath)
        outState.putString(STATE_TEMP_VIDEO_FRAME_PATH, tempVideoFrameFile?.absolutePath)
        outState.putString(STATE_TEMP_IMAGE_SOURCE_PATH, tempImageSourceFile?.absolutePath)
    }

    override fun onDestroy() {
        launchJob?.cancel()
        processingJob?.cancel()
        dismissProcessingDialog()
        if (isFinishing) {
            tempOutputFile?.let { if (it.exists()) it.delete() }
            tempVideoFrameFile?.let { if (it.exists()) it.delete() }
            tempImageSourceFile?.let { if (it.exists()) it.delete() }
        }
        super.onDestroy()
    }

    private sealed interface WallpaperApplyResult {
        object Success : WallpaperApplyResult
        data class UserError(@StringRes val messageRes: Int) : WallpaperApplyResult
        data class Error(val throwable: Throwable) : WallpaperApplyResult
    }

    private sealed interface CropSourcePreparation {
        data class Ready(val cropSourceUri: Uri) : CropSourcePreparation
        data class UserError(@StringRes val messageRes: Int) : CropSourcePreparation
        data class Error(val throwable: Throwable) : CropSourcePreparation
    }

    private data class VideoInfo(
        val width: Int,
        val height: Int,
        val bitrate: Int?
    )

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "webm", "mkv", "mov", "avi", "3gp", "3gpp", "mpeg", "mpg", "ts", "m2ts", "mts"
        )
        private const val GIF_EXTENSION = "gif"
        private const val WEBP_EXTENSION = "webp"

        private const val VIDEO_PREVIEW_MAX_WIDTH = 1600
        private const val VIDEO_PREVIEW_MAX_HEIGHT = 900
        private const val VIDEO_PREVIEW_JPEG_QUALITY = 90

        private const val MAX_VIDEO_FILE_SIZE_BYTES = 120L * 1024 * 1024
        private const val MAX_VIDEO_DIMENSION = 2560
        private const val MAX_VIDEO_PIXELS = 2560L * 1440L
        private const val MAX_VIDEO_BITRATE = 20_000_000

        private const val STATE_SOURCE_URI = "state_source_uri"
        private const val STATE_TARGET_WIDTH = "state_target_width"
        private const val STATE_TARGET_HEIGHT = "state_target_height"
        private const val STATE_CROP_LAUNCHED = "state_crop_launched"
        private const val STATE_IS_VIDEO_SOURCE = "state_is_video_source"
        private const val STATE_TEMP_OUTPUT_PATH = "state_temp_output_path"
        private const val STATE_TEMP_VIDEO_FRAME_PATH = "state_temp_video_frame_path"
        private const val STATE_TEMP_IMAGE_SOURCE_PATH = "state_temp_image_source_path"
    }
}
