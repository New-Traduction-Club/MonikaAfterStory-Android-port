package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Renpy784InstallerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testIsInstalledWhenVersionFileMissing() {
        val gameDir = tempFolder.newFolder("my_game")
        assertFalse(Renpy784Installer.isInstalled(gameDir))
    }

    @Test
    fun testIsInstalledWhenVersionMatches() {
        val gameDir = tempFolder.newFolder("my_game_784")
        File(gameDir, ".runtime_784.version").writeText("7.8.4")
        assertTrue(Renpy784Installer.isInstalled(gameDir))
    }

    @Test
    fun testIsInstalledWhenVersionMismatch() {
        val gameDir = tempFolder.newFolder("my_game_old")
        File(gameDir, ".runtime_784.version").writeText("7.8.3")
        assertFalse(Renpy784Installer.isInstalled(gameDir))
    }

    @Test
    fun testCleanForeignBinaries() {
        val gameDir = tempFolder.newFolder("game_with_libs")
        val libDir = File(gameDir, "lib")
        libDir.mkdirs()

        val win64 = File(libDir, "windows-x86_64").apply { mkdirs() }
        val linux64 = File(libDir, "linux-x86_64").apply { mkdirs() }
        val pythonDir = File(libDir, "python2.7").apply { mkdirs() }
        File(pythonDir, "test.py").writeText("# python lib")

        assertTrue(win64.exists())
        assertTrue(linux64.exists())
        assertTrue(pythonDir.exists())

        Renpy784Installer.cleanForeignBinaries(gameDir)

        assertFalse(win64.exists())
        assertFalse(linux64.exists())
        assertTrue(pythonDir.exists())
        assertTrue(File(pythonDir, "test.py").exists())
    }

    @Test
    fun testCleanOldEngineFiles() {
        val gameDir = tempFolder.newFolder("game_to_clean")
        val renpyDir = File(gameDir, "renpy").apply { mkdirs() }
        val libDir = File(gameDir, "lib").apply { mkdirs() }
        val includeDir = File(gameDir, "include").apply { mkdirs() }
        val mainPyo = File(gameDir, "main.pyo").apply { writeText("dummy") }
        val mainPyc = File(gameDir, "main.pyc").apply { writeText("dummy") }
        val mainPy = File(gameDir, "main.py").apply { writeText("dummy") }
        val runtime699 = File(gameDir, ".runtime_699.version").apply { writeText("6.99") }
        val runtime7411 = File(gameDir, ".runtime_7411.version").apply { writeText("7.4.11") }
        val runtime784 = File(gameDir, ".runtime_784.version").apply { writeText("7.8.4") }
        val runtime837 = File(gameDir, ".runtime_837.version").apply { writeText("8.3.7") }
        val runtime841 = File(gameDir, ".runtime_841.version").apply { writeText("8.4.1") }
        val privateVer = File(gameDir, "private.version").apply { writeText("6.99") }
        val dotPrivateVer = File(gameDir, ".private.version").apply { writeText("6.99") }
        File(renpyDir, "__init__.py").writeText("# old renpy")
        File(libDir, "test.so").writeText("so")

        assertTrue(renpyDir.exists())
        assertTrue(libDir.exists())
        assertTrue(includeDir.exists())
        assertTrue(mainPyo.exists())
        assertTrue(mainPyc.exists())
        assertTrue(mainPy.exists())
        assertTrue(runtime699.exists())
        assertTrue(runtime7411.exists())
        assertTrue(runtime784.exists())
        assertTrue(runtime837.exists())
        assertTrue(runtime841.exists())
        assertTrue(privateVer.exists())
        assertTrue(dotPrivateVer.exists())

        Renpy784Installer.cleanOldEngineFiles(gameDir)

        assertFalse(renpyDir.exists())
        assertFalse(libDir.exists())
        assertFalse(includeDir.exists())
        assertFalse(mainPyo.exists())
        assertFalse(mainPyc.exists())
        assertFalse(mainPy.exists())
        assertFalse(runtime699.exists())
        assertFalse(runtime7411.exists())
        assertFalse(runtime784.exists())
        assertFalse(runtime837.exists())
        assertFalse(runtime841.exists())
        assertFalse(privateVer.exists())
        assertFalse(dotPrivateVer.exists())
    }

    @Test
    fun testRuntimeConstants() {
        assertEquals("6.99", ExperimentsActivity.RUNTIME_699)
        assertEquals("7.4.11", ExperimentsActivity.RUNTIME_7411)
        assertEquals("7.8.4", ExperimentsActivity.RUNTIME_784)
        assertEquals("8.3.7", ExperimentsActivity.RUNTIME_837)
        assertEquals("8.4.1", ExperimentsActivity.RUNTIME_841)
    }

    @Test
    fun testEnsureDeandroidPatchCreatesFile() {
        val gameFolder = tempFolder.newFolder("DDLC-test")
        File(gameFolder, "game").mkdirs()

        val result = ExperimentsActivity.ensureDeandroidPatch(gameFolder)
        assertTrue(result)

        val patchFile = File(gameFolder, "game/a_masl_patches/deandroid.rpy")
        assertTrue(patchFile.exists())
        assertEquals("init -999 python:\n    renpy.android = False\n", patchFile.readText())
    }

    @Test
    fun testEnsureDeandroidPatchPreservesExistingFile() {
        val gameFolder = tempFolder.newFolder("DDLC-custom")
        val patchesDir = File(gameFolder, "game/a_masl_patches").apply { mkdirs() }
        val patchFile = File(patchesDir, "deandroid.rpy")
        patchFile.writeText("# custom content")

        val result = ExperimentsActivity.ensureDeandroidPatch(gameFolder)
        assertTrue(result)
        assertEquals("# custom content", patchFile.readText())
    }

    @Test
    fun testEnsureDeandroidPatchRegeneratesWhenDeleted() {
        val gameFolder = tempFolder.newFolder("DDLC-regen")
        File(gameFolder, "game").mkdirs()

        assertTrue(ExperimentsActivity.ensureDeandroidPatch(gameFolder))
        val patchFile = File(gameFolder, "game/a_masl_patches/deandroid.rpy")
        assertTrue(patchFile.exists())

        patchFile.delete()
        assertFalse(patchFile.exists())

        assertTrue(ExperimentsActivity.ensureDeandroidPatch(gameFolder))
        assertTrue(patchFile.exists())
        assertEquals("init -999 python:\n    renpy.android = False\n", patchFile.readText())
    }

    @Test
    fun testEnsureDeandroidPatchExcludesMas() {
        val masFolder = tempFolder.newFolder("monikaafterstory-masl-edition")
        File(masFolder, "game").mkdirs()

        val result = ExperimentsActivity.ensureDeandroidPatch(masFolder)
        assertFalse(result)

        val patchesDir = File(masFolder, "game/a_masl_patches")
        assertFalse(patchesDir.exists())
    }

    @Test
    fun testEnsureDeandroidPatchFailsWhenNoGameFolder() {
        val emptyFolder = tempFolder.newFolder("not_a_game")
        val result = ExperimentsActivity.ensureDeandroidPatch(emptyFolder)
        assertFalse(result)
    }
}
