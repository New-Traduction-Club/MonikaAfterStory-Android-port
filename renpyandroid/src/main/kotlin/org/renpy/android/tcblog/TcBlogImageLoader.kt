package org.renpy.android.tcblog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object TcBlogImageLoader {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val memoryCache: LruCache<String, Bitmap> = run {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSizeKb = (maxMemoryKb / 8).coerceAtLeast(1024 * 8)
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    fun loadImage(
        context: Context,
        url: String,
        imageView: ImageView,
        progressBar: ProgressBar? = null,
        reqWidth: Int = 1080,
        reqHeight: Int = 800,
        onLoaded: (() -> Unit)? = null
    ) {
        if (url.isBlank()) {
            progressBar?.visibility = View.GONE
            return
        }

        imageView.tag = url

        val cachedBitmap = memoryCache.get(url)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            progressBar?.visibility = View.GONE
            onLoaded?.invoke()
            return
        }

        progressBar?.visibility = View.VISIBLE

        scope.launch {
            val diskFile = withContext(Dispatchers.IO) {
                getOrDownloadImage(context, url)
            }

            if (diskFile != null && diskFile.exists() && imageView.tag == url) {
                val isGif = withContext(Dispatchers.IO) { isGifFile(diskFile) }

                if (isGif) {
                    try {
                        val gifDrawable = GifDrawable(diskFile).apply { setLoopCount(0) }
                        if (imageView.tag == url) {
                            imageView.setImageDrawable(gifDrawable)
                            imageView.alpha = 0f
                            imageView.animate().alpha(1f).setDuration(150).start()
                            progressBar?.visibility = View.GONE
                            onLoaded?.invoke()
                        }
                        return@launch
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val bitmap = withContext(Dispatchers.IO) {
                    decodeSampledBitmap(diskFile, reqWidth, reqHeight)
                }

                if (bitmap != null && imageView.tag == url) {
                    memoryCache.put(url, bitmap)
                    imageView.alpha = 0f
                    imageView.setImageBitmap(bitmap)
                    imageView.animate().alpha(1f).setDuration(150).start()
                    progressBar?.visibility = View.GONE
                    onLoaded?.invoke()
                } else if (imageView.tag == url) {
                    progressBar?.visibility = View.GONE
                }
            } else if (imageView.tag == url) {
                progressBar?.visibility = View.GONE
            }
        }
    }

    fun getCachedFile(context: Context, url: String): File? {
        val file = getDiskFile(context, url)
        return if (file.exists()) file else null
    }

    private fun getDiskFile(context: Context, url: String): File {
        val cacheDir = File(context.cacheDir, "tc_blog_cache/images").apply { mkdirs() }
        val hash = hashUrl(url)
        val ext = if (url.contains(".gif", ignoreCase = true)) ".gif" else ".img"
        return File(cacheDir, "$hash$ext")
    }

    private fun getOrDownloadImage(context: Context, urlString: String): File? {
        val targetFile = getDiskFile(context, urlString)
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", "MonikaAfterStory-AndroidPort/1.0")
            }

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    if (tempFile.renameTo(targetFile)) {
                        return targetFile
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
            if (tempFile.exists()) tempFile.delete()
        }

        return if (targetFile.exists() && targetFile.length() > 0) targetFile else null
    }

    private fun isGifFile(file: File): Boolean {
        if (!file.exists() || file.length() < 6) return false
        try {
            FileInputStream(file).use { stream ->
                val header = ByteArray(6)
                val read = stream.read(header)
                if (read == 6) {
                    val headerStr = String(header, Charsets.US_ASCII)
                    return headerStr.startsWith("GIF87a") || headerStr.startsWith("GIF89a")
                }
            }
        } catch (e: Exception) {
        }
        return false
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            return BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun hashUrl(url: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(url.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }
}
