package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileNavigationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testMasProfileWithIncompleteSetupNavigatesToSetup() {
        val target = ProfileNavigationHelper.determineLoginTarget(
            profile = ProfileNavigationHelper.PROFILE_MAS,
            isSetupCompleted = false
        )
        assertEquals(ProfileNavigationHelper.NavigationTarget.SETUP, target)
    }

    @Test
    fun testMasProfileWithCompleteSetupNavigatesToDesktop() {
        val target = ProfileNavigationHelper.determineLoginTarget(
            profile = ProfileNavigationHelper.PROFILE_MAS,
            isSetupCompleted = true
        )
        assertEquals(ProfileNavigationHelper.NavigationTarget.DESKTOP, target)
    }

    @Test
    fun testRenpyProfileWithIncompleteSetupNavigatesToDesktop() {
        val target = ProfileNavigationHelper.determineLoginTarget(
            profile = ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER,
            isSetupCompleted = false
        )
        assertEquals(ProfileNavigationHelper.NavigationTarget.DESKTOP, target)
    }

    @Test
    fun testRenpyProfileWithCompleteSetupNavigatesToDesktop() {
        val target = ProfileNavigationHelper.determineLoginTarget(
            profile = ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER,
            isSetupCompleted = true
        )
        assertEquals(ProfileNavigationHelper.NavigationTarget.DESKTOP, target)
    }

    @Test
    fun testCanStartGameOnlyWhenSetupCompleted() {
        assertFalse(ProfileNavigationHelper.canStartGame(isSetupCompleted = false))
        assertTrue(ProfileNavigationHelper.canStartGame(isSetupCompleted = true))
    }

    @Test
    fun testTempFilesCleanupOnSetupExit() {
        val filesDir = tempFolder.newFolder("files")
        val cacheDir = tempFolder.newFolder("cache")

        val modTemp = File(filesDir, "mod_temp.zip").apply { writeText("dummy mod zip") }
        val ddlcTemp = File(cacheDir, "ddlc_temp.zip").apply { writeText("dummy ddlc zip") }
        val installTemp = File(cacheDir, "mod_temp_install.zip").apply { writeText("dummy install zip") }

        assertTrue(modTemp.exists())
        assertTrue(ddlcTemp.exists())
        assertTrue(installTemp.exists())

        val filesToDelete = ProfileNavigationHelper.getTempFilesToDelete(filesDir, cacheDir)
        assertEquals(3, filesToDelete.size)

        for (file in filesToDelete) {
            if (file.exists()) {
                file.delete()
            }
        }

        assertFalse(modTemp.exists())
        assertFalse(ddlcTemp.exists())
        assertFalse(installTemp.exists())
    }

    @Test
    fun testGetLanguageShortCode() {
        assertEquals("EN", ProfileNavigationHelper.getLanguageShortCode("English"))
        assertEquals("ES", ProfileNavigationHelper.getLanguageShortCode("Español"))
        assertEquals("PT", ProfileNavigationHelper.getLanguageShortCode("Português"))
        assertEquals("EN", ProfileNavigationHelper.getLanguageShortCode("Unknown"))
        assertEquals("EN", ProfileNavigationHelper.getLanguageShortCode(""))
    }

    @Test
    fun testMasProfileStartMenuItems() {
        val pinned = ProfileNavigationHelper.getPinnedItems(ProfileNavigationHelper.PROFILE_MAS)
        val expanded = ProfileNavigationHelper.getExpandedItems(ProfileNavigationHelper.PROFILE_MAS)

        assertEquals(6, pinned.size)
        assertEquals(
            listOf("start_game", "internal_files", "import", "export", "settings", "toggle_expand"),
            pinned.map { it.actionId }
        )

        assertEquals(9, expanded.size)
        assertEquals(
            listOf("external_files", "update_game", "extra_content", "discord_rpc", "backups", "wallpapers", "app_info", "experiments", "switch_user"),
            expanded.map { it.actionId }
        )
        assertEquals(R.string.title_experiments, expanded[7].titleResId)
    }

    @Test
    fun testRenpyProfileStartMenuItems() {
        val pinned = ProfileNavigationHelper.getPinnedItems(ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER)
        val expanded = ProfileNavigationHelper.getExpandedItems(ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER)

        assertEquals(6, pinned.size)
        assertEquals(
            listOf("experiments", "internal_files", "external_files", "wallpapers", "settings", "toggle_expand"),
            pinned.map { it.actionId }
        )
        assertEquals(R.string.title_mine, pinned[0].titleResId)

        assertEquals(2, expanded.size)
        assertEquals(
            listOf("app_info", "switch_user"),
            expanded.map { it.actionId }
        )
    }

    @Test
    fun testRenpyProfileExcludesMasItems() {
        val pinned = ProfileNavigationHelper.getPinnedItems(ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER)
        val expanded = ProfileNavigationHelper.getExpandedItems(ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER)
        val allRenpyActions = (pinned + expanded).map { it.actionId }.toSet()

        val excludedItems = listOf(
            "start_game",
            "import",
            "export",
            "update_game",
            "extra_content",
            "discord_rpc",
            "backups"
        )

        for (excluded in excludedItems) {
            assertFalse(
                "Action $excluded should not be present in Ren'Py profile start menu",
                allRenpyActions.contains(excluded)
            )
        }
    }
}
