package org.renpy.android

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyIfAvailable(this)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    private fun updateBaseContextLocale(context: Context): Context {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val language = prefs.getString("language", "English") ?: "English"
        val locale = getLocaleFromLanguage(language)
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun getLocaleFromLanguage(language: String): Locale {
        return when (language) {
            "Español" -> Locale("es")
            "Português" -> Locale("pt")
            else -> Locale.ENGLISH
        }
    }
}