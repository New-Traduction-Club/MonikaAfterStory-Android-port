package com.nowbar.api.notification

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import android.widget.RemoteViews

/**
 * Extra keys observed in live Samsung AOD / Now Bar notification dumps.
 *
 * These keys are intentionally kept separate from [NowBarExtrasKeys]: the existing object
 * documents keys extracted from decompiled Samsung apps, while this file documents keys that
 * were seen in `dumpsys notification` records for Google Sports / Google Finance Now Bar cards
 * rendered through `com.samsung.android.app.aodservice`.
 *
 * Samsung does not publish this contract. Treat every key here as best-effort and OEM-specific.
 */
object SamsungOngoingActivityDumpKeys {
    // Standard Android extras used by the dumped OngoingActivityStyle notifications.
    const val TEMPLATE = "android.template"
    const val TEMPLATE_ONGOING_ACTIVITY_STYLE = "android.app.Notification\$OngoingActivityStyle"
    const val TITLE = "android.title"
    const val SUBST_NAME = "android.substName"
    const val SHOW_WHEN = "android.showWhen"
    const val REDUCED_IMAGES = "android.reduced.images"

    // Remote app identity shown by Samsung AOD / Now Bar.
    const val AOD_REMOTE_APP_PENDING_INTENT = NowBarExtrasKeys.AOD_REMOTE_APP_PENDING_INTENT
    const val AOD_REMOTE_APP_ICON = NowBarExtrasKeys.AOD_REMOTE_APP_ICON
    const val AOD_REMOTE_APP_NAME = NowBarExtrasKeys.AOD_REMOTE_APP_NAME

    // Main Samsung OngoingActivity / Now Bar extras.
    const val STYLE = "android.ongoingActivityNoti.style"
    const val SHOW = "android.ongoingActivityNoti.show"
    const val PRIMARY_INFO = "android.ongoingActivityNoti.primaryInfo"
    const val SECONDARY_INFO = "android.ongoingActivityNoti.secondaryInfo"
    const val NOWBAR_PRIMARY_INFO = "android.ongoingActivityNoti.nowbarPrimaryInfo"
    const val NOWBAR_SECONDARY_INFO = "android.ongoingActivityNoti.nowbarSecondaryInfo"

    // Dump-only wrapper keys without the `.Noti` segment.
    const val ONGOING_ACTIVITY_PRIMARY_INFO = "android.ongoingActivityPrimaryInfo"
    const val ONGOING_ACTIVITY_SECONDARY_INFO = "android.ongoingActivitySecondaryInfo"
    const val ONGOING_ACTIVITY_PRIMARY_ACTION = "android.ongoingActivityPrimaryAction"

    // Chip / visual state.
    const val CHIP_BG_COLOR = "android.ongoingActivityNoti.chipBgColor"
    const val CHIP_ICON = "android.ongoingActivityNoti.chipIcon"
    const val CHIP_EXPANDED_TEXT = "android.ongoingActivityNoti.chipExpandedText"
    const val CHIP_EXPANDED_VIEW = "android.ongoingActivityNoti.chipExpandedView"
    const val ONGOING_ACTIVITY_CHIP_ICON = "android.ongoingActivityChipIcon"
    const val ONGOING_ACTIVITY_CHIP_BACKGROUND = "android.ongoingActivityChipBackground"
    const val ONGOING_ACTIVITY_CARD_BACKGROUND = "android.ongoingActivityCardBackground"
    const val ONGOING_ACTIVITY_ACTION_BG_COLORS = "android.ongoingActivityActionBgColors"
    const val ONGOING_ACTIVITY_BADGE = "android.ongoingActivityBadge"
    const val ONGOING_ACTIVITY_CARD_ICON = "android.ongoingActivityCardIcon"
    const val ONGOING_ACTIVITY_NOW_BAR_EXPANDABLE_TYPE = "android.ongoingActivityNowBarExpandableType"
    const val ONGOING_ACTIVITY_MORE_INFO = "android.ongoingActivityMoreInfo"

    // RemoteViews used by Google Sports / Finance cards in the dumps.
    const val NOWBAR_REMOTE_VIEW = "android.ongoingActivityNoti.nowbarRemoteView"
    const val EXPANDED_REMOTE_VIEW = "android.ongoingActivityNoti.expandedRemoteView"
    const val CUSTOM_EXPANDED_CARD_VIEW = "android.ongoingActivityCustomExpandedCardView"
    const val EXPANDED_CHIP_TEXT = "android.ongoingActivityExpandedChipText"
    const val EXPANDED_CHIP_VIEW = "android.ongoingActivityExpandedChipView"
    const val EXPANDED_NOW_BAR_VIEW = "android.ongoingActivityExpandedNowBarView"
    const val CUSTOM_CARD_VIEW_CENTER_UI = "android.ongoingActivityCustomCardViewCenterUI"

