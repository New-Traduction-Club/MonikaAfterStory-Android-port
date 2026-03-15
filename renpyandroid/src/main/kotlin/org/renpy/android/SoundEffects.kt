package org.renpy.android

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Shared click sound helper backed by a single SoundPool instance.
 */
object SoundEffects {
    private var soundPool: SoundPool? = null
    private var loadedSoundId: Int = 0
    private var currentEffect: String = ""

    /**
     * Prepare SoundPool and load the preferred effect if needed.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (soundPool == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                .setMaxStreams(2)
                .build()
        }

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val effect = prefs.getString("sound_effect", "default") ?: "default"
        if (effect != currentEffect) {
            loadEffect(context, effect)
        }
    }

    /**
     * Play the current click sound. No-op if nothing is loaded.
     */
    fun playClick(context: Context) {
        initialize(context)
        val pool = soundPool ?: return
        if (loadedSoundId != 0) {
            pool.play(loadedSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    @Synchronized
    private fun loadEffect(context: Context, effect: String) {
        val pool = soundPool ?: return

        if (loadedSoundId != 0) {
            pool.unload(loadedSoundId)
            loadedSoundId = 0
        }

        val resName = if (effect == "reimagined") "taskbar_click_reimagined" else "taskbar_click_default"
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)

        if (resId != 0) {
            loadedSoundId = pool.load(context, resId, 1)
            currentEffect = effect
        }
    }
}
