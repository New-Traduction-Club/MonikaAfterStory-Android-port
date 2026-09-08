package com.nowbar.api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.nowbar.api.cards.NowBarCard
import com.nowbar.api.fallback.FallbackStrategyResolver
import com.nowbar.api.notification.SafeNotificationPoster
import com.nowbar.api.notification.NowBarNotificationBuilder

/**
 * Main entry point.
 *
 * This object does three things:
 * 1. feature detection,
 * 2. channel creation,
 * 3. build/post/cancel notifications.
 */
object NowBarManager {

    @JvmStatic
    fun isSupported(context: Context): Boolean =
        FeatureDetector.isNativeSurfaceSupported(context)

    @JvmStatic
    fun getSupportedPlatform(context: Context): NowBarPlatform =
        FeatureDetector.getSupportedPlatform(context)

    @JvmStatic
    fun createSession(context: Context, config: NowBarConfig): NowBarSession =
        NowBarSessionImpl(context.applicationContext, config)

    @JvmStatic
    fun buildNotification(
        context: Context,
        config: NowBarConfig,
        card: NowBarCard
    ): Notification =
        NowBarNotificationBuilder(context.applicationContext, config).build(card)

    @JvmStatic
    fun inspectReadiness(
        context: Context,
        config: NowBarConfig,
        card: NowBarCard
    ): NowBarReadinessReport =
        NowBarReadiness.inspect(context, config, card)

    /**
     * Posts a notification immediately.
     *
     * Returns false when the SDK should not post on this device
     * or the app cannot currently post notifications.
     */
    @JvmStatic
    fun notify(
        context: Context,
        config: NowBarConfig,
        card: NowBarCard
    ): Boolean {
        if (!shouldPost(context, config)) return false

        createNotificationChannel(context, config)

        return SafeNotificationPoster.notify(
            context = context,
            notificationManager = NotificationManagerCompat.from(context),
            notificationId = config.notificationId,
            notification = buildNotification(context, config, card)
        )
    }

    @JvmStatic
    fun cancel(context: Context, config: NowBarConfig) {
        NotificationManagerCompat.from(context).cancel(config.notificationId)
    }

    @JvmStatic
    fun createNotificationChannel(context: Context, config: NowBarConfig) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val channel = NotificationChannel(
            config.channelId,
            config.channelName,
            config.channelImportance
        ).apply {
            description = config.channelDescription
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun shouldPost(context: Context, config: NowBarConfig): Boolean {
        return FallbackStrategyResolver.resolve(
            strategy = config.fallbackStrategy,
            nativeSurfaceSupported = FeatureDetector.isNativeSurfaceSupported(context)
        ).shouldPost
    }
}
