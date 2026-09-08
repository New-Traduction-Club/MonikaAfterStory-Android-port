package com.nowbar.api.notification

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.RemoteViews

/**
 * Notification-level evidence inspector for Samsung Now Bar and Android Live Updates.
 *
 * This is intentionally a local, deterministic check over a built [Notification]. It does
 * not claim that SystemUI will render the card, but it lets apps and tests confirm that the
 * notification carries the expected Samsung extras, Samsung dump/native-style hints, Android
 * promoted ongoing request, BigTextStyle/CallStyle/ProgressStyle/MetricStyle templates, and foldable capsule extras
 * before posting it or while analysing `dumpsys notification` output.
 */
object NowBarNotificationEvidence {

    const val EXTRA_CALL_TYPE = "android.callType"
    const val EXTRA_CALL_IS_VIDEO = "android.callIsVideo"
    const val EXTRA_CALL_PERSON = "android.callPerson"
    const val EXTRA_CALL_PERSON_COMPAT = "android.callPersonCompat"
    const val EXTRA_ANSWER_INTENT = "android.answerIntent"
    const val EXTRA_DECLINE_INTENT = "android.declineIntent"
    const val EXTRA_HANG_UP_INTENT = "android.hangUpIntent"
    const val EXTRA_ANSWER_COLOR = "android.answerColor"
    const val EXTRA_DECLINE_COLOR = "android.declineColor"
    const val EXTRA_VERIFICATION_TEXT = "android.verificationText"
    const val EXTRA_VERIFICATION_ICON = "android.verificationIcon"
    const val EXTRA_PROGRESS = "android.progress"
    const val EXTRA_PROGRESS_MAX = "android.progressMax"
    const val EXTRA_PROGRESS_INDETERMINATE = "android.progressIndeterminate"
    const val EXTRA_PROGRESS_SEGMENTS = "android.progressSegments"
    const val EXTRA_PROGRESS_POINTS = "android.progressPoints"
    const val EXTRA_PROGRESS_TRACKER_ICON = "android.progressTrackerIcon"
    const val EXTRA_PROGRESS_START_ICON = "android.progressStartIcon"
    const val EXTRA_PROGRESS_END_ICON = "android.progressEndIcon"
    const val EXTRA_STYLED_BY_PROGRESS = "android.styledByProgress"

    private val samsungNowBarKeys = listOf(
        NowBarExtrasKeys.CHIP_BG_COLOR,
        NowBarExtrasKeys.CHIP_ICON,
        NowBarExtrasKeys.CHIP_EXPANDED_TEXT,
        NowBarExtrasKeys.PRIMARY_INFO,
        NowBarExtrasKeys.SECONDARY_INFO,
        NowBarExtrasKeys.NOWBAR_SECONDARY_INFO,
        NowBarExtrasKeys.SHOW_SMALL_ICON,
        NowBarExtrasKeys.STYLE,
        NowBarExtrasKeys.ACTION_TYPE,
        NowBarExtrasKeys.ACTION_PRIMARY_SET,
        NowBarExtrasKeys.CHRONOMETER_REMOTE_VIEW,
        NowBarExtrasKeys.CHRONOMETER_REMOTE_VIEW_TAG,
        NowBarExtrasKeys.CHRONOMETER_REMOTE_VIEW_POSITION,
        NowBarExtrasKeys.NOWBAR_CHRONOMETER_POSITION,
        NowBarExtrasKeys.NOWBAR_PENDING_INTENT_ON_SUB_SCREEN,
        NowBarExtrasKeys.SUBST_NAME,
        NowBarExtrasKeys.IS_CAPSULE,
        NowBarExtrasKeys.CAPSULE_LAYOUT,
        NowBarExtrasKeys.CAPSULE_ACTION,
        NowBarExtrasKeys.CAPSULE_BG_START_COLOR,
        NowBarExtrasKeys.CAPSULE_BG_END_COLOR,
        NowBarExtrasKeys.CAPSULE_PRIORITY,
        NowBarExtrasKeys.PROGRESS,
        NowBarExtrasKeys.PROGRESS_MAX,
        NowBarExtrasKeys.PROGRESS_SEGMENTS,
        NowBarExtrasKeys.PROGRESS_COLOR,
        NowBarExtrasKeys.PROGRESS_SEGMENT_ICON,
        NowBarExtrasKeys.NOWBAR_ICON,
        NowBarExtrasKeys.ACTION_BG_COLOR,
        NowBarExtrasKeys.FIRST_ICON,
        NowBarExtrasKeys.SECOND_ICON,
        NowBarExtrasKeys.SECONDARY_INFO_ICON,
        NowBarExtrasKeys.NOWBAR_PRIMARY_INFO,
        NowBarExtrasKeys.AOD_REMOTE_APP_PENDING_INTENT,
        NowBarExtrasKeys.AOD_REMOTE_APP_ICON,
        NowBarExtrasKeys.AOD_REMOTE_APP_NAME
    )

    private val samsungDumpKeys = listOf(
        SamsungOngoingActivityDumpKeys.TEMPLATE,
        SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_PENDING_INTENT,
        SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_ICON,
        SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_NAME,
        SamsungOngoingActivityDumpKeys.SHOW,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_PRIMARY_INFO,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_SECONDARY_INFO,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_PRIMARY_ACTION,
        SamsungOngoingActivityDumpKeys.CHIP_EXPANDED_VIEW,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CHIP_ICON,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CHIP_BACKGROUND,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CARD_BACKGROUND,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_ACTION_BG_COLORS,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_BADGE,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CARD_ICON,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_NOW_BAR_EXPANDABLE_TYPE,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_MORE_INFO,
        SamsungOngoingActivityDumpKeys.NOWBAR_REMOTE_VIEW,
        SamsungOngoingActivityDumpKeys.EXPANDED_REMOTE_VIEW,
        SamsungOngoingActivityDumpKeys.CUSTOM_EXPANDED_CARD_VIEW,
        SamsungOngoingActivityDumpKeys.EXPANDED_CHIP_TEXT,
        SamsungOngoingActivityDumpKeys.EXPANDED_CHIP_VIEW,
        SamsungOngoingActivityDumpKeys.EXPANDED_NOW_BAR_VIEW,
        SamsungOngoingActivityDumpKeys.CUSTOM_CARD_VIEW_CENTER_UI,
        SamsungOngoingActivityDumpKeys.CHRONOMETER_FORMAT,
        SamsungOngoingActivityDumpKeys.CHRONOMETER_COUNTDOWN,
        SamsungOngoingActivityDumpKeys.CHRONOMETER_BASE,
        SamsungOngoingActivityDumpKeys.CHRONOMETER_SPEED,
        SamsungOngoingActivityDumpKeys.CHRONOMETER_START
    )

