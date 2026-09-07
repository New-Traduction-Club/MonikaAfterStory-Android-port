package org.renpy.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Singleton managing persistent virtual desktop notification history using a local JSON file.
 */
object NotificationHistoryManager {
    private const val FILE_NAME = "desktop_notifications.json"
    private val lock = Any()

    fun getNotifications(context: Context): List<DesktopNotification> = synchronized(lock) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        try {
            val jsonStr = file.readText()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<DesktopNotification>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DesktopNotification(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        message = obj.optString("message", ""),
                        imagePath = obj.optString("imagePath").takeIf { it.isNotEmpty() && it != "null" },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isRead = obj.optBoolean("isRead", false)
                    )
                )
            }
            return list
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun saveNotifications(context: Context, list: List<DesktopNotification>) = synchronized(lock) {
        val file = File(context.filesDir, FILE_NAME)
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("message", item.message)
                    put("imagePath", item.imagePath ?: "")
                    put("timestamp", item.timestamp)
                    put("isRead", item.isRead)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addNotification(context: Context, title: String, message: String, imagePath: String?): DesktopNotification {
        val newNotif = DesktopNotification(title = title, message = message, imagePath = imagePath)
        val current = getNotifications(context).toMutableList()
        current.add(0, newNotif)
        saveNotifications(context, current)
        return newNotif
    }

    fun deleteNotification(context: Context, id: String) {
        val current = getNotifications(context).filter { it.id != id }
        saveNotifications(context, current)
    }

    fun clearAll(context: Context) {
        saveNotifications(context, emptyList())
    }

    fun markAllAsRead(context: Context) {
        val current = getNotifications(context)
        for (item in current) {
            item.isRead = true
        }
        saveNotifications(context, current)
    }

    fun getUnreadCount(context: Context): Int {
        return getNotifications(context).count { !it.isRead }
    }
}
