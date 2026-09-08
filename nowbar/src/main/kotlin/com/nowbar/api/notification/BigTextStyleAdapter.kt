package com.nowbar.api.notification

import androidx.core.app.NotificationCompat
import com.nowbar.api.cards.NowBarCard

internal object BigTextStyleAdapter {

    fun apply(builder: NotificationCompat.Builder, card: NowBarCard): Boolean {
        val text = card.toBigText()?.takeIf { it.isNotBlank() } ?: return false

        builder.setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle(LiveUpdateTextStyler.styleTitle(card.toPrimaryInfo(), card.toSemanticStyle()))
                .bigText(text)
        )
        return true
    }
}