    private val samsungRemoteViewKeys = listOf(
        SamsungOngoingActivityDumpKeys.NOWBAR_REMOTE_VIEW,
        SamsungOngoingActivityDumpKeys.EXPANDED_REMOTE_VIEW,
        SamsungOngoingActivityDumpKeys.CHIP_EXPANDED_VIEW,
        SamsungOngoingActivityDumpKeys.CUSTOM_EXPANDED_CARD_VIEW,
        SamsungOngoingActivityDumpKeys.EXPANDED_CHIP_VIEW,
        SamsungOngoingActivityDumpKeys.EXPANDED_NOW_BAR_VIEW,
        SamsungOngoingActivityDumpKeys.CUSTOM_CARD_VIEW_CENTER_UI
    )

    private val samsungTextKeys = listOf(
        SamsungOngoingActivityDumpKeys.TITLE,
        SamsungOngoingActivityDumpKeys.SUBST_NAME,
        SamsungOngoingActivityDumpKeys.PRIMARY_INFO,
        SamsungOngoingActivityDumpKeys.SECONDARY_INFO,
        SamsungOngoingActivityDumpKeys.NOWBAR_PRIMARY_INFO,
        SamsungOngoingActivityDumpKeys.NOWBAR_SECONDARY_INFO,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_PRIMARY_INFO,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_SECONDARY_INFO,
        SamsungOngoingActivityDumpKeys.CHIP_EXPANDED_TEXT,
        SamsungOngoingActivityDumpKeys.EXPANDED_CHIP_TEXT,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_MORE_INFO,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_PRIMARY_ACTION
    )

    private val samsungVisualKeys = listOf(
        SamsungOngoingActivityDumpKeys.SHOW,
        SamsungOngoingActivityDumpKeys.CHIP_ICON,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CHIP_ICON,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_BADGE,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CARD_ICON,
        SamsungOngoingActivityDumpKeys.CHIP_BG_COLOR,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CHIP_BACKGROUND,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CARD_BACKGROUND,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_ACTION_BG_COLORS,
        SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_NOW_BAR_EXPANDABLE_TYPE
    )

    private val samsungPdeKeys = listOf(
        SamsungOngoingActivityDumpKeys.PDE_FIRST_SHOWN_TIME_MS,
        SamsungOngoingActivityDumpKeys.PDE_FIRST_EXPANDED_TIME_MS,
        SamsungOngoingActivityDumpKeys.PDE_ENQUEUED_TIME_MS,
        SamsungOngoingActivityDumpKeys.PDE_NOTI_CLICKED_COUNT,
        SamsungOngoingActivityDumpKeys.PDE_NOTI_ACTION_CLICKED_COUNT,
        SamsungOngoingActivityDumpKeys.PDE_NOTI_ID,
        SamsungOngoingActivityDumpKeys.PDE_NOTI_PKG,
        SamsungOngoingActivityDumpKeys.PDE_NOTI_TAG
    )

    private val samsungChronometerKeys = listOf(
        SamsungOngoingActivityDumpKeys.CHRONOMETER_BASE,
        SamsungOngoingActivityDumpKeys.CHRONOMETER_COUNTDOWN,
        SamsungOngoingActivityDumpKeys.CHRONOMETER_FORMAT,
        SamsungOngoingActivityDumpKeys.CHRONOMETER_SPEED,
        SamsungOngoingActivityDumpKeys.CHRONOMETER_START
    )

    private val samsungNowBarTextKeys = listOf(
        NowBarExtrasKeys.PRIMARY_INFO,
        NowBarExtrasKeys.SECONDARY_INFO,
        NowBarExtrasKeys.NOWBAR_PRIMARY_INFO,
        NowBarExtrasKeys.NOWBAR_SECONDARY_INFO,
        NowBarExtrasKeys.CHIP_EXPANDED_TEXT,
        NowBarExtrasKeys.SUBST_NAME
    )

    private val samsungNowBarVisualKeys = listOf(
        NowBarExtrasKeys.CHIP_BG_COLOR,
        NowBarExtrasKeys.CHIP_ICON,
        NowBarExtrasKeys.SHOW_SMALL_ICON,
        NowBarExtrasKeys.NOWBAR_ICON,
        NowBarExtrasKeys.ACTION_BG_COLOR,
        NowBarExtrasKeys.FIRST_ICON,
        NowBarExtrasKeys.SECOND_ICON,
        NowBarExtrasKeys.SECONDARY_INFO_ICON
    )

    private val samsungNowBarActionKeys = listOf(
        NowBarExtrasKeys.ACTION_TYPE,
        NowBarExtrasKeys.ACTION_PRIMARY_SET,
        NowBarExtrasKeys.NOWBAR_PENDING_INTENT_ON_SUB_SCREEN
    )

    private val samsungNowBarProgressKeys = listOf(
        NowBarExtrasKeys.PROGRESS,
        NowBarExtrasKeys.PROGRESS_MAX,
        NowBarExtrasKeys.PROGRESS_COLOR,
        NowBarExtrasKeys.PROGRESS_SEGMENT_ICON,
        NowBarExtrasKeys.PROGRESS_SEGMENTS
    )

    private val samsungNowBarChronometerViewKeys = listOf(
        NowBarExtrasKeys.CHRONOMETER_REMOTE_VIEW,
        NowBarExtrasKeys.CHRONOMETER_REMOTE_VIEW_TAG,
        NowBarExtrasKeys.CHRONOMETER_REMOTE_VIEW_POSITION,
        NowBarExtrasKeys.NOWBAR_CHRONOMETER_POSITION
    )

    private val samsungNowBarCapsuleKeys = listOf(
        NowBarExtrasKeys.IS_CAPSULE,
        NowBarExtrasKeys.CAPSULE_LAYOUT,
        NowBarExtrasKeys.CAPSULE_ACTION,
        NowBarExtrasKeys.CAPSULE_BG_START_COLOR,
        NowBarExtrasKeys.CAPSULE_BG_END_COLOR,
        NowBarExtrasKeys.CAPSULE_PRIORITY
    )

