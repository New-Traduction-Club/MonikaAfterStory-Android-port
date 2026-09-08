package com.nowbar.api.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.CheckResult
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Runtime helpers for Android 16 promoted ongoing / Live Update eligibility.
 *
 * This intentionally checks only the public Android API. OEMs, including Samsung,
 * can apply additional ranking and allow-list rules after these checks pass.
 */
object LiveUpdateDiagnostics {

    const val PERMISSION_POST_PROMOTED_NOTIFICATIONS =
        "android.permission.POST_PROMOTED_NOTIFICATIONS"

    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    const val EXTRA_SUB_TEXT = "android.subText"

    const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"

    const val ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS =
        "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"

    /** Compatibility alias for older docs and samples. */
    const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS =
        ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS

    @JvmStatic
    @ChecksSdkIntAtLeast(api = 36)
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 36

    @JvmStatic
    fun canPostPromotedNotifications(context: Context): Boolean {
        if (!isSupported()) return false

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.canPostPromotedNotifications()
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun declaresPostPromotedNotifications(context: Context): Boolean =
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            packageInfo.requestedPermissions?.contains(PERMISSION_POST_PROMOTED_NOTIFICATIONS) == true
        }.getOrDefault(false)

    @JvmStatic
    fun hasPromotableCharacteristics(notification: Notification): Boolean {
        if (!isSupported()) return false

        return notification.hasPromotableCharacteristics()
    }

    @JvmStatic
    fun isPromotedOngoing(notification: Notification): Boolean {
        if (!isSupported()) return false

        return notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0
    }

