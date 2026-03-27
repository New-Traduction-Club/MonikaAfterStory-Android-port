package org.renpy.android

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.renpy.android.databinding.ActivityFileViewerBinding
import java.io.File
import java.io.FileReader
import kotlin.math.max

class FileViewerActivity : GameWindowActivity() {

    private lateinit var binding: ActivityFileViewerBinding
    private lateinit var file: File
    
    // Audio Player
    private var mediaPlayer: MediaPlayer? = null
    private var isTrackingTouch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val filePath = intent.getStringExtra("file_path")
        if (filePath == null) {
            finish()
            return
        }

        file = File(filePath)
        
        setTitle(file.name)
        setTitle("${file.name} - ${file.length() / 1024} KB")

        loadFileContent()
    }

    private fun loadFileContent() {
        binding.progressBar.visibility = View.VISIBLE
        binding.imagePreview.visibility = View.GONE
        binding.textScrollView.visibility = View.GONE
        binding.audioContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val ext = file.extension.lowercase()

            val isImage = ext in listOf("png", "jpg", "jpeg", "webp")
            val isText = ext in listOf("rpy", "py", "json", "txt", "log", "xml", "md", "csv")
            val isAudio = ext in listOf("mp3", "ogg", "wav", "m4a")

            try {
                if (isImage) {
                    val bitmap = decodeSampledBitmap(file, 1920, 1080)
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        if (bitmap != null) {
                            binding.imagePreview.setImageBitmap(bitmap)
                            binding.imagePreview.visibility = View.VISIBLE
                        } else {
                            showError(getString(R.string.viewer_error_image_decode))
                        }
                    }
                } else if (isText) {
                    val maxSize = 1024 * 1024 * 5 // Max 5MB for text preview
                    if (file.length() > maxSize) {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            showError(getString(R.string.viewer_error_file_large))
                        }
                    } else {
                        val content = file.readText()
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.textPreview.text = content
                            binding.textScrollView.visibility = View.VISIBLE
                        }
                    }
                } else if (isAudio) {
                    withContext(Dispatchers.Main) {
                        setupAudioPlayer()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showError(getString(R.string.viewer_error_unsupported))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showError(getString(R.string.viewer_error_read_failed, e.message))
                }
            }
        }
    }

    private fun setupAudioPlayer() {
        binding.progressBar.visibility = View.GONE
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
                    if (fromUser) {
                        binding.audioCurrentTime.text = formatTime(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isTrackingTouch = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isTrackingTouch = false
                    seekBar?.let {
                        mediaPlayer?.seekTo(it.progress)
                    }
                }
            })
            
            // Coroutine to update SeekBar
            lifecycleScope.launch {
                while (isActive) {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying && !isTrackingTouch) {
                            binding.audioSeekBar.progress = player.currentPosition
                            binding.audioCurrentTime.text = formatTime(player.currentPosition)
                        }
                    }
                    delay(100) // update every 100ms
                }
            }

        } catch (e: Exception) {
            showError(getString(R.string.viewer_error_audio_failed, e.message))
            binding.audioContainer.visibility = View.GONE
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
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
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.errorContainer.visibility = View.VISIBLE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