    @JvmStatic
    @Suppress("DEPRECATION")
    fun inspect(notification: Notification): NowBarNotificationEvidenceReport {
        val extras = notification.extras ?: Bundle()
        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        val samsungExtrasCount = countPresentKeys(extras, samsungNowBarKeys)
        val samsungDumpExtrasCount = countPresentKeys(extras, samsungDumpKeys)
        val samsungRemoteViewCount = countPresentKeys(extras, samsungRemoteViewKeys)
        val samsungTextExtrasCount = countPresentKeys(extras, samsungTextKeys)
        val samsungVisualExtrasCount = countPresentKeys(extras, samsungVisualKeys)
        val samsungPdeExtrasCount = countPresentKeys(extras, samsungPdeKeys)
        val samsungChronometerExtrasCount = countPresentKeys(extras, samsungChronometerKeys)
        val progressSegmentCount = readProgressSegmentCount(extras)
        val hasBigTextStyleTemplate = template.isBigTextStyleTemplate()
        val hasCallStyleTemplate = template.isCallStyleTemplate()
        val hasProgressStyleTemplate = template.isProgressStyleTemplate()
        val hasMetricStyleTemplate = template.isMetricStyleTemplate()
        val hasNativeOngoingActivityTemplate =
            template == SamsungOngoingActivityDumpKeys.TEMPLATE_ONGOING_ACTIVITY_STYLE
        val actions = readAndroidActions(notification)

        return NowBarNotificationEvidenceReport(
            ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            requestPromotedOngoing = LiveUpdateDiagnostics.isRequestPromotedOngoing(notification),
            promotedOngoing = LiveUpdateDiagnostics.isPromotedOngoing(notification),
            hasContentTitle = !extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank(),
            hasContentIntent = notification.contentIntent != null,
            hasDeleteIntent = notification.deleteIntent != null,
            androidActions = actions,
            subText = extras.getCharSequence(LiveUpdateDiagnostics.EXTRA_SUB_TEXT)?.toString(),
            shortCriticalText = extras.getCharSequence(LiveUpdateDiagnostics.EXTRA_SHORT_CRITICAL_TEXT)?.toString(),
            statusChipWhenTimeMillis = notification.`when`.takeIf { it > 0L },
            statusChipShowWhen = extras.getBoolean(Notification.EXTRA_SHOW_WHEN, false),
            statusChipUsesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
            statusChipChronometerCountDown =
                extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
            androidProgress = extras.optionalInt(EXTRA_PROGRESS),
            androidProgressMax = extras.optionalInt(EXTRA_PROGRESS_MAX),
            androidProgressIndeterminate = extras.optionalBoolean(EXTRA_PROGRESS_INDETERMINATE),
            androidProgressSegmentCount = readAndroidProgressElementCount(extras, EXTRA_PROGRESS_SEGMENTS),
            androidProgressPointCount = readAndroidProgressElementCount(extras, EXTRA_PROGRESS_POINTS),
            androidHasProgressTrackerIcon = extras.containsKey(EXTRA_PROGRESS_TRACKER_ICON),
            androidHasProgressStartIcon = extras.containsKey(EXTRA_PROGRESS_START_ICON),
            androidHasProgressEndIcon = extras.containsKey(EXTRA_PROGRESS_END_ICON),
            androidStyledByProgress = extras.optionalBoolean(EXTRA_STYLED_BY_PROGRESS),
            template = template,
            samsungStyle = extras.optionalInt(NowBarExtrasKeys.STYLE),
            samsungNowBarExtrasCount = samsungExtrasCount,
            samsungNowBar = readSamsungNowBarExtras(extras),
            samsungDumpExtrasCount = samsungDumpExtrasCount,
            samsungRemoteViewCount = samsungRemoteViewCount,
            samsungPdeExtrasCount = samsungPdeExtrasCount,
            progressSegmentCount = progressSegmentCount,
            hasSamsungProgress = extras.containsKey(NowBarExtrasKeys.PROGRESS),
            hasSamsungProgressSegments = progressSegmentCount > 0,
            hasCapsule = extras.getBoolean(NowBarExtrasKeys.IS_CAPSULE, false) ||
                extras.containsKey(NowBarExtrasKeys.CAPSULE_LAYOUT),
            hasSamsungRemoteViews = samsungRemoteViewCount > 0,
            samsungViews = readSamsungViews(extras),
            hasNowBarRemoteView = extras.containsKey(SamsungOngoingActivityDumpKeys.NOWBAR_REMOTE_VIEW),
            hasExpandedRemoteView = extras.containsKey(SamsungOngoingActivityDumpKeys.EXPANDED_REMOTE_VIEW),
            hasChipExpandedView = extras.containsKey(SamsungOngoingActivityDumpKeys.CHIP_EXPANDED_VIEW),
            hasCustomExpandedCardView = extras.containsKey(SamsungOngoingActivityDumpKeys.CUSTOM_EXPANDED_CARD_VIEW),
            hasExpandedChipView = extras.containsKey(SamsungOngoingActivityDumpKeys.EXPANDED_CHIP_VIEW),
            hasExpandedNowBarView = extras.containsKey(SamsungOngoingActivityDumpKeys.EXPANDED_NOW_BAR_VIEW),
            hasCustomCardViewCenterUi = extras.containsKey(SamsungOngoingActivityDumpKeys.CUSTOM_CARD_VIEW_CENTER_UI),
            hasAodRemoteApp = extras.containsKey(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_NAME) ||
                extras.containsKey(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_ICON) ||
                extras.containsKey(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_PENDING_INTENT),
            aodRemoteAppName =
                extras.getCharSequence(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_NAME)?.toString(),
            aodRemoteApp = readAodRemoteApp(extras),
            hasAodRemoteAppIcon = extras.containsKey(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_ICON),
            hasAodRemoteAppPendingIntent =
                extras.containsKey(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_PENDING_INTENT),
            samsungDumpShow = extras.optionalBoolean(SamsungOngoingActivityDumpKeys.SHOW),
            samsungReducedImages = extras.optionalBoolean(SamsungOngoingActivityDumpKeys.REDUCED_IMAGES),
            samsungTextExtrasCount = samsungTextExtrasCount,
            samsungText = readSamsungText(extras),
            samsungVisualExtrasCount = samsungVisualExtrasCount,
            samsungVisuals = readSamsungVisuals(extras),
            samsungPrimaryAction = extras.optionalInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_PRIMARY_ACTION),
            samsungNowBarExpandableType =
                extras.optionalInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_NOW_BAR_EXPANDABLE_TYPE),
            samsungActionBackgroundColorCount =
                extras.getIntegerArrayList(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_ACTION_BG_COLORS)?.size ?: 0,
            hasOngoingActivityChipIcon = extras.containsKey(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CHIP_ICON),
            hasOngoingActivityBadge = extras.containsKey(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_BADGE),
            hasOngoingActivityCardIcon = extras.containsKey(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CARD_ICON),
            samsungChronometerExtrasCount = samsungChronometerExtrasCount,
            samsungChronometerState = readSamsungChronometerState(extras),
            pdeState = readPdeState(extras),
            nativeOngoingActivityTemplate = hasNativeOngoingActivityTemplate,
            callStyleTemplate = hasCallStyleTemplate,
            progressStyleTemplate = hasProgressStyleTemplate,
            metricStyleTemplate = hasMetricStyleTemplate,
            callStyleType = extras.optionalInt(EXTRA_CALL_TYPE),
            callStyleIsVideo = extras.optionalBoolean(EXTRA_CALL_IS_VIDEO),
            callStyleHasPerson = extras.containsKey(EXTRA_CALL_PERSON) ||
                extras.containsKey(EXTRA_CALL_PERSON_COMPAT),
            callStyleHasAnswerIntent = extras.containsKey(EXTRA_ANSWER_INTENT),
            callStyleHasDeclineIntent = extras.containsKey(EXTRA_DECLINE_INTENT),
            callStyleHasHangUpIntent = extras.containsKey(EXTRA_HANG_UP_INTENT),
            callStyleAnswerColor = extras.optionalInt(EXTRA_ANSWER_COLOR),
            callStyleDeclineColor = extras.optionalInt(EXTRA_DECLINE_COLOR),
            verificationText = extras.getCharSequence(EXTRA_VERIFICATION_TEXT)?.toString(),
            callStyleHasVerificationIcon = extras.containsKey(EXTRA_VERIFICATION_ICON),
            bigTextStyleTemplate = hasBigTextStyleTemplate,
            hasCustomContentView = notification.contentView != null ||
                notification.bigContentView != null ||
                notification.headsUpContentView != null,
            colorized = extras.getBoolean(Notification.EXTRA_COLORIZED, false)
        )
    }

    private fun countPresentKeys(extras: Bundle, keys: List<String>): Int =
        keys.count(extras::containsKey)

    private fun readAndroidActions(notification: Notification): List<AndroidActionState> {
        @Suppress("DEPRECATION")
        return notification.actions.orEmpty().map(Notification.Action::toAndroidActionState)
    }

    private fun readProgressSegmentCount(extras: Bundle): Int {
        @Suppress("DEPRECATION")
        val segments = extras.getParcelableArray(NowBarExtrasKeys.PROGRESS_SEGMENTS)
        return segments?.size ?: 0
    }

    private fun readAndroidProgressElementCount(extras: Bundle, key: String): Int {
        @Suppress("DEPRECATION")
        val items = extras.getParcelableArrayList<Bundle>(key)
        if (items != null) return items.size

        @Suppress("DEPRECATION")
        val array = extras.getParcelableArray(key)
        return array?.size ?: 0
    }

    private fun readSamsungNowBarExtras(extras: Bundle): SamsungNowBarExtrasState? {
        if (countPresentKeys(extras, samsungNowBarKeys) == 0) return null

        return SamsungNowBarExtrasState(
            style = extras.optionalInt(NowBarExtrasKeys.STYLE),
            text = readSamsungNowBarText(extras),
            visuals = readSamsungNowBarVisuals(extras),
            action = readSamsungNowBarAction(extras),
            progress = readSamsungNowBarProgress(extras),
            chronometer = readSamsungNowBarChronometerView(extras),
            capsule = readSamsungNowBarCapsule(extras),
            remoteApp = readAodRemoteApp(extras)
        )
    }

    private fun readSamsungNowBarText(extras: Bundle): SamsungNowBarTextState? {
        if (countPresentKeys(extras, samsungNowBarTextKeys) == 0) return null

        return SamsungNowBarTextState(
            primaryInfo = extras.getCharSequence(NowBarExtrasKeys.PRIMARY_INFO),
            secondaryInfo = extras.getCharSequence(NowBarExtrasKeys.SECONDARY_INFO),
            nowBarPrimaryInfo = extras.getCharSequence(NowBarExtrasKeys.NOWBAR_PRIMARY_INFO),
            nowBarSecondaryInfo = extras.getCharSequence(NowBarExtrasKeys.NOWBAR_SECONDARY_INFO),
            chipExpandedText = extras.getCharSequence(NowBarExtrasKeys.CHIP_EXPANDED_TEXT),
            substName = extras.getCharSequence(NowBarExtrasKeys.SUBST_NAME)
        )
    }

    private fun readSamsungNowBarVisuals(extras: Bundle): SamsungNowBarVisualState? {
        if (countPresentKeys(extras, samsungNowBarVisualKeys) == 0) return null

        return SamsungNowBarVisualState(
            chipBackgroundColor = extras.optionalInt(NowBarExtrasKeys.CHIP_BG_COLOR),
            chipIcon = extras.optionalIcon(NowBarExtrasKeys.CHIP_ICON),
            showSmallIcon = extras.optionalBoolean(NowBarExtrasKeys.SHOW_SMALL_ICON),
            nowBarIcon = extras.optionalIcon(NowBarExtrasKeys.NOWBAR_ICON),
            actionBackgroundColor = extras.optionalInt(NowBarExtrasKeys.ACTION_BG_COLOR),
            firstIcon = extras.optionalIcon(NowBarExtrasKeys.FIRST_ICON),
            secondIcon = extras.optionalIcon(NowBarExtrasKeys.SECOND_ICON),
            secondaryInfoIcon = extras.optionalIcon(NowBarExtrasKeys.SECONDARY_INFO_ICON)
        )
    }

    private fun readSamsungNowBarAction(extras: Bundle): SamsungNowBarActionState? {
        if (countPresentKeys(extras, samsungNowBarActionKeys) == 0) return null

        return SamsungNowBarActionState(
            actionType = extras.optionalInt(NowBarExtrasKeys.ACTION_TYPE),
            actionPrimarySet = extras.optionalInt(NowBarExtrasKeys.ACTION_PRIMARY_SET),
            subScreenIntent = extras.optionalPendingIntent(NowBarExtrasKeys.NOWBAR_PENDING_INTENT_ON_SUB_SCREEN)
        )
    }

    private fun readSamsungNowBarProgress(extras: Bundle): SamsungNowBarProgressState? {
        if (countPresentKeys(extras, samsungNowBarProgressKeys) == 0) return null

        return SamsungNowBarProgressState(
            current = extras.optionalInt(NowBarExtrasKeys.PROGRESS),
            max = extras.optionalInt(NowBarExtrasKeys.PROGRESS_MAX),
            color = extras.optionalInt(NowBarExtrasKeys.PROGRESS_COLOR),
            topLevelSegmentIcon = extras.optionalIcon(NowBarExtrasKeys.PROGRESS_SEGMENT_ICON),
            segments = readSamsungNowBarProgressSegments(extras)
        )
    }

    private fun readSamsungNowBarProgressSegments(extras: Bundle): List<SamsungNowBarProgressSegmentState> {
        @Suppress("DEPRECATION")
        val segments = extras.getParcelableArray(NowBarExtrasKeys.PROGRESS_SEGMENTS) ?: return emptyList()

        return segments.mapNotNull { it as? Bundle }.map { segment ->
            SamsungNowBarProgressSegmentState(
                startPosition = segment.optionalFloat(NowBarExtrasKeys.PROGRESS_SEGMENT_START),
                color = segment.optionalInt(NowBarExtrasKeys.PROGRESS_SEGMENT_COLOR),
                icon = segment.optionalIcon(NowBarExtrasKeys.PROGRESS_SEGMENT_ICON)
            )
        }
    }

    private fun readSamsungNowBarChronometerView(extras: Bundle): SamsungNowBarChronometerViewState? {
        if (countPresentKeys(extras, samsungNowBarChronometerViewKeys) == 0) return null

        return SamsungNowBarChronometerViewState(
            remoteView = extras.optionalRemoteViews(NowBarExtrasKeys.CHRONOMETER_REMOTE_VIEW),
            tag = extras.getCharSequence(NowBarExtrasKeys.CHRONOMETER_REMOTE_VIEW_TAG),
            viewPosition = extras.optionalInt(NowBarExtrasKeys.CHRONOMETER_REMOTE_VIEW_POSITION),
            nowBarPosition = extras.optionalInt(NowBarExtrasKeys.NOWBAR_CHRONOMETER_POSITION)
        )
    }

    private fun readSamsungNowBarCapsule(extras: Bundle): SamsungNowBarCapsuleState? {
        if (countPresentKeys(extras, samsungNowBarCapsuleKeys) == 0) return null

        return SamsungNowBarCapsuleState(
            enabled = extras.optionalBoolean(NowBarExtrasKeys.IS_CAPSULE),
            layout = extras.optionalRemoteViews(NowBarExtrasKeys.CAPSULE_LAYOUT),
            action = extras.optionalPendingIntent(NowBarExtrasKeys.CAPSULE_ACTION),
            bgStartColor = extras.optionalInt(NowBarExtrasKeys.CAPSULE_BG_START_COLOR),
            bgEndColor = extras.optionalInt(NowBarExtrasKeys.CAPSULE_BG_END_COLOR),
            priority = extras.optionalString(NowBarExtrasKeys.CAPSULE_PRIORITY)
        )
    }

    private fun readSamsungText(extras: Bundle): SamsungOngoingActivityText? {
        if (countPresentKeys(extras, samsungTextKeys) == 0) return null

        return SamsungOngoingActivityText(
            title = extras.getCharSequence(SamsungOngoingActivityDumpKeys.TITLE),
            primaryInfo = extras.getCharSequence(SamsungOngoingActivityDumpKeys.PRIMARY_INFO),
            secondaryInfo = extras.getCharSequence(SamsungOngoingActivityDumpKeys.SECONDARY_INFO),
            nowBarPrimaryInfo = extras.getCharSequence(SamsungOngoingActivityDumpKeys.NOWBAR_PRIMARY_INFO),
            nowBarSecondaryInfo = extras.getCharSequence(SamsungOngoingActivityDumpKeys.NOWBAR_SECONDARY_INFO),
            notificationPrimaryInfo =
                extras.getCharSequence(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_PRIMARY_INFO),
            notificationSecondaryInfo =
                extras.getCharSequence(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_SECONDARY_INFO),
            chipExpandedText = extras.getCharSequence(SamsungOngoingActivityDumpKeys.CHIP_EXPANDED_TEXT),
            expandedChipText = extras.getCharSequence(SamsungOngoingActivityDumpKeys.EXPANDED_CHIP_TEXT),
            moreInfo = extras.getCharSequence(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_MORE_INFO),
            primaryAction = extras.optionalInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_PRIMARY_ACTION)
        )
    }

    private fun readAodRemoteApp(extras: Bundle): SamsungRemoteAppConfig? {
        val hasRemoteApp = extras.containsKey(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_NAME) ||
            extras.containsKey(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_ICON) ||
            extras.containsKey(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_PENDING_INTENT)
        if (!hasRemoteApp) return null

        @Suppress("DEPRECATION")
        val icon = extras.getParcelable<Icon>(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_ICON)
        @Suppress("DEPRECATION")
        val pendingIntent =
            extras.getParcelable<PendingIntent>(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_PENDING_INTENT)

        return SamsungRemoteAppConfig(
            name = extras.getCharSequence(SamsungOngoingActivityDumpKeys.AOD_REMOTE_APP_NAME) ?: "",
            icon = icon,
            pendingIntent = pendingIntent
        )
    }

    private fun readSamsungViews(extras: Bundle): SamsungOngoingActivityViews? {
        if (countPresentKeys(extras, samsungRemoteViewKeys) == 0) return null

        @Suppress("DEPRECATION")
        val nowBarRemoteView = extras.getParcelable<RemoteViews>(SamsungOngoingActivityDumpKeys.NOWBAR_REMOTE_VIEW)
        @Suppress("DEPRECATION")
        val expandedRemoteView = extras.getParcelable<RemoteViews>(SamsungOngoingActivityDumpKeys.EXPANDED_REMOTE_VIEW)
        @Suppress("DEPRECATION")
        val chipExpandedView = extras.getParcelable<RemoteViews>(SamsungOngoingActivityDumpKeys.CHIP_EXPANDED_VIEW)
        @Suppress("DEPRECATION")
        val customExpandedCardView =
            extras.getParcelable<RemoteViews>(SamsungOngoingActivityDumpKeys.CUSTOM_EXPANDED_CARD_VIEW)
        @Suppress("DEPRECATION")
        val expandedChipView = extras.getParcelable<RemoteViews>(SamsungOngoingActivityDumpKeys.EXPANDED_CHIP_VIEW)
        @Suppress("DEPRECATION")
        val expandedNowBarView = extras.getParcelable<RemoteViews>(SamsungOngoingActivityDumpKeys.EXPANDED_NOW_BAR_VIEW)
        @Suppress("DEPRECATION")
        val customCardViewCenterUi =
            extras.getParcelable<RemoteViews>(SamsungOngoingActivityDumpKeys.CUSTOM_CARD_VIEW_CENTER_UI)

        return SamsungOngoingActivityViews(
            nowBarRemoteView = nowBarRemoteView,
            expandedRemoteView = expandedRemoteView,
            chipExpandedView = chipExpandedView,
            customExpandedCardView = customExpandedCardView,
            expandedChipView = expandedChipView,
            expandedNowBarView = expandedNowBarView,
            customCardViewCenterUi = customCardViewCenterUi
        )
    }

    private fun readSamsungVisuals(extras: Bundle): SamsungOngoingActivityVisuals? {
        if (countPresentKeys(extras, samsungVisualKeys) == 0) return null

        @Suppress("DEPRECATION")
        val chipIcon = extras.getParcelable<Icon>(SamsungOngoingActivityDumpKeys.CHIP_ICON)
        @Suppress("DEPRECATION")
        val ongoingActivityChipIcon =
            extras.getParcelable<Icon>(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CHIP_ICON)
        @Suppress("DEPRECATION")
        val badge = extras.getParcelable<Icon>(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_BADGE)
        @Suppress("DEPRECATION")
        val cardIcon = extras.getParcelable<Icon>(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CARD_ICON)

        return SamsungOngoingActivityVisuals(
            chipIcon = chipIcon,
            ongoingActivityChipIcon = ongoingActivityChipIcon,
            badge = badge,
            cardIcon = cardIcon,
            chipBackgroundColor = extras.optionalInt(SamsungOngoingActivityDumpKeys.CHIP_BG_COLOR),
            ongoingActivityChipBackground =
                extras.optionalInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CHIP_BACKGROUND),
            cardBackground = extras.optionalInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_CARD_BACKGROUND),
            actionBackgroundColors =
                extras.getIntegerArrayList(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_ACTION_BG_COLORS)
                    ?.toList()
                    ?: emptyList(),
            nowBarExpandableType =
                extras.optionalInt(SamsungOngoingActivityDumpKeys.ONGOING_ACTIVITY_NOW_BAR_EXPANDABLE_TYPE),
            show = extras.optionalBoolean(SamsungOngoingActivityDumpKeys.SHOW) ?: true
        )
    }

    private fun readPdeState(extras: Bundle): SamsungPdeState? {
        if (countPresentKeys(extras, samsungPdeKeys) == 0) return null

        return SamsungPdeState(
            firstShownTimeMs = extras.optionalLong(SamsungOngoingActivityDumpKeys.PDE_FIRST_SHOWN_TIME_MS),
            firstExpandedTimeMs = extras.optionalLong(SamsungOngoingActivityDumpKeys.PDE_FIRST_EXPANDED_TIME_MS),
            enqueuedTimeMs = extras.optionalLong(SamsungOngoingActivityDumpKeys.PDE_ENQUEUED_TIME_MS),
            notificationClickedCount =
                extras.optionalInt(SamsungOngoingActivityDumpKeys.PDE_NOTI_CLICKED_COUNT),
            notificationActionClickedCount =
                extras.optionalInt(SamsungOngoingActivityDumpKeys.PDE_NOTI_ACTION_CLICKED_COUNT),
            notificationId = extras.optionalInt(SamsungOngoingActivityDumpKeys.PDE_NOTI_ID),
            notificationPackage = extras.optionalString(SamsungOngoingActivityDumpKeys.PDE_NOTI_PKG),
            notificationTag = extras.optionalString(SamsungOngoingActivityDumpKeys.PDE_NOTI_TAG)
        )
    }

    private fun readSamsungChronometerState(extras: Bundle): SamsungOngoingActivityChronometerState? {
        if (countPresentKeys(extras, samsungChronometerKeys) == 0) return null

        return SamsungOngoingActivityChronometerState(
            base = extras.optionalLong(SamsungOngoingActivityDumpKeys.CHRONOMETER_BASE),
            countdown = extras.optionalBoolean(SamsungOngoingActivityDumpKeys.CHRONOMETER_COUNTDOWN),
            format = extras.getCharSequence(SamsungOngoingActivityDumpKeys.CHRONOMETER_FORMAT),
            speed = extras.optionalFloat(SamsungOngoingActivityDumpKeys.CHRONOMETER_SPEED),
            start = extras.optionalBoolean(SamsungOngoingActivityDumpKeys.CHRONOMETER_START)
        )
    }

    private fun Bundle.optionalInt(key: String): Int? =
        if (containsKey(key)) getInt(key) else null

    private fun Bundle.optionalLong(key: String): Long? =
        if (containsKey(key)) getLong(key) else null

    private fun Bundle.optionalFloat(key: String): Float? =
        if (containsKey(key)) getFloat(key) else null

    private fun Bundle.optionalBoolean(key: String): Boolean? =
        if (containsKey(key)) getBoolean(key) else null

    private fun Bundle.optionalString(key: String): String? =
        if (containsKey(key)) getString(key) else null

    @Suppress("DEPRECATION")
    private fun Bundle.optionalIcon(key: String): Icon? =
        getParcelable(key)

    @Suppress("DEPRECATION")
    private fun Bundle.optionalPendingIntent(key: String): PendingIntent? =
        getParcelable(key)

    @Suppress("DEPRECATION")
    private fun Bundle.optionalRemoteViews(key: String): RemoteViews? =
        getParcelable(key)

    private fun String?.isBigTextStyleTemplate(): Boolean =
        this == "android.app.Notification\$BigTextStyle" ||
            this == "androidx.core.app.NotificationCompat\$BigTextStyle"

    private fun String?.isCallStyleTemplate(): Boolean =
        this == "android.app.Notification\$CallStyle" ||
            this == "androidx.core.app.NotificationCompat\$CallStyle"

    private fun String?.isProgressStyleTemplate(): Boolean =
        this == "android.app.Notification\$ProgressStyle" ||
            this == "androidx.core.app.NotificationCompat\$ProgressStyle"

    private fun String?.isMetricStyleTemplate(): Boolean =
        this == "android.app.Notification\$MetricStyle" ||
            this == "androidx.core.app.NotificationCompat\$MetricStyle"
}

