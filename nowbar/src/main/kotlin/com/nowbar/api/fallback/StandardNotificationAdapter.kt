package com.nowbar.api.fallback

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import com.nowbar.api.cards.CallCard
import com.nowbar.api.cards.CallCardType
import com.nowbar.api.cards.DeliveryCard
import com.nowbar.api.cards.MediaCard
import com.nowbar.api.cards.MetricCard
import com.nowbar.api.cards.NavigationCard
import com.nowbar.api.cards.NowBarCard
import com.nowbar.api.cards.TimerCard
import com.nowbar.api.cards.WorkoutCard
import com.nowbar.api.notification.BigTextStyleAdapter
import com.nowbar.api.notification.CallStyleAdapter
import com.nowbar.api.notification.LiveUpdateTextStyler
import com.nowbar.api.notification.NowBarActionLimits
import com.nowbar.api.notification.StatusChipAdapter
import com.nowbar.api.notification.toCompatAction

object StandardNotificationAdapter {

    fun build(context: Context, channelId: String, card: NowBarCard): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(card.icon)
            .setContentTitle(LiveUpdateTextStyler.styleTitle(card.toPrimaryInfo(), card.toSemanticStyle()))
            .setContentText(card.toSecondaryInfo())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(getCategoryForCard(card))

        card.accentColor?.let(builder::setColor)
        card.tapAction?.let(builder::setContentIntent)
        card.toDeleteIntent()?.let(builder::setDeleteIntent)
        safeLargeIcon(context, card)?.let(builder::setLargeIcon)

        (card.toSubText() ?: card.toNowBarPrimaryInfo())
            ?.takeIf { it.isNotBlank() && it != card.toPrimaryInfo() }
            ?.let(builder::setSubText)

        card.toProgress()?.let { progress ->
            builder.setProgress(card.toProgressMax(), progress, false)
        }
        if (card.isProgressIndeterminate()) {
            builder.setProgress(card.toProgressMax(), 0, true)
        }

        BigTextStyleAdapter.apply(builder, card)

        StatusChipAdapter.apply(builder, card)

        card.toActions().take(NowBarActionLimits.MAX_ACTIONS).forEach { action ->
            builder.addAction(action.toCompatAction())
        }

        when (card) {
            is MediaCard -> {
                card.albumArt?.let(builder::setLargeIcon)

                card.playAction?.let { action ->
                    builder.addAction(0, if (card.isPlaying) "Pause" else "Play", action)
                }
                card.skipAction?.let { action ->
                    builder.addAction(0, "Next", action)
                }
            }

            is CallCard -> {
                if (!CallStyleAdapter.apply(context, builder, card)) {
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
            }

            else -> Unit
        }

        return builder.build()
    }

    private fun safeLargeIcon(context: Context, card: NowBarCard): Icon? =
        card.toLargeIcon()?.let { icon ->
            runCatching { icon.toIcon(context) }.getOrNull()
        }

    private fun getCategoryForCard(card: NowBarCard): String = when (card) {
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
