package com.nowbar.api.cards

import android.app.PendingIntent
import androidx.core.graphics.drawable.IconCompat
import com.nowbar.api.notification.ActionConfig
import com.nowbar.api.notification.StylePoint
import com.nowbar.api.notification.StyleSegment

data class CustomCard(
    override val title: String,
    override val icon: IconCompat,
    override val accentColor: Int? = null,
    override val tapAction: PendingIntent? = null,
    override val chipText: String? = null,
    val primaryText: String,
    val shortCriticalText: String? = null,
    val secondaryText: String? = null,
    val nowBarText: String? = null,
    val progressValue: Int? = null,
    val progressMax: Int = 100,
    val customProgressColor: Int? = null,
    val firstIcon: IconCompat? = null,
    val secondaryInfoIcon: IconCompat? = null,
    val subScreenIntent: PendingIntent? = null,
    val actions: List<ActionConfig> = emptyList(),
    override val deleteIntent: PendingIntent? = null,
    override val largeIcon: IconCompat? = null,
    val progressSegments: List<StyleSegment> = emptyList(),
    val progressPoints: List<StylePoint> = emptyList(),
    val progressTrackerIcon: IconCompat? = null,
    val progressStartIcon: IconCompat? = null,
    val progressEndIcon: IconCompat? = null,
    val progressStyledByProgress: Boolean = false,
    val bigText: CharSequence? = null,
    val chipWhenTimeMillis: Long? = null,
    val chipChronometerCountDown: Boolean = false
) : NowBarCard(
    type = CardType.CUSTOM,
    title = title,
    icon = icon,
    accentColor = accentColor,
    tapAction = tapAction,
    chipText = chipText,
    deleteIntent = deleteIntent,
    largeIcon = largeIcon
) {
    init {
        require(progressMax > 0) { "progressMax must be positive" }
    }

    override fun toPrimaryInfo(): String = primaryText

    override fun toSecondaryInfo(): String = secondaryText ?: ""

    override fun toNowBarSecondaryInfo(): String? = nowBarText

    override fun toShortCriticalText(): String? =
        shortCriticalText?.takeIf { it.isNotBlank() } ?: super.toShortCriticalText()

    override fun toProgress(): Int? = progressValue?.coerceIn(0, toProgressMax())

    override fun toProgressMax(): Int =
        progressSegments.sumOf { it.length }.takeIf { it > 0 } ?: progressMax

    override fun toNowBarPrimaryInfo(): String = primaryText

    override fun toSubstName(): String = title

    override fun toFirstIcon(): IconCompat? = firstIcon

    override fun toSecondaryInfoIcon(): IconCompat? = secondaryInfoIcon

    override fun toNowBarSubScreenIntent(): PendingIntent? = subScreenIntent

    override fun toBigText(): CharSequence? =
        bigText?.takeIf { it.isNotBlank() }

    override fun toChipWhenTimeMillis(): Long? = chipWhenTimeMillis

    override fun isChipChronometerCountDown(): Boolean = chipChronometerCountDown

    override fun toActions(): List<ActionConfig> = actions

    class Builder(
        private val title: String,
        private val icon: IconCompat,
        private val primaryText: String
    ) {
        private var accentColor: Int? = null
        private var tapAction: PendingIntent? = null
        private var chipText: String? = null
        private var shortCriticalText: String? = null
        private var deleteIntent: PendingIntent? = null
        private var largeIcon: IconCompat? = null
        private var secondaryText: String? = null
        private var nowBarText: String? = null
        private var progressValue: Int? = null
        private var progressMax: Int = 100
        private var customProgressColor: Int? = null
        private var progressSegments: List<StyleSegment> = emptyList()
        private var progressPoints: List<StylePoint> = emptyList()
        private var progressTrackerIcon: IconCompat? = null
        private var progressStartIcon: IconCompat? = null
        private var progressEndIcon: IconCompat? = null
        private var progressStyledByProgress: Boolean = false
        private var bigText: CharSequence? = null
        private var chipWhenTimeMillis: Long? = null
        private var chipChronometerCountDown: Boolean = false
        private var firstIcon: IconCompat? = null
        private var secondaryInfoIcon: IconCompat? = null
        private var subScreenIntent: PendingIntent? = null
        private val actions: MutableList<ActionConfig> = mutableListOf()

        fun accentColor(color: Int) = apply { this.accentColor = color }
        fun tapAction(action: PendingIntent) = apply { this.tapAction = action }
        fun chipText(text: String) = apply { this.chipText = text }
        fun shortCriticalText(text: String) = apply { this.shortCriticalText = text }
        fun deleteIntent(intent: PendingIntent) = apply { this.deleteIntent = intent }
        fun largeIcon(icon: IconCompat) = apply { this.largeIcon = icon }
        fun secondaryText(text: String) = apply { this.secondaryText = text }
        fun nowBarText(text: String) = apply { this.nowBarText = text }
        fun progressValue(value: Int) = apply { this.progressValue = value }
        fun progressMax(max: Int) = apply { this.progressMax = max }
        fun customProgressColor(color: Int) = apply { this.customProgressColor = color }
        fun progressSegments(segments: List<StyleSegment>) = apply {
            this.progressSegments = segments
        }
        fun progressPoints(points: List<StylePoint>) = apply {
            this.progressPoints = points
        }
        fun progressTrackerIcon(icon: IconCompat) = apply { this.progressTrackerIcon = icon }
        fun progressStartIcon(icon: IconCompat) = apply { this.progressStartIcon = icon }
        fun progressEndIcon(icon: IconCompat) = apply { this.progressEndIcon = icon }
        fun progressStyledByProgress(enabled: Boolean = true) = apply {
            this.progressStyledByProgress = enabled
        }
        fun bigText(text: CharSequence) = apply { this.bigText = text }
        fun chipWhenTimeMillis(timestampMillis: Long, countDown: Boolean = false) = apply {
            this.chipWhenTimeMillis = timestampMillis
            this.chipChronometerCountDown = countDown
        }
        fun firstIcon(icon: IconCompat) = apply { this.firstIcon = icon }
        fun secondaryInfoIcon(icon: IconCompat) = apply { this.secondaryInfoIcon = icon }
        fun subScreenIntent(intent: PendingIntent) = apply { this.subScreenIntent = intent }
        fun action(action: ActionConfig) = apply { this.actions += action }
        fun actions(actions: List<ActionConfig>) = apply {
            this.actions.clear()
            this.actions += actions
        }

        fun build(): CustomCard = CustomCard(
            title = title,
            icon = icon,
            accentColor = accentColor,
            tapAction = tapAction,
            chipText = chipText,
            shortCriticalText = shortCriticalText,
            primaryText = primaryText,
            secondaryText = secondaryText,
            nowBarText = nowBarText,
            progressValue = progressValue,
            progressMax = progressMax,
            customProgressColor = customProgressColor,
            progressSegments = progressSegments,
            progressPoints = progressPoints,
            progressTrackerIcon = progressTrackerIcon,
            progressStartIcon = progressStartIcon,
            progressEndIcon = progressEndIcon,
            progressStyledByProgress = progressStyledByProgress,
            bigText = bigText,
            chipWhenTimeMillis = chipWhenTimeMillis,
            chipChronometerCountDown = chipChronometerCountDown,
            firstIcon = firstIcon,
            secondaryInfoIcon = secondaryInfoIcon,
            subScreenIntent = subScreenIntent,
            actions = actions.toList(),
            deleteIntent = deleteIntent,
            largeIcon = largeIcon
        )

        companion object {
            @JvmStatic
            fun create(title: String, icon: IconCompat, primaryText: String) =
                Builder(title, icon, primaryText)
        }
    }
}
