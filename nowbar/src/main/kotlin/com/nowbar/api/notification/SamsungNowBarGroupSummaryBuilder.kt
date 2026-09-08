package com.nowbar.api.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat

/**
 * Helper for the Samsung AOD-service pattern observed in Google Sports / Finance dumps:
 * one GROUP_SUMMARY notification plus one child notification in the same group.
 *
 * The child notification should use the same [groupKey] and usually carries the rich
 * OngoingActivity extras produced by [SamsungOngoingActivityDumpExtras].
 */
data class SamsungNowBarGroupSummarySpec(
    val channelId: String,
    val groupKey: String,
    val summaryNotificationId: Int,
    val title: CharSequence,
    val smallIconResId: Int,
    val remoteApp: SamsungRemoteAppConfig? = null,
    val substName: CharSequence? = title,
    val contentIntent: PendingIntent? = null,
    val deleteIntent: PendingIntent? = null,
    val category: String = NotificationCompat.CATEGORY_PROGRESS,
    val priority: Int = NotificationCompat.PRIORITY_LOW,
    val color: Int? = null,
    val autoCancel: Boolean = false,
    val showWhen: Boolean = true,
    val reducedImages: Boolean = true
)

object SamsungNowBarGroupSummaryBuilder {
    const val GOOGLE_FINANCE_CHANNEL_ID = "google_finance_nowbar_ongoing_channel"
    const val GOOGLE_FINANCE_GROUP_KEY = "google_finance_nowbar_group_key"
    const val GOOGLE_FINANCE_SUMMARY_ID = 3000
    const val GOOGLE_FINANCE_CHILD_ID = 3115
    const val GOOGLE_SPORTS_CHANNEL_ID = "google_sports_nowbar_ongoing_channel"
    const val GOOGLE_SPORTS_GROUP_KEY = "google_sports_nowbar_group_key"
    const val GOOGLE_SPORTS_SUMMARY_ID = 1000
    const val GOOGLE_SPORTS_CHILD_ID = 1123

    @JvmStatic
    fun build(
        context: Context,
        spec: SamsungNowBarGroupSummarySpec,
        extraExtras: Bundle = Bundle()
    ): Notification {
        val samsungExtras = SamsungOngoingActivityDumpExtras.build(
            remoteApp = spec.remoteApp,
            text = SamsungOngoingActivityText(title = spec.title),
            substName = spec.substName,
            showWhen = spec.showWhen,
            reducedImages = spec.reducedImages
        ).apply { putAll(extraExtras) }

        val builder = NotificationCompat.Builder(context, spec.channelId)
            .setSmallIcon(spec.smallIconResId)
            .setContentTitle(spec.title)
            .setCategory(spec.category)
            .setPriority(spec.priority)
            .setAutoCancel(spec.autoCancel)
            .setShowWhen(spec.showWhen)
            .setGroup(spec.groupKey)
            .setGroupSummary(true)
            .addExtras(samsungExtras)

        spec.contentIntent?.let(builder::setContentIntent)
        spec.deleteIntent?.let(builder::setDeleteIntent)
        spec.color?.let(builder::setColor)

        return builder.build()
    }

    @JvmStatic
    fun applyChildGroup(
        builder: NotificationCompat.Builder,
        groupKey: String
    ): NotificationCompat.Builder = builder.setGroup(groupKey).setGroupSummary(false)
}
