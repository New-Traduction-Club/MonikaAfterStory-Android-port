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
        
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        currentLanguage = prefs.getString("language", "English") ?: "English"
        currentSoundEffect = prefs.getString("sound_effect", "default") ?: "default"
        
        setTitle(R.string.settings_title)
        setupLanguageUI()
        setupSoundUI(prefs)
        setupNetworkUI(prefs)
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

    private fun setupSoundUI(prefs: android.content.SharedPreferences) {
        binding.txtCurrentSoundEffect.text = soundLabelFor(currentSoundEffect)

        binding.cardSoundEffect.setOnClickListener {
            showSoundEffectDialog(prefs)
        }
    }

    private fun showSoundEffectDialog(prefs: android.content.SharedPreferences) {
        val options = arrayOf(
            getString(R.string.settings_sound_effect_default),
            getString(R.string.settings_sound_effect_reimagined)
        )
        val values = arrayOf("default", "reimagined")
        val checkedIndex = values.indexOf(currentSoundEffect).takeIf { it >= 0 } ?: 0

        GameDialogBuilder(this)
            .setTitle(getString(R.string.settings_sound_effect_title))
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                val chosenValue = values.getOrNull(which) ?: "default"
                currentSoundEffect = chosenValue
                prefs.edit().putString("sound_effect", chosenValue).apply()
                binding.txtCurrentSoundEffect.text = soundLabelFor(chosenValue)
                SoundEffects.initialize(this)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun soundLabelFor(value: String): String {
        return when (value) {
            "reimagined" -> getString(R.string.settings_sound_effect_reimagined)
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
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("language", selectedLang)
                        .apply()
                    
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

    private fun createLanguageFile(language: String) {
        try {
            val gameDir = File(filesDir, "game")
            if (!gameDir.exists()) {
                gameDir.mkdirs()
            }

            gameDir.listFiles { file -> file.name.startsWith("language_") && file.name.endsWith(".txt") }
                ?.forEach { it.delete() }

            val langParam = if (language == "Español") "spanish" else "english"
            val langFile = File(gameDir, "language_$langParam.txt")
            langFile.createNewFile()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}