package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            listOf(
                "external_files",
                "update_game",
                "extra_content",
                "discord_rpc",
                "backups",
                "wallpapers",
                "app_info",
                "experiments",
                "switch_user"
            ),
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

    @Test
    fun testMineLauncherMetadataPriority() {
        val gameFolder = tempFolder.newFolder("test_game")
        val rootEngine = File(gameFolder, "engine.txt")
        rootEngine.writeText("7.8.4")

        val mineFolder = File(gameFolder, ".mine")
        mineFolder.mkdirs()
        val mineEngine = File(mineFolder, "engine.txt")
        mineEngine.writeText("8.3.7")

        val resolved = if (mineEngine.exists()) mineEngine.readText().trim() else rootEngine.readText().trim()
        assertEquals("8.3.7", resolved)
    }

    @Test
    fun testMineLauncherConfigsJsonTitleAndRuntime() {
        val gameFolder = tempFolder.newFolder("my_visual_novel")

        val initialConfig = MineLauncherConfigHelper.MineGameConfig(
            title = "Awesome VN Project",
            runtime = "8.3.7"
        )
        MineLauncherConfigHelper.saveGameConfig(gameFolder, initialConfig)

        val loadedConfig = MineLauncherConfigHelper.getGameConfig(gameFolder)
        assertEquals("Awesome VN Project", loadedConfig.title)
        assertEquals("8.3.7", loadedConfig.runtime)
        assertEquals("Awesome VN Project", MineLauncherConfigHelper.getGameTitle(gameFolder))
        assertEquals("8.3.7", MineLauncherConfigHelper.getGameEngine(gameFolder))

        val rootEngineFile = File(gameFolder, "engine.txt")
        assertTrue(rootEngineFile.exists())
        assertEquals("8.3.7", rootEngineFile.readText().trim())

        val mineEngineFile = File(File(gameFolder, ".mine"), "engine.txt")
        assertTrue(mineEngineFile.exists())
        assertEquals("8.3.7", mineEngineFile.readText().trim())
    }

    @Test
    fun testMineLauncherTitleFallbackToFolderName() {
        val gameFolder = tempFolder.newFolder("doki-doki_special_mod")

        assertEquals("Doki Doki Special Mod", MineLauncherConfigHelper.getGameTitle(gameFolder))

        MineLauncherConfigHelper.setGameTitle(gameFolder, "   ")
        assertEquals("Doki Doki Special Mod", MineLauncherConfigHelper.getGameTitle(gameFolder))

        MineLauncherConfigHelper.setGameTitle(gameFolder, "Custom Doki Story")
        assertEquals("Custom Doki Story", MineLauncherConfigHelper.getGameTitle(gameFolder))

        MineLauncherConfigHelper.setGameTitle(gameFolder, null)
        assertEquals("Doki Doki Special Mod", MineLauncherConfigHelper.getGameTitle(gameFolder))
    }

    @Test
    fun testMineLauncherSortGamesAlphabetically() {
        val folder1 = tempFolder.newFolder("zebra_game")
        val folder2 = tempFolder.newFolder("middle_game")
        val folder3 = tempFolder.newFolder("alpha_folder")

        MineLauncherConfigHelper.setGameTitle(folder1, "Alpha Game")
        MineLauncherConfigHelper.setGameTitle(folder3, "Zoo Game")

        val unordered = listOf(folder2, folder3, folder1)
        val sorted = MineLauncherConfigHelper.sortGames(unordered)

        assertEquals(listOf(folder1, folder2, folder3), sorted)
        assertEquals("Alpha Game", MineLauncherConfigHelper.getGameTitle(sorted[0]))
        assertEquals("Middle Game", MineLauncherConfigHelper.getGameTitle(sorted[1]))
        assertEquals("Zoo Game", MineLauncherConfigHelper.getGameTitle(sorted[2]))
    }

    @Test
    fun testRenpyVersionDetectionFamily8() {
        val gameFolder = tempFolder.newFolder("family8_game")
        val renpyDir = File(gameFolder, "renpy")
        renpyDir.mkdirs()
        val vcVersion = File(renpyDir, "vc_version.py")
        vcVersion.writeText(
            """
            # Version numbers.
            version = '8.3.4.24120703'
            official = True
            nightly = False
        """.trimIndent()
        )

        val detected = RenpyVersionDetector.detectVersion(gameFolder)
        assertEquals("8.3.4", detected)

        val recommended = RenpyVersionDetector.recommendVersion(detected)
        assertEquals(ExperimentsActivity.RUNTIME_837, recommended)
    }

    @Test
    fun testRenpyVersionDetectionFamily7Py2() {
        val gameFolder = tempFolder.newFolder("family7_game")
        val renpyDir = File(gameFolder, "renpy")
        renpyDir.mkdirs()
        val initFile = File(renpyDir, "__init__.py")
        initFile.writeText(
            """
            if PY2:
                # The tuple giving the version number.
                version_tuple = (7, 5, 3, vc_version)
                version_name = "Heck's Getting Frosty"
            else:
                # The tuple giving the version number.
                version_tuple = (8, 0, 3, vc_version)
                version_name = "Heck Freezes Over"
        """.trimIndent()
        )

        val detected = RenpyVersionDetector.detectVersion(gameFolder)
        assertEquals("7.5.3", detected)

        val recommended = RenpyVersionDetector.recommendVersion(detected)
        assertEquals(ExperimentsActivity.RUNTIME_784, recommended)
    }

    @Test
    fun testRenpyVersionDetectionFamily6() {
        val gameFolder = tempFolder.newFolder("family6_game")
        val renpyDir = File(gameFolder, "renpy")
        renpyDir.mkdirs()
        val initFile = File(renpyDir, "__init__.py")
        initFile.writeText(
            """
            version_tuple = (6, 99, 12, 4)
            version_name = "Idk"
        """.trimIndent()
        )

        val detected = RenpyVersionDetector.detectVersion(gameFolder)
        assertEquals("6.99.12", detected)

        val recommended = RenpyVersionDetector.recommendVersion(detected)
        assertEquals(ExperimentsActivity.RUNTIME_699, recommended)
    }

    @Test
    fun testRenpyVersionRecommendationAlgorithm() {
        assertEquals(ExperimentsActivity.RUNTIME_699, RenpyVersionDetector.recommendVersion("6.18"))
        assertEquals(ExperimentsActivity.RUNTIME_699, RenpyVersionDetector.recommendVersion("6.99.12"))

        assertEquals(ExperimentsActivity.RUNTIME_7411, RenpyVersionDetector.recommendVersion("7.2.1"))
        assertEquals(ExperimentsActivity.RUNTIME_7411, RenpyVersionDetector.recommendVersion("7.4.11"))
        assertEquals(ExperimentsActivity.RUNTIME_784, RenpyVersionDetector.recommendVersion("7.5.0"))
        assertEquals(ExperimentsActivity.RUNTIME_784, RenpyVersionDetector.recommendVersion("7.8.4"))
        assertEquals(ExperimentsActivity.RUNTIME_784, RenpyVersionDetector.recommendVersion("7.9.9"))

        assertEquals(ExperimentsActivity.RUNTIME_803, RenpyVersionDetector.recommendVersion("8.0.0"))
        assertEquals(ExperimentsActivity.RUNTIME_803, RenpyVersionDetector.recommendVersion("8.0.3"))
        assertEquals(ExperimentsActivity.RUNTIME_837, RenpyVersionDetector.recommendVersion("8.1.0"))
        assertEquals(ExperimentsActivity.RUNTIME_837, RenpyVersionDetector.recommendVersion("8.3.7"))
        assertEquals(ExperimentsActivity.RUNTIME_841, RenpyVersionDetector.recommendVersion("8.4.0"))
        assertEquals(ExperimentsActivity.RUNTIME_841, RenpyVersionDetector.recommendVersion("8.4.1"))
        assertEquals(ExperimentsActivity.RUNTIME_853, RenpyVersionDetector.recommendVersion("8.5.0"))
        assertEquals(ExperimentsActivity.RUNTIME_853, RenpyVersionDetector.recommendVersion("8.5.3"))
        assertEquals(ExperimentsActivity.RUNTIME_853, RenpyVersionDetector.recommendVersion("8.9.0"))

        assertNull(RenpyVersionDetector.recommendVersion(null))
        assertNull(RenpyVersionDetector.recommendVersion(""))
        assertNull(RenpyVersionDetector.recommendVersion("invalid"))
        assertNull(RenpyVersionDetector.recommendVersion("5.0.0"))
    }

    @Test
    fun testRenpyVersionCachingInMetadata() {
        val gameFolder = tempFolder.newFolder("cache_test_game")
        val renpyDir = File(gameFolder, "renpy")
        renpyDir.mkdirs()
        val vcVersion = File(renpyDir, "vc_version.py")
        vcVersion.writeText("version = '8.3.4.24120703'")

        val (detected, recommended) = RenpyVersionDetector.detectAndSave(gameFolder)
        assertEquals("8.3.4", detected)
        assertEquals(ExperimentsActivity.RUNTIME_837, recommended)

        val config = MineLauncherConfigHelper.getGameConfig(gameFolder)
        assertEquals("8.3.4", config.detectedVersion)
        assertEquals(ExperimentsActivity.RUNTIME_837, config.recommendedVersion)

        renpyDir.deleteRecursively()
        assertFalse(renpyDir.exists())

        val (cachedDetected, cachedRecommended) = RenpyVersionDetector.detectAndSave(gameFolder)
        assertEquals("8.3.4", cachedDetected)
        assertEquals(ExperimentsActivity.RUNTIME_837, cachedRecommended)
    }

    private fun createZipArchive(zipFile: File, entries: Map<String, String>) {
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
            for ((path, content) in entries) {
                val entry = java.util.zip.ZipEntry(path)
                zos.putNextEntry(entry)
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    @Test
    fun testGameInstallerSha256() {
        val file = tempFolder.newFile("hash_test.txt")
        file.writeText("hello world\n")
        val expected = "A948904F2F0F479B8F8197694B30184B0D2ED1C1CD2A1EC0FB85D299A192A447"
        val actual = GameInstallerEngine.computeSha256(file)
        assertEquals(expected, actual)
    }

    @Test
    fun testGameInstallerIsRarArchive() {
        val rarByExt = tempFolder.newFile("test_game.rar").apply { writeText("dummy") }
        assertTrue(GameInstallerEngine.isRarArchive(rarByExt))

        val rarByMagic = tempFolder.newFile("test_game.archive").apply {
            writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))
        }
        assertTrue(GameInstallerEngine.isRarArchive(rarByMagic))

        val zipFile = tempFolder.newFile("test_game.zip").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        }
        assertFalse(GameInstallerEngine.isRarArchive(zipFile))

        val txtFile = tempFolder.newFile("test.txt").apply { writeText("hello") }
        assertFalse(GameInstallerEngine.isRarArchive(txtFile))
    }

    @Test
    fun testGameInstallerIsRar5Archive() {
        val rar5File = tempFolder.newFile("test_v5.archive").apply {
            writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00))
        }
        assertTrue(GameInstallerEngine.isRar5Archive(rar5File))
        assertTrue(GameInstallerEngine.isRarArchive(rar5File))

        val rar4File = tempFolder.newFile("test_v4.archive").apply {
            writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0x00))
        }
        assertFalse(GameInstallerEngine.isRar5Archive(rar4File))
        assertTrue(GameInstallerEngine.isRarArchive(rar4File))
    }

    @Test
    fun testGameInstallerUniqueFolderCollision() {
        val baseDir = tempFolder.newFolder("games_dir")
        val name = "Cool Game"
        val folder1 = GameInstallerEngine.resolveUniqueFolder(baseDir, name)
        assertEquals("Cool Game", folder1.name)
        folder1.mkdirs()

        val folder2 = GameInstallerEngine.resolveUniqueFolder(baseDir, name)
        assertEquals("Cool Game (1)", folder2.name)
        folder2.mkdirs()

        val folder3 = GameInstallerEngine.resolveUniqueFolder(baseDir, name)
        assertEquals("Cool Game (2)", folder3.name)

        val sanitized = GameInstallerEngine.resolveUniqueFolder(baseDir, "Bad:Name*Test?")
        assertEquals("Bad_Name_Test_", sanitized.name)
    }

    @Test
    fun testGameInstallerEffectiveRootUnwrapping() {
        val rootDir = tempFolder.newFolder("unwrap_test")
        val singleWrapper = File(rootDir, "SingleFolder").apply { mkdirs() }
        File(singleWrapper, "game").apply { mkdirs() }
        File(singleWrapper, "game/script.rpy").writeText("renpy code")

        val unwrapped = GameInstallerEngine.resolveEffectiveSourceRoot(rootDir)
        assertEquals(singleWrapper.absolutePath, unwrapped.absolutePath)

        val multiRoot = tempFolder.newFolder("multi_root_test")
        File(multiRoot, "Folder1").apply { mkdirs() }
        File(multiRoot, "Folder2").apply { mkdirs() }
        val multiResult = GameInstallerEngine.resolveEffectiveSourceRoot(multiRoot)
        assertEquals(multiRoot.absolutePath, multiResult.absolutePath)
    }

    @Test
    fun testGameInstallerBaseGameDir() {
        val tempDdlc = tempFolder.newFolder("ddlc_root_test")
        val ddlcWin = File(tempDdlc, "ddlc-win").apply { mkdirs() }
        File(ddlcWin, "game").apply { mkdirs() }

        val resolved = GameInstallerEngine.resolveBaseGameDir(tempDdlc)
        assertEquals(ddlcWin.absolutePath, resolved.absolutePath)
    }

    @Test
    fun testGameInstallerGenericGameZip() {
        val rootDir = tempFolder.newFolder("installer_test")
        val archiveFile = File(rootDir, "my_awesome_game.zip")
        createZipArchive(
            archiveFile,
            mapOf(
                "game/script.rpy" to "label start:\n    return\n",
                "renpy/__init__.py" to "if PY2:\n    version_tuple = (7, 4, 11, vc_version)\n"
            )
        )

        val targetDir = File(rootDir, "my_awesome_game")
        GameInstallerEngine.installGenericGame(
            archiveFile = archiveFile,
            targetDir = targetDir,
            title = "My Awesome Game"
        )

        assertTrue(targetDir.exists())
        assertTrue(File(targetDir, "game/script.rpy").exists())
        assertTrue(File(targetDir, "renpy/__init__.py").exists())
        assertEquals("My Awesome Game", MineLauncherConfigHelper.getGameTitle(targetDir))

        val config = MineLauncherConfigHelper.getGameConfig(targetDir)
        assertEquals("7.4.11", config.detectedVersion)
        assertEquals(ExperimentsActivity.RUNTIME_7411, config.recommendedVersion)
    }

    @Test
    fun testGameInstallerDdlcModHashMismatch() {
        val rootDir = tempFolder.newFolder("ddlc_test")
        val modArchive = File(rootDir, "mod.zip")
        createZipArchive(modArchive, mapOf("game/mod_script.rpy" to "label mod:\n return\n"))

        val fakeBaseArchive = File(rootDir, "ddlc-win.zip")
        createZipArchive(fakeBaseArchive, mapOf("game/script.rpy" to "label original:\n return\n"))

        val targetDir = File(rootDir, "installed_mod")
        var errorThrown = false
        try {
            GameInstallerEngine.installDdlcMod(
                modArchiveFile = modArchive,
                baseDdlcZipFile = fakeBaseArchive,
                targetDir = targetDir,
                title = "DDLC Mod Test"
            )
        } catch (e: IllegalArgumentException) {
            errorThrown = true
            assertTrue(e.message!!.contains("mismatch", ignoreCase = true))
        }
        assertTrue(errorThrown)
        assertFalse(targetDir.exists())
    }

    @Test
    fun testGameInstallerCancellationRollback() {
        val rootDir = tempFolder.newFolder("cancel_test")
        val archiveFile = File(rootDir, "game.zip")
        createZipArchive(archiveFile, mapOf("game/script.rpy" to "pass"))

        val targetDir = File(rootDir, "cancelled_game")
        var cancelled = false
        try {
            GameInstallerEngine.installGenericGame(
                archiveFile = archiveFile,
                targetDir = targetDir,
                title = "Cancelled Game",
                isCancelled = { true }
            )
        } catch (e: java.util.concurrent.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(targetDir.exists())
    }

    @Test
    fun testGameInstallerUnwrapWithLooseFilesAtRoot() {
        val rootDir = tempFolder.newFolder("loose_files_test")
        val wrapper = File(rootDir, "anotherfolder").apply { mkdirs() }
        File(wrapper, "game").apply { mkdirs() }
        File(wrapper, "game/script.rpy").writeText("renpy script")
        File(rootDir, "readme.txt").writeText("notes")
        File(rootDir, "license.txt").writeText("license")

        val effective = GameInstallerEngine.resolveEffectiveSourceRoot(rootDir)
        assertEquals(wrapper.absolutePath, effective.absolutePath)
    }

    @Test
    fun testGameInstallerNestedFolderUnwrapWithLooseFilesAtRoot() {
        val rootDir = tempFolder.newFolder("archive_loose_test")
        val archiveFile = File(rootDir, "my_packaged_game.zip")
        createZipArchive(
            archiveFile,
            mapOf(
                "anotherfolder/game/script.rpy" to "label start:\n    return\n",
                "anotherfolder/renpy/__init__.py" to "if PY2:\n    version_tuple = (7, 4, 11, vc_version)\n",
                "anotherfolder/game.exe" to "binary",
                "readme.txt" to "loose readme at root",
                "instructions.txt" to "how to play"
            )
        )

        val targetDir = File(rootDir, "installed_packaged_game")
        GameInstallerEngine.installGenericGame(
            archiveFile = archiveFile,
            targetDir = targetDir,
            title = "Packaged Game"
        )

        assertTrue(targetDir.exists())
        assertTrue(File(targetDir, "game").isDirectory)
        assertTrue(File(targetDir, "game/script.rpy").exists())
        assertTrue(File(targetDir, "renpy/__init__.py").exists())
        assertFalse(File(targetDir, "anotherfolder").exists())

        assertTrue(File(targetDir, "readme.txt").exists())
        assertEquals("loose readme at root", File(targetDir, "readme.txt").readText())
        assertTrue(File(targetDir, "instructions.txt").exists())
    }

    @Test
    fun testGameInstallerUnwrapCaseInsensitiveGameFolder() {
        val rootDir = tempFolder.newFolder("archive_casing_test")
        val archiveFile = File(rootDir, "case_test_game.zip")
        createZipArchive(
            archiveFile,
            mapOf(
                "NestedGame/Game/script.rpy" to "label start:\n    return\n",
                "NestedGame/RenPy/__init__.py" to "if PY2:\n    version_tuple = (7, 4, 11, vc_version)\n",
                "notes.txt" to "release notes"
            )
        )

        val targetDir = File(rootDir, "installed_case_game")
        GameInstallerEngine.installGenericGame(
            archiveFile = archiveFile,
            targetDir = targetDir,
            title = "Case Game"
        )

        assertTrue(targetDir.exists())
        assertTrue(File(targetDir, "game").isDirectory)
        assertTrue(File(targetDir, "game/script.rpy").exists())
        assertTrue(File(targetDir, "renpy").isDirectory)
        assertTrue(File(targetDir, "renpy/__init__.py").exists())
        assertTrue(File(targetDir, "notes.txt").exists())
    }
}

