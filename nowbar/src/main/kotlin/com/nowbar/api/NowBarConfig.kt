package com.nowbar.api

import android.app.NotificationManager
import com.nowbar.api.fallback.FallbackStrategy
import com.nowbar.api.notification.CapsuleConfig
import com.nowbar.api.notification.ChronometerConfig
import com.nowbar.api.notification.SamsungRemoteAppConfig

/**
 * Small configuration object for the module.
 *
 * Keep it boring:
 * - one channel,
 * - one notification id,
 * - one rendering policy.
 */
data class NowBarConfig(
    val channelId: String,
    val channelName: String,
    val channelDescription: String = "",
    val channelImportance: Int = NotificationManager.IMPORTANCE_LOW,
    val notificationId: Int = DEFAULT_NOTIFICATION_ID,
    val fallbackStrategy: FallbackStrategy = FallbackStrategy.AUTO,
    val samsungStyle: Int = STYLE_BOTH,
    val requestPromotedOngoing: Boolean = true,
    val showSmallIcon: Boolean = true,
    val actionPrimarySet: Int? = null,
    val chronometerConfig: ChronometerConfig? = null,
    val capsuleConfig: CapsuleConfig? = null,
    val aodRemoteApp: SamsungRemoteAppConfig? = null
) {
    companion object {
        const val DEFAULT_NOTIFICATION_ID = 200
        const val STYLE_NOTIFICATION_ONLY = 0
        const val STYLE_BOTH = 1
        const val STYLE_NOW_BAR_ONLY = 2
    }
}
