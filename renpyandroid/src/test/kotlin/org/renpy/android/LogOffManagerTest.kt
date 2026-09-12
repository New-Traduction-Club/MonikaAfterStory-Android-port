package org.renpy.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogOffManagerTest {

    private val testPackage = "org.renpy.android"

    @Test
    fun testIsRenpyProcessMatchesStandardProcess() {
        assertTrue(LogOffManager.isRenpyProcess("$testPackage:renpy", testPackage))
    }

    @Test
    fun testIsRenpyProcessMatchesNumberedEngines() {
        assertTrue(LogOffManager.isRenpyProcess("$testPackage:renpy2", testPackage))
        assertTrue(LogOffManager.isRenpyProcess("$testPackage:renpy3", testPackage))
        assertTrue(LogOffManager.isRenpyProcess("$testPackage:renpy7411", testPackage))
        assertTrue(LogOffManager.isRenpyProcess("$testPackage:renpy784", testPackage))
        assertTrue(LogOffManager.isRenpyProcess("$testPackage:renpy803", testPackage))
        assertTrue(LogOffManager.isRenpyProcess("$testPackage:renpy837", testPackage))
        assertTrue(LogOffManager.isRenpyProcess("$testPackage:renpy841", testPackage))
        assertTrue(LogOffManager.isRenpyProcess("$testPackage:renpy853", testPackage))
    }

    @Test
    fun testIsRenpyProcessRejectsMainProcess() {
        assertFalse(LogOffManager.isRenpyProcess(testPackage, testPackage))
    }

    @Test
    fun testIsRenpyProcessRejectsOtherSubprocesses() {
        assertFalse(LogOffManager.isRenpyProcess("$testPackage:downloader", testPackage))
        assertFalse(LogOffManager.isRenpyProcess("$testPackage:service", testPackage))
        assertFalse(LogOffManager.isRenpyProcess("$testPackage:ipc", testPackage))
    }

    @Test
    fun testIsRenpyProcessRejectsOtherPackageNames() {
        assertFalse(LogOffManager.isRenpyProcess("com.other.app:renpy", testPackage))
        assertFalse(LogOffManager.isRenpyProcess("com.other.app:renpy837", testPackage))
    }

    @Test
    fun testHasRunningGamesOrWindowsReturnsFalseWhenAllEmpty() {
        val runningApps = emptyMap<String, String>()
        val activeActivities = emptySet<String>()

        val result = LogOffManager.hasRunningGamesOrWindows(
            runningApps = runningApps,
            activeActivities = activeActivities,
            hasRunningProcesses = { false }
        )
        assertFalse(result)
    }

    @Test
    fun testHasRunningGamesOrWindowsReturnsTrueWhenRunningAppsNonEmpty() {
        val runningApps = mapOf("org.renpy.android.PythonSDLActivity" to "RUNNING")
        val activeActivities = emptySet<String>()

        val result = LogOffManager.hasRunningGamesOrWindows(
            runningApps = runningApps,
            activeActivities = activeActivities,
            hasRunningProcesses = { false }
        )
        assertTrue(result)
    }

    @Test
    fun testHasRunningGamesOrWindowsReturnsTrueWhenActiveActivitiesNonEmpty() {
        val runningApps = emptyMap<String, String>()
        val activeActivities = setOf("org.renpy.android.SettingsActivity")

        val result = LogOffManager.hasRunningGamesOrWindows(
            runningApps = runningApps,
            activeActivities = activeActivities,
            hasRunningProcesses = { false }
        )
        assertTrue(result)
    }

    @Test
    fun testHasRunningGamesOrWindowsReturnsTrueWhenProcessRunning() {
        val runningApps = emptyMap<String, String>()
        val activeActivities = emptySet<String>()

        val result = LogOffManager.hasRunningGamesOrWindows(
            runningApps = runningApps,
            activeActivities = activeActivities,
            hasRunningProcesses = { true }
        )
        assertTrue(result)
    }

    @Test
    fun testHasRunningGamesOrWindowsReturnsTrueWhenBothAppsAndActivitiesPresent() {
        val runningApps = mapOf("org.renpy.android.FileExplorerActivity" to "MINIMIZED")
        val activeActivities = setOf("org.renpy.android.FileExplorerActivity")

        val result = LogOffManager.hasRunningGamesOrWindows(
            runningApps = runningApps,
            activeActivities = activeActivities,
            hasRunningProcesses = { false }
        )
        assertTrue(result)
    }
}
