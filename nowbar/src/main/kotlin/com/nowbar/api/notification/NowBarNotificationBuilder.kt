package com.nowbar.api.notification

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import com.nowbar.api.FeatureDetector
import com.nowbar.api.NowBarConfig
import com.nowbar.api.cards.CallCard
import com.nowbar.api.cards.CallCardType
import com.nowbar.api.cards.CustomCard
import com.nowbar.api.cards.DeliveryCard
import com.nowbar.api.cards.MediaCard
import com.nowbar.api.cards.MetricCard
import com.nowbar.api.cards.NavigationCard
import com.nowbar.api.cards.NowBarCard
import com.nowbar.api.cards.TimerCard
import com.nowbar.api.cards.WorkoutCard
import com.nowbar.api.fallback.FallbackStrategyResolver
import com.nowbar.api.fallback.StandardNotificationAdapter

/**
 * One builder, one result.
 *
 * Base notification is always standard Android.
 * Then we layer optional capabilities on top:
 * - Samsung extras when the device supports them
 * - promoted ongoing / ProgressStyle on Android 16+
 */
class NowBarNotificationBuilder(
    private val context: Context,
    private val config: NowBarConfig
) {

    private val liveUpdateBuilder = LiveUpdateBuilder()

    fun build(card: NowBarCard): Notification =
        build(card, config.samsungStyle, config.requestPromotedOngoing)

    internal fun build(
        card: NowBarCard,
        samsungStyle: Int,
        requestPromotedOngoing: Boolean
    ): Notification {
        val fallback = FallbackStrategyResolver.resolve(
            strategy = config.fallbackStrategy,
            nativeSurfaceSupported = FeatureDetector.isNativeSurfaceSupported(context)
        )

        if (fallback.useStandardNotificationOnly) {
            return StandardNotificationAdapter.build(context, config.channelId, card)
        }

        val samsungExtras = if (FeatureDetector.canApplySamsungNowBarExtras(context)) {
            buildSamsungExtras(card, samsungStyle)
        } else {
            null
        }

        PlatformMetricNotificationBuilder.build(
            context = context,
            config = config,
            card = card,
            category = categoryFor(card),
            requestPromotedOngoing = requestPromotedOngoing,
            samsungExtras = samsungExtras
        )?.let { return it }

        val builder = NotificationCompat.Builder(context, config.channelId)
            .setSmallIcon(card.icon)
            .setContentTitle(LiveUpdateTextStyler.styleTitle(card.toPrimaryInfo(), card.toSemanticStyle()))
            .setContentText(card.toSecondaryInfo())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(categoryFor(card))

        card.tapAction?.let(builder::setContentIntent)
        card.toDeleteIntent()?.let(builder::setDeleteIntent)
        safeLargeIcon(card)?.let(builder::setLargeIcon)
        card.toSubText()
            ?.takeIf { it.isNotBlank() }
            ?.let(builder::setSubText)
        StatusChipAdapter.apply(builder, card)
        card.accentColor?.let(builder::setColor)

        card.toProgress()?.let { progress ->
            builder.setProgress(card.toProgressMax(), progress, false)
        }
        if (card.isProgressIndeterminate()) {
            builder.setProgress(card.toProgressMax(), 0, true)
        }

        BigTextStyleAdapter.apply(builder, card)

        applyCardDetails(builder, card)

        liveUpdateBuilder.apply(
            builder = builder,
            card = card,
            requestPromotedOngoing = requestPromotedOngoing
        )

        samsungExtras?.let(builder::addExtras)

        // Set substName and showSmallIcon directly on notification extras
        // (Samsung Clock sets these on the builder extras separately from the ongoing bundle)
        card.toSubstName()?.let { name ->
            builder.addExtras(android.os.Bundle().apply {
                putCharSequence("android.substName", name)
            })
        }
        builder.addExtras(android.os.Bundle().apply {
            putBoolean("android.showSmallIcon", config.showSmallIcon)
        })

        return builder.build()
    }

    private fun buildSamsungExtras(
        card: NowBarCard,
        samsungStyle: Int
    ) = OngoingExtrasBuilder()
        .setStyle(samsungStyle)
        .setActionType(OngoingExtrasBuilder.ACTION_TYPE_BUTTON_TEXT)
        .setShowSmallIcon(config.showSmallIcon)
        .setChipConfig(
            ChipConfig(
                icon = safeIcon(card),
                backgroundColor = card.toChipBackgroundColor(),
                expandedText = card.toChipText()
            )
        )
        .setPrimaryInfo(card.toPrimaryInfo())
        // Action primary set for collapsed Now Bar buttons
        .setActionPrimarySet(card.toActionPrimarySet())
        .apply {
            val secondary = card.toSecondaryInfo()
            if (secondary.isNotBlank()) setSecondaryInfo(secondary)

            card.toNowBarSecondaryInfo()
                ?.takeIf { it.isNotBlank() }
                ?.let(::setNowBarSecondaryInfo)

            card.toProgress()?.let { setProgress(it, card.toProgressMax()) }

            samsungProgressColor(card)?.let(::setProgressColor)

            samsungProgressIcon(card)?.let(::setProgressSegmentIcon)

            samsungSegments(card)
                .takeIf { it.isNotEmpty() }
                ?.let(::setProgressSegments)

            card.toNowBarPrimaryInfo()
                ?.takeIf { it.isNotBlank() }
                ?.let(::setNowBarPrimaryInfo)

            safeNowBarIcon(card)?.let(::setNowBarIcon)
            safeSecondIcon(card)?.let(::setSecondIcon)
            safeFirstIcon(card)?.let(::setFirstIcon)
            safeSecondaryInfoIcon(card)?.let(::setSecondaryInfoIcon)
            card.toActionBgColor()?.let(::setActionBgColor)
            config.actionPrimarySet?.let(::setActionPrimarySet)
            if (card.hasChronometerSupport()) {
                config.chronometerConfig?.let(::setChronometerConfig)
            }
            config.capsuleConfig?.let(::setCapsuleConfig)
            config.aodRemoteApp?.let(::setAodRemoteApp)

            card.toSubstName()?.takeIf { it.isNotBlank() }?.let(::setSubstName)
            card.toNowBarSubScreenIntent()?.let(::setNowBarSubScreenIntent)
        }
        .build()

    private fun safeIcon(card: NowBarCard): Icon? {
        return runCatching { card.toChipIcon().toIcon(context) }.getOrNull()
    }

    private fun safeNowBarIcon(card: NowBarCard): Icon? {
        return card.toNowBarIcon()?.let { icon ->
            runCatching { icon.toIcon(context) }.getOrNull()
        }
    }

    private fun safeSecondIcon(card: NowBarCard): Icon? {
        return card.toSecondIcon()?.let { icon ->
            runCatching { icon.toIcon(context) }.getOrNull()
        }
    }

    private fun safeLargeIcon(card: NowBarCard): Icon? {
        return card.toLargeIcon()?.let { icon ->
            runCatching { icon.toIcon(context) }.getOrNull()
        }
    }

    private fun safeFirstIcon(card: NowBarCard): Icon? {
        return card.toFirstIcon()?.let { icon ->
            runCatching { icon.toIcon(context) }.getOrNull()
        }
    }

    private fun safeSecondaryInfoIcon(card: NowBarCard): Icon? {
        return card.toSecondaryInfoIcon()?.let { icon ->
            runCatching { icon.toIcon(context) }.getOrNull()
        }
    }

    private fun samsungProgressColor(card: NowBarCard): Int? = when (card) {
        is CustomCard -> card.customProgressColor ?: card.accentColor
        else -> card.accentColor
    }

    private fun samsungProgressIcon(card: NowBarCard): Icon? = when (card) {
        is WorkoutCard -> safeIcon(card)
        is NavigationCard -> card.turnIcon?.let { icon ->
            runCatching { icon.toIcon(context) }.getOrNull()
        }
        is DeliveryCard -> (card.trackerIcon ?: card.icon).let { icon ->
            runCatching { icon.toIcon(context) }.getOrNull()
        }
        is CustomCard -> card.progressTrackerIcon?.let { icon ->
            runCatching { icon.toIcon(context) }.getOrNull()
        }
        else -> null
    }

    /**
     * Keep Samsung Health-like segmented workout bars by default.
     * Real custom segment APIs are still preserved in OngoingExtrasBuilder for power users.
     */
    private fun samsungSegments(card: NowBarCard): List<ProgressSegment> {
        return when (card) {
            is CustomCard -> customSamsungSegments(card)
            is DeliveryCard -> styleSegmentsToSamsungSegments(ProgressStyleAdapter.adapt(card).segments)
            is WorkoutCard -> {
                val color = samsungProgressColor(card) ?: return emptyList()
                listOf(
                    ProgressSegment(startPosition = 0.0f, color = color),
                    ProgressSegment(startPosition = 0.34f, color = color),
                    ProgressSegment(startPosition = 0.67f, color = color)
                )
            }
            else -> emptyList()
        }
    }

    private fun customSamsungSegments(card: CustomCard): List<ProgressSegment> {
        return styleSegmentsToSamsungSegments(card.progressSegments)
    }

    private fun styleSegmentsToSamsungSegments(segments: List<StyleSegment>): List<ProgressSegment> {
        if (segments.isEmpty()) return emptyList()

        val totalLength = segments.sumOf { it.length }
        if (totalLength <= 0) return emptyList()

        var start = 0
        return segments.map { segment ->
            val startPosition = (start.toFloat() / totalLength).coerceIn(0f, 1f)
            start += segment.length
            ProgressSegment(startPosition = startPosition, color = segment.color)
        }
    }

    private fun applyCardDetails(
        builder: NotificationCompat.Builder,
        card: NowBarCard
    ) {
        card.toActions().take(NowBarActionLimits.MAX_ACTIONS).forEach { action ->
            builder.addAction(action.toCompatAction())
        }

        when (card) {
            is MediaCard -> {
                card.albumArt?.let(builder::setLargeIcon)

                card.playAction?.let { action ->
                    builder.addAction(
                        0,
                        if (card.isPlaying) "Pause" else "Play",
                        action
                    )
                }

                card.skipAction?.let { action ->
                    builder.addAction(0, "Next", action)
                }
            }

            is CallCard -> {
                if (CallStyleAdapter.apply(context, builder, card)) return

                when (card.callType) {
                    CallCardType.SCREENING -> {
                        card.hangupAction?.let { builder.addAction(0, "Hang up", it) }
                        card.answerAction?.let { builder.addAction(0, "Answer", it) }
                    }
                    CallCardType.INCOMING -> {
                        card.answerAction?.let { builder.addAction(0, "Answer", it) }
                        card.declineAction?.let { builder.addAction(0, "Decline", it) }
                    }
                    CallCardType.ONGOING -> {
                        card.hangupAction?.let { builder.addAction(0, "Hang up", it) }
                    }
                }
            }

            else -> Unit
        }
    }

    private fun categoryFor(card: NowBarCard): String = when (card) {
        is TimerCard -> NotificationCompat.CATEGORY_STOPWATCH
        is WorkoutCard -> NotificationCompat.CATEGORY_WORKOUT
        is MediaCard -> NotificationCompat.CATEGORY_TRANSPORT
        is NavigationCard -> NotificationCompat.CATEGORY_NAVIGATION
        is DeliveryCard -> NotificationCompat.CATEGORY_PROGRESS
        is MetricCard -> NotificationCompat.CATEGORY_PROGRESS
        is CallCard -> NotificationCompat.CATEGORY_CALL
        else -> NotificationCompat.CATEGORY_PROGRESS
    }
}
