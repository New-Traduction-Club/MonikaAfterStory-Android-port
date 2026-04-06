package org.renpy.android

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.cardview.widget.CardView
import android.widget.LinearLayout

abstract class GameWindowActivity : BaseActivity() {

    enum class WindowMode { MAXIMIZED, WINDOWED }

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        val metrics = newBase.resources.displayMetrics

        val prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val forcedMode = overrideWindowMode()
        val isWindowedPreferred = when (forcedMode) {
            WindowMode.WINDOWED -> true
            WindowMode.MAXIMIZED -> false
            null -> prefs.getString(KEY_WINDOW_MODE, null) == "windowed"
        }

        val virtualHeight = if (isWindowedPreferred) 580f else 490f
        val rawHeight = Math.min(metrics.widthPixels, metrics.heightPixels)
        val targetDensity = rawHeight / virtualHeight
        val targetDensityDpi = (targetDensity * DisplayMetrics.DENSITY_DEFAULT).toInt()
        
        config.densityDpi = targetDensityDpi
        config.fontScale = 1.0f
        
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    private lateinit var contentContainer: FrameLayout
    private lateinit var txtWindowTitle: TextView
    private lateinit var btnWindowClose: TextView
    private var windowRootLayout: ViewGroup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        applyImmersiveFullscreen()
        
        // Let derived activities set their own content via setContentView()
        overridePendingTransition(R.anim.window_fade_in, R.anim.window_fade_out)