    // Dumped chronometer state. This is distinct from the SDK's RemoteViews chronometer keys.
    const val CHRONOMETER_FORMAT = "android.ongoingActivityChronometerFormat"
    const val CHRONOMETER_COUNTDOWN = "android.ongoingActivityChronometerCountdown"
    const val CHRONOMETER_BASE = "android.ongoingActivityChronometerBase"
    const val CHRONOMETER_SPEED = "android.ongoingActivityChronometerSpeed"
    const val CHRONOMETER_START = "android.ongoingActivityChronometerStart"

    // Samsung PDE telemetry extras observed in the Finance dump. Apps normally do not need these.
    const val PDE_FIRST_SHOWN_TIME_MS = "pde_first_shown_time_ms"
    const val PDE_FIRST_EXPANDED_TIME_MS = "pde_first_expanded_time_ms"
    const val PDE_ENQUEUED_TIME_MS = "pde_enqueued_time_ms"
    const val PDE_NOTI_CLICKED_COUNT = "pde_noti_clicked_count"
    const val PDE_NOTI_ACTION_CLICKED_COUNT = "pde_noti_action_clicked_count"
    const val PDE_NOTI_ID = "pde_noti_id"
    const val PDE_NOTI_PKG = "pde_noti_pkg"
    const val PDE_NOTI_TAG = "pde_noti_tag"
}

data class SamsungRemoteAppConfig(
    val name: CharSequence,
    val icon: Icon? = null,
    val pendingIntent: PendingIntent? = null
)

data class SamsungOngoingActivityText(
    val title: CharSequence? = null,
    val primaryInfo: CharSequence? = null,
    val secondaryInfo: CharSequence? = null,
    val nowBarPrimaryInfo: CharSequence? = null,
    val nowBarSecondaryInfo: CharSequence? = null,
    val notificationPrimaryInfo: CharSequence? = null,
    val notificationSecondaryInfo: CharSequence? = null,
    val chipExpandedText: CharSequence? = null,
    val expandedChipText: CharSequence? = null,
    val moreInfo: CharSequence? = null,
    val primaryAction: Int? = null
)

data class SamsungOngoingActivityViews(
    val nowBarRemoteView: RemoteViews? = null,
    val expandedRemoteView: RemoteViews? = null,
    val chipExpandedView: RemoteViews? = null,
    val customExpandedCardView: RemoteViews? = null,
    val expandedChipView: RemoteViews? = null,
    val expandedNowBarView: RemoteViews? = null,
    val customCardViewCenterUi: RemoteViews? = null
)

data class SamsungOngoingActivityVisuals(
    val chipIcon: Icon? = null,
    val ongoingActivityChipIcon: Icon? = null,
    val badge: Icon? = null,
    val cardIcon: Icon? = null,
    val chipBackgroundColor: Int? = null,
    val ongoingActivityChipBackground: Int? = null,
    val cardBackground: Int? = null,
    val actionBackgroundColors: List<Int> = emptyList(),
    val nowBarExpandableType: Int? = null,
    val show: Boolean = true
)

data class SamsungOngoingActivityChronometerState(
    val base: Long? = null,
    val countdown: Boolean? = null,
    val format: CharSequence? = null,
    val speed: Float? = null,
    val start: Boolean? = null
)

data class SamsungOngoingActivityProgress(
    val current: Int? = null,
    val max: Int = OngoingExtrasBuilder.MAX_PROGRESS,
    val color: Int? = null,
    val segmentIcon: Icon? = null,
    val segments: List<ProgressSegment> = emptyList()
)

data class SamsungPdeState(
    val firstShownTimeMs: Long? = null,
    val firstExpandedTimeMs: Long? = null,
    val enqueuedTimeMs: Long? = null,
    val notificationClickedCount: Int? = null,
    val notificationActionClickedCount: Int? = null,
    val notificationId: Int? = null,
    val notificationPackage: String? = null,
    val notificationTag: String? = null
)

/**
 * Builds a bundle that mirrors the extra layout observed in live Samsung AOD service dumps.
 *
 * Usage: call [build] and pass the result to `NotificationCompat.Builder.addExtras(...)`,
 * or build a normal SDK notification with [NowBarManager] and then call [applyTo].
 */
