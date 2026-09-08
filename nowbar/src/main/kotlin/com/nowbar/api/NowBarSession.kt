package com.nowbar.api

import com.nowbar.api.cards.NowBarCard
import kotlinx.coroutines.flow.StateFlow

/**
 * Tiny stateful wrapper around a single notification id.
 *
 * It does not manage your business logic.
 * It only remembers the last card and lets you:
 * - start / resume,
 * - update,
 * - unpin the promoted / Samsung surface but keep the notification,
 * - stop everything.
 */
interface NowBarSession {

    val id: String
    val state: StateFlow<SessionState>

    fun start(card: NowBarCard)

    fun update(card: NowBarCard)

    /**
     * Keeps the notification, but removes Samsung / promoted ongoing hints
     * by rebuilding the notification as plain ongoing.
     */
    fun unpin()

    /**
     * Compatibility alias for [unpin].
     */
    fun dismiss()

    /**
     * Cancels the notification completely.
     */
    fun stop()
}
