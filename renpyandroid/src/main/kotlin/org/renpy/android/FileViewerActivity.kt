package org.renpy.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.renpy.android.databinding.ActivityFileViewerBinding
import java.io.File
import java.nio.charset.Charset
import kotlin.math.max
import kotlin.math.min

class FileViewerActivity : GameWindowActivity() {

    companion object {
        private const val MAX_TEXT_PREVIEW_SIZE_BYTES = 8L * 1024L * 1024L

        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif")
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "webm", "mkv", "mov", "avi", "3gp", "3gpp", "mpeg", "mpg", "ts", "m2ts", "mts"
        )
        private val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "wav", "m4a", "flac", "aac", "opus")
        private val TEXT_EXTENSIONS = setOf(
            "rpy", "py", "kt", "kts", "java", "js", "ts", "tsx", "jsx", "json", "xml", "html", "htm",
            "css", "scss", "sass", "less", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
            "gradle", "md", "markdown", "txt", "csv", "log", "sh", "bat", "ps1", "lua", "rb", "php",
            "go", "rs", "c", "h", "cpp", "hpp", "cc", "cs", "swift", "sql"
        )
    }

    private lateinit var binding: ActivityFileViewerBinding
    private lateinit var file: File

    // Audio player
    private var mediaPlayer: MediaPlayer? = null
    private var isTrackingTouch = false

    // Video player (TextureView + MediaPlayer)
    private var videoTextureView: TextureView? = null
    private var videoPlayer: MediaPlayer? = null
    private var videoSurface: Surface? = null
    private var isVideoPrepared = false
    private var isVideoSeeking = false
    private var videoDurationMs: Int = 0
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filePath = intent.getStringExtra("file_path")
        if (filePath.isNullOrBlank()) {
            finish()
            return
        }

        file = File(filePath)
        if (!file.exists() || !file.isFile) {
            showError(getString(R.string.viewer_error_read_failed, "File not found"))
            return
        }

        setTitle("${file.name} - ${formatSize(file.length())}")
        loadFileContent()
    }

    private fun loadFileContent() {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            val ext = file.extension.lowercase()
            val preview = try {
                when {
                    ext in IMAGE_EXTENSIONS -> PreviewType.Image(decodeSampledBitmap(file, 1920, 1080))
                    ext in VIDEO_EXTENSIONS -> PreviewType.Video
                    ext in AUDIO_EXTENSIONS -> PreviewType.Audio
                    ext in TEXT_EXTENSIONS || isLikelyTextFile(file) -> PreviewType.Text(loadTextPreview(file))
                    else -> PreviewType.Unsupported
                }
            } catch (e: Exception) {
                PreviewType.Error(getString(R.string.viewer_error_read_failed, e.message))
            }

            withContext(Dispatchers.Main) {
                when (preview) {
                    is PreviewType.Image -> {
                        hideLoading()
                        if (preview.bitmap != null) {
                            binding.imagePreview.setImageBitmap(preview.bitmap)
                            binding.imagePreview.visibility = View.VISIBLE
                        } else {
                            showError(getString(R.string.viewer_error_image_decode))
                        }
                    }
                    is PreviewType.Text -> {
                        hideLoading()
                        if (preview.content != null) {
                            binding.textPreview.text = preview.content
                            binding.textScrollView.visibility = View.VISIBLE
                        } else {
                            showError(getString(R.string.viewer_error_file_large))
                        }
                    }
                    PreviewType.Audio -> setupAudioPlayer()
                    PreviewType.Video -> setupVideoPlayer()
                    PreviewType.Unsupported -> {
                        hideLoading()
                        showError(getString(R.string.viewer_error_unsupported))
                    }
                    is PreviewType.Error -> {
                        hideLoading()
                        showError(preview.message)
                    }
                }
            }
        }
    }

    private fun setupAudioPlayer() {
        hideLoading()
        binding.audioContainer.visibility = View.VISIBLE
        binding.audioTitle.text = file.name

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()

                binding.audioSeekBar.max = duration
                binding.audioTotalTime.text = formatTime(duration)

                setOnCompletionListener {
                    binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    binding.audioSeekBar.progress = 0
                    binding.audioCurrentTime.text = formatTime(0)
                }
            }

            binding.fabPlayPause.setOnClickListener {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                        binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    } else {
                        player.start()
                        binding.fabPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    }
                }
            }

            binding.audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) binding.audioCurrentTime.text = formatTime(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isTrackingTouch = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isTrackingTouch = false
                    seekBar?.let { mediaPlayer?.seekTo(it.progress) }
                }
            })

            lifecycleScope.launch {
                while (isActive) {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying && !isTrackingTouch) {
                            binding.audioSeekBar.progress = player.currentPosition
                            binding.audioCurrentTime.text = formatTime(player.currentPosition)
                        }
                    }
                    delay(120)
                }
            }
        } catch (e: Exception) {
            binding.audioContainer.visibility = View.GONE
            showError(getString(R.string.viewer_error_audio_failed, e.message))
        }
    }

    private fun setupVideoPlayer() {
        hideLoading()
        binding.videoContainer.visibility = View.VISIBLE
        binding.videoTitle.text = file.name
        binding.videoCurrentTime.text = formatTime(0)
        binding.videoTotalTime.text = formatTime(0)
        binding.videoSeekBar.progress = 0
        binding.videoSeekBar.max = 0
        updateVideoPlayPauseIcon(false)

        releaseVideoPlayer()

        val textureView = TextureView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isOpaque = true
        }
        videoTextureView = textureView
        binding.videoSurfaceContainer.removeAllViews()
        binding.videoSurfaceContainer.addView(textureView)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                startVideoPlayback(surface)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                applyVideoLayout()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseVideoPlayer()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }

        binding.videoPlayPause.setOnClickListener {
            val player = videoPlayer ?: return@setOnClickListener
            if (!isVideoPrepared) return@setOnClickListener
            if (player.isPlaying) {
                player.pause()
                updateVideoPlayPauseIcon(false)
            } else {
                player.start()
                updateVideoPlayPauseIcon(true)
            }
        }

        binding.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.videoCurrentTime.text = formatTime(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isVideoSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val target = seekBar?.progress ?: 0
                videoPlayer?.seekTo(target)
                isVideoSeeking = false
            }
        })

        lifecycleScope.launch {
            while (isActive) {
                val player = videoPlayer
                if (player != null && isVideoPrepared && !isVideoSeeking) {
                    val pos = player.currentPosition
                    binding.videoSeekBar.progress = pos
                    binding.videoCurrentTime.text = formatTime(pos)
                }
                delay(120)
            }
        }
    }

    private fun startVideoPlayback(surfaceTexture: SurfaceTexture) {
        releaseVideoPlayer()
        isVideoPrepared = false
        videoWidth = 0
        videoHeight = 0

        val player = MediaPlayer()
        val surface = Surface(surfaceTexture)
        videoPlayer = player
        videoSurface = surface

        try {
            player.setSurface(surface)
            player.setDataSource(file.absolutePath)
            player.isLooping = true
            player.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width
                videoHeight = height
                applyVideoLayout()
            }
            player.setOnPreparedListener { preparedPlayer ->
                isVideoPrepared = true
                videoDurationMs = max(preparedPlayer.duration, 0)
                binding.videoSeekBar.max = videoDurationMs
                binding.videoTotalTime.text = formatTime(videoDurationMs)
                videoWidth = max(preparedPlayer.videoWidth, videoWidth)
                videoHeight = max(preparedPlayer.videoHeight, videoHeight)
                applyVideoLayout()
                preparedPlayer.start()
                updateVideoPlayPauseIcon(true)
            }
            player.setOnErrorListener { _, _, _ ->
                showError(getString(R.string.viewer_error_video_failed, "Playback error"))
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            showError(getString(R.string.viewer_error_video_failed, e.message))
            releaseVideoPlayer()
        }
    }

    private fun applyVideoLayout() {
        val textureView = videoTextureView ?: return
        val container = binding.videoSurfaceContainer
        if (videoWidth <= 0 || videoHeight <= 0) return
        if (container.width <= 0 || container.height <= 0) return

        val viewWidth = container.width.toFloat()
        val viewHeight = container.height.toFloat()
        val scale = max(viewWidth / videoWidth.toFloat(), viewHeight / videoHeight.toFloat())
        val scaledWidth = max(1, (videoWidth * scale).toInt())
        val scaledHeight = max(1, (videoHeight * scale).toInt())

        val topLeftX = ((viewWidth - scaledWidth) / 2f).toInt()
        val topLeftY = ((viewHeight - scaledHeight) / 2f).toInt()

        val params = textureView.layoutParams
        params.width = scaledWidth
        params.height = scaledHeight
        textureView.layoutParams = params
        textureView.translationX = topLeftX.toFloat()
        textureView.translationY = topLeftY.toFloat()
    }

    private fun releaseVideoPlayer() {
        videoPlayer?.let { player ->
            try {
                player.setSurface(null)
            } catch (_: RuntimeException) {
            }
            try {
                if (player.isPlaying) player.stop()
            } catch (_: RuntimeException) {
            }
            try {
                player.reset()
            } catch (_: RuntimeException) {
            }
            try {
                player.release()
            } catch (_: RuntimeException) {
            }
        }
        videoPlayer = null
        videoSurface?.release()
        videoSurface = null
        isVideoPrepared = false
    }

    private fun updateVideoPlayPauseIcon(isPlaying: Boolean) {
        binding.videoPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.imagePreview.visibility = View.GONE
        binding.textScrollView.visibility = View.GONE
        binding.audioContainer.visibility = View.GONE
        binding.videoContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.imagePreview.visibility = View.GONE
        binding.textScrollView.visibility = View.GONE
        binding.audioContainer.visibility = View.GONE
        binding.videoContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.VISIBLE
        binding.textError.text = message
    }

    private fun loadTextPreview(file: File): String? {
        val size = file.length()
        if (size > MAX_TEXT_PREVIEW_SIZE_BYTES) return null

        val bytes = file.readBytes()
        return when {
            looksLikeBinary(bytes) -> null
            else -> decodeText(bytes)
        }
    }

    private fun isLikelyTextFile(file: File): Boolean {
        if (file.length() <= 0L) return true
        if (file.length() > MAX_TEXT_PREVIEW_SIZE_BYTES) return false
        val sampleSize = min(file.length().toInt(), 4096)
        val sample = ByteArray(sampleSize)
        file.inputStream().use { input ->
            val read = input.read(sample)
            if (read <= 0) return true
            return !looksLikeBinary(sample.copyOf(read))
        }
    }

    private fun looksLikeBinary(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        var controlCount = 0
        for (b in bytes) {
            val value = b.toInt() and 0xFF
            if (value == 0) return true
            if (value < 0x09 || (value in 0x0E..0x1F)) controlCount++
        }
        return controlCount > bytes.size / 8
    }

    private fun decodeText(bytes: ByteArray): String {
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                String(bytes, Charset.forName("ISO-8859-1"))
            } catch (_: Exception) {
                String(bytes)
            }
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
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

    private fun formatSize(size: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L
        return when {
            size < kb -> "$size B"
            size < mb -> "${size / kb} KB"
            size < gb -> String.format("%.1f MB", size / mb.toDouble())
            else -> String.format("%.1f GB", size / gb.toDouble())
        }
    }

    override fun onPause() {
        super.onPause()
        videoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                updateVideoPlayPauseIcon(false)
            }
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null

        releaseVideoPlayer()
        videoTextureView?.surfaceTextureListener = null
        binding.videoSurfaceContainer.removeAllViews()
        videoTextureView = null

        super.onDestroy()
    }

    private sealed interface PreviewType {
        data class Image(val bitmap: Bitmap?) : PreviewType
        data class Text(val content: String?) : PreviewType
        data object Audio : PreviewType
        data object Video : PreviewType
        data object Unsupported : PreviewType
        data class Error(val message: String) : PreviewType
    }
}