    @JvmStatic
    fun isRequestPromotedOngoing(notification: Notification): Boolean =
        readRequestPromotedOngoingFromPlatform(notification)
            ?: notification.extras.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, false)

    @JvmStatic
    @Suppress("DEPRECATION")
    fun inspect(context: Context, notification: Notification): LiveUpdateEligibilityReport {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelImportance = if (notification.channelId != null) {
            manager.getNotificationChannel(notification.channelId)?.importance
        } else {
            null
        }
        val template = notification.extras.getString(Notification.EXTRA_TEMPLATE)
        val channelImportanceValid = channelImportance == null ||
            channelImportance > NotificationManager.IMPORTANCE_MIN
        val androidProgressSegmentCount =
            readAndroidProgressElementCount(notification.extras, NowBarNotificationEvidence.EXTRA_PROGRESS_SEGMENTS)
        val androidProgressPointCount =
            readAndroidProgressElementCount(notification.extras, NowBarNotificationEvidence.EXTRA_PROGRESS_POINTS)
        val androidHasProgressTrackerIcon =
            notification.extras.containsKey(NowBarNotificationEvidence.EXTRA_PROGRESS_TRACKER_ICON)
        val androidHasProgressStartIcon =
            notification.extras.containsKey(NowBarNotificationEvidence.EXTRA_PROGRESS_START_ICON)
        val androidHasProgressEndIcon =
            notification.extras.containsKey(NowBarNotificationEvidence.EXTRA_PROGRESS_END_ICON)
        val androidStyledByProgress =
            notification.extras.optionalBoolean(NowBarNotificationEvidence.EXTRA_STYLED_BY_PROGRESS)
        val hasProgressStylePayload =
            androidProgressSegmentCount > 0 ||
                androidProgressPointCount > 0 ||
                androidHasProgressTrackerIcon ||
                androidHasProgressStartIcon ||
                androidHasProgressEndIcon ||
                androidStyledByProgress != null
        val templateLiveUpdateStyle = liveUpdateStyleForTemplate(template)
        val liveUpdateStyle = if (hasProgressStylePayload && templateLiveUpdateStyle == LiveUpdateAllowedStyle.STANDARD) {
            LiveUpdateAllowedStyle.PROGRESS
        } else {
            templateLiveUpdateStyle ?: LiveUpdateAllowedStyle.PROGRESS.takeIf { hasProgressStylePayload }
        }

        val shortCriticalText = notification.extras.getCharSequence(EXTRA_SHORT_CRITICAL_TEXT)?.toString()
        val statusChipWhenTimeMillis = notification.`when`.takeIf { it > 0L }
        val statusChipShowWhen = notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, false)
        val statusChipUsesChronometer = notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val statusChipChronometerCountDown =
            notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val statusChipRelevantWhen = statusChipUsesChronometer ||
            (statusChipShowWhen && shortCriticalText.isNullOrBlank())
        val now = System.currentTimeMillis()
        val actions = readAndroidActions(notification)

        return LiveUpdateEligibilityReport(
            apiSupported = isSupported(),
            declaresPostPromotedNotifications = declaresPostPromotedNotifications(context),
            promotionSettingsAvailable = canOpenPromotionSettings(context),
            requestPromotedOngoing = isRequestPromotedOngoing(notification),
            canPostPromotedNotifications = canPostPromotedNotifications(context),
            hasPromotableCharacteristics = hasPromotableCharacteristics(notification),
            promotedOngoing = isPromotedOngoing(notification),
            ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            hasContentTitle = !notification.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank(),
            hasContentIntent = notification.contentIntent != null,
            hasDeleteIntent = notification.deleteIntent != null,
            androidActions = actions,
            subText = notification.extras.getCharSequence(EXTRA_SUB_TEXT)?.toString(),
            shortCriticalText = shortCriticalText,
            statusChipTextLength = shortCriticalText?.takeIf { it.isNotBlank() }?.length,
            statusChipTextLikelyFullyVisible = shortCriticalText
                ?.takeIf { it.isNotBlank() }
                ?.let { it.length < STATUS_CHIP_FULL_TEXT_MAX_CHARS },
            statusChipWhenTimeMillis = statusChipWhenTimeMillis,
            statusChipShowWhen = statusChipShowWhen,
            statusChipUsesChronometer = statusChipUsesChronometer,
            statusChipChronometerCountDown = statusChipChronometerCountDown,
            statusChipWhenInPast = statusChipRelevantWhen &&
                statusChipWhenTimeMillis != null &&
                statusChipWhenTimeMillis < now,
            statusChipWhenTooSoon = statusChipRelevantWhen &&
                statusChipWhenTimeMillis != null &&
                statusChipWhenTimeMillis > now &&
                statusChipWhenTimeMillis - now < STATUS_CHIP_MIN_FUTURE_MILLIS,
            statusChipCountdownExpired = statusChipUsesChronometer &&
                statusChipChronometerCountDown &&
                statusChipWhenTimeMillis != null &&
                statusChipWhenTimeMillis <= now,
            androidProgress = notification.extras.optionalInt(NowBarNotificationEvidence.EXTRA_PROGRESS),
            androidProgressMax = notification.extras.optionalInt(NowBarNotificationEvidence.EXTRA_PROGRESS_MAX),
            androidProgressIndeterminate =
                notification.extras.optionalBoolean(NowBarNotificationEvidence.EXTRA_PROGRESS_INDETERMINATE),
            androidProgressSegmentCount = androidProgressSegmentCount,
            androidProgressPointCount = androidProgressPointCount,
            androidHasProgressTrackerIcon = androidHasProgressTrackerIcon,
            androidHasProgressStartIcon = androidHasProgressStartIcon,
            androidHasProgressEndIcon = androidHasProgressEndIcon,
            androidStyledByProgress = androidStyledByProgress,
            hasCustomContentView = notification.contentView != null ||
                notification.bigContentView != null ||
                notification.headsUpContentView != null,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            colorized = notification.extras.getBoolean(Notification.EXTRA_COLORIZED, false),
            channelImportance = channelImportance,
            channelImportanceValid = channelImportanceValid,
            template = template,
            allowedStyle = liveUpdateStyle != null,
            liveUpdateStyle = liveUpdateStyle
        )
    }

    @JvmStatic
    @SuppressLint("InlinedApi")
    fun createPromotionSettingsIntent(context: Context): Intent =
        Intent(ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @JvmStatic
    @SuppressLint("InlinedApi")
    fun createManageAppPromotedNotificationsIntent(context: Context): Intent =
        createPromotionSettingsIntent(context)

    @JvmStatic
    @CheckResult
    fun resolvePromotionSettingsIntent(context: Context): Intent? {
        val intent = createPromotionSettingsIntent(context)
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }

    @JvmStatic
    @CheckResult
    fun resolveManageAppPromotedNotificationsIntent(context: Context): Intent? =
        resolvePromotionSettingsIntent(context)

    @JvmStatic
    fun canOpenPromotionSettings(context: Context): Boolean =
        resolvePromotionSettingsIntent(context) != null

    @JvmStatic
    fun canOpenManageAppPromotedNotifications(context: Context): Boolean =
        canOpenPromotionSettings(context)

    private fun Bundle.optionalInt(key: String): Int? =
        if (containsKey(key)) getInt(key) else null

    private fun Bundle.optionalBoolean(key: String): Boolean? =
        if (containsKey(key)) getBoolean(key) else null

    private fun readAndroidProgressElementCount(extras: Bundle, key: String): Int {
        @Suppress("DEPRECATION")
        val items = extras.getParcelableArrayList<Bundle>(key)
        if (items != null) return items.size

        @Suppress("DEPRECATION")
        val array = extras.getParcelableArray(key)
        return array?.size ?: 0
    }

    private fun readAndroidActions(notification: Notification): List<AndroidActionState> {
        @Suppress("DEPRECATION")
        return notification.actions.orEmpty().map(Notification.Action::toAndroidActionState)
    }

    private fun readRequestPromotedOngoingFromPlatform(notification: Notification): Boolean? =
        runCatching {
            Notification::class.java
                .getMethod("isRequestPromotedOngoing")
                .invoke(notification) as Boolean
        }.getOrNull()

    private fun liveUpdateStyleForTemplate(template: String?): LiveUpdateAllowedStyle? =
        when (template) {
            null -> LiveUpdateAllowedStyle.STANDARD
            "android.app.Notification\$BigTextStyle",
            "androidx.core.app.NotificationCompat\$BigTextStyle" -> LiveUpdateAllowedStyle.BIG_TEXT
            "android.app.Notification\$CallStyle",
            "androidx.core.app.NotificationCompat\$CallStyle" -> LiveUpdateAllowedStyle.CALL
            "android.app.Notification\$ProgressStyle",
            "androidx.core.app.NotificationCompat\$ProgressStyle" -> LiveUpdateAllowedStyle.PROGRESS
            "android.app.Notification\$MetricStyle",
            "androidx.core.app.NotificationCompat\$MetricStyle" -> LiveUpdateAllowedStyle.METRIC
            else -> null
        }

    private const val STATUS_CHIP_FULL_TEXT_MAX_CHARS = 7
    private const val STATUS_CHIP_MIN_FUTURE_MILLIS = 2 * 60 * 1000L
}

