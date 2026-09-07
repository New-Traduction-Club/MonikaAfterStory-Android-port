package org.renpy.android

import android.content.Context
import android.content.Intent

object ActiveActivityRegistry {
    val activeActivities: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile
    var currentActivity: android.app.Activity? = null
}

object DesktopWindowManager {
    const val ACTION_WINDOW_STATE_CHANGED = "org.renpy.android.ACTION_WINDOW_STATE_CHANGED"
    const val ACTION_WINDOW_COMMAND = "org.renpy.android.ACTION_WINDOW_COMMAND"

    const val EXTRA_ACTIVITY_ID = "activity_id"
    const val EXTRA_ACTIVITY_NAME = "activity_name"
    const val EXTRA_STATE = "state" // "RUNNING", "MINIMIZED", "DESTROYED"
    const val EXTRA_COMMAND = "command" // "RESTORE", "MINIMIZE"

    fun notifyStateChanged(context: Context, activityId: String, activityName: String, state: String) {
        val intent = Intent(ACTION_WINDOW_STATE_CHANGED).apply {
            putExtra(EXTRA_ACTIVITY_ID, activityId)
            putExtra(EXTRA_ACTIVITY_NAME, activityName)
            putExtra(EXTRA_STATE, state)
        }
        context.sendBroadcast(intent)
    }

    fun sendCommand(context: Context, activityId: String, command: String) {
        val intent = Intent(ACTION_WINDOW_COMMAND).apply {
            putExtra(EXTRA_ACTIVITY_ID, activityId)
            putExtra(EXTRA_COMMAND, command)
        }
        context.sendBroadcast(intent)
    }

    private val runningApps = java.util.Collections.synchronizedMap(mutableMapOf<String, Pair<String, String>>()) // id -> Pair(name, state)
    @Volatile
    private var lastFocusedAppId: String? = null

    private val windowStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_WINDOW_STATE_CHANGED) {
                val id = intent.getStringExtra(EXTRA_ACTIVITY_ID) ?: return
                val name = intent.getStringExtra(EXTRA_ACTIVITY_NAME) ?: "App"
                val state = intent.getStringExtra(EXTRA_STATE) ?: return

                if (state == "DESTROYED") {
                    runningApps.remove(id)
                    if (lastFocusedAppId == id) {
                        lastFocusedAppId = runningApps.entries.lastOrNull { it.value.second == "RUNNING" }?.key
                    }
                } else {
                    runningApps[id] = Pair(name, state)
                    if (state == "RUNNING") {
                        lastFocusedAppId = id
                    } else if (lastFocusedAppId == id) {
                        lastFocusedAppId = runningApps.entries.lastOrNull { it.value.second == "RUNNING" }?.key
                    }
                }
            }
        }
    }

    private var isReceiverRegistered = false

    @JvmStatic
    fun registerReceiver(context: Context) {
        if (isReceiverRegistered) return
        val filter = android.content.IntentFilter(ACTION_WINDOW_STATE_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(windowStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(windowStateReceiver, filter)
        }
        isReceiverRegistered = true
    }

    @JvmStatic
    fun unregisterReceiver(context: Context) {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(windowStateReceiver)
        } catch (e: Exception) {}
        isReceiverRegistered = false
    }

    @JvmStatic
    fun getActiveWindowName(): String {
        val currentId = lastFocusedAppId ?: return "Desktop"
        val appPair = runningApps[currentId] ?: return "Desktop"
        if (appPair.second != "RUNNING") return "Desktop"
        return appPair.first
    }
}
