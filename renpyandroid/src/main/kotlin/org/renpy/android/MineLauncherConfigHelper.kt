package org.renpy.android

import java.io.File

object MineLauncherConfigHelper {

    data class MineGameConfig(
        var title: String? = null,
        var runtime: String? = null,
        var detectedVersion: String? = null,
        var recommendedVersion: String? = null
    )

    fun isValidEngine(content: String?): Boolean {
        return content == ExperimentsActivity.RUNTIME_699 ||
                content == ExperimentsActivity.RUNTIME_7411 ||
                content == ExperimentsActivity.RUNTIME_784 ||
                content == ExperimentsActivity.RUNTIME_803 ||
                content == ExperimentsActivity.RUNTIME_837 ||
                content == ExperimentsActivity.RUNTIME_841 ||
                content == ExperimentsActivity.RUNTIME_853
    }

    fun getGameDefaultDisplayName(file: File): String {
        return file.name
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    fun getGameConfig(gameFolder: File): MineGameConfig {
        val mineDir = File(gameFolder, ".mine")
        val configFile = File(mineDir, "configs.json")
        var title: String? = null
        var runtime: String? = null
        var detectedVersion: String? = null
        var recommendedVersion: String? = null

        if (configFile.exists()) {
            try {
                val content = configFile.readText()
                val parsedTitle = extractJsonString(content, "title")?.trim()
                if (!parsedTitle.isNullOrEmpty()) {
                    title = parsedTitle
                }
                val parsedRuntime = extractJsonString(content, "runtime")?.trim()
                if (isValidEngine(parsedRuntime)) {
                    runtime = parsedRuntime
                }
                val parsedDetected = extractJsonString(content, "detected_version")?.trim()
                if (!parsedDetected.isNullOrEmpty()) {
                    detectedVersion = parsedDetected
                }
                val parsedRecommended = extractJsonString(content, "recommended_version")?.trim()
                if (!parsedRecommended.isNullOrEmpty()) {
                    recommendedVersion = parsedRecommended
                }
            } catch (e: Exception) {
            }
        }

        if (runtime == null) {
            val mineEngine = File(mineDir, "engine.txt")
            if (mineEngine.exists()) {
                val content = try {
                    mineEngine.readText().trim()
                } catch (e: Exception) {
                    null
                }
                if (isValidEngine(content)) runtime = content
            }
        }

        if (runtime == null) {
            val rootEngine = File(gameFolder, "engine.txt")
            if (rootEngine.exists()) {
                val content = try {
                    rootEngine.readText().trim()
                } catch (e: Exception) {
                    null
                }
                if (isValidEngine(content)) runtime = content
            }
        }

        return MineGameConfig(
            title = title,
            runtime = runtime,
            detectedVersion = detectedVersion,
            recommendedVersion = recommendedVersion
        )
    }

    fun saveGameConfig(gameFolder: File, config: MineGameConfig) {
        try {
            val mineDir = File(gameFolder, ".mine")
            if (!mineDir.exists()) {
                mineDir.mkdirs()
            }
            val jsonContent = buildJsonString(
                title = config.title,
                runtime = config.runtime,
                detectedVersion = config.detectedVersion,
                recommendedVersion = config.recommendedVersion
            )
            File(mineDir, "configs.json").writeText(jsonContent)

            if (!config.runtime.isNullOrBlank() && isValidEngine(config.runtime)) {
                File(mineDir, "engine.txt").writeText(config.runtime!!)
                File(gameFolder, "engine.txt").writeText(config.runtime!!)

                File(gameFolder, ".runtime_699.version").delete()
                File(gameFolder, ".runtime_7411.version").delete()
                File(gameFolder, ".runtime_784.version").delete()
                File(gameFolder, ".runtime_803.version").delete()
                File(gameFolder, ".runtime_837.version").delete()
                File(gameFolder, ".runtime_841.version").delete()
                File(gameFolder, ".runtime_853.version").delete()
                File(gameFolder, "private.version").delete()
                File(gameFolder, ".private.version").delete()
            }
        } catch (e: Exception) {
        }
    }

    fun getGameTitle(gameFolder: File): String {
        val config = getGameConfig(gameFolder)
        return config.title?.takeIf { it.isNotBlank() } ?: getGameDefaultDisplayName(gameFolder)
    }

    fun getGameEngine(gameFolder: File): String? {
        return getGameConfig(gameFolder).runtime
    }

    fun setGameEngine(gameFolder: File, engine: String) {
        val config = getGameConfig(gameFolder)
        config.runtime = engine
        saveGameConfig(gameFolder, config)
    }

    fun setGameTitle(gameFolder: File, title: String?) {
        val config = getGameConfig(gameFolder)
        config.title = title?.trim()?.takeIf { it.isNotBlank() }
        saveGameConfig(gameFolder, config)
    }

    fun getDetectedVersion(gameFolder: File): String? {
        return getGameConfig(gameFolder).detectedVersion
    }

    fun getRecommendedVersion(gameFolder: File): String? {
        return getGameConfig(gameFolder).recommendedVersion
    }

    fun saveDetectedAndRecommended(gameFolder: File, detected: String?, recommended: String?) {
        val config = getGameConfig(gameFolder)
        config.detectedVersion = detected?.trim()?.takeIf { it.isNotBlank() }
        config.recommendedVersion = recommended?.trim()?.takeIf { it.isNotBlank() }
        saveGameConfig(gameFolder, config)
    }

    fun getCoverArtFile(gameFolder: File): File {
        return File(File(gameFolder, ".mine"), "cover.png")
    }

    fun hasCoverArt(gameFolder: File): Boolean {
        return getCoverArtFile(gameFolder).exists()
    }

    fun deleteCoverArt(gameFolder: File): Boolean {
        val file = getCoverArtFile(gameFolder)
        return if (file.exists()) file.delete() else false
    }

    fun saveCoverArt(gameFolder: File, sourceFile: File): Boolean {
        return try {
            val target = getCoverArtFile(gameFolder)
            target.parentFile?.mkdirs()
            sourceFile.copyTo(target, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sortGames(games: List<File>): List<File> {
        return games.sortedWith { f1, f2 ->
            val comp = String.CASE_INSENSITIVE_ORDER.compare(getGameTitle(f1), getGameTitle(f2))
            if (comp != 0) comp else String.CASE_INSENSITIVE_ORDER.compare(f1.name, f2.name)
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
        val match = pattern.find(json) ?: return null
        return match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun buildJsonString(
        title: String?,
        runtime: String?,
        detectedVersion: String? = null,
        recommendedVersion: String? = null
    ): String {
        val lines = mutableListOf<String>()
        if (!title.isNullOrBlank()) {
            val escapedTitle = title.trim().replace("\\", "\\\\").replace("\"", "\\\"")
            lines.add("  \"title\": \"$escapedTitle\"")
        }
        if (!runtime.isNullOrBlank() && isValidEngine(runtime)) {
            lines.add("  \"runtime\": \"${runtime.trim()}\"")
        }
        if (!detectedVersion.isNullOrBlank()) {
            val escaped = detectedVersion.trim().replace("\\", "\\\\").replace("\"", "\\\"")
            lines.add("  \"detected_version\": \"$escaped\"")
        }
        if (!recommendedVersion.isNullOrBlank()) {
            val escaped = recommendedVersion.trim().replace("\\", "\\\\").replace("\"", "\\\"")
            lines.add("  \"recommended_version\": \"$escaped\"")
        }
        return "{\n" + lines.joinToString(",\n") + "\n}"
    }
}
