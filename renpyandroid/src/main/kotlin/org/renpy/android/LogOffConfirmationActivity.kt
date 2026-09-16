package org.renpy.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class LogOffConfirmationActivity : BaseActivity() {

    companion object {
        private var onProceedCallback: (() -> Unit)? = null

        fun start(context: Context, onProceed: () -> Unit) {
            onProceedCallback = onProceed
            val intent = Intent(context, LogOffConfirmationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            if (context is Activity) {
                @Suppress("DEPRECATION")
                context.overridePendingTransition(0, 0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        applyImmersiveFullscreen()

        val dialog = GameDialogBuilder(this)
            .setTitle(getString(R.string.launcher_log_off))
            .setMessage(getString(R.string.launcher_log_off_warning_message))
            .setPositiveButton(getString(R.string.launcher_proceed)) { _, _ ->
                val callback = onProceedCallback
                onProceedCallback = null
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
                callback?.invoke()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                onProceedCallback = null
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            .create()

        dialog.setOnDismissListener {
            onProceedCallback = null
            if (!isFinishing) {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }

        dialog.show()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun applyImmersiveFullscreen() {
        if (isChromeOsDevice()) return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            val flags = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }
}