        SoundEffects.initialize(this)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.window_fade_in, R.anim.window_fade_out)
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveFullscreen()
        }
    }

    private fun applyImmersiveFullscreen() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
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

    private fun setupWindowChrome(rootLayout: ViewGroup) {
        val headerLayout = rootLayout.findViewById<View>(R.id.headerLayout)
        val footerBar = rootLayout.findViewById<View>(R.id.footerBar)
        
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            val density = resources.displayMetrics.density
            val px16 = (16 * density).toInt()
            val px12 = (12 * density).toInt()
            val extraRightMargin = (24 * density).toInt()
            
            headerLayout.setPadding(
                px16,
                px12,
                px16 + extraRightMargin,
                px12
            )
            
            contentContainer.setPadding(
                0,
                0,
                extraRightMargin,
                0
            )
            
            val px24 = (24 * density).toInt()
            val layoutParams = footerBar.layoutParams
            layoutParams.height = px24 + systemInsets.bottom
            footerBar.layoutParams = layoutParams
            
            insets
        }
    }

    override fun setContentView(layoutResID: Int) {
        val rootLayout = layoutInflater.inflate(R.layout.layout_game_window_chrome, null) as ViewGroup
        contentContainer = rootLayout.findViewById(R.id.windowContent)
        txtWindowTitle = rootLayout.findViewById(R.id.txtWindowTitle)
        btnWindowClose = rootLayout.findViewById(R.id.btnWindowClose)
        windowRootLayout = rootLayout

        applyWindowMode(rootLayout)
        setupWindowChrome(rootLayout)

        // Inflate the child activity's layout into the container
        layoutInflater.inflate(layoutResID, contentContainer, true)

        btnWindowClose.setOnClickListener {
            SoundEffects.playClick(this)
            onBackPressed()
        }

        super.setContentView(rootLayout)
        promptWindowModeIfNeeded(rootLayout)
    }

    override fun setContentView(view: View?) {
        if (view == null) {
            super.setContentView(null)
            return
        }
        val rootLayout = layoutInflater.inflate(R.layout.layout_game_window_chrome, null) as ViewGroup
        contentContainer = rootLayout.findViewById(R.id.windowContent)
        txtWindowTitle = rootLayout.findViewById(R.id.txtWindowTitle)
        btnWindowClose = rootLayout.findViewById(R.id.btnWindowClose)
        windowRootLayout = rootLayout

        applyWindowMode(rootLayout)
        setupWindowChrome(rootLayout)

        contentContainer.addView(view)

        btnWindowClose.setOnClickListener {
            SoundEffects.playClick(this)
            onBackPressed()
        }

        super.setContentView(rootLayout)
        promptWindowModeIfNeeded(rootLayout)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        if (::txtWindowTitle.isInitialized) {
            txtWindowTitle.setText(titleId)
        }
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        if (::txtWindowTitle.isInitialized) {
            txtWindowTitle.text = title
        }
    }

    protected open fun overrideWindowMode(): WindowMode? = null

    protected fun getWindowMode(): WindowMode {
        overrideWindowMode()?.let { return it }
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_WINDOW_MODE, null)) {
            "windowed" -> WindowMode.WINDOWED
            else -> WindowMode.MAXIMIZED
        }
    }

    private fun setWindowMode(mode: WindowMode) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = if (mode == WindowMode.WINDOWED) "windowed" else "maximized"
        prefs.edit().putString(KEY_WINDOW_MODE, value).apply()
    }

    private fun applyWindowMode(rootLayout: ViewGroup) {
        val card = rootLayout.findViewById<CardView>(R.id.cardWindowContainer) ?: return
        val params = card.layoutParams as? ConstraintLayout.LayoutParams ?: return

        when (getWindowMode()) {
            WindowMode.MAXIMIZED -> {
                params.width = 0
                params.height = 0
                params.matchConstraintPercentWidth = 1.0f
                params.matchConstraintPercentHeight = 1.0f
                card.cardElevation = 0f
                card.radius = 0f
            }
            WindowMode.WINDOWED -> {
                params.width = 0
                params.height = 0
                params.matchConstraintPercentWidth = 0.8f
                params.matchConstraintPercentHeight = 0.9f
                card.cardElevation = dp(12f)
                card.radius = dp(8f)
            }
        }
        card.layoutParams = params
    }

    protected fun showWindowModeChooser(
        recreateOnChange: Boolean = true,
        onApplied: (() -> Unit)? = null
    ) {
        val rootLayout = windowRootLayout ?: return
        showWindowModeDialog(
            rootLayout,
            allowCancel = true,
            recreateOnChange = recreateOnChange,
            onApplied = onApplied
        )
    }

    private fun promptWindowModeIfNeeded(rootLayout: ViewGroup) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_WINDOW_MODE)) return

        showWindowModeDialog(
            rootLayout,
            allowCancel = false,
            recreateOnChange = true,
            onApplied = null
        )
    }

    private fun showWindowModeDialog(
        rootLayout: ViewGroup,
        allowCancel: Boolean,
        recreateOnChange: Boolean,
        onApplied: (() -> Unit)?
    ) {
        val dialogContentView = layoutInflater.inflate(R.layout.dialog_window_mode_choice, null)
        val optionWindowed = dialogContentView.findViewById<LinearLayout>(R.id.optionWindowed)
        val optionMaximized = dialogContentView.findViewById<LinearLayout>(R.id.optionMaximized)

        val scrollableDialogView = NestedScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                dialogContentView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        var selected = getWindowMode()

        fun updateSelection() {
            optionWindowed.isSelected = selected == WindowMode.WINDOWED
            optionMaximized.isSelected = selected == WindowMode.MAXIMIZED
        }

        optionWindowed.setOnClickListener {
            SoundEffects.playClick(this)
            selected = WindowMode.WINDOWED
            updateSelection()
        }

        optionMaximized.setOnClickListener {
            SoundEffects.playClick(this)
            selected = WindowMode.MAXIMIZED
            updateSelection()
        }

        updateSelection()

        val builder = GameDialogBuilder(this)
            .setTitle(getString(R.string.window_mode_prompt_title))
            .setView(scrollableDialogView)
            .setPositiveButton(getString(R.string.window_mode_apply), null)

        if (allowCancel) {
            builder.setNegativeButton(getString(R.string.cancel), null)
        } else {
            builder.setCancelable(false)
        }

        val dialog = builder.create()

        dialog.show()
        val positiveButton = dialog.findViewById<TextView>(R.id.dialogPositiveButton)
        positiveButton?.setOnClickListener {
            SoundEffects.playClick(this)
            val previous = getWindowMode()
            setWindowMode(selected)
            applyWindowMode(rootLayout)
            onApplied?.invoke()
            val shouldRecreate = recreateOnChange && previous != selected
            dialog.dismiss()
            if (shouldRecreate) {
                recreate()
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
