package com.nowbar.api.cards

import android.app.PendingIntent
import androidx.core.graphics.drawable.IconCompat
import com.nowbar.api.notification.ActionConfig
import com.nowbar.api.notification.LiveUpdateMetricStyle
import com.nowbar.api.notification.LiveUpdateSemanticStyle

data class MetricCard(
    override val title: String,
    override val icon: IconCompat,
    override val accentColor: Int? = null,
    override val tapAction: PendingIntent? = null,
    override val chipText: String? = null,
    val primaryText: String = title,
    val secondaryText: String = "",
    val metricStyle: LiveUpdateMetricStyle,
    val semanticStyle: LiveUpdateSemanticStyle = LiveUpdateSemanticStyle.UNSPECIFIED,
    val actions: List<ActionConfig> = emptyList(),
    val chipWhenTimeMillis: Long? = null,
    val chipChronometerCountDown: Boolean = false,
    val shortCriticalText: String? = null,
    override val deleteIntent: PendingIntent? = null,
    override val largeIcon: IconCompat? = null
) : NowBarCard(
    type = CardType.METRIC,
    title = title,
    icon = icon,
    accentColor = accentColor,
    tapAction = tapAction,
    chipText = chipText,
    deleteIntent = deleteIntent,
    largeIcon = largeIcon
) {
    override fun toPrimaryInfo(): String = primaryText

    override fun toSecondaryInfo(): String = secondaryText

    override fun toChipWhenTimeMillis(): Long? = chipWhenTimeMillis

    override fun toShortCriticalText(): String? =
        shortCriticalText?.takeIf { it.isNotBlank() } ?: super.toShortCriticalText()

    override fun toSubText(): String? = secondaryText.takeIf { it.isNotBlank() }

    override fun isChipChronometerCountDown(): Boolean = chipChronometerCountDown

    override fun toMetricStyle(): LiveUpdateMetricStyle = metricStyle

    override fun toSemanticStyle(): LiveUpdateSemanticStyle = semanticStyle

    override fun toActions(): List<ActionConfig> = actions

    class Builder(
        private val title: String,
        private val icon: IconCompat,
        private val metricStyle: LiveUpdateMetricStyle
    ) {
        private var accentColor: Int? = null
        private var tapAction: PendingIntent? = null
        private var chipText: String? = null
        private var shortCriticalText: String? = null
        private var primaryText: String = title
        private var secondaryText: String = ""
        private var semanticStyle: LiveUpdateSemanticStyle = LiveUpdateSemanticStyle.UNSPECIFIED
        private var chipWhenTimeMillis: Long? = null
        private var chipChronometerCountDown: Boolean = false
        private var deleteIntent: PendingIntent? = null
        private var largeIcon: IconCompat? = null
        private val actions: MutableList<ActionConfig> = mutableListOf()

        fun accentColor(color: Int) = apply { this.accentColor = color }
        fun tapAction(action: PendingIntent) = apply { this.tapAction = action }
        fun chipText(text: String) = apply { this.chipText = text }
        fun shortCriticalText(text: String) = apply { this.shortCriticalText = text }
        fun primaryText(text: String) = apply { this.primaryText = text }
        fun secondaryText(text: String) = apply { this.secondaryText = text }
        fun semanticStyle(style: LiveUpdateSemanticStyle) = apply { this.semanticStyle = style }
        fun deleteIntent(intent: PendingIntent) = apply { this.deleteIntent = intent }
        fun largeIcon(icon: IconCompat) = apply { this.largeIcon = icon }
        fun chipWhenTimeMillis(timestampMillis: Long, countDown: Boolean = false) = apply {
            this.chipWhenTimeMillis = timestampMillis
            this.chipChronometerCountDown = countDown
        }
        fun action(action: ActionConfig) = apply { this.actions += action }
        fun actions(actions: List<ActionConfig>) = apply {
            this.actions.clear()
            this.actions += actions
        }

        fun build(): MetricCard = MetricCard(
            title = title,
            icon = icon,
            accentColor = accentColor,
            tapAction = tapAction,
            chipText = chipText,
            shortCriticalText = shortCriticalText,
            primaryText = primaryText,
            secondaryText = secondaryText,
            metricStyle = metricStyle,
            semanticStyle = semanticStyle,
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
                metricStyle: LiveUpdateMetricStyle
            ) = Builder(title, icon, metricStyle)
        }
    }
}