enum class NowBarEvidencePath {
    SAMSUNG_EXTRAS,
    SAMSUNG_DUMP_EXTRAS,
    SAMSUNG_NATIVE_TEMPLATE,
    SAMSUNG_REMOTE_VIEWS,
    SAMSUNG_AOD_REMOTE_APP,
    SAMSUNG_TEXT_STATE,
    SAMSUNG_VISUAL_STATE,
    SAMSUNG_CHRONOMETER_STATE,
    SAMSUNG_PDE_STATE,
    FOLDABLE_CAPSULE,
    ANDROID_PROMOTED_ONGOING,
    ANDROID_CONTENT_INTENT,
    ANDROID_DELETE_INTENT,
    ANDROID_ACTION_BUTTONS,
    ANDROID_PROGRESS,
    ANDROID_SUB_TEXT,
    ANDROID_SHORT_CRITICAL_TEXT,
    ANDROID_STATUS_CHIP,
    ANDROID_BIG_TEXT_STYLE,
    ANDROID_CALL_STYLE,
    ANDROID_PROGRESS_STYLE,
    ANDROID_METRIC_STYLE
}

data class SamsungNowBarExtrasState(
    val style: Int? = null,
    val text: SamsungNowBarTextState? = null,
    val visuals: SamsungNowBarVisualState? = null,
    val action: SamsungNowBarActionState? = null,
    val progress: SamsungNowBarProgressState? = null,
    val chronometer: SamsungNowBarChronometerViewState? = null,
    val capsule: SamsungNowBarCapsuleState? = null,
    val remoteApp: SamsungRemoteAppConfig? = null
)