data class LiveUpdateEligibilityReport(
    val apiSupported: Boolean,
    val declaresPostPromotedNotifications: Boolean,
    val promotionSettingsAvailable: Boolean,
    val requestPromotedOngoing: Boolean,
    val canPostPromotedNotifications: Boolean,
    val hasPromotableCharacteristics: Boolean,
    val promotedOngoing: Boolean,
    val ongoing: Boolean,
    val hasContentTitle: Boolean,
    val hasContentIntent: Boolean = false,
    val hasDeleteIntent: Boolean = false,
    val androidActions: List<AndroidActionState> = emptyList(),
    val subText: String?,
    val shortCriticalText: String?,
    val statusChipTextLength: Int? = null,
    val statusChipTextLikelyFullyVisible: Boolean? = null,
    val statusChipWhenTimeMillis: Long? = null,
    val statusChipShowWhen: Boolean = false,
    val statusChipUsesChronometer: Boolean = false,
    val statusChipChronometerCountDown: Boolean = false,
    val statusChipWhenInPast: Boolean = false,
    val statusChipWhenTooSoon: Boolean = false,
    val statusChipCountdownExpired: Boolean = false,
    val hasCustomContentView: Boolean,
    val groupSummary: Boolean,
    val colorized: Boolean,
    val channelImportance: Int?,
    val channelImportanceValid: Boolean,
    val template: String?,
    val allowedStyle: Boolean,
    val liveUpdateStyle: LiveUpdateAllowedStyle? = null,
    val androidProgress: Int? = null,
    val androidProgressMax: Int? = null,
    val androidProgressIndeterminate: Boolean? = null,
    val androidProgressSegmentCount: Int = 0,
    val androidProgressPointCount: Int = 0,
    val androidHasProgressTrackerIcon: Boolean = false,
    val androidHasProgressStartIcon: Boolean = false,
    val androidHasProgressEndIcon: Boolean = false,
    val androidStyledByProgress: Boolean? = null
) {
    val androidActionCount: Int
        get() = androidActions.size

    val androidActionTitles: List<String>
        get() = androidActions.map { it.title }

    val androidActionIds: List<String>
        get() = androidActions.mapNotNull { it.id }

    val androidActionSemantics: List<ActionSemantic>
        get() = androidActions.mapNotNull { it.semantic }

    val androidTextOnlyActionCount: Int
        get() = androidActions.count { it.textOnly }

    val androidDisabledActionCount: Int
        get() = androidActions.count { it.disabled }

    val hasStatusChip: Boolean
        get() = !shortCriticalText.isNullOrBlank() ||
            (statusChipShowWhen && statusChipWhenTimeMillis != null)

    val statusChipAdvisoryReasons: List<String>
        get() = buildList {
            if (statusChipTextLikelyFullyVisible == false) add("status-chip-text-may-truncate")
            if (statusChipWhenInPast && !statusChipUsesChronometer) add("status-chip-when-in-past")
            if (statusChipWhenTooSoon) add("status-chip-when-too-soon")
            if (statusChipCountdownExpired) add("status-chip-countdown-expired")
        }

    val advisoryReasons: List<String>
        get() = buildList {
            addAll(statusChipAdvisoryReasons)
            if (!hasDeleteIntent) add("missing-delete-intent")
            if (androidActionCount > NowBarActionLimits.MAX_ACTIONS) add("too-many-action-buttons")
        }

    val hasAndroidProgress: Boolean
        get() = androidProgress != null ||
            androidProgressMax != null ||
            androidProgressIndeterminate == true ||
            hasAndroidProgressStylePayload

    val hasAndroidProgressStylePayload: Boolean
        get() =
            androidProgressSegmentCount > 0 ||
            androidProgressPointCount > 0 ||
            androidHasProgressTrackerIcon ||
            androidHasProgressStartIcon ||
            androidHasProgressEndIcon ||
            androidStyledByProgress != null

    val eligible: Boolean
        get() = apiSupported &&
            declaresPostPromotedNotifications &&
            requestPromotedOngoing &&
            canPostPromotedNotifications &&
            hasPromotableCharacteristics &&
            ongoing &&
            hasContentTitle &&
            !hasCustomContentView &&
            !groupSummary &&
            !colorized &&
            channelImportanceValid &&
            allowedStyle

    val blockingReasons: List<String>
        get() = buildList {
            if (!apiSupported) add("api<36")
            if (!declaresPostPromotedNotifications) add("missing-post-promoted-notifications-permission")
            if (!requestPromotedOngoing) add("missing-request-promoted-ongoing")
            if (!canPostPromotedNotifications) add("promoted-notifications-disabled")
            if (!hasPromotableCharacteristics) add("not-promotable-characteristics")
            if (!ongoing) add("not-ongoing")
            if (!hasContentTitle) add("missing-content-title")
            if (hasCustomContentView) add("custom-content-view")
            if (groupSummary) add("group-summary")
            if (colorized) add("colorized")
            if (!channelImportanceValid) add("channel-importance-min")
            if (!allowedStyle) add("unsupported-style")
        }
}

enum class LiveUpdateAllowedStyle {
    STANDARD,
    BIG_TEXT,
    CALL,
    PROGRESS,
    METRIC
}
