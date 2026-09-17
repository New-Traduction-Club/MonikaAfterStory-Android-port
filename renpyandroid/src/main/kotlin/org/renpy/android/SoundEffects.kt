package org.renpy.android

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

/**
 * Shared click sound helper backed by a single SoundPool instance.
 */
object SoundEffects {
    private var soundPool: SoundPool? = null
    private var loadedSoundId: Int = 0
    private var currentEffect: String = ""
    private var pendingPreview: Boolean = false

    /**
     * Prepare SoundPool and load the preferred effect if needed.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (soundPool == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            soundPool = SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                .setMaxStreams(2)
                .build().apply {
                    setOnLoadCompleteListener { pool, sampleId, status ->
                        if (status == 0 && pendingPreview && sampleId == loadedSoundId) {
                            pendingPreview = false
                            pool.play(sampleId, 1f, 1f, 1, 0, 1f)
                        }
                    }
                }
        }

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val effect = prefs.getString("sound_effect", "default") ?: "default"
        if (effect != currentEffect) {
            loadEffect(context, effect)
        }
    }

    /**
     * Play the current click sound. No-op if nothing is loaded or muted.
     */
    fun playClick(context: Context) {
        initialize(context)
        val pool = soundPool ?: return
        if (loadedSoundId != 0) {
            pool.play(loadedSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun playPreview(context: Context) {
        initialize(context)
        val pool = soundPool ?: return
        if (loadedSoundId != 0) {
            val streamId = pool.play(loadedSoundId, 1f, 1f, 1, 0, 1f)
            if (streamId == 0) {
                pendingPreview = true
            }
        } else {
            pendingPreview = true
        }
    }

    @Synchronized
    fun reload(context: Context) {
        initialize(context)
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val effect = prefs.getString("sound_effect", "default") ?: "default"
        loadEffect(context, effect)
    }

    @Synchronized
    private fun loadEffect(context: Context, effect: String) {
        val pool = soundPool ?: return

        if (loadedSoundId != 0) {
            pool.unload(loadedSoundId)
            loadedSoundId = 0
        }

        currentEffect = effect

        when (effect) {
            "none" -> {
            }

            "custom" -> {
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val path = prefs.getString("custom_sound_path", null)
                if (path != null && File(path).exists()) {
                    loadedSoundId = pool.load(path, 1)
                } else {
                    loadEffect(context, "default")
                }
            }

            "reimagined" -> {
                val resId = context.resources.getIdentifier("taskbar_click_reimagined", "raw", context.packageName)
                if (resId != 0) {
                    loadedSoundId = pool.load(context, resId, 1)
                }
            }

            else -> {
                val resId = context.resources.getIdentifier("taskbar_click_default", "raw", context.packageName)
                if (resId != 0) {
                    loadedSoundId = pool.load(context, resId, 1)
                }
            }
        }
    }
}
