package org.renpy.android

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserSelectionActivity : BaseActivity() {

    override val preferredOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val language = prefs.getString("language", "English") ?: "English"
        val locale = when (language) {
            "Español" -> Locale("es")
            "Português" -> Locale("pt")
            else -> Locale.ENGLISH
        }
        Locale.setDefault(locale)

        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val localeContext = newBase.createConfigurationContext(config)

        val metrics = localeContext.resources.displayMetrics
        val virtualHeight = 500f
        val rawHeight = Math.min(metrics.widthPixels, metrics.heightPixels)
        val targetDensity = rawHeight / virtualHeight
        val targetDensityDpi = (targetDensity * DisplayMetrics.DENSITY_DEFAULT).toInt()

        val dpiConfig = Configuration(localeContext.resources.configuration)
        dpiConfig.densityDpi = targetDensityDpi
        dpiConfig.fontScale = 1.0f

        val finalContext = localeContext.createConfigurationContext(dpiConfig)
        super.attachBaseContext(finalContext)
    }

    private enum class UserProfile {
        NONE,
        MAS,
        RENPY_LAUNCHER
    }

    private var selectedProfile: UserProfile = UserProfile.NONE

    private lateinit var cardUserMas: MaterialCardView
    private lateinit var cardUserRenpy: MaterialCardView
    private lateinit var blurUserMas: BlurGlassView
    private lateinit var blurUserRenpy: BlurGlassView
    private lateinit var tvUserDescription: TextView
    private lateinit var btnLogIn: Button
    private lateinit var spinnerLogIn: WindowsSpinnerView
    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var ivBattery: ImageView
    private lateinit var tvBatteryPercentage: TextView
    private lateinit var layoutLanguageContainer: LinearLayout
    private lateinit var cardLanguageMenu: MaterialCardView
    private lateinit var blurLanguageMenu: BlurGlassView
    private lateinit var cardLanguageButton: MaterialCardView
    private lateinit var blurLanguageButton: BlurGlassView
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var ivLanguageChevron: ImageView
    private lateinit var viewLanguageDismissOverlay: View
    private lateinit var ivCheckEnglish: ImageView
    private lateinit var ivCheckSpanish: ImageView
    private lateinit var ivCheckPortuguese: ImageView
    private var isLanguageMenuOpen: Boolean = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateBattery(it) }
        }
    }

    private var clockJob: Job? = null

    private val setupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            finish()
        }
    }

    companion object {
        private const val TAG = "UserSelectionActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyIfAvailable(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !isChromeOsDevice()) {
            try {
                window.attributes = window.attributes.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        @Suppress("DEPRECATION")
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to apply display cutout mode", e)
            }
        }
        setContentView(R.layout.activity_user_selection)

        cardUserMas = findViewById(R.id.cardUserMas)
        cardUserRenpy = findViewById(R.id.cardUserRenpy)
        blurUserMas = findViewById(R.id.blurUserMas)
        blurUserRenpy = findViewById(R.id.blurUserRenpy)
        tvUserDescription = findViewById(R.id.tvUserDescription)
        btnLogIn = findViewById(R.id.btnLogIn)
        spinnerLogIn = findViewById(R.id.spinnerLogIn)
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        ivBattery = findViewById(R.id.ivBattery)
        tvBatteryPercentage = findViewById(R.id.tvBatteryPercentage)
        layoutLanguageContainer = findViewById(R.id.layoutLanguageContainer)
        cardLanguageMenu = findViewById(R.id.cardLanguageMenu)
        blurLanguageMenu = findViewById(R.id.blurLanguageMenu)
        cardLanguageButton = findViewById(R.id.cardLanguageButton)
        blurLanguageButton = findViewById(R.id.blurLanguageButton)
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)
        ivLanguageChevron = findViewById(R.id.ivLanguageChevron)
        viewLanguageDismissOverlay = findViewById(R.id.viewLanguageDismissOverlay)
        ivCheckEnglish = findViewById(R.id.ivCheckEnglish)
        ivCheckSpanish = findViewById(R.id.ivCheckSpanish)
        ivCheckPortuguese = findViewById(R.id.ivCheckPortuguese)

        val root = findViewById<View>(R.id.userSelectionRoot)
        WallpaperManager.applyWallpaper(this, root, WallpaperManager.WallpaperTarget.LOCKSCREEN)
        blurUserMas.setupWith(root)
        blurUserRenpy.setupWith(root)
        blurLanguageMenu.setupWith(root)
        blurLanguageButton.setupWith(root)

        setupEdgeToEdgeInsets()
        setupProfileInteractions()
        setupLanguageSelector()
        selectProfile(UserProfile.RENPY_LAUNCHER)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isLanguageMenuOpen) {
                    closeLanguageMenuAnimated()
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        enableImmersiveFullscreen()
        window.decorView.post {
            enableImmersiveFullscreen()
        }

        playEntranceAnimation()
    }

    private fun playEntranceAnimation() {
        val header = findViewById<View>(R.id.headerContainer)
        header?.alpha = 0f
        header?.translationY = -20f * resources.displayMetrics.density
        header?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(500)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()

        val cards = listOf(cardUserMas, cardUserRenpy).filter { it.visibility == View.VISIBLE }
        val distance = 30f * resources.displayMetrics.density
        for ((index, card) in cards.withIndex()) {
            card.alpha = 0f
            card.translationY = distance
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(100L * index)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        val langButton = findViewById<View>(R.id.cardLanguageButton)
        langButton?.alpha = 0f
        langButton?.animate()
            ?.alpha(1f)
            ?.setDuration(400)
            ?.setStartDelay(200)
            ?.start()
    }

    private fun setupEdgeToEdgeInsets() {
        val content = findViewById<View>(R.id.userSelectionContent)
        val initialLeft = content.paddingLeft
        val initialTop = content.paddingTop
        val initialRight = content.paddingRight
        val initialBottom = content.paddingBottom

        val langContainer = findViewById<View>(R.id.layoutLanguageContainer)
        val langMarginParams = langContainer.layoutParams as? FrameLayout.LayoutParams
        val initialLangMarginEnd = langMarginParams?.marginEnd ?: 0
        val initialLangMarginBottom = langMarginParams?.bottomMargin ?: 0

        val root = findViewById<View>(R.id.userSelectionRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val cutout = windowInsets.displayCutout

            val safeLeft = Math.max(cutoutInsets.left, cutout?.safeInsetLeft ?: 0)
            val safeRight = Math.max(cutoutInsets.right, cutout?.safeInsetRight ?: 0)
            val safeBottom = Math.max(cutoutInsets.bottom, cutout?.safeInsetBottom ?: 0)

            content.setPadding(
                initialLeft + safeLeft,
                initialTop,
                initialRight + safeRight,
                initialBottom
            )

            langMarginParams?.let { params ->
                params.marginEnd = initialLangMarginEnd + safeRight
                params.bottomMargin = initialLangMarginBottom + safeBottom
                langContainer.layoutParams = params
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupProfileInteractions() {
        cardUserMas.setOnClickListener {
            selectProfile(UserProfile.MAS)
        }

        cardUserRenpy.setOnClickListener {
            selectProfile(UserProfile.RENPY_LAUNCHER)
        }

        btnLogIn.setOnClickListener {
            if (selectedProfile == UserProfile.NONE) return@setOnClickListener
            SoundEffects.playClick(this)

            btnLogIn.isClickable = false
            btnLogIn.text = ""
            spinnerLogIn.bringToFront()
            spinnerLogIn.visibility = View.VISIBLE

            lifecycleScope.launch {
                delay(1800L)
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val isSetupCompleted = prefs.getBoolean("is_setup_completed", false)

                val profileName = if (selectedProfile == UserProfile.MAS) {
                    ProfileNavigationHelper.PROFILE_MAS
                } else {
                    ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER
                }

                val target = ProfileNavigationHelper.determineLoginTarget(profileName, isSetupCompleted)
                if (target == ProfileNavigationHelper.NavigationTarget.SETUP) {
                    spinnerLogIn.visibility = View.GONE
                    btnLogIn.isClickable = true
                    btnLogIn.setText(R.string.user_selection_btn_login)

                    val intent = Intent(this@UserSelectionActivity, SetupActivity::class.java)
                    setupLauncher.launch(intent)
                    applyFadeTransition()
                } else {
                    prefs.edit().putString("active_user_profile", profileName).apply()
                    AutoLoginHelper.recordLastUsedProfile(this@UserSelectionActivity, profileName)

                    val intent = Intent(this@UserSelectionActivity, LauncherActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra(LauncherActivity.EXTRA_FROM_LOGIN, true)
                        putExtra(LauncherActivity.EXTRA_LOGGED_IN_PROFILE, profileName)
                    }
                    startActivity(intent)
                    applyFadeTransition()
                    finish()
                }
            }
        }
    }

    private fun selectProfile(profile: UserProfile) {
        if (selectedProfile == profile) return
        SoundEffects.playClick(this)
        selectedProfile = profile

        val strokeWidthPx = (3 * resources.displayMetrics.density).toInt()
        val activeColor = ContextCompat.getColor(this, R.color.colorPrimary)

        when (profile) {
            UserProfile.MAS -> {
                cardUserMas.strokeWidth = strokeWidthPx
                cardUserMas.strokeColor = activeColor
                cardUserMas.cardElevation = (8 * resources.displayMetrics.density)

                cardUserRenpy.strokeWidth = 0
                cardUserRenpy.cardElevation = (4 * resources.displayMetrics.density)

                updateDescriptionWithFade(R.string.user_selection_desc_mas)
            }

            UserProfile.RENPY_LAUNCHER -> {
                cardUserRenpy.strokeWidth = strokeWidthPx
                cardUserRenpy.strokeColor = activeColor
                cardUserRenpy.cardElevation = (8 * resources.displayMetrics.density)

                cardUserMas.strokeWidth = 0
                cardUserMas.cardElevation = (4 * resources.displayMetrics.density)

                updateDescriptionWithFade(R.string.user_selection_desc_renpy)
            }

            UserProfile.NONE -> {
                cardUserMas.strokeWidth = 0
                cardUserRenpy.strokeWidth = 0
                tvUserDescription.text = ""
            }
        }

        if (!btnLogIn.isEnabled && selectedProfile != UserProfile.NONE) {
            btnLogIn.isEnabled = true
        }
    }

    private fun updateDescriptionWithFade(textResId: Int) {
        val targetText = getString(textResId)
        val currentText = tvUserDescription.text?.toString() ?: ""
        if (currentText == targetText && tvUserDescription.alpha > 0f) return

        if (currentText.isEmpty() || tvUserDescription.alpha == 0f) {
            tvUserDescription.text = targetText
            tvUserDescription.alpha = 0f
            tvUserDescription.animate()
                .alpha(1f)
                .setDuration(250)
                .start()
        } else {
            tvUserDescription.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    tvUserDescription.text = targetText
                    tvUserDescription.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }
    }

    override fun onResume() {
        super.onResume()

        btnLogIn.isClickable = true
        btnLogIn.setText(R.string.user_selection_btn_login)
        spinnerLogIn.visibility = View.GONE

        enableImmersiveFullscreen()
        findViewById<View>(R.id.userSelectionRoot)?.let { root ->
            WallpaperManager.applyWallpaper(this, root, WallpaperManager.WallpaperTarget.LOCKSCREEN)
            ViewCompat.requestApplyInsets(root)
        }
        startClock()
        blurUserMas.refreshBlur()
        blurUserRenpy.refreshBlur()
        blurLanguageMenu.refreshBlur()
        blurLanguageButton.refreshBlur()
        registerBatteryReceiver()
    }

    override fun onPause() {
        super.onPause()
        clockJob?.cancel()
        clockJob = null
        unregisterBatteryReceiver()
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialStatus = registerReceiver(batteryReceiver, filter)
        if (initialStatus != null) {
            updateBattery(initialStatus)
        }
    }

    private fun unregisterBatteryReceiver() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        val state = BatteryUtils.calculateBatteryState(level, scale, status)
        if (state.percentage >= 0) {
            tvBatteryPercentage.text = "${state.percentage}%"
            ivBattery.setImageResource(state.iconRes)
            ivBattery.visibility = View.VISIBLE
            tvBatteryPercentage.visibility = View.VISIBLE

            val tintColor = when {
                state.isCharging -> ContextCompat.getColor(this, R.color.colorPrimary)
                state.isLow -> Color.parseColor("#FF6B6B")
                else -> Color.parseColor("#B0FFFFFF")
            }
            ivBattery.setColorFilter(tintColor)
            tvBatteryPercentage.setTextColor(tintColor)
        } else {
            ivBattery.visibility = View.GONE
            tvBatteryPercentage.visibility = View.GONE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
            findViewById<View>(R.id.userSelectionRoot)?.let { ViewCompat.requestApplyInsets(it) }
        }
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = lifecycleScope.launch {
            while (true) {
                updateTimeAndDate()
                delay(1000)
            }
        }
    }

    private fun updateTimeAndDate() {
        val now = Date()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

        tvClock.text = timeFormat.format(now)
        tvDate.text = dateFormat.format(now)
    }

    private fun enableImmersiveFullscreen() {
        if (isChromeOsDevice()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun setupLanguageSelector() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentLang = prefs.getString("language", "English") ?: "English"
        tvCurrentLanguage.text = ProfileNavigationHelper.getLanguageShortCode(currentLang)
        updateLanguageCheckmarks(currentLang)

        cardLanguageButton.setOnClickListener {
            SoundEffects.playClick(this)
            if (isLanguageMenuOpen) {
                closeLanguageMenuAnimated()
            } else {
                openLanguageMenuAnimated()
            }
        }

        viewLanguageDismissOverlay.setOnClickListener {
            closeLanguageMenuAnimated()
        }

        findViewById<View>(R.id.itemLangEnglish).setOnClickListener {
            selectLanguage("English")
        }

        findViewById<View>(R.id.itemLangSpanish).setOnClickListener {
            selectLanguage("Español")
        }

        findViewById<View>(R.id.itemLangPortuguese).setOnClickListener {
            selectLanguage("Português")
        }
    }

    private fun updateLanguageCheckmarks(selectedLang: String) {
        ivCheckEnglish.visibility = if (selectedLang == "English") View.VISIBLE else View.GONE
        ivCheckSpanish.visibility = if (selectedLang == "Español") View.VISIBLE else View.GONE
        ivCheckPortuguese.visibility = if (selectedLang == "Português") View.VISIBLE else View.GONE
    }

    private fun openLanguageMenuAnimated() {
        if (isLanguageMenuOpen) return
        isLanguageMenuOpen = true
        viewLanguageDismissOverlay.visibility = View.VISIBLE

        cardLanguageMenu.clearAnimation()
        cardLanguageMenu.visibility = View.INVISIBLE
        cardLanguageMenu.post {
            val slideDistance = resources.displayMetrics.density * 24f
            cardLanguageMenu.translationY = slideDistance
            cardLanguageMenu.alpha = 0f
            cardLanguageMenu.visibility = View.VISIBLE

            cardLanguageMenu.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()

            ivLanguageChevron.animate()
                .rotation(0f)
                .setDuration(220)
                .start()
        }
    }

    private fun closeLanguageMenuAnimated(onEnd: (() -> Unit)? = null) {
        if (!isLanguageMenuOpen && cardLanguageMenu.visibility != View.VISIBLE) {
            onEnd?.invoke()
            return
        }
        isLanguageMenuOpen = false
        viewLanguageDismissOverlay.visibility = View.GONE

        cardLanguageMenu.clearAnimation()
        val slideDistance = resources.displayMetrics.density * 18f

        cardLanguageMenu.animate()
            .translationY(slideDistance)
            .alpha(0f)
            .setDuration(160)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                cardLanguageMenu.visibility = View.GONE
                cardLanguageMenu.translationY = 0f
                onEnd?.invoke()
            }
            .start()

        ivLanguageChevron.animate()
            .rotation(180f)
            .setDuration(160)
            .start()
    }

    private fun selectLanguage(lang: String) {
        SoundEffects.playClick(this)
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentLang = prefs.getString("language", "English") ?: "English"

        if (lang == currentLang) {
            closeLanguageMenuAnimated()
            return
        }

        updateLanguageCheckmarks(lang)
        closeLanguageMenuAnimated {
            prefs.edit()
                .putString("language", lang)
                .putBoolean("setup_language_confirmed", true)
                .putBoolean("is_first_launch", false)
                .apply()

            val locale = when (lang) {
                "Español" -> Locale("es")
                "Português" -> Locale("pt")
                else -> Locale.ENGLISH
            }
            Locale.setDefault(locale)

            val intent = Intent(this@UserSelectionActivity, UserSelectionActivity::class.java)
            finish()
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
