package org.renpy.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import android.os.Build

class WindowDecorator(private val activity: Activity) {
    private var lastWindowX = 0
    private var lastWindowY = 0
    private var isWindowMinimized = false
    val isWindowMinimizedState: Boolean
        get() = isWindowMinimized
    private var preMinimizeParams: WindowManager.LayoutParams? = null
    private var windowRootLayout: ViewGroup? = null
    private var txtWindowTitle: TextView? = null
    
    private val activityId: String = activity::class.java.name
    @Volatile
    private var activityName: String = "Monika After Story"

    fun setWindowTitle(title: String) {
        activityName = title
        activity.runOnUiThread {
            txtWindowTitle?.text = title
        }
        if (!isWindowMinimized) {
            notifyState("RUNNING")
        }
    }

    fun isWindowedMode(): Boolean {
        val prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("renpy_window_mode", "fullscreen") == "windowed"
    }

    fun decorate(view: View, windowTitle: String): View {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.layout_game_window_chrome_renpy, null) as ViewGroup
        windowRootLayout = root

        val contentContainer = root.findViewById<FrameLayout>(R.id.windowContent)
        txtWindowTitle = root.findViewById<TextView>(R.id.txtWindowTitle)
        val btnWindowClose = root.findViewById<View>(R.id.btnWindowClose)

        txtWindowTitle?.text = windowTitle
        activityName = windowTitle
        txtWindowTitle?.textSize = 12f
        contentContainer.addView(view)

        val footerBar = root.findViewById<View>(R.id.footerBar)
        footerBar?.visibility = View.GONE

        btnWindowClose.setOnClickListener {
            if (activity is org.libsdl.app.SDLActivity) {
                org.libsdl.app.SDLActivity.nativeQuit()
            } else {
                activity.onBackPressed()
            }
        }

        val btnWindowMinimize = root.findViewById<View>(R.id.btnWindowMinimize)
        btnWindowMinimize?.setOnClickListener {
            minimizeWindow()
        }

        val btnWindowMaximize = root.findViewById<View>(R.id.btnWindowMaximize)
        btnWindowMaximize?.setOnClickListener {
            toggleMaximize()
        }

        val headerLayout = root.findViewById<View>(R.id.headerLayout)
        if (headerLayout != null) {
            setupDragging(headerLayout)
        }

        activity.window?.setBackgroundDrawableResource(android.R.color.transparent)
        applyWindowDimensions()

