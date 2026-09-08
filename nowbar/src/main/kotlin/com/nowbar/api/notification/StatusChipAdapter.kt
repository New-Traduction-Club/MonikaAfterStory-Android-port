package com.nowbar.api.notification

import android.annotation.SuppressLint
import android.app.Notification
import androidx.core.app.NotificationCompat
import com.nowbar.api.cards.NowBarCard
import com.nowbar.api.cards.TimerCard

@SuppressLint("NewApi")
internal object StatusChipAdapter {

    fun apply(builder: NotificationCompat.Builder, card: NowBarCard) {
        countdownChronometerWhenTimeMillis(card)?.let { whenTime ->
            builder
                .setWhen(whenTime)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
            return
        }

        card.toChipWhenTimeMillis()?.let { whenTime ->
            builder
                .setWhen(whenTime)
                .setShowWhen(true)
                .setUsesChronometer(card.isChipChronometerCountDown())
                .setChronometerCountDown(card.isChipChronometerCountDown())
            return
        }

        shortCriticalTextFor(card)
            ?.takeIf { it.isNotBlank() }
            ?.let(builder::setShortCriticalText)
    }

    fun apply(builder: Notification.Builder, card: NowBarCard) {
        countdownChronometerWhenTimeMillis(card)?.let { whenTime ->
            builder
                .setWhen(whenTime)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
            return
        }

        card.toChipWhenTimeMillis()?.let { whenTime ->
            builder
                .setWhen(whenTime)
                .setShowWhen(true)
                .setUsesChronometer(card.isChipChronometerCountDown())
                .setChronometerCountDown(card.isChipChronometerCountDown())
            return
        }

        shortCriticalTextFor(card)
            ?.takeIf { it.isNotBlank() }
            ?.let(builder::setShortCriticalText)
    }

    fun shortCriticalTextFor(card: NowBarCard): String? =
        card.toShortCriticalText() ?: ProgressStyleAdapter.getChipText(card)

    private fun countdownChronometerWhenTimeMillis(card: NowBarCard): Long? {
        if (!ProgressStyleAdapter.shouldUseChronometer(card)) return null
        val timer = card as TimerCard
        return System.currentTimeMillis() + timer.remainingDuration.inWholeMilliseconds
    }
}
