package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RenpyVersionDetectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testHasPython3InLib() {
        val gameDir = tempFolder.newFolder("game_py3")
        val libDir = File(gameDir, "lib")
        libDir.mkdirs()

        assertFalse(RenpyVersionDetector.hasPython3InLib(gameDir))

        File(libDir, "python2.7").mkdirs()
        assertFalse(RenpyVersionDetector.hasPython3InLib(gameDir))

        File(libDir, "python3.9").mkdirs()
        assertTrue(RenpyVersionDetector.hasPython3InLib(gameDir))
    }

    @Test
    fun testHasPy3PrefixInLib() {
        val gameDir = tempFolder.newFolder("game_py3_prefix")
        val libDir = File(gameDir, "lib")
        libDir.mkdirs()

        File(libDir, "py3-linux-x86_64").mkdirs()
        assertTrue(RenpyVersionDetector.hasPython3InLib(gameDir))
    }

    @Test
    fun testPy3DetectionFromInitPyWhenLibContainsPython3() {
        val gameDir = tempFolder.newFolder("wintermute_like")
        val libDir = File(gameDir, "lib")
        libDir.mkdirs()
        File(libDir, "python3.9").mkdirs()

        val renpyDir = File(gameDir, "renpy")
        renpyDir.mkdirs()

        val initPyContent = """
            if PY2:
                version_tuple = (7, 5, 0, vc_version)
                version_name = "My game"
            else:
                version_tuple = (8, 0, 0, vc_version)
                version_name = "My game over"
            version_only = ".".join(str(i) for i in version_tuple)
        """.trimIndent()
        File(renpyDir, "__init__.py").writeText(initPyContent)

        val detected = RenpyVersionDetector.detectVersion(gameDir)
        assertEquals("8.0.0", detected)

        val recommended = RenpyVersionDetector.recommendVersion(detected)
        assertEquals(ExperimentsActivity.RUNTIME_803, recommended)
    }

    @Test
    fun testPy2DetectionFromInitPyWhenLibContainsPython2() {
        val gameDir = tempFolder.newFolder("sans_like")
        val libDir = File(gameDir, "lib")
        libDir.mkdirs()
        File(libDir, "python2.7").mkdirs()

        val renpyDir = File(gameDir, "renpy")
        renpyDir.mkdirs()

        val initPyContent = """
            if PY2:
                version_tuple = (7, 5, 3, vc_version)
                version_name = "My game"
            else:
                version_tuple = (8, 0, 3, vc_version)
                version_name = "My game over"
            version_only = ".".join(str(i) for i in version_tuple)
        """.trimIndent()
        File(renpyDir, "__init__.py").writeText(initPyContent)

        val detected = RenpyVersionDetector.detectVersion(gameDir)
        assertEquals("7.5.3", detected)

        val recommended = RenpyVersionDetector.recommendVersion(detected)
        assertEquals(ExperimentsActivity.RUNTIME_784, recommended)
    }

    @Test
    fun testClassicRenpy6DetectionWithoutPy2Branch() {
        val gameDir = tempFolder.newFolder("ddlc_classic")
        val libDir = File(gameDir, "lib")
        libDir.mkdirs()
        File(libDir, "pythonlib2.7").mkdirs()

        val renpyDir = File(gameDir, "renpy")
        renpyDir.mkdirs()

        val initPyContent = """
            version_tuple = (6, 99, 12, 4, vc_version)
            version_name = "We get the job done."
            version_only = ".".join(str(i) for i in version_tuple)
        """.trimIndent()
        File(renpyDir, "__init__.py").writeText(initPyContent)

        val detected = RenpyVersionDetector.detectVersion(gameDir)
        assertEquals("6.99.12", detected)

        val recommended = RenpyVersionDetector.recommendVersion(detected)
        assertEquals(ExperimentsActivity.RUNTIME_699, recommended)
    }

    @Test
    fun testVcVersionPyTakesPrecedenceIfPresent() {
        val gameDir = tempFolder.newFolder("renpy841_game")
        val renpyDir = File(gameDir, "renpy")
        renpyDir.mkdirs()

        val vcVersionContent = """
            version = '8.4.1.25072401'
            version_name = 'Tomorrowland'
        """.trimIndent()
        File(renpyDir, "vc_version.py").writeText(vcVersionContent)

        val detected = RenpyVersionDetector.detectVersion(gameDir)
        assertEquals("8.4.1", detected)

        val recommended = RenpyVersionDetector.recommendVersion(detected)
        assertEquals(ExperimentsActivity.RUNTIME_841, recommended)
    }

    @Test
    fun testStaleCacheInvalidationWhenPy3FoundInLib() {
        val gameDir = tempFolder.newFolder("stale_game")
        val libDir = File(gameDir, "lib")
        libDir.mkdirs()
        File(libDir, "python3.9").mkdirs()

        val renpyDir = File(gameDir, "renpy")
        renpyDir.mkdirs()

        val initPyContent = """
            if PY2:
                version_tuple = (7, 5, 0, vc_version)
            else:
                version_tuple = (8, 0, 0, vc_version)
            version_only = ".".join(str(i) for i in version_tuple)
        """.trimIndent()
        File(renpyDir, "__init__.py").writeText(initPyContent)

        MineLauncherConfigHelper.saveDetectedAndRecommended(
            gameDir,
            "7.5.0",
            ExperimentsActivity.RUNTIME_784
        )

        val (detected, recommended) = RenpyVersionDetector.detectAndSave(gameDir)
        assertEquals("8.0.0", detected)
        assertEquals(ExperimentsActivity.RUNTIME_803, recommended)
    }
}
