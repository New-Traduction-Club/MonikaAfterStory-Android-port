package org.renpy.android

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.doOnLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

object InAppNotifier {
    private const val VIRTUAL_DPI = 420
    private const val FONT_SCALE = 1f
    private const val SHORT_DURATION = 2000L
    private const val LONG_DURATION = 3600L
    private const val OUT_OFFSET_DP = 32f
    private const val INSET_MARGIN_DP = 16f
    private const val TOP_MARGIN_DP = 24f

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentHost: ViewGroup? = null
    private var pendingHide: Runnable? = null

    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity?, message: CharSequence?, isLong: Boolean = false) {
        if (activity == null || message.isNullOrBlank()) return
        mainHandler.post { display(activity, message.toString(), isLong) }
    }

    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity?, @StringRes resId: Int, isLong: Boolean = false) {
        if (activity == null) return
        show(activity, activity.getString(resId), isLong)
    }

    private fun display(activity: Activity, message: String, isLong: Boolean) {
        hideCurrent(animateOut = false)

        val host = activity.window?.decorView as? ViewGroup ?: return
        val themedContext = themedContext(activity)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.view_in_app_notification, host, false)
        val textView = view.findViewById<TextView>(R.id.notificationText)
        textView.text = message

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            val margin = dpToPx(themedContext, INSET_MARGIN_DP).toInt()
            val topMargin = dpToPx(themedContext, TOP_MARGIN_DP).toInt()
            setMargins(margin, topMargin, margin, 0)
        }
        view.layoutParams = params
        view.alpha = 0f
        view.translationX = dpToPx(themedContext, OUT_OFFSET_DP)
        view.elevation = dpToPx(themedContext, 8f)

        currentView = view
        currentHost = host
        host.addView(view)

        view.doOnLayout {
            val startX = view.width.toFloat() + dpToPx(themedContext, OUT_OFFSET_DP)
            view.translationX = startX
            view.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }

        val duration = if (isLong) LONG_DURATION else SHORT_DURATION
        val hide = Runnable { hideCurrent(animateOut = true) }
        pendingHide = hide
        mainHandler.postDelayed(hide, duration)
    }

    private fun hideCurrent(animateOut: Boolean) {
        pendingHide?.let { mainHandler.removeCallbacks(it) }
        pendingHide = null
        val view = currentView ?: return
        val host = currentHost
        currentView = null
        currentHost = null

        if (!animateOut) {
            host?.removeView(view)
            return
        }

        val ctx = view.context
        val outDistance = view.width.toFloat() + dpToPx(ctx, OUT_OFFSET_DP)
        view.animate()
            .translationX(outDistance)
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction { host?.removeView(view) }
            .start()
    }

    private fun themedContext(activity: Activity): Context {
        val baseConfig = Configuration(activity.resources.configuration)
        baseConfig.fontScale = FONT_SCALE
        baseConfig.densityDpi = VIRTUAL_DPI
        val fixed = activity.createConfigurationContext(baseConfig)
        return ContextThemeWrapper(fixed, activity.theme)
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
