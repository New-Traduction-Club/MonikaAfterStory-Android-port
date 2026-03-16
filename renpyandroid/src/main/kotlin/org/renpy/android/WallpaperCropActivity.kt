package org.renpy.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.DisplayMetrics
import android.view.Display
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.canhub.cropper.CropException
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.max

class WallpaperCropActivity : BaseActivity() {

    private var sourceUri: Uri? = null
    private var preparedSourceUri: Uri? = null
    private var tempOutputFile: File? = null

    private var targetWidth = 0
    private var targetHeight = 0
    private var cropLaunched = false

    private var tempSourceFile: File? = null

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cropResult = extractCropResult(result.data)
        val outputUri = cropResult?.uriContent
        val error = cropResult?.error

        if (result.resultCode == Activity.RESULT_OK && outputUri != null && error == null) {
            applyCroppedWallpaper(outputUri)
            return@registerForActivityResult
        }

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

        preparedSourceUri = savedInstanceState?.getString(STATE_PREPARED_SOURCE_URI)?.let(Uri::parse)
        targetWidth = savedInstanceState?.getInt(STATE_TARGET_WIDTH) ?: 0
        targetHeight = savedInstanceState?.getInt(STATE_TARGET_HEIGHT) ?: 0
        cropLaunched = savedInstanceState?.getBoolean(STATE_CROP_LAUNCHED, false) ?: false
        savedInstanceState?.getString(STATE_TEMP_SOURCE_PATH)?.let { tempSourceFile = File(it) }
        savedInstanceState?.getString(STATE_TEMP_OUTPUT_PATH)?.let { tempOutputFile = File(it) }

        if (targetWidth <= 0 || targetHeight <= 0) {
            val (resolvedWidth, resolvedHeight) = resolveTargetWallpaperSize()
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

        try {
            val localSource = preparedSourceUri ?: copySourceToCache(source).also { preparedSourceUri = it }
            val outputFile = tempOutputFile ?: File(
                cacheDir,
                "wallpaper_crop_result_${System.currentTimeMillis()}.png"
            ).also { tempOutputFile = it }
            val outputUri = Uri.fromFile(outputFile)

            val ratio = simplifyRatio(targetWidth, targetHeight)
            val cropOptions = CropImageOptions(
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

            cropLaunched = true
            val cropIntent = Intent(this, FullscreenCropImageActivity::class.java).apply {
                putExtra(
                    CropImage.CROP_IMAGE_EXTRA_BUNDLE,
                    Bundle(2).apply {
                        putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, localSource)
                        putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, cropOptions)
                    }
                )
            }
            cropLauncher.launch(cropIntent)
        } catch (e: IOException) {
            showCropError(e)
            finish()
        } catch (e: SecurityException) {
            showCropError(e)
            finish()
        }
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

    private fun applyCroppedWallpaper(resultUri: Uri) {
        val decoded = try {
            decodeBitmap(resultUri, targetWidth, targetHeight)
        } catch (e: IOException) {
            showCropError(e)
            finish()
            return
        }

        if (decoded == null) {
            InAppNotifier.show(this, getString(R.string.viewer_error_image_decode), true)
            finish()
            return
        }

        val finalBitmap = if (decoded.width != targetWidth || decoded.height != targetHeight) {
            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also { decoded.recycle() }
        } else {
            decoded
        }

        try {
            val name = "wallpaper_${System.currentTimeMillis()}.png"
            WallpaperManager.saveWallpaper(this, finalBitmap, name)
            WallpaperManager.setActive(this, name)
            window?.decorView?.rootView?.let { WallpaperManager.applyWallpaper(this, it) }
            InAppNotifier.show(this, getString(R.string.wallpaper_applied))
        } catch (e: RuntimeException) {
            showCropError(e)
        } finally {
            finalBitmap.recycle()
            finish()
        }
    }

    @Throws(IOException::class)
    private fun decodeBitmap(uri: Uri, desiredWidth: Int, desiredHeight: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                decoder.setTargetSize(desiredWidth, desiredHeight)
            }
        }
        return decodeSampledBitmapLegacy(uri, desiredWidth, desiredHeight)
    }

    @Throws(IOException::class)
    private fun decodeSampledBitmapLegacy(uri: Uri, desiredWidth: Int, desiredHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

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

    @Throws(IOException::class)
    private fun copySourceToCache(uri: Uri): Uri {
        val extension = guessExtension(uri)
        val file = File(cacheDir, "wallpaper_crop_source_${System.currentTimeMillis()}.$extension")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to open source image")
        tempSourceFile = file
        return Uri.fromFile(file)
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

    private fun resolveTargetWallpaperSize(): Pair<Int, Int> {
        val orientation = resources.configuration.orientation
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
                val shortSide = minOf(mode.physicalWidth, mode.physicalHeight)
                return if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    Pair(max(longSide, 1), max(shortSide, 1))
                } else {
                    Pair(max(shortSide, 1), max(longSide, 1))
                }
            }

            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(realMetrics)
            val longSide = max(realMetrics.widthPixels, realMetrics.heightPixels)
            val shortSide = minOf(realMetrics.widthPixels, realMetrics.heightPixels)
            return if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                Pair(max(longSide, 1), max(shortSide, 1))
            } else {
                Pair(max(shortSide, 1), max(longSide, 1))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return Pair(max(bounds.width(), 1), max(bounds.height(), 1))
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Pair(max(metrics.widthPixels, 1), max(metrics.heightPixels, 1))
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SOURCE_URI, sourceUri?.toString())
        outState.putString(STATE_PREPARED_SOURCE_URI, preparedSourceUri?.toString())
        outState.putInt(STATE_TARGET_WIDTH, targetWidth)
        outState.putInt(STATE_TARGET_HEIGHT, targetHeight)
        outState.putBoolean(STATE_CROP_LAUNCHED, cropLaunched)
        outState.putString(STATE_TEMP_SOURCE_PATH, tempSourceFile?.absolutePath)
        outState.putString(STATE_TEMP_OUTPUT_PATH, tempOutputFile?.absolutePath)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            tempSourceFile?.let { if (it.exists()) it.delete() }
            tempOutputFile?.let { if (it.exists()) it.delete() }
        }
    }

    companion object {
        private const val STATE_SOURCE_URI = "state_source_uri"
        private const val STATE_PREPARED_SOURCE_URI = "state_prepared_source_uri"
        private const val STATE_TARGET_WIDTH = "state_target_width"
        private const val STATE_TARGET_HEIGHT = "state_target_height"
        private const val STATE_CROP_LAUNCHED = "state_crop_launched"
        private const val STATE_TEMP_SOURCE_PATH = "state_temp_source_path"
        private const val STATE_TEMP_OUTPUT_PATH = "state_temp_output_path"
    }
}
