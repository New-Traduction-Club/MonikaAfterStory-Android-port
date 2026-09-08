package com.nowbar.api

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.nowbar.api.cards.NowBarCard
import com.nowbar.api.fallback.FallbackStrategy
import com.nowbar.api.fallback.FallbackStrategyResolver
import com.nowbar.api.notification.NowBarNotificationBuilder
import com.nowbar.api.notification.SafeNotificationPoster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal class NowBarSessionImpl(
    private val context: Context,
    private val config: NowBarConfig
) : NowBarSession {

    override val id: String = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(SessionState.IDLE)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val notificationManager = NotificationManagerCompat.from(context)
    private val notificationBuilder = NowBarNotificationBuilder(context, config)
    private val lock = Any()

    private var lastCard: NowBarCard? = null

    override fun start(card: NowBarCard) = synchronized(lock) {
        check(_state.value != SessionState.STOPPED) {
            "Session is already stopped."
        }

        lastCard = card
        _state.value = if (post(card, config.samsungStyle, config.requestPromotedOngoing)) {
            SessionState.ACTIVE
        } else {
            SessionState.IDLE
        }
    }

    override fun update(card: NowBarCard) = synchronized(lock) {
        check(_state.value == SessionState.ACTIVE || _state.value == SessionState.PAUSED) {
            "Session must be ACTIVE or PAUSED before update()."
        }

        lastCard = card
        val posted = if (_state.value == SessionState.PAUSED) {
            postStandard(card)
        } else {
            post(
                card = card,
                samsungStyle = config.samsungStyle,
                requestPromotedOngoing = config.requestPromotedOngoing
            )
        }
        if (!posted) {
            _state.value = SessionState.PAUSED
        }
    }

    override fun unpin() = synchronized(lock) {
        check(_state.value == SessionState.ACTIVE || _state.value == SessionState.PAUSED) {
            "Session must be ACTIVE or PAUSED before unpin()."
        }

        val card = lastCard ?: return
        if (postStandard(card)) {
            _state.value = SessionState.PAUSED
        }
    }

    override fun dismiss() {
        unpin()
    }

    override fun stop() = synchronized(lock) {
        if (_state.value == SessionState.STOPPED) return

        notificationManager.cancel(config.notificationId)
        _state.value = SessionState.STOPPED
    }

    private fun post(
        card: NowBarCard,
        samsungStyle: Int,
        requestPromotedOngoing: Boolean
    ): Boolean {
        val fallback = FallbackStrategyResolver.resolve(
            strategy = config.fallbackStrategy,
            nativeSurfaceSupported = FeatureDetector.isNativeSurfaceSupported(context)
        )

        if (!fallback.shouldPost) {
            return false
        }

        NowBarManager.createNotificationChannel(context, config)

        return SafeNotificationPoster.notify(
            context = context,
            notificationManager = notificationManager,
            notificationId = config.notificationId,
            notification = notificationBuilder.build(card, samsungStyle, requestPromotedOngoing)
        )
    }

    private fun postStandard(card: NowBarCard): Boolean {
        val unpinnedConfig = config.copy(
            fallbackStrategy = FallbackStrategy.STANDARD_NOTIFICATION,
            requestPromotedOngoing = false
        )

        NowBarManager.createNotificationChannel(context, unpinnedConfig)

        return SafeNotificationPoster.notify(
            context = context,
            notificationManager = notificationManager,
            notificationId = unpinnedConfig.notificationId,
            notification = NowBarNotificationBuilder(context, unpinnedConfig).build(card)
        )
    }
}