object SamsungOngoingActivityDumpExtras {
    @JvmStatic
    fun build(
        remoteApp: SamsungRemoteAppConfig? = null,
        text: SamsungOngoingActivityText = SamsungOngoingActivityText(),
        views: SamsungOngoingActivityViews = SamsungOngoingActivityViews(),
        visuals: SamsungOngoingActivityVisuals = SamsungOngoingActivityVisuals(),
        chronometer: SamsungOngoingActivityChronometerState = SamsungOngoingActivityChronometerState(),
        progress: SamsungOngoingActivityProgress? = null,
        pde: SamsungPdeState? = null,
        substName: CharSequence? = null,
        style: Int = NowBarExtrasKeys.Style.BOTH,
        showWhen: Boolean? = true,
        reducedImages: Boolean? = true,
        template: String = SamsungOngoingActivityDumpKeys.TEMPLATE_ONGOING_ACTIVITY_STYLE
    ): Bundle = Bundle().apply {
        putString(SamsungOngoingActivityDumpKeys.TEMPLATE, template)
        putInt(SamsungOngoingActivityDumpKeys.STYLE, style)
        putBoolean(SamsungOngoingActivityDumpKeys.SHOW, visuals.show)

        showWhen?.let { putBoolean(SamsungOngoingActivityDumpKeys.SHOW_WHEN, it) }
        reducedImages?.let { putBoolean(SamsungOngoingActivityDumpKeys.REDUCED_IMAGES, it) }

        remoteApp?.let { app ->
            putCharSequence(SamsungOngoingActivityDumpKeys.TITLE, text.title ?: app.name)
            putCharSequence(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_NAME, app.name)
            app.icon?.let { putParcelable(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_ICON, it) }
            app.pendingIntent?.let { putParcelable(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_PENDING_INTENT, it) }
        }

        text.title?.let { putCharSequence(SamsungOngoingActivityDumpKeys.TITLE, it) }
        substName?.let { putCharSequence(SamsungOngoingActivityDumpKeys.SUBST_NAME, it) }

        text.primaryInfo?.let { putCharSequence(SamsungOngoingActivityDumpKeys.PRIMARY_INFO, it) }
        text.secondaryInfo?.let { putCharSequence(SamsungOngoingActivityDumpKeys.SECONDARY_INFO, it) }
        text.nowBarPrimaryInfo?.let { putCharSequence(SamsungOngoingActivityDumpKeys.NOWBAR_PRIMARY_INFO, it) }
        text.nowBarSecondaryInfo?.let { putCharSequence(SamsungOngoingActivityDumpKeys.NOWBAR_SECONDARY_INFO, it) }
        text.notificationPrimaryInfo?.let { putCharSequence(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_PRIMARY_INFO, it) }
        text.notificationSecondaryInfo?.let { putCharSequence(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_SECONDARY_INFO, it) }
        text.chipExpandedText?.let { putCharSequence(SamsungOngoingActivityDumpKeys.CHIP_EXPANDED_TEXT, it) }
        text.expandedChipText?.let { putCharSequence(SamsungOngoingActivityDumpKeys.EXPANDED_CHIP_TEXT, it) }
        text.moreInfo?.let { putCharSequence(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_MORE_INFO, it) }
        text.primaryAction?.let { putInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_PRIMARY_ACTION, it) }

        views.nowBarRemoteView?.let { putParcelable(SamsungOngoingActivityDumpKeys.NOWBAR_REMOTE_VIEW, it) }
        views.expandedRemoteView?.let { putParcelable(SamsungOngoingActivityDumpKeys.EXPANDED_REMOTE_VIEW, it) }
        views.chipExpandedView?.let { putParcelable(SamsungOngoingActivityDumpKeys.CHIP_EXPANDED_VIEW, it) }
        views.customExpandedCardView?.let { putParcelable(SamsungOngoingActivityDumpKeys.CUSTOM_EXPANDED_CARD_VIEW, it) }
        views.expandedChipView?.let { putParcelable(SamsungOngoingActivityDumpKeys.EXPANDED_CHIP_VIEW, it) }
        views.expandedNowBarView?.let { putParcelable(SamsungOngoingActivityDumpKeys.EXPANDED_NOW_BAR_VIEW, it) }
        views.customCardViewCenterUi?.let { putParcelable(SamsungOngoingActivityDumpKeys.CUSTOM_CARD_VIEW_CENTER_UI, it) }

