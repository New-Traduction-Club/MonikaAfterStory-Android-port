package org.renpy.android

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build

object OrientationPolicy {

    private const val ANDROID_16_API_LEVEL = 36
    private const val LARGE_SCREEN_MIN_SW_DP = 600

    @JvmStatic
    fun shouldUseFlexibleOrientation(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= ANDROID_16_API_LEVEL &&
            context.resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_MIN_SW_DP
    }

    @JvmStatic
    fun applyRequestedOrientation(activity: Activity, lockedOrientation: Int) {
        val targetOrientation = if (shouldUseFlexibleOrientation(activity)) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            lockedOrientation
        }

        if (activity.requestedOrientation != targetOrientation) {
            activity.requestedOrientation = targetOrientation
        }
    }
}
