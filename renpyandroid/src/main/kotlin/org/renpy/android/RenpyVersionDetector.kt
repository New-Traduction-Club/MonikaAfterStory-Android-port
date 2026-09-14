package org.renpy.android

import java.io.File

object RenpyVersionDetector {

    fun detectVersion(gameFolder: File): String? {
        val cached = MineLauncherConfigHelper.getDetectedVersion(gameFolder)
        if (!cached.isNullOrBlank()) {
            return cached
        }

        val vcVersionFile = File(File(gameFolder, "renpy"), "vc_version.py")
        if (vcVersionFile.exists()) {
            try {
                val content = vcVersionFile.readText()
                val match = Regex("""version\s*=\s*['"]([0-9.]+)['"]""").find(content)
                if (match != null) {
                    val rawVersion = match.groupValues[1].trim()
                    if (rawVersion.startsWith("8.")) {
                        val parts = rawVersion.split('.').filter { it.isNotEmpty() }
                        return parts.take(3).joinToString(".")
                    }
                }
            } catch (e: Exception) {
            }
        }

        val initFile = File(File(gameFolder, "renpy"), "__init__.py")
        if (initFile.exists()) {
            try {
                val content = initFile.readText()

                val py2BlockRegex = Regex("""if\s+PY2\s*:([\s\S]*?)(?:else\s*:|\Z)""")
                val py2Match = py2BlockRegex.find(content)
                val targetText = py2Match?.groupValues?.get(1) ?: content

                val tupleRegex = Regex("""version_tuple\s*=\s*\(\s*([0-9]+)\s*,\s*([0-9]+)(?:\s*,\s*([0-9]+))?""")
                val tupleMatch = tupleRegex.find(targetText)
                if (tupleMatch != null) {
                    val major = tupleMatch.groupValues[1]
                    val minor = tupleMatch.groupValues[2]
                    val patch = tupleMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
                    return if (patch != null) "$major.$minor.$patch" else "$major.$minor"
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

        if (!cachedDetected.isNullOrBlank() && !cachedRecommended.isNullOrBlank()) {
            return Pair(cachedDetected, cachedRecommended)
        }

        val detected = cachedDetected ?: detectVersion(gameFolder)
        val recommended = cachedRecommended ?: recommendVersion(detected)

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
