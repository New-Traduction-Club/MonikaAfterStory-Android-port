package com.nowbar.api.cards

import android.app.PendingIntent
import androidx.core.graphics.drawable.IconCompat
import kotlin.time.Duration

enum class CallCardType {
    INCOMING,
    ONGOING,
    SCREENING
}

data class CallCard(
    override val title: String,
    override val icon: IconCompat,
    override val accentColor: Int? = null,
    override val tapAction: PendingIntent? = null,
    override val chipText: String? = null,
    val callerName: String,
    val callerNumber: String? = null,
    val isIncoming: Boolean = true,
    val callDuration: Duration? = null,
    val answerAction: PendingIntent? = null,
    val declineAction: PendingIntent? = null,
    val hangupAction: PendingIntent? = null,
    val secondaryInfoIcon: IconCompat? = null,
    override val deleteIntent: PendingIntent? = null,
    override val largeIcon: IconCompat? = null,
    val callerUri: String? = callerNumber?.let { "tel:$it" },
    val isVideo: Boolean = false,
    val verificationText: CharSequence? = null,
    val verificationIcon: IconCompat? = null,
    val answerButtonColor: Int? = null,
    val declineButtonColor: Int? = null,
    val callType: CallCardType = if (isIncoming) CallCardType.INCOMING else CallCardType.ONGOING
) : NowBarCard(
    type = CardType.CALL,
    title = title,
    icon = icon,
    accentColor = accentColor,
    tapAction = tapAction,
    chipText = chipText,
    deleteIntent = deleteIntent,
    largeIcon = largeIcon
) {
    override fun toPrimaryInfo(): String = callerName

    override fun toSecondaryInfo(): String {
        callDuration?.let { duration ->
            val totalSeconds = duration.inWholeSeconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }

        return when (callType) {
            CallCardType.SCREENING -> "Screening call"
            else -> if (isIncoming) "Incoming call" else "Outgoing call"
        }
    }

    override fun toNowBarSecondaryInfo(): String = toSecondaryInfo()

    override fun toNowBarPrimaryInfo(): String = callerName

    override fun toSubstName(): String = title

    override fun toSecondaryInfoIcon(): IconCompat? = secondaryInfoIcon

    class Builder(
        private val title: String,
        private val icon: IconCompat,
        private val callerName: String
    ) {
        private var accentColor: Int? = null
        private var tapAction: PendingIntent? = null
        private var chipText: String? = null
        private var deleteIntent: PendingIntent? = null
        private var largeIcon: IconCompat? = null
        private var callerNumber: String? = null
        private var isIncoming: Boolean = true
        private var callDuration: Duration? = null
        private var answerAction: PendingIntent? = null
        private var declineAction: PendingIntent? = null
        private var hangupAction: PendingIntent? = null
        private var secondaryInfoIcon: IconCompat? = null
        private var callerUri: String? = null
        private var isVideo: Boolean = false
        private var verificationText: CharSequence? = null
        private var verificationIcon: IconCompat? = null
        private var answerButtonColor: Int? = null
        private var declineButtonColor: Int? = null
        private var selectedCallType: CallCardType? = null

        fun accentColor(color: Int) = apply { this.accentColor = color }
        fun tapAction(action: PendingIntent) = apply { this.tapAction = action }
        fun chipText(text: String) = apply { this.chipText = text }
        fun deleteIntent(intent: PendingIntent) = apply { this.deleteIntent = intent }
        fun largeIcon(icon: IconCompat) = apply { this.largeIcon = icon }
        fun callerNumber(number: String) = apply {
            this.callerNumber = number
            if (callerUri == null) callerUri = "tel:$number"
        }
        fun callerUri(uri: String) = apply { this.callerUri = uri }
        fun isIncoming(incoming: Boolean) = apply {
            this.isIncoming = incoming
            this.selectedCallType = if (incoming) CallCardType.INCOMING else CallCardType.ONGOING
        }
        fun callType(type: CallCardType) = apply {
            this.selectedCallType = type
            this.isIncoming = type != CallCardType.ONGOING
        }
        fun screeningCall() = callType(CallCardType.SCREENING)
        fun screeningCall(answerAction: PendingIntent, hangupAction: PendingIntent) = apply {
            callType(CallCardType.SCREENING)
            this.answerAction = answerAction
            this.hangupAction = hangupAction
        }
        fun isVideo(video: Boolean = true) = apply { this.isVideo = video }
        fun callDuration(duration: Duration) = apply { this.callDuration = duration }
        fun answerAction(action: PendingIntent) = apply { this.answerAction = action }
        fun declineAction(action: PendingIntent) = apply { this.declineAction = action }
        fun hangupAction(action: PendingIntent) = apply { this.hangupAction = action }
        fun secondaryInfoIcon(icon: IconCompat) = apply { this.secondaryInfoIcon = icon }
        fun verificationText(text: CharSequence) = apply { this.verificationText = text }
        fun verificationIcon(icon: IconCompat) = apply { this.verificationIcon = icon }
        fun answerButtonColor(color: Int) = apply { this.answerButtonColor = color }
        fun declineButtonColor(color: Int) = apply { this.declineButtonColor = color }

        fun build(): CallCard = CallCard(
            title = title,
            icon = icon,
            accentColor = accentColor,
            tapAction = tapAction,
            chipText = chipText,
            callerName = callerName,
            callerNumber = callerNumber,
            isIncoming = isIncoming,
            callDuration = callDuration,
            answerAction = answerAction,
            declineAction = declineAction,
            hangupAction = hangupAction,
            secondaryInfoIcon = secondaryInfoIcon,
            deleteIntent = deleteIntent,
            largeIcon = largeIcon,
            callerUri = callerUri ?: callerNumber?.let { "tel:$it" },
            isVideo = isVideo,
            verificationText = verificationText,
            verificationIcon = verificationIcon,
            answerButtonColor = answerButtonColor,
            declineButtonColor = declineButtonColor,
            callType = selectedCallType ?: if (isIncoming) CallCardType.INCOMING else CallCardType.ONGOING
        )

        companion object {
            @JvmStatic
            fun create(title: String, icon: IconCompat, callerName: String) =
                Builder(title, icon, callerName)
        }
    }
}
