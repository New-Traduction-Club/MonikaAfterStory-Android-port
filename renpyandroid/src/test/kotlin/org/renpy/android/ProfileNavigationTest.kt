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
}
