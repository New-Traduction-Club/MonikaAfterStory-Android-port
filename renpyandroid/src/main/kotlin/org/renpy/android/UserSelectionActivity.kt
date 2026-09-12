package org.renpy.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import android.widget.Button
import android.widget.ImageView
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

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateBattery(it) }
        }
    }

    private var clockJob: Job? = null

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

        val root = findViewById<View>(R.id.userSelectionRoot)
        WallpaperManager.applyWallpaper(this, root)
        blurUserMas.setupWith(root)
        blurUserRenpy.setupWith(root)

        setupEdgeToEdgeInsets()
        setupProfileInteractions()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        enableImmersiveFullscreen()
        window.decorView.post {
            enableImmersiveFullscreen()
        }
    }

    private fun setupEdgeToEdgeInsets() {
        val content = findViewById<View>(R.id.userSelectionContent)
        val initialLeft = content.paddingLeft
        val initialTop = content.paddingTop
        val initialRight = content.paddingRight
        val initialBottom = content.paddingBottom

        val root = findViewById<View>(R.id.userSelectionRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val cutout = windowInsets.displayCutout

            val safeLeft = Math.max(cutoutInsets.left, cutout?.safeInsetLeft ?: 0)
            val safeRight = Math.max(cutoutInsets.right, cutout?.safeInsetRight ?: 0)

            content.setPadding(
                initialLeft + safeLeft,
                initialTop,
                initialRight + safeRight,
                initialBottom
            )

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
                val intent = Intent(this@UserSelectionActivity, LauncherActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra(LauncherActivity.EXTRA_FROM_LOGIN, true)
                }
                startActivity(intent)
                applyFadeTransition()
                finish()
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
        enableImmersiveFullscreen()
        findViewById<View>(R.id.userSelectionRoot)?.let { ViewCompat.requestApplyInsets(it) }
        startClock()
        blurUserMas.refreshBlur()
        blurUserRenpy.refreshBlur()
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
}