data class SamsungNowBarTextState(
    val primaryInfo: CharSequence? = null,
    val secondaryInfo: CharSequence? = null,
    val nowBarPrimaryInfo: CharSequence? = null,
    val nowBarSecondaryInfo: CharSequence? = null,
    val chipExpandedText: CharSequence? = null,
    val substName: CharSequence? = null
)

data class SamsungNowBarVisualState(
    val chipBackgroundColor: Int? = null,
    val chipIcon: Icon? = null,
    val showSmallIcon: Boolean? = null,
    val nowBarIcon: Icon? = null,
    val actionBackgroundColor: Int? = null,
    val firstIcon: Icon? = null,
    val secondIcon: Icon? = null,
    val secondaryInfoIcon: Icon? = null
)

data class SamsungNowBarActionState(
    val actionType: Int? = null,
    val actionPrimarySet: Int? = null,
    val subScreenIntent: PendingIntent? = null
)

data class SamsungNowBarProgressState(
    val current: Int? = null,
    val max: Int? = null,
    val color: Int? = null,
    val topLevelSegmentIcon: Icon? = null,
    val segments: List<SamsungNowBarProgressSegmentState> = emptyList()
)

data class SamsungNowBarProgressSegmentState(
    val startPosition: Float? = null,
    val color: Int? = null,
    val icon: Icon? = null
)

