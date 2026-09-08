package com.nowbar.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.CheckResult
import com.nowbar.api.notification.LiveUpdateDiagnostics
import com.nowbar.api.notification.SamsungOngoingActivityStyleBuilder

/**
 * Environment-level diagnostics for Now Bar / Live Updates capability.
 *
 * Use [inspect] before interpreting notification-level failures. It reports whether the
 * current device can use Samsung extras, Android promoted ongoing notifications, settings
 * shortcuts, and the optional hidden Samsung OngoingActivityStyle path.
 */
object NowBarDiagnostics {

    const val ACTION_LOCK_SCREEN_SETTINGS = "android.settings.LOCK_SCREEN_SETTINGS"

    @JvmStatic
    fun inspect(context: Context): NowBarCapabilityReport {
        val appContext = context.applicationContext
        val samsungFeature = FeatureDetector.isSamsungNowBarSupported(appContext)
        val samsungDevice = FeatureDetector.isSamsungDevice()
        val android16LiveUpdates = FeatureDetector.isAndroid16LiveUpdatesSupported()
        val canApplySamsungExtras = FeatureDetector.canApplySamsungNowBarExtras(appContext)

        return NowBarCapabilityReport(
            platform = FeatureDetector.getSupportedPlatform(appContext),
            nativeSurfaceSupported = FeatureDetector.isNativeSurfaceSupported(appContext),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            samsungDevice = samsungDevice,
            samsungNowBarFeature = samsungFeature,
            canApplySamsungExtras = canApplySamsungExtras,
            samsungHiddenOngoingActivityStyleAvailable = SamsungOngoingActivityStyleBuilder.isAvailable(),
            android16LiveUpdatesSupported = android16LiveUpdates,
            declaresPostPromotedNotifications = LiveUpdateDiagnostics.declaresPostPromotedNotifications(appContext),
            nowBarSettingsShortcutAvailable = resolveNowBarSettingsIntent(appContext) != null,
            promotionSettingsAvailable = LiveUpdateDiagnostics.canOpenPromotionSettings(appContext),
            appNotificationSettingsAvailable = resolveAppNotificationSettingsIntent(appContext) != null,
            canPostPromotedNotifications = LiveUpdateDiagnostics.canPostPromotedNotifications(appContext),
            googleAmbientSupported = FeatureDetector.isGoogleAmbientSupported(appContext),
            oppoAmbientSupported = FeatureDetector.isOppoAmbientSupported(appContext)
        )
    }

    /**
     * Best-effort shortcut to the settings area where Samsung documents Now bar app toggles.
     *
     * Samsung does not expose a public per-app Now bar settings intent. The documented user
     * path is Settings -> Lock screen and AOD -> Now bar -> View more, so this opens the
     * platform lock-screen settings screen and lets the user finish the OEM-specific step.
     */
    @JvmStatic
    fun createNowBarSettingsIntent(context: Context): Intent =
        Intent(ACTION_LOCK_SCREEN_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @JvmStatic
    fun createAppNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @JvmStatic
    fun createAppDetailsSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @JvmStatic
    fun createRecommendedSettingsIntents(context: Context): List<Intent> =
        listOf(
            createNowBarSettingsIntent(context),
            LiveUpdateDiagnostics.createManageAppPromotedNotificationsIntent(context),
            createAppNotificationSettingsIntent(context),
            createAppDetailsSettingsIntent(context)
        )

    @JvmStatic
    @CheckResult
    fun resolveNowBarSettingsIntent(context: Context): Intent? =
        createNowBarSettingsIntent(context).takeIf { it.resolveActivity(context.packageManager) != null }

    @JvmStatic
    @CheckResult
    fun resolveAppNotificationSettingsIntent(context: Context): Intent? =
        createAppNotificationSettingsIntent(context).takeIf { it.resolveActivity(context.packageManager) != null }

    @JvmStatic
    @CheckResult
    fun resolveRecommendedSettingsIntent(context: Context): Intent? =
        createRecommendedSettingsIntents(context).firstOrNull {
            it.resolveActivity(context.packageManager) != null
        }

    @JvmStatic
    fun canOpenNowBarSettings(context: Context): Boolean =
        resolveNowBarSettingsIntent(context) != null
}

data class NowBarCapabilityReport(
    val platform: NowBarPlatform,
    val nativeSurfaceSupported: Boolean,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val sdkInt: Int,
    val samsungDevice: Boolean,
    val samsungNowBarFeature: Boolean,
    val canApplySamsungExtras: Boolean,
    val samsungHiddenOngoingActivityStyleAvailable: Boolean,
    val android16LiveUpdatesSupported: Boolean,
    val declaresPostPromotedNotifications: Boolean,
    val nowBarSettingsShortcutAvailable: Boolean,
    val promotionSettingsAvailable: Boolean,
    val appNotificationSettingsAvailable: Boolean,
    val canPostPromotedNotifications: Boolean,
    val googleAmbientSupported: Boolean,
    val oppoAmbientSupported: Boolean
) {
    val samsungPathAvailable: Boolean
        get() = canApplySamsungExtras

    val androidLiveUpdatesPathAvailable: Boolean
        get() = android16LiveUpdatesSupported

    val blockingReasons: List<String>
        get() = buildList {
            if (!nativeSurfaceSupported) add("no-native-nowbar-or-live-updates-surface")
            if (androidLiveUpdatesPathAvailable && !declaresPostPromotedNotifications) {
                add("missing-post-promoted-notifications-permission")
            }
            if (androidLiveUpdatesPathAvailable && !canPostPromotedNotifications) {
                add("promoted-notifications-disabled")
            }
        }

    fun toDisplayString(): String = buildString {
        appendLine("Platform: $platform")
        appendLine("Native supported: $nativeSurfaceSupported")
        appendLine("Device: $manufacturer $model / API $sdkInt")
        appendLine("Samsung device: $samsungDevice")
        appendLine("Samsung feature flag: $samsungNowBarFeature")
        appendLine("Samsung extras path: $canApplySamsungExtras")
        appendLine("Samsung hidden style: $samsungHiddenOngoingActivityStyleAvailable")
        appendLine("Android Live Updates: $android16LiveUpdatesSupported")
        appendLine("Now Bar settings shortcut: $nowBarSettingsShortcutAvailable")
        appendLine("Promoted permission: $declaresPostPromotedNotifications")
        appendLine("Promoted settings: $promotionSettingsAvailable")
        appendLine("App notification settings: $appNotificationSettingsAvailable")
        appendLine("Promoted allowed: $canPostPromotedNotifications")
        append("Blockers: ")
        append(blockingReasons.joinToString().ifBlank { "none" })
    }
}
