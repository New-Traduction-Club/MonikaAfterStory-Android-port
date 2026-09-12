package org.renpy.android

import java.io.File

object ProfileNavigationHelper {
    const val PROFILE_MAS = "MAS"
    const val PROFILE_RENPY_LAUNCHER = "RENPY_LAUNCHER"

    enum class NavigationTarget {
        SETUP,
        DESKTOP
    }

    fun determineLoginTarget(profile: String, isSetupCompleted: Boolean): NavigationTarget {
        return if (profile == PROFILE_MAS && !isSetupCompleted) {
            NavigationTarget.SETUP
        } else {
            NavigationTarget.DESKTOP
        }
    }

    fun canStartGame(isSetupCompleted: Boolean): Boolean {
        return isSetupCompleted
    }

    fun getTempFilesToDelete(filesDir: File, cacheDir: File): List<File> {
        return listOf(
            File(filesDir, "mod_temp.zip"),
            File(cacheDir, "ddlc_temp.zip"),
            File(cacheDir, "mod_temp_install.zip")
        )
    }

    fun getLanguageShortCode(language: String): String {
        return when (language) {
            "Español" -> "ES"
            "Português" -> "PT"
            else -> "EN"
        }
    }
}
