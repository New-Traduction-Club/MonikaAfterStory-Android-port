package org.renpy.android

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

abstract class GameWindowActivity : BaseActivity() {

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        val metrics = newBase.resources.displayMetrics
        
        // Use a fixed virtual height of 450dp for all windows
        val virtualHeight = 450f
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        
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

    private fun setupWindowChrome(rootLayout: ViewGroup) {
        val headerLayout = rootLayout.findViewById<View>(R.id.headerLayout)
        val footerBar = rootLayout.findViewById<View>(R.id.footerBar)
        
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
            
            val density = resources.displayMetrics.density
            val px16 = (16 * density).toInt()
            val px12 = (12 * density).toInt()
            val extraRightMargin = (24 * density).toInt()
            
            headerLayout.setPadding(
                systemInsets.left + px16,
                systemInsets.top + px12,
                systemInsets.right + px16 + extraRightMargin,
                px12
            )
            
            contentContainer.setPadding(
                systemInsets.left,
                0,
                systemInsets.right + extraRightMargin,
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

        setupWindowChrome(rootLayout)

        // Inflate the child activity's layout into the container
        layoutInflater.inflate(layoutResID, contentContainer, true)

        btnWindowClose.setOnClickListener {
            SoundEffects.playClick(this)
            onBackPressed()
        }

        super.setContentView(rootLayout)
    }

    override fun setContentView(view: View) {
        val rootLayout = layoutInflater.inflate(R.layout.layout_game_window_chrome, null) as ViewGroup
        contentContainer = rootLayout.findViewById(R.id.windowContent)
        txtWindowTitle = rootLayout.findViewById(R.id.txtWindowTitle)
        btnWindowClose = rootLayout.findViewById(R.id.btnWindowClose)

        setupWindowChrome(rootLayout)

        contentContainer.addView(view)

        btnWindowClose.setOnClickListener {
            SoundEffects.playClick(this)
            onBackPressed()
        }

        super.setContentView(rootLayout)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
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
}
