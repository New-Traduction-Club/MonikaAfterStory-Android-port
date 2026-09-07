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
import android.content.Intent
import android.content.IntentFilter
import android.view.MotionEvent
import android.view.Gravity
import android.view.WindowManager

abstract class GameWindowActivity : BaseActivity() {

    enum class WindowMode { MAXIMIZED, WINDOWED }

    private var lastWindowX = 0
    private var lastWindowY = 0
    private var isWindowMinimized = false
    val isWindowMinimizedState: Boolean
        get() = isWindowMinimized
    private var preMinimizeParams: WindowManager.LayoutParams? = null

    private val commandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DesktopWindowManager.ACTION_WINDOW_COMMAND) {
                val targetId = intent.getStringExtra(DesktopWindowManager.EXTRA_ACTIVITY_ID) ?: return
                val command = intent.getStringExtra(DesktopWindowManager.EXTRA_COMMAND) ?: return

                if (targetId == this@GameWindowActivity::class.java.name) {
                    when (command) {
                        "MINIMIZE" -> minimizeWindow()
                        "RESTORE" -> restoreWindow()
                    }
                }
            }
        }
    }

    private fun notifyState(state: String) {
        val appName = if (::txtWindowTitle.isInitialized && txtWindowTitle.text.isNotEmpty()) {
            txtWindowTitle.text.toString()
        } else {
            title?.toString() ?: this::class.java.simpleName
        }
        DesktopWindowManager.notifyStateChanged(this, this::class.java.name, appName, state)
    }

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
        ActiveActivityRegistry.activeActivities.add(this::class.java.name)
        super.onCreate(savedInstanceState)
        
        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyImmersiveFullscreen()
        
        // Let derived activities set their own content via setContentView()
        overridePendingTransition(R.anim.window_scale_in, R.anim.window_fade_out)

        SoundEffects.initialize(this)

        val filter = IntentFilter(DesktopWindowManager.ACTION_WINDOW_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }

    override fun finish() {
        ActiveActivityRegistry.activeActivities.remove(this::class.java.name)
        super.finish()
        overridePendingTransition(R.anim.window_fade_in, R.anim.window_scale_out)
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        applyImmersiveFullscreen()
        windowRootLayout?.let { applyWindowMode(it) }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFullscreen()
        if (!isWindowMinimized) {
            notifyState("RUNNING")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isWindowMinimized) {
            restoreWindow()
        }
    }

    override fun onDestroy() {
        ActiveActivityRegistry.activeActivities.remove(this::class.java.name)
        notifyState("DESTROYED")
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveFullscreen()
        }
    }

    private fun applyImmersiveFullscreen() {
        if (isChromeOsDevice()) return
        WindowCompat.setDecorFitsSystemWindows(window, false)
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

    private fun setupWindowDecorations(root: ViewGroup) {
        val headerLayout = root.findViewById<View>(R.id.headerLayout)
        if (headerLayout != null) {
            setupDragging(headerLayout)
        }

        val btnWindowMinimize = root.findViewById<View>(R.id.btnWindowMinimize)
        btnWindowMinimize?.setOnClickListener {
            SoundEffects.playClick(this)
            minimizeWindow()
        }

        val btnWindowMaximize = root.findViewById<View>(R.id.btnWindowMaximize)
        btnWindowMaximize?.setOnClickListener {
            SoundEffects.playClick(this)
            toggleMaximize()
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupDragging(headerView: View) {
        var startRawX = 0f
        var startRawY = 0f
        var initialX = 0
        var initialY = 0

        headerView.setOnTouchListener { _, event ->
            if (getWindowMode() != WindowMode.WINDOWED) return@setOnTouchListener false

            val w = window ?: return@setOnTouchListener false
            val params = w.attributes
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    
                    val displayMetrics = resources.displayMetrics
                    val halfScreenWidth = displayMetrics.widthPixels / 2
                    val halfScreenHeight = displayMetrics.heightPixels / 2
                    
                    val newX = initialX + dx.toInt()
                    val newY = initialY + dy.toInt()
                    
                    params.x = newX.coerceIn(-halfScreenWidth, halfScreenWidth)
                    
                    val windowHeight = if (params.height > 0) params.height else displayMetrics.heightPixels
                    val minY = (windowHeight / 2) - halfScreenHeight
                    params.y = newY.coerceIn(minY, halfScreenHeight)
                    
                    lastWindowX = params.x
                    lastWindowY = params.y

                    w.attributes = params
                    true
                }
                else -> false
            }
        }
    }

    private fun minimizeWindow() {
        if (isWindowMinimized) return
        val w = window ?: return
        val root = windowRootLayout ?: return

        isWindowMinimized = true
        preMinimizeParams = WindowManager.LayoutParams().apply {
            copyFrom(w.attributes)
        }

        val card = root.findViewById<CardView>(R.id.cardWindowContainer) ?: return
        card.animate().cancel()
        card.pivotX = card.width / 2f
        card.pivotY = card.height.toFloat()

        card.animate()
            .scaleX(0.1f)
            .scaleY(0.1f)
            .translationY(card.height * 0.5f)
            .alpha(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                card.visibility = View.GONE
                
                val params = w.attributes
                params.width = 1
                params.height = 1
                params.gravity = Gravity.TOP or Gravity.LEFT
                params.x = 0
                params.y = 0
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                w.attributes = params

                notifyState("MINIMIZED")
            }
            .start()
    }

    private fun restoreWindow() {
        if (!isWindowMinimized) return
        val w = window ?: return
        val root = windowRootLayout ?: return

        isWindowMinimized = false

        val card = root.findViewById<CardView>(R.id.cardWindowContainer) ?: return
        card.animate().cancel()
        card.visibility = View.VISIBLE
        card.alpha = 0f
        card.scaleX = 0.1f
        card.scaleY = 0.1f

        val params = w.attributes
        preMinimizeParams?.let {
            params.width = it.width
            params.height = it.height
            params.x = it.x
            params.y = it.y
            params.gravity = it.gravity
            params.flags = it.flags
        }
        w.attributes = params

        card.post {
            card.pivotX = card.width / 2f
            card.pivotY = card.height.toFloat()
            card.translationY = card.height * 0.5f

            card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    val intent = Intent(this, this::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(intent)
                    notifyState("RUNNING")
                }
                .start()
        }
    }

    private fun toggleMaximize() {
        val root = windowRootLayout ?: return
        val currentMode = getWindowMode()
        val newMode = if (currentMode == WindowMode.WINDOWED) WindowMode.MAXIMIZED else WindowMode.WINDOWED
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WINDOW_MODE, if (newMode == WindowMode.WINDOWED) "windowed" else "maximized").apply()
        
        applyWindowMode(root)
        notifyState("RUNNING")
    }

    override fun setContentView(layoutResID: Int) {
        val rootLayout = layoutInflater.inflate(R.layout.layout_game_window_chrome, null) as ViewGroup
        contentContainer = rootLayout.findViewById(R.id.windowContent)
        txtWindowTitle = rootLayout.findViewById(R.id.txtWindowTitle)
        btnWindowClose = rootLayout.findViewById(R.id.btnWindowClose)
        windowRootLayout = rootLayout

        applyWindowMode(rootLayout)
        setupWindowChrome(rootLayout)
        setupWindowDecorations(rootLayout)

        // Inflate the child activity's layout into the container
        layoutInflater.inflate(layoutResID, contentContainer, true)

        btnWindowClose.setOnClickListener {
            SoundEffects.playClick(this)
            finish()
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
        setupWindowDecorations(rootLayout)

        contentContainer.addView(view)

        btnWindowClose.setOnClickListener {
            SoundEffects.playClick(this)
            finish()
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
        notifyState("RUNNING")
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        if (::txtWindowTitle.isInitialized) {
            txtWindowTitle.text = title
        }
        notifyState("RUNNING")
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
        val w = window ?: return

        val cardParams = card.layoutParams as? ConstraintLayout.LayoutParams
        cardParams?.let {
            it.width = ViewGroup.LayoutParams.MATCH_PARENT
            it.height = ViewGroup.LayoutParams.MATCH_PARENT
            card.layoutParams = it
        }

        val displayMetrics = resources.displayMetrics
        val wParams = w.attributes
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        when (getWindowMode()) {
            WindowMode.MAXIMIZED -> {
                wParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                wParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                wParams.gravity = Gravity.CENTER
                wParams.x = 0
                wParams.y = 0
                card.cardElevation = 0f
                card.radius = 0f
            }
            WindowMode.WINDOWED -> {
                val minSize = (200 * displayMetrics.density).toInt()
                val targetWidth = Math.max(minSize, (displayMetrics.widthPixels * 0.8).toInt())
                val targetHeight = Math.max(minSize, (displayMetrics.heightPixels * 0.9).toInt())
                wParams.width = targetWidth
                wParams.height = targetHeight
                wParams.gravity = Gravity.CENTER

                val halfScreenWidth = displayMetrics.widthPixels / 2
                val halfScreenHeight = displayMetrics.heightPixels / 2
                val minY = (targetHeight / 2) - halfScreenHeight

                lastWindowX = lastWindowX.coerceIn(-halfScreenWidth, halfScreenWidth)
                lastWindowY = lastWindowY.coerceIn(minY, halfScreenHeight)

                wParams.x = lastWindowX
                wParams.y = lastWindowY
                wParams.flags = wParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                card.cardElevation = dp(12f)
                card.radius = dp(8f)
            }
        }
        w.attributes = wParams
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

        // Keep maximized as the default when the first-run prompt is dismissed
        setWindowMode(WindowMode.MAXIMIZED)

        showWindowModeDialog(
            rootLayout,
            allowCancel = true,
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && !hasWindowFocus()) {
            val intent = Intent(this, this::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
