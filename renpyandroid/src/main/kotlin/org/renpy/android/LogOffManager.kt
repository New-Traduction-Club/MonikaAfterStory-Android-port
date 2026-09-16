package org.renpy.android

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Job

object LogOffManager {

    fun isRenpyProcess(processName: String, packageName: String): Boolean {
        if (!processName.startsWith("$packageName:")) return false
        val suffix = processName.removePrefix("$packageName:")
        return suffix.startsWith("renpy")
    }

    fun hasRunningGamesOrWindows(
        runningApps: Map<String, *>,
        activeActivities: Set<String>,
        hasRunningProcesses: () -> Boolean = { false }
    ): Boolean {
        if (runningApps.isNotEmpty()) return true
        if (activeActivities.isNotEmpty()) return true
        return hasRunningProcesses()
    }

    fun hasRunningGamesOrWindows(
        context: Context,
        runningApps: Map<String, *>,
        activeActivities: Set<String>
    ): Boolean {
        return hasRunningGamesOrWindows(runningApps, activeActivities) {
            hasRunningRenpyProcesses(context)
        }
    }

    fun hasRunningRenpyProcesses(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val runningProcesses = manager.runningAppProcesses ?: return false
        val packageName = context.packageName
        for (processInfo in runningProcesses) {
            if (isRenpyProcess(processInfo.processName, packageName)) {
                return true
            }
        }
        return false
    }

    fun terminateRenpyProcesses(context: Context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val runningProcesses = manager.runningAppProcesses ?: return
        val myUid = android.os.Process.myUid()
        val packageName = context.packageName
        for (processInfo in runningProcesses) {
            if (processInfo.uid == myUid && isRenpyProcess(processInfo.processName, packageName)) {
                try {
                    android.os.Process.killProcess(processInfo.pid)
                } catch (e: Exception) {
                }
            }
        }
    }

    fun closeAllWindowsAndGames(
        context: Context,
        runningApps: MutableMap<String, *>?,
        renpyMonitorJobs: MutableMap<String, Job>?
    ) {
        DesktopWindowManager.sendCommand(context, "ALL", DesktopWindowManager.COMMAND_CLOSE)

        ActiveActivityRegistry.finishAll()

        renpyMonitorJobs?.let {
            for (job in it.values) {
                try {
                    job.cancel()
                } catch (e: Exception) {

                }
            }
            it.clear()
        }

        runningApps?.clear()

        terminateRenpyProcesses(context)
    }
}
