package com.nowbar.api.notification

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.app.NotificationCompat
import com.nowbar.api.cards.NowBarCard

/**
 * Thin AndroidX wrapper for Android 16 Live Updates / promoted ongoing notifications.
 *
 * No reflection, no hidden magic:
 * - request promoted ongoing,
 * - attach ProgressStyle when progress exists,
 * - configure a chip via short text or chronometer.
 */
class LiveUpdateBuilder {

    companion object {
        @JvmStatic
        @ChecksSdkIntAtLeast(api = 36)
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 36
    }

    fun apply(
        builder: NotificationCompat.Builder,
        card: NowBarCard,
        requestPromotedOngoing: Boolean = true
    ) {
        if (!isSupported() || !requestPromotedOngoing) return

        builder.setRequestPromotedOngoing(true)

        val progress = card.toProgress()
        if (progress != null || card.isProgressIndeterminate()) {
            val config = ProgressStyleAdapter.adapt(card)

            val style = NotificationCompat.ProgressStyle()
                .setStyledByProgress(config.styledByProgress)

            if (card.isProgressIndeterminate()) {
                style.setProgressIndeterminate(true)
            } else {
                style.setProgress(progress ?: 0)
            }

            if (config.segments.isNotEmpty()) {
                style.setProgressSegments(
                    config.segments.map { segment ->
                        NotificationCompat.ProgressStyle.Segment(segment.length)
                            .setColor(segment.color)
                            .setId(segment.id)
                    }
                )
            }

            if (config.points.isNotEmpty()) {
                style.setProgressPoints(
                    config.points.map { point ->
                        NotificationCompat.ProgressStyle.Point(point.position)
                            .setColor(point.color)
                            .setId(point.id)
                    }
                )
            }

            config.trackerIcon?.let(style::setProgressTrackerIcon)
            config.startIcon?.let(style::setProgressStartIcon)
            config.endIcon?.let(style::setProgressEndIcon)

            builder.setStyle(style)
        }

        StatusChipAdapter.apply(builder, card)
    }

    internal fun shortCriticalTextFor(card: NowBarCard): String? =
        StatusChipAdapter.shortCriticalTextFor(card)
}
