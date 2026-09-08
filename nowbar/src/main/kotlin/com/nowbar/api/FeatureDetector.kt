package com.nowbar.api

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Runtime feature detection.
 *
 * Public API stays simple:
 * - Samsung extras path
 * - Android 16 Live Updates path
 * - basic notification fallback
 *
 * Google / OPPO feature flags are still exposed because they were already present
 * in the original source, but the actual render backends remain Samsung extras
 * and standard Android Live Updates.
 */
object FeatureDetector {

    const val FEATURE_SAMSUNG_NOWBAR = "com.samsung.feature.nowbar"
    const val FEATURE_GOOGLE_AMBIENT = "com.google.android.feature.AMBIENT_DATA"
    const val FEATURE_OPPO_AMBIENT = "com.oplus.software.feature.ambient_alerts"

    private const val ANDROID_16_SDK = 36

    @JvmStatic
    fun isSamsungNowBarSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(FEATURE_SAMSUNG_NOWBAR)

    @JvmStatic
    fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            Build.BRAND.equals("samsung", ignoreCase = true)

    /**
     * Samsung extras are harmless on unsupported builds and useful on One UI builds
     * that do not expose a stable public Now Bar feature flag.
     */
    @JvmStatic
    fun canApplySamsungNowBarExtras(context: Context): Boolean =
        isSamsungNowBarSupported(context) || isSamsungDevice()

    @JvmStatic
    fun isGoogleAmbientSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(FEATURE_GOOGLE_AMBIENT)

    @JvmStatic
    fun isOppoAmbientSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(FEATURE_OPPO_AMBIENT)

    @JvmStatic
    @ChecksSdkIntAtLeast(api = 36)
    fun isAndroid16LiveUpdatesSupported(): Boolean =
        Build.VERSION.SDK_INT >= ANDROID_16_SDK

    /**
     * "Supported" here means the module can try a native enhanced surface now:
     * Samsung Now Bar extras or Android 16 promoted ongoing / Live Updates.
     */
    @JvmStatic
    fun isNativeSurfaceSupported(context: Context): Boolean =
        canApplySamsungNowBarExtras(context) || isAndroid16LiveUpdatesSupported()

    @JvmStatic
    fun isAnyNowBarSupported(context: Context): Boolean = isNativeSurfaceSupported(context)

    @JvmStatic
    fun getSupportedPlatform(context: Context): NowBarPlatform {
        return when {
            canApplySamsungNowBarExtras(context) -> NowBarPlatform.SAMSUNG
            isAndroid16LiveUpdatesSupported() -> NowBarPlatform.ANDROID_16
            isGoogleAmbientSupported(context) -> NowBarPlatform.GOOGLE
            isOppoAmbientSupported(context) -> NowBarPlatform.OPPO
            else -> NowBarPlatform.NONE
        }
    }
}
