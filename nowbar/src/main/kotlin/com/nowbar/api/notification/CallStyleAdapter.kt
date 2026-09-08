package com.nowbar.api.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.nowbar.api.cards.CallCard
import com.nowbar.api.cards.CallCardType

internal object CallStyleAdapter {

    fun apply(
        context: Context,
        builder: NotificationCompat.Builder,
        card: CallCard
    ): Boolean {
        val person = Person.Builder()
            .setName(card.callerName)
            .apply {
                card.callerUri?.let(::setUri)
            }
            .build()

        val style = when {
            card.callType == CallCardType.SCREENING &&
                card.answerAction != null &&
                card.hangupAction != null ->
                NotificationCompat.CallStyle.forScreeningCall(
                    person,
                    card.hangupAction,
                    card.answerAction
                )

            card.callType == CallCardType.INCOMING &&
                card.answerAction != null &&
                card.declineAction != null ->
                NotificationCompat.CallStyle.forIncomingCall(
                    person,
                    card.declineAction,
                    card.answerAction
                )

            card.callType == CallCardType.ONGOING && card.hangupAction != null ->
                NotificationCompat.CallStyle.forOngoingCall(person, card.hangupAction)

            else -> return false
        }

        style.setIsVideo(card.isVideo)
        card.verificationText?.let(style::setVerificationText)
        card.verificationIcon
            ?.let { icon -> runCatching { icon.toIcon(context) }.getOrNull() }
            ?.let(style::setVerificationIcon)
        card.answerButtonColor?.let(style::setAnswerButtonColorHint)
        card.declineButtonColor?.let(style::setDeclineButtonColorHint)

        builder.setStyle(style)
        return true
    }
}
