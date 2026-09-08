package com.nowbar.api.notification

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Android 17 MetricStyle model for Live Updates.
 *
 * The platform classes are created reflectively so the SDK can still compile against
 * Android 16 while exposing the Android 17 notification template when available.
 */
data class LiveUpdateMetricStyle(
    val metrics: List<LiveUpdateMetric>,
    val criticalMetricIndex: Int = CRITICAL_METRIC_DEFAULT
) {
    init {
        require(metrics.isNotEmpty()) { "MetricStyle requires at least one metric" }
        require(metrics.size <= MAX_METRICS) { "MetricStyle supports at most three metrics" }
        require(criticalMetricIndex == METRIC_INDEX_NONE || criticalMetricIndex in metrics.indices) {
            "criticalMetricIndex must be -1 or a valid metric index"
        }
    }

    companion object {
        const val MAX_METRICS = 3
        const val METRIC_INDEX_NONE = -1
        const val CRITICAL_METRIC_DEFAULT = 0
    }
}

data class LiveUpdateMetric(
    val label: CharSequence,
    val value: LiveUpdateMetricValue,
    val semanticStyle: LiveUpdateSemanticStyle = LiveUpdateSemanticStyle.UNSPECIFIED
) {
    init {
        require(label.isNotBlank()) { "Metric label is required" }
    }
}

enum class LiveUpdateMetricTimeFormat(val platformValue: Int) {
    ADAPTIVE(1),
    CHRONOMETER(2)
}

enum class LiveUpdateMetricDateFormat(val platformValue: Int) {
    AUTOMATIC(0),
    LONG_DATE(1),
    SHORT_DATE(2)
}

sealed class LiveUpdateMetricValue {
    data class FixedInt(
        val value: Int,
        val unit: CharSequence? = null
    ) : LiveUpdateMetricValue()

    data class FixedFloat(
        val value: Float,
        val unit: CharSequence? = null,
        val minFractionDigits: Int = 0,
        val maxFractionDigits: Int = 2
    ) : LiveUpdateMetricValue() {
        init {
            require(value.isFinite()) { "Metric float value must be finite" }
            require(minFractionDigits in 0..6) { "minFractionDigits must be 0..6" }
            require(maxFractionDigits in 0..6) { "maxFractionDigits must be 0..6" }
            require(minFractionDigits <= maxFractionDigits) {
                "minFractionDigits must be <= maxFractionDigits"
            }
        }
    }

    data class FixedText(
        val value: CharSequence,
        val unit: CharSequence? = null
    ) : LiveUpdateMetricValue() {
        init {
            require(value.isNotBlank()) { "Metric text value is required" }
        }
    }

    data class Timer(
        val endTime: Instant,
        val format: LiveUpdateMetricTimeFormat = LiveUpdateMetricTimeFormat.ADAPTIVE
    ) : LiveUpdateMetricValue()

    data class Stopwatch(
        val startTime: Instant,
        val format: LiveUpdateMetricTimeFormat = LiveUpdateMetricTimeFormat.ADAPTIVE
    ) : LiveUpdateMetricValue()

    data class ElapsedRealtimeTimer(
        val endElapsedRealtimeMillis: Long,
        val format: LiveUpdateMetricTimeFormat = LiveUpdateMetricTimeFormat.ADAPTIVE
    ) : LiveUpdateMetricValue() {
        init {
            require(endElapsedRealtimeMillis >= 0L) {
                "endElapsedRealtimeMillis must be non-negative"
            }
        }
    }

    data class ElapsedRealtimeStopwatch(
        val startElapsedRealtimeMillis: Long,
        val format: LiveUpdateMetricTimeFormat = LiveUpdateMetricTimeFormat.ADAPTIVE
    ) : LiveUpdateMetricValue() {
        init {
            require(startElapsedRealtimeMillis >= 0L) {
                "startElapsedRealtimeMillis must be non-negative"
            }
        }
    }

    data class PausedTimer(
        val remainingTime: Duration,
        val format: LiveUpdateMetricTimeFormat = LiveUpdateMetricTimeFormat.ADAPTIVE
    ) : LiveUpdateMetricValue() {
        init {
            require(!remainingTime.isNegative) { "remainingTime must be non-negative" }
        }
    }

    data class PausedStopwatch(
        val elapsedTime: Duration,
        val format: LiveUpdateMetricTimeFormat = LiveUpdateMetricTimeFormat.ADAPTIVE
    ) : LiveUpdateMetricValue() {
        init {
            require(!elapsedTime.isNegative) { "elapsedTime must be non-negative" }
        }
    }

    data class FixedDate(
        val value: LocalDate,
        val format: LiveUpdateMetricDateFormat = LiveUpdateMetricDateFormat.AUTOMATIC
    ) : LiveUpdateMetricValue()

    data class FixedTime(
        val value: LocalTime
    ) : LiveUpdateMetricValue()
}