data class SamsungNowBarChronometerViewState(
    val remoteView: RemoteViews? = null,
    val tag: CharSequence? = null,
    val viewPosition: Int? = null,
    val nowBarPosition: Int? = null
)

data class SamsungNowBarCapsuleState(
    val enabled: Boolean? = null,
    val layout: RemoteViews? = null,
    val action: PendingIntent? = null,
    val bgStartColor: Int? = null,
    val bgEndColor: Int? = null,
    val priority: String? = null
)

data class AndroidActionState(
    val title: String,
    val hasIcon: Boolean,
    val hasIntent: Boolean,
    val id: String? = null,
    val semantic: ActionSemantic? = null
) {
    val textOnly: Boolean
        get() = !hasIcon

    val disabled: Boolean
        get() = !hasIntent
}

data class NowBarNotificationEvidenceReport(
    val ongoing: Boolean,
    val groupSummary: Boolean,
    val requestPromotedOngoing: Boolean,
    val promotedOngoing: Boolean,
    val hasContentTitle: Boolean = false,
    val hasContentIntent: Boolean = false,
    val hasDeleteIntent: Boolean = false,
    val androidActions: List<AndroidActionState> = emptyList(),
    val subText: String?,
    val shortCriticalText: String?,
    val statusChipWhenTimeMillis: Long? = null,
    val statusChipShowWhen: Boolean = false,
    val statusChipUsesChronometer: Boolean = false,
    val statusChipChronometerCountDown: Boolean = false,
    val template: String?,
    val samsungStyle: Int?,
    val samsungNowBarExtrasCount: Int,
    val samsungNowBar: SamsungNowBarExtrasState? = null,
    val samsungDumpExtrasCount: Int,
    val samsungRemoteViewCount: Int = 0,
    val samsungPdeExtrasCount: Int = 0,
    val progressSegmentCount: Int,
    val hasSamsungProgress: Boolean,
    val hasSamsungProgressSegments: Boolean,
    val hasCapsule: Boolean,
    val hasSamsungRemoteViews: Boolean,
    val samsungViews: SamsungOngoingActivityViews? = null,
    val hasNowBarRemoteView: Boolean = false,
    val hasExpandedRemoteView: Boolean = false,
    val hasChipExpandedView: Boolean = false,
    val hasCustomExpandedCardView: Boolean = false,
    val hasExpandedChipView: Boolean = false,
    val hasExpandedNowBarView: Boolean = false,
    val hasCustomCardViewCenterUi: Boolean = false,
    val hasAodRemoteApp: Boolean,
    val aodRemoteAppName: String? = null,
    val aodRemoteApp: SamsungRemoteAppConfig? = null,
    val hasAodRemoteAppIcon: Boolean = false,
    val hasAodRemoteAppPendingIntent: Boolean = false,
    val samsungDumpShow: Boolean? = null,
    val samsungReducedImages: Boolean? = null,
    val samsungTextExtrasCount: Int = 0,
    val samsungText: SamsungOngoingActivityText? = null,
    val samsungVisualExtrasCount: Int = 0,
    val samsungVisuals: SamsungOngoingActivityVisuals? = null,
    val samsungPrimaryAction: Int? = null,
    val samsungNowBarExpandableType: Int? = null,
    val samsungActionBackgroundColorCount: Int = 0,
    val hasOngoingActivityChipIcon: Boolean = false,
    val hasOngoingActivityBadge: Boolean = false,
    val hasOngoingActivityCardIcon: Boolean = false,
    val samsungChronometerExtrasCount: Int = 0,
    val samsungChronometerState: SamsungOngoingActivityChronometerState? = null,
    val pdeState: SamsungPdeState? = null,
    val nativeOngoingActivityTemplate: Boolean,
    val callStyleTemplate: Boolean,
    val progressStyleTemplate: Boolean,
    val metricStyleTemplate: Boolean,
    val callStyleType: Int? = null,
    val callStyleIsVideo: Boolean? = null,
    val callStyleHasPerson: Boolean = false,
    val callStyleHasAnswerIntent: Boolean = false,
    val callStyleHasDeclineIntent: Boolean = false,
    val callStyleHasHangUpIntent: Boolean = false,
    val callStyleAnswerColor: Int? = null,
    val callStyleDeclineColor: Int? = null,
    val verificationText: String? = null,
    val callStyleHasVerificationIcon: Boolean = false,
    val bigTextStyleTemplate: Boolean = false,
    val hasCustomContentView: Boolean = false,
    val colorized: Boolean = false,
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

    val hasSamsungNowBarEvidence: Boolean
        get() = samsungNowBarExtrasCount > 0 ||
            samsungDumpExtrasCount > 0 ||
            nativeOngoingActivityTemplate

    val hasAndroidLiveUpdateEvidence: Boolean
        get() = requestPromotedOngoing ||
            promotedOngoing ||
            bigTextStyleTemplate ||
            callStyleTemplate ||
            progressStyleTemplate ||
            hasAndroidProgressStylePayload ||
            metricStyleTemplate

    val hasEligibleCoreFields: Boolean
        get() = ongoing &&
            !groupSummary &&
            hasContentTitle &&
            !hasCustomContentView &&
            !colorized

    val likelyNowBarCompatible: Boolean
        get() = hasEligibleCoreFields && (hasSamsungNowBarEvidence || hasAndroidLiveUpdateEvidence)

    val evidencePaths: Set<NowBarEvidencePath>
        get() = buildSet {
            if (samsungNowBarExtrasCount > 0) add(NowBarEvidencePath.SAMSUNG_EXTRAS)
            if (samsungDumpExtrasCount > 0) add(NowBarEvidencePath.SAMSUNG_DUMP_EXTRAS)
            if (nativeOngoingActivityTemplate) add(NowBarEvidencePath.SAMSUNG_NATIVE_TEMPLATE)
            if (hasSamsungRemoteViews) add(NowBarEvidencePath.SAMSUNG_REMOTE_VIEWS)
            if (hasAodRemoteApp) add(NowBarEvidencePath.SAMSUNG_AOD_REMOTE_APP)
            if (samsungTextExtrasCount > 0) add(NowBarEvidencePath.SAMSUNG_TEXT_STATE)
            if (samsungVisualExtrasCount > 0) add(NowBarEvidencePath.SAMSUNG_VISUAL_STATE)
            if (samsungChronometerExtrasCount > 0) add(NowBarEvidencePath.SAMSUNG_CHRONOMETER_STATE)
            if (samsungPdeExtrasCount > 0) add(NowBarEvidencePath.SAMSUNG_PDE_STATE)
            if (hasCapsule) add(NowBarEvidencePath.FOLDABLE_CAPSULE)
            if (requestPromotedOngoing || promotedOngoing) add(NowBarEvidencePath.ANDROID_PROMOTED_ONGOING)
            if (hasContentIntent) add(NowBarEvidencePath.ANDROID_CONTENT_INTENT)
            if (hasDeleteIntent) add(NowBarEvidencePath.ANDROID_DELETE_INTENT)
            if (androidActions.isNotEmpty()) add(NowBarEvidencePath.ANDROID_ACTION_BUTTONS)
            if (hasAndroidProgress) add(NowBarEvidencePath.ANDROID_PROGRESS)
            if (!subText.isNullOrBlank()) add(NowBarEvidencePath.ANDROID_SUB_TEXT)
            if (!shortCriticalText.isNullOrBlank()) add(NowBarEvidencePath.ANDROID_SHORT_CRITICAL_TEXT)
            if (hasStatusChip) add(NowBarEvidencePath.ANDROID_STATUS_CHIP)
            if (bigTextStyleTemplate) add(NowBarEvidencePath.ANDROID_BIG_TEXT_STYLE)
            if (callStyleTemplate) add(NowBarEvidencePath.ANDROID_CALL_STYLE)
            if (progressStyleTemplate || hasAndroidProgressStylePayload) {
                add(NowBarEvidencePath.ANDROID_PROGRESS_STYLE)
            }
            if (metricStyleTemplate) add(NowBarEvidencePath.ANDROID_METRIC_STYLE)
        }

    val missingCoreHints: List<String>
        get() = buildList {
            if (!ongoing) add("not-ongoing")
            if (groupSummary) add("group-summary")
            if (!hasContentTitle) add("missing-content-title")
            if (hasCustomContentView) add("custom-content-view")
            if (colorized) add("colorized")
            if (!hasSamsungNowBarEvidence && !hasAndroidLiveUpdateEvidence) {
                add("no-nowbar-or-live-update-hints")
            }
        }
}
