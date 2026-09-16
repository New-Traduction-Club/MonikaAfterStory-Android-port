package org.renpy.android

import java.io.File

object RenpyVersionDetector {

    fun hasPython3InLib(gameFolder: File): Boolean {
        val candidateDirs = mutableListOf<File>()
        candidateDirs.add(File(gameFolder, "lib"))
        candidateDirs.add(File(File(gameFolder, "game"), "lib"))

        gameFolder.listFiles()?.firstOrNull { it.isDirectory && it.name.equals("lib", ignoreCase = true) }?.let {
            if (!candidateDirs.contains(it)) {
                candidateDirs.add(it)
            }
        }

        for (libDir in candidateDirs) {
            if (!libDir.isDirectory) continue
            val entries = libDir.listFiles() ?: continue
            for (entry in entries) {
                val name = entry.name.lowercase()
                if (isPython3Name(name)) {
                    return true
                }
                if (entry.isDirectory) {
                    val subEntries = entry.listFiles() ?: continue
                    for (sub in subEntries) {
                        if (isPython3Name(sub.name.lowercase())) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun isPython3Name(name: String): Boolean {
        return name.startsWith("python3.") ||
                name.startsWith("py3-") ||
                Regex("""^python3\.\d+""").containsMatchIn(name)
    }

    fun detectVersion(gameFolder: File): String? {
        val hasPy3 = hasPython3InLib(gameFolder)

        val cached = MineLauncherConfigHelper.getDetectedVersion(gameFolder)
        if (!cached.isNullOrBlank()) {
            val isCacheStale = (cached.startsWith("7.") && hasPy3) ||
                    (cached.startsWith("8.") && !hasPy3 && (File(gameFolder, "lib").isDirectory || File(
                        File(
                            gameFolder,
                            "game"
                        ), "lib"
                    ).isDirectory))
            if (!isCacheStale) {
                return cached
            }
        }

        val vcVersionFile = File(File(gameFolder, "renpy"), "vc_version.py")
        if (vcVersionFile.exists()) {
            try {
                val content = vcVersionFile.readText()
                val match = Regex("""version\s*=\s*['"]([0-9.]+)['"]""").find(content)
                if (match != null) {
                    val rawVersion = match.groupValues[1].trim()
                    if (rawVersion.isNotEmpty() && rawVersion[0].isDigit()) {
                        val parts = rawVersion.split('.').filter { it.isNotEmpty() }
                        if (parts.isNotEmpty()) {
                            return parts.take(3).joinToString(".")
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }

        val initFile = File(File(gameFolder, "renpy"), "__init__.py")
        if (initFile.exists()) {
            try {
                val content = initFile.readText()

                val targetText = if (hasPy3) {
                    val py3BlockRegex = Regex("""if\s+PY2\s*:[\s\S]*?else\s*:([\s\S]*?)(?:version_only|\Z)""")
                    py3BlockRegex.find(content)?.groupValues?.get(1) ?: content
                } else {
                    val py2BlockRegex = Regex("""if\s+PY2\s*:([\s\S]*?)(?:else\s*:|\Z)""")
                    py2BlockRegex.find(content)?.groupValues?.get(1) ?: content
                }

                val tupleRegex =
                    Regex("""version_tuple\s*=\s*(?:\([^\d]*|VersionTuple\([^\d]*)\s*([0-9]+)\s*,\s*([0-9]+)(?:\s*,\s*([0-9]+))?""")
                var tupleMatch = tupleRegex.find(targetText)
                if (tupleMatch == null && targetText !== content) {
                    tupleMatch = tupleRegex.find(content)
                }

                if (tupleMatch != null) {
                    val major = tupleMatch.groupValues[1]
                    val minor = tupleMatch.groupValues[2]
                    val patch = tupleMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
                    return if (patch != null) "$major.$minor.$patch" else "$major.$minor"
                }

                val fallbackMatch = Regex("""version\s*=\s*['"]([0-9.]+)['"]""").find(content)
                if (fallbackMatch != null) {
                    val rawVersion = fallbackMatch.groupValues[1].trim()
                    val parts = rawVersion.split('.').filter { it.isNotEmpty() }
                    if (parts.isNotEmpty()) {
                        return parts.take(3).joinToString(".")
                    }
                }
            } catch (e: Exception) {
            }
        }

        return null
    }

    fun recommendVersion(detectedVersion: String?): String? {
        if (detectedVersion.isNullOrBlank()) return null

        val parts = detectedVersion.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null

        val major = parts[0]
        return when (major) {
            6 -> ExperimentsActivity.RUNTIME_699
            7 -> {
                if (compareSemantic(parts, listOf(7, 4, 11)) <= 0) {
                    ExperimentsActivity.RUNTIME_7411
                } else {
                    ExperimentsActivity.RUNTIME_784
                }
            }

            8 -> {
                if (compareSemantic(parts, listOf(8, 0, 3)) <= 0) {
                    ExperimentsActivity.RUNTIME_803
                } else if (compareSemantic(parts, listOf(8, 3, 7)) <= 0) {
                    ExperimentsActivity.RUNTIME_837
                } else if (compareSemantic(parts, listOf(8, 4, 1)) <= 0) {
                    ExperimentsActivity.RUNTIME_841
                } else {
                    ExperimentsActivity.RUNTIME_853
                }
            }

            else -> null
        }
    }

    fun detectAndSave(gameFolder: File): Pair<String?, String?> {
        val cachedDetected = MineLauncherConfigHelper.getDetectedVersion(gameFolder)
        val cachedRecommended = MineLauncherConfigHelper.getRecommendedVersion(gameFolder)

        val hasPy3 = hasPython3InLib(gameFolder)
        val isCacheStale = (cachedDetected != null && cachedDetected.startsWith("7.") && hasPy3) ||
                (cachedDetected != null && cachedDetected.startsWith("8.") && !hasPy3 && (File(
                    gameFolder,
                    "lib"
                ).isDirectory || File(File(gameFolder, "game"), "lib").isDirectory))

        if (!isCacheStale && !cachedDetected.isNullOrBlank() && !cachedRecommended.isNullOrBlank()) {
            return Pair(cachedDetected, cachedRecommended)
        }

        val detected = detectVersion(gameFolder)
        val recommended = recommendVersion(detected)

        if (detected != null || recommended != null) {
            MineLauncherConfigHelper.saveDetectedAndRecommended(gameFolder, detected, recommended)
        }

        return Pair(detected, recommended)
    }

    private fun compareSemantic(v1: List<Int>, v2: List<Int>): Int {
        val maxLen = maxOf(v1.size, v2.size)
        for (i in 0 until maxLen) {
            val p1 = v1.getOrElse(i) { 0 }
            val p2 = v2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }
}