        visuals.chipIcon?.let { putParcelable(SamsungOngoingActivityDumpKeys.CHIP_ICON, it) }
        visuals.ongoingActivityChipIcon?.let { putParcelable(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CHIP_ICON, it) }
        visuals.badge?.let { putParcelable(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_BADGE, it) }
        visuals.cardIcon?.let { putParcelable(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CARD_ICON, it) }
        visuals.chipBackgroundColor?.let { putInt(SamsungOngoingActivityDumpKeys.CHIP_BG_COLOR, it) }
        visuals.ongoingActivityChipBackground?.let { putInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CHIP_BACKGROUND, it) }
        visuals.cardBackground?.let { putInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CARD_BACKGROUND, it) }
        visuals.nowBarExpandableType?.let { putInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_NOW_BAR_EXPANDABLE_TYPE, it) }
        if (visuals.actionBackgroundColors.isNotEmpty()) {
            putIntegerArrayList(
                SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_ACTION_BG_COLORS,
                ArrayList(visuals.actionBackgroundColors)
            )
        }

        chronometer.base?.let { putLong(SamsungOngoingActivityDumpKeys.CHRONOMETER_BASE, it) }
        chronometer.countdown?.let { putBoolean(SamsungOngoingActivityDumpKeys.CHRONOMETER_COUNTDOWN, it) }
        chronometer.format?.let { putCharSequence(SamsungOngoingActivityDumpKeys.CHRONOMETER_FORMAT, it) }
        chronometer.speed?.let { putFloat(SamsungOngoingActivityDumpKeys.CHRONOMETER_SPEED, it) }
        chronometer.start?.let { putBoolean(SamsungOngoingActivityDumpKeys.CHRONOMETER_START, it) }

        progress?.let { putProgressState(it) }
        pde?.let { putPdeState(it) }
    }

    /** Mutates a built notification with the dump-backed extras and returns the same instance. */
    @JvmStatic
    fun applyTo(notification: Notification, extras: Bundle): Notification = notification.apply {
        this.extras.putAll(extras)
    }

    private fun Bundle.putPdeState(pde: SamsungPdeState) {
        pde.firstShownTimeMs?.let { putLong(SamsungOngoingActivityDumpKeys.PDE_FIRST_SHOWN_TIME_MS, it) }
        pde.firstExpandedTimeMs?.let { putLong(SamsungOngoingActivityDumpKeys.PDE_FIRST_EXPANDED_TIME_MS, it) }
        pde.enqueuedTimeMs?.let { putLong(SamsungOngoingActivityDumpKeys.PDE_ENQUEUED_TIME_MS, it) }
        pde.notificationClickedCount?.let { putInt(SamsungOngoingActivityDumpKeys.PDE_NOTI_CLICKED_COUNT, it) }
        pde.notificationActionClickedCount?.let { putInt(SamsungOngoingActivityDumpKeys.PDE_NOTI_ACTION_CLICKED_COUNT, it) }
        pde.notificationId?.let { putInt(SamsungOngoingActivityDumpKeys.PDE_NOTI_ID, it) }
        pde.notificationPackage?.let { putString(SamsungOngoingActivityDumpKeys.PDE_NOTI_PKG, it) }
        if (pde.notificationTag != null) {
            putString(SamsungOngoingActivityDumpKeys.PDE_NOTI_TAG, pde.notificationTag)
        }
    }

    private fun Bundle.putProgressState(progress: SamsungOngoingActivityProgress) {
        require(progress.max > 0) { "Progress max must be positive" }

        progress.current?.let { current ->
            putInt(NowBarExtrasKeys.PROGRESS, current.coerceIn(0, progress.max))
            putInt(NowBarExtrasKeys.PROGRESS_MAX, progress.max)
        }
        progress.color?.let { putInt(NowBarExtrasKeys.PROGRESS_COLOR, it) }
        progress.segmentIcon?.let { putParcelable(NowBarExtrasKeys.PROGRESS_SEGMENT_ICON, it) }

        if (progress.segments.isNotEmpty()) {
            putParcelableArray(
                NowBarExtrasKeys.PROGRESS_SEGMENTS,
                progress.segments.sortedBy { it.startPosition }.map { segment ->
                    Bundle().apply {
                        putFloat(NowBarExtrasKeys.PROGRESS_SEGMENT_START, segment.startPosition)
                        putInt(NowBarExtrasKeys.PROGRESS_SEGMENT_COLOR, segment.color)
                        segment.icon?.let { putParcelable(NowBarExtrasKeys.PROGRESS_SEGMENT_ICON, it) }
                    }
                }.toTypedArray<Parcelable>()
            )
        }
    }
}