        return root
    }

    fun notifyState(state: String) {
        DesktopWindowManager.notifyStateChanged(activity, activityId, activityName, state)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupDragging(headerView: View) {
        var startRawX = 0f
        var startRawY = 0f
        var initialX = 0
        var initialY = 0

        headerView.setOnTouchListener { _, event ->
            val w = activity.window ?: return@setOnTouchListener false
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
                    
                    val displayMetrics = activity.resources.displayMetrics
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

    private fun getWindowedDimensions(displayMetrics: android.util.DisplayMetrics): Pair<Int, Int> {
        val minSize = (200 * displayMetrics.density).toInt()
        val screenWidth = Math.max(minSize, displayMetrics.widthPixels)
        val screenHeight = Math.max(minSize, displayMetrics.heightPixels)

        val targetHeight = Math.max(minSize, (screenHeight * 0.85).toInt())
        var targetWidth = Math.max(minSize, (targetHeight * 16.0 / 9.0).toInt())
        if (targetWidth > screenWidth * 0.9) {
            targetWidth = Math.max(minSize, (screenWidth * 0.9).toInt())
            val adjustedHeight = Math.max(minSize, (targetWidth * 9.0 / 16.0).toInt())
            return Pair(targetWidth, adjustedHeight)
        }
        return Pair(targetWidth, targetHeight)
    }

    fun applyWindowDimensions() {
        val root = windowRootLayout ?: return
        val w = activity.window ?: return
        val card = root.findViewById<CardView>(R.id.cardWindowContainer) ?: return
        val headerLayout = root.findViewById<View>(R.id.headerLayout) ?: return
        val headerDivider = root.findViewById<View>(R.id.windowHeaderDivider)

        val cardParams = card.layoutParams as? ConstraintLayout.LayoutParams
        cardParams?.let {
            it.width = 0
            it.height = 0
            card.layoutParams = it
        }

        w.decorView.setPadding(0, 0, 0, 0)

        val displayMetrics = activity.resources.displayMetrics
        val wParams = w.attributes

        if (isWindowedMode()) {
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            headerLayout.visibility = View.VISIBLE
            headerDivider?.visibility = View.VISIBLE
            val (targetWidth, targetHeight) = getWindowedDimensions(displayMetrics)
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
            card.cardElevation = 12f * displayMetrics.density
            card.radius = 8f * displayMetrics.density
        } else {
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            headerLayout.visibility = View.GONE
            headerDivider?.visibility = View.GONE
            wParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            wParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            wParams.gravity = Gravity.CENTER
            wParams.x = 0
            wParams.y = 0
            wParams.flags = wParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
            card.cardElevation = 0f
            card.radius = 0f
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            wParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        w.attributes = wParams
    }

    fun setWindowModeDynamically(windowed: Boolean) {
        val prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val modeStr = if (windowed) "windowed" else "fullscreen"
        prefs.edit().putString("renpy_window_mode", modeStr).apply()

        activity.runOnUiThread {
            val root = windowRootLayout ?: return@runOnUiThread
            val card = root.findViewById<CardView>(R.id.cardWindowContainer) ?: return@runOnUiThread
            val w = activity.window ?: return@runOnUiThread
            val displayMetrics = activity.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            card.animate().cancel()

            if (windowed) {
                // Fullscreen to Windowed
                val (targetWidth, targetHeight) = getWindowedDimensions(displayMetrics)
                
                card.pivotX = card.width / 2f
                card.pivotY = card.height / 2f
                
                val endScaleX = targetWidth.toFloat() / Math.max(1, screenWidth)
                val endScaleY = targetHeight.toFloat() / Math.max(1, screenHeight)

                card.animate()
                    .scaleX(endScaleX)
                    .scaleY(endScaleY)
                    .translationX(lastWindowX.toFloat())
                    .translationY(lastWindowY.toFloat())
                    .setDuration(300)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction {
                        applyWindowDimensions()
                        card.scaleX = 1f
                        card.scaleY = 1f
                        card.translationX = 0f
                        card.translationY = 0f
                        notifyState("RUNNING")
                    }
                    .start()
            } else {
                // Windowed to Fullscreen
                val (targetWidth, targetHeight) = getWindowedDimensions(displayMetrics)
                
                val wParams = w.attributes
                wParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                wParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                wParams.x = 0
                wParams.y = 0
                w.attributes = wParams

                card.scaleX = targetWidth.toFloat() / Math.max(1, screenWidth)
                card.scaleY = targetHeight.toFloat() / Math.max(1, screenHeight)
                card.translationX = lastWindowX.toFloat()
                card.translationY = lastWindowY.toFloat()

                if (activity is PythonSDLActivity) {
                    activity.applyImmersiveFullscreen()
                }

                card.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction {
                        applyWindowDimensions()
                        notifyState("DESTROYED")
                    }
                    .start()
            }
        }
    }

    fun minimizeWindow() {
        if (isWindowMinimized) return
        val w = activity.window ?: return
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

    fun restoreWindow() {
        if (!isWindowMinimized) return
        val w = activity.window ?: return
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
                    val intent = Intent(activity, activity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    activity.startActivity(intent)
                    notifyState("RUNNING")
                }
                .start()
        }
    }

    fun toggleMaximize() {
        val root = windowRootLayout ?: return
        val w = activity.window ?: return
        val card = root.findViewById<CardView>(R.id.cardWindowContainer) ?: return

        val wParams = w.attributes
        val displayMetrics = activity.resources.displayMetrics

        val isCurrentlyMaximized = wParams.width == ViewGroup.LayoutParams.MATCH_PARENT
        if (isCurrentlyMaximized) {
            val (targetWidth, targetHeight) = getWindowedDimensions(displayMetrics)
            wParams.width = targetWidth
            wParams.height = targetHeight
            wParams.gravity = Gravity.CENTER
            wParams.x = lastWindowX
            wParams.y = lastWindowY
            wParams.flags = wParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            card.cardElevation = 12f * displayMetrics.density
            card.radius = 8f * displayMetrics.density
        } else {
            wParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            wParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            wParams.gravity = Gravity.CENTER
            wParams.x = 0
            wParams.y = 0
            card.cardElevation = 0f
            card.radius = 0f
        }
        w.attributes = wParams
    }

    fun bringToFrontSelf() {
        val intent = Intent(activity, activity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        activity.startActivity(intent)
    }
}
