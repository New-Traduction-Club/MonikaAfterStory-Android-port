package com.nowbar.api.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.graphics.drawable.IconCompat
import com.nowbar.api.NowBarConfig
import com.nowbar.api.cards.NowBarCard
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@SuppressLint("NewApi", "PrivateApi")
internal object PlatformMetricNotificationBuilder {

    fun build(
        context: Context,
        config: NowBarConfig,
        card: NowBarCard,
        category: String,
        requestPromotedOngoing: Boolean,
        samsungExtras: Bundle?
    ): Notification? {
        val metricStyle = card.toMetricStyle() ?: return null
        if (Build.VERSION.SDK_INT < 37) return null

        return runCatching {
            val builder = Notification.Builder(context, config.channelId)
                .setSmallIcon(card.icon.toIcon(context))
                .setContentTitle(LiveUpdateTextStyler.styleTitle(card.toPrimaryInfo(), card.toSemanticStyle()))
                .setContentText(card.toSecondaryInfo())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(category)
                .setStyle(buildPlatformMetricStyle(metricStyle))

            card.toSubText()?.let(builder::setSubText)
            card.tapAction?.let(builder::setContentIntent)
            card.toDeleteIntent()?.let(builder::setDeleteIntent)
            card.toLargeIcon()?.toSafeIcon(context)?.let(builder::setLargeIcon)
            card.accentColor?.let(builder::setColor)

            applyPromotionRequest(builder, requestPromotedOngoing)
            StatusChipAdapter.apply(builder, card)
            applyActions(context, builder, card)

            samsungExtras?.let(builder::addExtras)
            builder.addExtras(Bundle().apply {
                card.toSubstName()?.let { putCharSequence("android.substName", it) }
                putBoolean("android.showSmallIcon", config.showSmallIcon)
            })

            builder.build()
        }.getOrNull()
    }

    private fun buildPlatformMetricStyle(style: LiveUpdateMetricStyle): Notification.Style {
        val metricStyleClass = Class.forName("android.app.Notification\$MetricStyle")
        val platformStyle = metricStyleClass.getConstructor().newInstance()
        val addMetric = metricStyleClass.getMethod(
            "addMetric",
            Class.forName("android.app.Notification\$Metric")
        )
        val setCriticalMetric = metricStyleClass.getMethod(
            "setCriticalMetric",
            Int::class.javaPrimitiveType
        )

        style.metrics.forEach { metric ->
            addMetric.invoke(platformStyle, buildPlatformMetric(metric))
        }
        setCriticalMetric.invoke(platformStyle, style.criticalMetricIndex)

        return platformStyle as Notification.Style
    }

    private fun buildPlatformMetric(metric: LiveUpdateMetric): Any {
        val metricClass = Class.forName("android.app.Notification\$Metric")
        val metricValueClass = Class.forName("android.app.Notification\$Metric\$MetricValue")
        return metricClass
            .getConstructor(metricValueClass, CharSequence::class.java, Int::class.javaPrimitiveType)
            .newInstance(
                buildPlatformMetricValue(metric.value),
                metric.label,
                metric.semanticStyle.platformValue
            )
    }

    private fun buildPlatformMetricValue(value: LiveUpdateMetricValue): Any =
        when (value) {
            is LiveUpdateMetricValue.FixedInt -> constructMetricValue(
                "FixedInt",
                arrayOf(Int::class.javaPrimitiveType, CharSequence::class.java),
                value.value,
                value.unit
            )

            is LiveUpdateMetricValue.FixedFloat -> constructMetricValue(
                "FixedFloat",
                arrayOf(
                    Float::class.javaPrimitiveType,
                    CharSequence::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ),
                value.value,
                value.unit,
                value.minFractionDigits,
                value.maxFractionDigits
            )

            is LiveUpdateMetricValue.FixedText -> constructMetricValue(
                "FixedText",
                arrayOf(CharSequence::class.java, CharSequence::class.java),
                value.value,
                value.unit
            )

            is LiveUpdateMetricValue.Timer -> invokeTimeDifference(
                "forTimer",
                Instant::class.java,
                value.endTime,
                value.format.platformValue
            )

            is LiveUpdateMetricValue.Stopwatch -> invokeTimeDifference(
                "forStopwatch",
                Instant::class.java,
                value.startTime,
                value.format.platformValue
            )

            is LiveUpdateMetricValue.ElapsedRealtimeTimer -> invokeTimeDifference(
                "forTimer",
                Long::class.javaPrimitiveType,
                value.endElapsedRealtimeMillis,
                value.format.platformValue
            )

            is LiveUpdateMetricValue.ElapsedRealtimeStopwatch -> invokeTimeDifference(
                "forStopwatch",
                Long::class.javaPrimitiveType,
                value.startElapsedRealtimeMillis,
                value.format.platformValue
            )

            is LiveUpdateMetricValue.PausedTimer -> invokeTimeDifference(
                "forPausedTimer",
                Duration::class.java,
                value.remainingTime,
                value.format.platformValue
            )

            is LiveUpdateMetricValue.PausedStopwatch -> invokeTimeDifference(
                "forPausedStopwatch",
                Duration::class.java,
                value.elapsedTime,
                value.format.platformValue
            )

            is LiveUpdateMetricValue.FixedDate -> constructMetricValue(
                "FixedDate",
                arrayOf(LocalDate::class.java, Int::class.javaPrimitiveType),
                value.value,
                value.format.platformValue
            )

            is LiveUpdateMetricValue.FixedTime -> constructMetricValue(
                "FixedTime",
                arrayOf(LocalTime::class.java),
                value.value
            )
        }

    private fun constructMetricValue(
        className: String,
        parameterTypes: Array<Class<*>?>,
        vararg args: Any?
    ): Any {
        val valueClass = Class.forName("android.app.Notification\$Metric\$$className")
        return valueClass.getConstructor(*parameterTypes).newInstance(*args)
    }

    private fun invokeTimeDifference(
        methodName: String,
        valueType: Class<*>?,
        value: Any,
        format: Int
    ): Any {
        val timeDifferenceClass = Class.forName("android.app.Notification\$Metric\$TimeDifference")
        return timeDifferenceClass
            .getMethod(methodName, valueType, Int::class.javaPrimitiveType)
            .invoke(null, value, format)
    }

    private fun applyPromotionRequest(
        builder: Notification.Builder,
        requestPromotedOngoing: Boolean
    ) {
        if (!requestPromotedOngoing) return

        val applied = runCatching {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        }.isSuccess

        if (!applied) {
            builder.addExtras(Bundle().apply {
                putBoolean(LiveUpdateDiagnostics.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            })
        }
    }

    private fun applyActions(
        context: Context,
        builder: Notification.Builder,
        card: NowBarCard
    ) {
        card.toActions().take(NowBarActionLimits.MAX_ACTIONS).forEach { action ->
            builder.addAction(action.toPlatformAction(context))
        }
    }

    private fun IconCompat.toSafeIcon(context: Context): Icon? =
        runCatching { toIcon(context) }.getOrNull()
}
