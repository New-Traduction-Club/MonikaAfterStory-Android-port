package com.nowbar.api

import android.content.Context
import com.nowbar.api.cards.NowBarCard
import com.nowbar.api.fallback.FallbackStrategyResolver
import com.nowbar.api.notification.LiveUpdateDiagnostics
import com.nowbar.api.notification.LiveUpdateEligibilityReport
import com.nowbar.api.notification.NowBarNotificationBuilder
import com.nowbar.api.notification.NowBarNotificationEvidence
import com.nowbar.api.notification.NowBarNotificationEvidenceReport

/**
 * One-shot preflight for a card/config pair.
 *
 * This builds the SDK notification locally and combines device capability,
 * Android Live Updates eligibility, and notification-level evidence. It does not
 * post anything and cannot prove OEM allow-list or ranking behavior.
 */
object NowBarReadiness {

    @JvmStatic
    fun inspect(
        context: Context,
        config: NowBarConfig,
        card: NowBarCard
    ): NowBarReadinessReport {
        val appContext = context.applicationContext
        val capability = NowBarDiagnostics.inspect(appContext)
        val fallback = FallbackStrategyResolver.resolve(
            strategy = config.fallbackStrategy,
            nativeSurfaceSupported = capability.nativeSurfaceSupported
        )
        val notification = NowBarNotificationBuilder(appContext, config).build(card)
        val liveUpdate = LiveUpdateDiagnostics.inspect(appContext, notification)
        val evidence = NowBarNotificationEvidence.inspect(notification)

        return NowBarReadinessReport(
            capability = capability,
            liveUpdate = liveUpdate,
            evidence = evidence,
            requestedActionCount = card.toActions().size,
            willPost = fallback.shouldPost,
            usesStandardNotificationOnly = fallback.useStandardNotificationOnly
        )
    }
}

data class NowBarReadinessReport(
    val capability: NowBarCapabilityReport,
    val liveUpdate: LiveUpdateEligibilityReport,
    val evidence: NowBarNotificationEvidenceReport,
    val requestedActionCount: Int = 0,
    val willPost: Boolean,
    val usesStandardNotificationOnly: Boolean
) {
    val readyForSamsungNowBar: Boolean
        get() = capability.samsungPathAvailable &&
            evidence.hasEligibleCoreFields &&
            evidence.hasSamsungNowBarEvidence

    val readyForAndroidLiveUpdates: Boolean
        get() = capability.androidLiveUpdatesPathAvailable &&
            liveUpdate.eligible &&
            evidence.hasAndroidLiveUpdateEvidence

    val readyForEnhancedSurface: Boolean
        get() = readyForSamsungNowBar || readyForAndroidLiveUpdates

    val willUseStandardFallback: Boolean
        get() = willPost && (usesStandardNotificationOnly || !readyForEnhancedSurface)

    val blockingReasons: List<String>
        get() = buildList {
            if (!willPost) add("posting-disabled-by-fallback-strategy")
            if (usesStandardNotificationOnly) add("standard-notification-only")

            if (!readyForEnhancedSurface) {
                addAll(capability.blockingReasons.map { "device:$it" })
                addAll(evidence.missingCoreHints.map { "notification:$it" })

                if (capability.androidLiveUpdatesPathAvailable) {
                    addAll(liveUpdate.blockingReasons.map { "live-update:$it" })
                }

                if (!capability.samsungPathAvailable && !capability.androidLiveUpdatesPathAvailable) {
                    add("device:no-enhanced-surface")
                }
            }
        }.distinct()

    val advisoryReasons: List<String>
        get() = buildList {
            addAll(liveUpdate.advisoryReasons.map { "live-update:$it" })
            if (requestedActionCount > liveUpdate.androidActionCount) {
                add("notification:action-buttons-truncated")
            }
        }.distinct()

    fun toDisplayString(): String = buildString {
        appendLine("Ready for enhanced surface: $readyForEnhancedSurface")
        appendLine("Samsung Now Bar ready: $readyForSamsungNowBar")
        appendLine("Android Live Updates ready: $readyForAndroidLiveUpdates")
        appendLine("Will post: $willPost")
        appendLine("Standard fallback: $willUseStandardFallback")
        appendLine("Blockers: ${blockingReasons.joinToString().ifBlank { "none" }}")
        append("Advisories: ")
        append(advisoryReasons.joinToString().ifBlank { "none" })
    }
}
