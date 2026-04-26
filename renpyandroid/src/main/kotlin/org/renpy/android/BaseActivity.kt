package org.renpy.android

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    protected open val preferredOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        applyUserNightMode(this)
        OrientationPolicy.applyRequestedOrientation(this, preferredOrientation)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isChromeOsDevice()) {
            try {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            } catch (e: IllegalArgumentException) {
                Log.w("BaseActivity", "Unable to apply display cutout mode", e)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    private fun updateBaseContextLocale(context: Context): Context {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val language = prefs.getString("language", "English") ?: "English"
        
        val locale = if (cachedLanguage == language && cachedLocale != null) {
            cachedLocale!!
        } else {
            val l = getLocaleFromLanguage(language)
            cachedLanguage = language
            cachedLocale = l
            Locale.setDefault(l)
            l
        }

        val config = context.resources.configuration
        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }

        if (currentLocale == locale) {
            return context
        }

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

    protected fun isChromeOsDevice(): Boolean {
        return packageManager.hasSystemFeature("org.chromium.arc") ||
            packageManager.hasSystemFeature("org.chromium.arc.device_management")
    }

    companion object {
        const val PREFS_NAME = "app_prefs"
        const val KEY_DARK_MODE = "dark_mode_enabled"
        const val KEY_WINDOW_MODE = "window_mode"

        private var cachedLanguage: String? = null
        private var cachedLocale: Locale? = null
        private var cachedDarkMode: Boolean? = null

        fun applyUserNightMode(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isDark = prefs.getBoolean(KEY_DARK_MODE, false)
            
            if (cachedDarkMode == isDark) return
            cachedDarkMode = isDark
            
            val mode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            if (AppCompatDelegate.getDefaultNightMode() != mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
        
        fun clearCache() {
            cachedLanguage = null
            cachedLocale = null
            cachedDarkMode = null
        }
    }
}
