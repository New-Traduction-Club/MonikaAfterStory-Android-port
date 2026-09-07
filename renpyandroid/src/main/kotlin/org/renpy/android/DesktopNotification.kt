package org.renpy.android

import java.util.UUID

/**
 * Data class representing a virtual desktop notification.
 */
data class DesktopNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val imagePath: String?,
    val timestamp: Long = System.currentTimeMillis(),
    var isRead: Boolean = false
)
