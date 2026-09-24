package org.renpy.android

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog

import org.renpy.android.databinding.SettingsActivityBinding
import java.io.File

class SettingsActivity : GameWindowActivity() {

    private lateinit var binding: SettingsActivityBinding
    private var currentLanguage: String = "English"
    private var currentSoundEffect: String = "default"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(BaseActivity.PREFS_NAME, MODE_PRIVATE)
        currentLanguage = prefs.getString("language", "English") ?: "English"
        currentSoundEffect = prefs.getString("sound_effect", "default") ?: "default"

        setTitle(R.string.settings_title)
        setupLanguageUI()
        setupSoundUI(prefs)
        setupAutoLoginUI(prefs)
        setupThemeUI(prefs)
        setupWindowModeUI()
        setupNetworkUI(prefs)
    }

    private fun setupAutoLoginUI(prefs: android.content.SharedPreferences) {
        val currentMode = AutoLoginHelper.getAutoLoginMode(this)
        binding.txtCurrentAutoLogin.text = AutoLoginHelper.getModeLabel(this, currentMode)

        binding.cardAutoLogin.setOnClickListener {
            showAutoLoginDialog(prefs)
        }
    }

    private fun showAutoLoginDialog(prefs: android.content.SharedPreferences) {
        val modes = AutoLoginHelper.MODES
        val labels = modes.map { AutoLoginHelper.getModeLabel(this, it) }.toTypedArray()
        val currentMode = AutoLoginHelper.getAutoLoginMode(this)
        val checkedIndex = modes.indexOf(currentMode).takeIf { it >= 0 } ?: 0

        GameDialogBuilder(this)
            .setTitle(getString(R.string.settings_auto_login_title))
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val chosenMode = modes.getOrNull(which) ?: AutoLoginHelper.MODE_DISABLED
                AutoLoginHelper.setAutoLoginMode(this, chosenMode)
                binding.txtCurrentAutoLogin.text = AutoLoginHelper.getModeLabel(this, chosenMode)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupLanguageUI() {
        binding.txtCurrentLanguage.text = currentLanguage

        binding.cardLanguage.setOnClickListener {
            showLanguageDialog()
        }
    }

    private fun setupNetworkUI(prefs: android.content.SharedPreferences) {
        val wifiOnly = prefs.getBoolean("wifi_only", false)
        binding.switchWifiOnly.isChecked = wifiOnly

        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wifi_only", isChecked).apply()
        }
    }

    private fun setupThemeUI(prefs: android.content.SharedPreferences) {
        val darkModeEnabled = prefs.getBoolean(BaseActivity.KEY_DARK_MODE, false)
        binding.switchDarkMode.isChecked = darkModeEnabled

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(BaseActivity.KEY_DARK_MODE, isChecked).apply()
            BaseActivity.clearCache()
            BaseActivity.applyUserNightMode(this)
            delegate.applyDayNight()
            recreate()
        }
    }

    private fun setupSoundUI(prefs: android.content.SharedPreferences) {
        binding.txtCurrentSoundEffect.text = soundLabelFor(currentSoundEffect)

        binding.cardSoundEffect.setOnClickListener {
            showSoundEffectDialog(prefs)
        }
    }

    private fun setupWindowModeUI() {
        binding.txtCurrentWindowMode.text = windowModeLabel()

        binding.cardWindowMode.setOnClickListener {
            showWindowModeChooser(recreateOnChange = true) {
                binding.txtCurrentWindowMode.text = windowModeLabel()
            }
        }
    }

    private val pickCustomSoundLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            handleCustomSoundSelected(uri)
        }
    }

    private fun handleCustomSoundSelected(uri: android.net.Uri) {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size > 1024 * 1024) {
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.settings_sound_effect_error_too_large),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            }
        } catch (e: Exception) {
        }

        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            if (durationMs > 5000L) {
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.settings_sound_effect_error_too_long),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return
            }
        } catch (e: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }

        val soundsDir = getExternalFilesDir("sounds") ?: File(filesDir, "sounds")
        if (!soundsDir.exists()) {
            soundsDir.mkdirs()
        }

        val originalName = queryFileName(uri) ?: "custom_click.ogg"
        val extension = originalName.substringAfterLast('.', "ogg").lowercase()
        val destFile = File(soundsDir, "custom_click.$extension")

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        totalBytes += bytesRead
                        if (totalBytes > 1024 * 1024) {
                            destFile.delete()
                            android.widget.Toast.makeText(
                                this,
                                getString(R.string.settings_sound_effect_error_too_large),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                }
            } ?: run {
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.settings_sound_effect_error_invalid),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                this,
                getString(R.string.settings_sound_effect_error_invalid),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val prefs = getSharedPreferences(BaseActivity.PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putString("sound_effect", "custom")
            .putString("custom_sound_path", destFile.absolutePath)
            .putString("custom_sound_name", originalName)
            .apply()

        currentSoundEffect = "custom"
        binding.txtCurrentSoundEffect.text = soundLabelFor("custom")
        SoundEffects.reload(this)
        SoundEffects.playPreview(this)
    }

    private fun queryFileName(uri: android.net.Uri): String? {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
            } catch (e: Exception) {
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    private fun showSoundEffectDialog(prefs: android.content.SharedPreferences) {
        val hasCustomSound = prefs.getString("custom_sound_path", null)?.let { File(it).exists() } == true
        val customOptionText = if (hasCustomSound) {
            getString(R.string.settings_sound_effect_custom_replace)
        } else {
            getString(R.string.settings_sound_effect_custom_select)
        }

        val options = arrayOf(
            getString(R.string.settings_sound_effect_default),
            getString(R.string.settings_sound_effect_reimagined),
            customOptionText,
            getString(R.string.settings_sound_effect_none)
        )
        val values = arrayOf("default", "reimagined", "custom", "none")
        val checkedIndex = values.indexOf(currentSoundEffect).takeIf { it >= 0 } ?: 0

        GameDialogBuilder(this)
            .setTitle(getString(R.string.settings_sound_effect_title))
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                when (which) {
                    2 -> {
                        if (currentSoundEffect == "custom" || !hasCustomSound) {
                            pickCustomSoundLauncher.launch("audio/*")
                            dialog.dismiss()
                        } else {
                            currentSoundEffect = "custom"
                            prefs.edit().putString("sound_effect", "custom").apply()
                            binding.txtCurrentSoundEffect.text = soundLabelFor("custom")
                            SoundEffects.reload(this)
                            SoundEffects.playPreview(this)
                            dialog.dismiss()
                        }
                    }
                    else -> {
                        val chosenValue = values.getOrNull(which) ?: "default"
                        currentSoundEffect = chosenValue
                        prefs.edit().putString("sound_effect", chosenValue).apply()
                        binding.txtCurrentSoundEffect.text = soundLabelFor(chosenValue)
                        SoundEffects.reload(this)
                        if (chosenValue != "none") {
                            SoundEffects.playPreview(this)
                        }
                        dialog.dismiss()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun soundLabelFor(value: String): String {
        val prefs = getSharedPreferences(BaseActivity.PREFS_NAME, MODE_PRIVATE)
        val customName = prefs.getString("custom_sound_name", null)
        return when (value) {
            "reimagined" -> getString(R.string.settings_sound_effect_reimagined)
            "none" -> getString(R.string.settings_sound_effect_none)
            "custom" -> {
                if (!customName.isNullOrEmpty()) {
                    "${getString(R.string.settings_sound_effect_custom)} ($customName)"
                } else {
                    getString(R.string.settings_sound_effect_custom)
                }
            }
            else -> getString(R.string.settings_sound_effect_default)
        }
    }

    private fun showLanguageDialog() {
        val languages = resources.getStringArray(R.array.languages)
        // Find current index
        var checkedItem = languages.indexOf(currentLanguage)
        if (checkedItem < 0) checkedItem = 0

        GameDialogBuilder(this)
            .setTitle(getString(R.string.select_language_title))
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val selectedLang = languages[which]
                if (selectedLang != currentLanguage) {
                    val prefs = getSharedPreferences(BaseActivity.PREFS_NAME, MODE_PRIVATE)
                    prefs.edit()
                        .putString("language", selectedLang)
                        .apply()

                    BaseActivity.clearCache()
                    createLanguageFile(selectedLang)
                    currentLanguage = selectedLang
                    binding.txtCurrentLanguage.text = currentLanguage

                    recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun windowModeLabel(): String {
        return when (getWindowMode()) {
            WindowMode.WINDOWED -> getString(R.string.window_mode_windowed)
            WindowMode.MAXIMIZED -> getString(R.string.window_mode_maximized)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            binding.txtCurrentWindowMode.text = windowModeLabel()
        }
    }

    private fun createLanguageFile(language: String) {
        try {
            val gameDir = File(filesDir, "monikaafterstory-masl-edition/game")
            if (!gameDir.exists()) {
                gameDir.mkdirs()
            }

            gameDir.listFiles { file -> file.name.startsWith("language_") && file.name.endsWith(".txt") }
                ?.forEach { it.delete() }

            val langParam = when (language) {
                "Español" -> "spanish"
                "Português" -> "portuguese"
                else -> "english"
            }
            val langFile = File(gameDir, "language_$langParam.txt")
            langFile.createNewFile()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
