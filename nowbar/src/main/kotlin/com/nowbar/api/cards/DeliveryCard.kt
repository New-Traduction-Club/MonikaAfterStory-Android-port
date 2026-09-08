package com.nowbar.api.cards

import android.app.PendingIntent
import androidx.core.graphics.drawable.IconCompat
import com.nowbar.api.notification.ActionConfig
import com.nowbar.api.notification.LiveUpdateSemanticStyle

enum class DeliveryStatus(
    val defaultProgress: Int?,
    val titleText: String,
    val chipText: String,
    val semanticStyle: LiveUpdateSemanticStyle
) {
    INITIALIZING(null, "Order is being placed", "Placing", LiveUpdateSemanticStyle.INFO),
    CONFIRMED(10, "Order confirmed", "Confirmed", LiveUpdateSemanticStyle.SAFE),
    PREPARING(25, "Order is being prepared", "Prepping", LiveUpdateSemanticStyle.INFO),
    READY_FOR_PICKUP(40, "Order is ready for pickup", "Ready", LiveUpdateSemanticStyle.INFO),
    EN_ROUTE(60, "Order is on its way", "En route", LiveUpdateSemanticStyle.INFO),
    ARRIVING(80, "Order is arriving", "Arriving", LiveUpdateSemanticStyle.CAUTION),
    DELIVERED(100, "Order delivered", "Delivered", LiveUpdateSemanticStyle.SAFE)
}

data class DeliveryCard(
    override val title: String,
    override val icon: IconCompat,
    override val accentColor: Int? = null,
    override val tapAction: PendingIntent? = null,
    override val chipText: String? = null,
    val status: DeliveryStatus,
    val merchantName: String,
    val eta: String? = null,
    val destination: String? = null,
    val courierName: String? = null,
    val progress: Int? = status.defaultProgress,
    val trackerIcon: IconCompat? = null,
    val startIcon: IconCompat? = null,
    val endIcon: IconCompat? = null,
    val actions: List<ActionConfig> = emptyList(),
    val chipWhenTimeMillis: Long? = null,
    val chipChronometerCountDown: Boolean = chipWhenTimeMillis != null,
    val shortCriticalText: String? = null,
    override val deleteIntent: PendingIntent? = null,
    override val largeIcon: IconCompat? = null
) : NowBarCard(
    type = CardType.DELIVERY,
    title = title,
    icon = icon,
    accentColor = accentColor,
    tapAction = tapAction,
    chipText = chipText,
    deleteIntent = deleteIntent,
    largeIcon = largeIcon
) {
    override fun toPrimaryInfo(): String = status.titleText

    override fun toSecondaryInfo(): String {
        val parts = buildList {
            eta?.let { add("ETA $it") }
            courierName?.let { add(it) }
            destination?.let { add(it) }
        }
        return parts.joinToString(" | ").ifBlank { merchantName }
    }

    override fun toNowBarPrimaryInfo(): String = chipText ?: status.chipText

    override fun toNowBarSecondaryInfo(): String = eta ?: courierName ?: merchantName

    override fun toChipText(): String = chipText ?: eta ?: status.chipText

    override fun toShortCriticalText(): String =
        shortCriticalText?.takeIf { it.isNotBlank() } ?: toChipText()

    override fun toSubText(): String = merchantName

    override fun toChipWhenTimeMillis(): Long? = chipWhenTimeMillis

    override fun isChipChronometerCountDown(): Boolean = chipChronometerCountDown

    override fun toProgress(): Int? = progress?.coerceIn(0, toProgressMax())

    override fun isProgressIndeterminate(): Boolean = progress == null

    override fun toNowBarIcon(): IconCompat? = trackerIcon

    override fun toSecondIcon(): IconCompat? = trackerIcon

    override fun toSemanticStyle(): LiveUpdateSemanticStyle = status.semanticStyle

    override fun toSubstName(): String = title

    override fun toActions(): List<ActionConfig> = actions

    class Builder(
        private val title: String,
        private val icon: IconCompat,
        private val merchantName: String,
        private val status: DeliveryStatus
    ) {
        private var accentColor: Int? = null
        private var tapAction: PendingIntent? = null
        private var chipText: String? = null
        private var shortCriticalText: String? = null
        private var deleteIntent: PendingIntent? = null
        private var largeIcon: IconCompat? = null
        private var eta: String? = null
        private var destination: String? = null
        private var courierName: String? = null
        private var progress: Int? = status.defaultProgress
        private var trackerIcon: IconCompat? = null
        private var startIcon: IconCompat? = null
        private var endIcon: IconCompat? = null
        private var chipWhenTimeMillis: Long? = null
        private var chipChronometerCountDown: Boolean = true
        private val actions: MutableList<ActionConfig> = mutableListOf()

        fun accentColor(color: Int) = apply { this.accentColor = color }
        fun tapAction(action: PendingIntent) = apply { this.tapAction = action }
        fun chipText(text: String) = apply { this.chipText = text }
        fun shortCriticalText(text: String) = apply { this.shortCriticalText = text }
        fun deleteIntent(intent: PendingIntent) = apply { this.deleteIntent = intent }
        fun largeIcon(icon: IconCompat) = apply { this.largeIcon = icon }
        fun eta(eta: String) = apply { this.eta = eta }
        fun destination(destination: String) = apply { this.destination = destination }
        fun courierName(name: String) = apply { this.courierName = name }
        fun progress(progress: Int?) = apply { this.progress = progress }
        fun trackerIcon(icon: IconCompat) = apply { this.trackerIcon = icon }
        fun startIcon(icon: IconCompat) = apply { this.startIcon = icon }
        fun endIcon(icon: IconCompat) = apply { this.endIcon = icon }
        fun chipWhenTimeMillis(timestampMillis: Long, countDown: Boolean = true) = apply {
            this.chipWhenTimeMillis = timestampMillis
            this.chipChronometerCountDown = countDown
        }
        fun action(action: ActionConfig) = apply { this.actions += action }
        fun actions(actions: List<ActionConfig>) = apply {
            this.actions.clear()
            this.actions += actions
        }

        fun build(): DeliveryCard = DeliveryCard(
            title = title,
            icon = icon,
            accentColor = accentColor,
            tapAction = tapAction,
            chipText = chipText,
            shortCriticalText = shortCriticalText,
            status = status,
            merchantName = merchantName,
            eta = eta,
            destination = destination,
            courierName = courierName,
            progress = progress,
            trackerIcon = trackerIcon,
            startIcon = startIcon,
            endIcon = endIcon,
            actions = actions.toList(),
            chipWhenTimeMillis = chipWhenTimeMillis,
            chipChronometerCountDown = chipChronometerCountDown,
            deleteIntent = deleteIntent,
            largeIcon = largeIcon
        )

        companion object {
            @JvmStatic
            fun create(
                title: String,
                icon: IconCompat,
                merchantName: String,
                status: DeliveryStatus
            ) = Builder(title, icon, merchantName, status)
        }
    }
}
