package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeandroidHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDefaultReturnsFalseWhenFileDoesNotExist() {
        val gameFolder = tempFolder.newFolder("TestGame")
        File(gameFolder, "game").mkdirs()

        val isAndroid = DeandroidHelper.isAndroidMode(gameFolder)
        assertFalse(isAndroid)
    }

    @Test
    fun testSetAndroidModeTrue() {
        val gameFolder = tempFolder.newFolder("TestGameTrue")
        File(gameFolder, "game").mkdirs()

        val success = DeandroidHelper.setAndroidMode(gameFolder, true)
        assertTrue(success)

        val patchFile = DeandroidHelper.getPatchFile(gameFolder)
        assertNotNull(patchFile)
        assertTrue(patchFile!!.exists())
        assertEquals("init -999 python:\n    renpy.android = True\n", patchFile.readText())
        assertTrue(DeandroidHelper.isAndroidMode(gameFolder))
    }

    @Test
    fun testSetAndroidModeFalse() {
        val gameFolder = tempFolder.newFolder("TestGameFalse")
        File(gameFolder, "game").mkdirs()

        DeandroidHelper.setAndroidMode(gameFolder, true)
        assertTrue(DeandroidHelper.isAndroidMode(gameFolder))

        val success = DeandroidHelper.setAndroidMode(gameFolder, false)
        assertTrue(success)

        val patchFile = DeandroidHelper.getPatchFile(gameFolder)
        assertNotNull(patchFile)
        assertEquals("init -999 python:\n    renpy.android = False\n", patchFile!!.readText())
        assertFalse(DeandroidHelper.isAndroidMode(gameFolder))
    }

    @Test
    fun testCaseInsensitiveAndWhitespaceParsing() {
        val gameFolder = tempFolder.newFolder("TestGameRegex")
        val patchesDir = File(gameFolder, "game/a_masl_patches")
        patchesDir.mkdirs()
        val patchFile = File(patchesDir, "deandroid.rpy")

        patchFile.writeText("init -999 python:\n   renpy.android  =  TRUE \n")
        assertTrue(DeandroidHelper.isAndroidMode(gameFolder))

        patchFile.writeText("init -999 python:\n   renpy.android=false\n")
        assertFalse(DeandroidHelper.isAndroidMode(gameFolder))
    }

    @Test
    fun testExcludedMasDirReturnsNull() {
        val masFolder = tempFolder.newFolder(ExperimentsActivity.EXCLUDED_MAS_DIR)
        File(masFolder, "game").mkdirs()

        assertNull(DeandroidHelper.getPatchFile(masFolder))
        assertFalse(DeandroidHelper.isAndroidMode(masFolder))
        assertFalse(DeandroidHelper.setAndroidMode(masFolder, true))
    }

    @Test
    fun testEnsureDeandroidPatchPreservesUserTrueSetting() {
        val gameFolder = tempFolder.newFolder("TestGamePreserve")
        File(gameFolder, "game").mkdirs()

        DeandroidHelper.setAndroidMode(gameFolder, true)
        assertTrue(DeandroidHelper.isAndroidMode(gameFolder))

        val ensured = ExperimentsActivity.ensureDeandroidPatch(gameFolder)
        assertTrue(ensured)
        assertTrue(DeandroidHelper.isAndroidMode(gameFolder))
    }

    @Test
    fun testGetFormattedStatus() {
        assertEquals("renpy.android = True", DeandroidHelper.getFormattedStatus(true))
        assertEquals("renpy.android = False", DeandroidHelper.getFormattedStatus(false))
        assertEquals("renpy.android = False (+2)", DeandroidHelper.getFormattedStatus(false, 2))
        assertEquals("renpy.android = True (+1)", DeandroidHelper.getFormattedStatus(true, 1))
    }

    @Test
    fun testIsValidVarName() {
        assertTrue(DeandroidHelper.isValidVarName("config.developer"))
        assertTrue(DeandroidHelper.isValidVarName("renpy.config.screen_width"))
        assertTrue(DeandroidHelper.isValidVarName("custom_var_1"))
        assertTrue(DeandroidHelper.isValidVarName("myVar"))

        assertFalse(DeandroidHelper.isValidVarName("renpy.android"))
        assertFalse(DeandroidHelper.isValidVarName("123bad"))
        assertFalse(DeandroidHelper.isValidVarName("var with spaces"))
        assertFalse(DeandroidHelper.isValidVarName("bad-char"))
        assertFalse(DeandroidHelper.isValidVarName(""))
    }

    @Test
    fun testIsValidNumber() {
        assertTrue(DeandroidHelper.isValidNumber("1280"))
        assertTrue(DeandroidHelper.isValidNumber("-1"))
        assertTrue(DeandroidHelper.isValidNumber("3.14"))
        assertTrue(DeandroidHelper.isValidNumber("0"))
        assertTrue(DeandroidHelper.isValidNumber("-0.5"))

        assertFalse(DeandroidHelper.isValidNumber("abc"))
        assertFalse(DeandroidHelper.isValidNumber("12.34.56"))
        assertFalse(DeandroidHelper.isValidNumber(""))
        assertFalse(DeandroidHelper.isValidNumber("1280px"))
    }

    @Test
    fun testSaveAndLoadCustomEnvVars() {
        val gameFolder = tempFolder.newFolder("TestGameCustomVars")
        File(gameFolder, "game").mkdirs()

        val initialVars = DeandroidHelper.getCustomEnvVars(gameFolder)
        assertTrue(initialVars.isEmpty())

        val customVars = listOf(
            CustomEnvVar("config.developer", DeandroidHelper.TYPE_BOOLEAN, "True"),
            CustomEnvVar("config.screen_width", DeandroidHelper.TYPE_NUMBER, "1280"),
            CustomEnvVar("custom_flag", DeandroidHelper.TYPE_CUSTOM, "\"debug_mode\"")
        )

        val saved = DeandroidHelper.saveEnvVars(gameFolder, false, customVars)
        assertTrue(saved)

        val loadedVars = DeandroidHelper.getCustomEnvVars(gameFolder)
        assertEquals(3, loadedVars.size)
        assertEquals("config.developer", loadedVars[0].name)
        assertEquals(DeandroidHelper.TYPE_BOOLEAN, loadedVars[0].type)
        assertEquals("True", loadedVars[0].value)

        assertEquals("config.screen_width", loadedVars[1].name)
        assertEquals(DeandroidHelper.TYPE_NUMBER, loadedVars[1].type)
        assertEquals("1280", loadedVars[1].value)

        assertEquals("custom_flag", loadedVars[2].name)
        assertEquals(DeandroidHelper.TYPE_CUSTOM, loadedVars[2].type)
        assertEquals("\"debug_mode\"", loadedVars[2].value)

        val patchFile = DeandroidHelper.getPatchFile(gameFolder)
        assertNotNull(patchFile)
        assertTrue(patchFile!!.exists())
        val patchContent = patchFile.readText()
        assertTrue(patchContent.contains("renpy.android = False"))
        assertTrue(patchContent.contains("config.developer = True"))
        assertTrue(patchContent.contains("config.screen_width = 1280"))
        assertTrue(patchContent.contains("custom_flag = \"debug_mode\""))
    }
}
