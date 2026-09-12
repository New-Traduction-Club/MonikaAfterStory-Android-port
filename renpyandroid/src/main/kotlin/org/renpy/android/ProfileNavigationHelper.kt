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

    fun getPinnedItems(profile: String): List<DesktopShortcut> {
        return if (profile == PROFILE_RENPY_LAUNCHER) {
            listOf(
                DesktopShortcut(R.string.title_mine, android.R.drawable.ic_menu_compass, "experiments"),
                DesktopShortcut(R.string.label_internal_files, R.drawable.ic_launcher_internal, "internal_files"),
                DesktopShortcut(R.string.launcher_browse_external, R.drawable.ic_launcher_external, "external_files"),
                DesktopShortcut(R.string.launcher_wallpapers, R.drawable.ic_launcher_wallpaper, "wallpapers"),
                DesktopShortcut(R.string.launcher_settings, R.drawable.ic_launcher_settings, "settings"),
                DesktopShortcut(R.string.launcher_all_programs, android.R.drawable.ic_menu_sort_by_size, "toggle_expand")
            )
        } else {
            listOf(
                DesktopShortcut(R.string.launcher_start_game, android.R.drawable.ic_media_play, "start_game"),
                DesktopShortcut(R.string.label_internal_files, R.drawable.ic_launcher_internal, "internal_files"),
                DesktopShortcut(R.string.launcher_import_button, R.drawable.ic_launcher_import, "import"),
                DesktopShortcut(R.string.launcher_export_button, R.drawable.ic_launcher_export, "export"),
                DesktopShortcut(R.string.launcher_settings, R.drawable.ic_launcher_settings, "settings"),
                DesktopShortcut(R.string.launcher_all_programs, android.R.drawable.ic_menu_sort_by_size, "toggle_expand")
            )
        }
    }

    fun getExpandedItems(profile: String): List<DesktopShortcut> {
        return if (profile == PROFILE_RENPY_LAUNCHER) {
            listOf(
                DesktopShortcut(R.string.title_app_info, android.R.drawable.ic_menu_info_details, "app_info"),
                DesktopShortcut(R.string.launcher_log_off, android.R.drawable.ic_lock_power_off, "switch_user")
            )
        } else {
            listOf(
                DesktopShortcut(R.string.launcher_browse_external, R.drawable.ic_launcher_external, "external_files"),
                DesktopShortcut(R.string.launcher_update_game, R.drawable.ic_launcher_export, "update_game"),
                DesktopShortcut(R.string.launcher_add_extra_content, android.R.drawable.ic_input_add, "extra_content"),
                DesktopShortcut(R.string.launcher_discord_rpc, android.R.drawable.stat_notify_chat, "discord_rpc"),
                DesktopShortcut(R.string.launcher_backups, R.drawable.ic_launcher_backup, "backups"),
                DesktopShortcut(R.string.launcher_wallpapers, R.drawable.ic_launcher_wallpaper, "wallpapers"),
                DesktopShortcut(R.string.title_app_info, android.R.drawable.ic_menu_info_details, "app_info"),
                DesktopShortcut(R.string.title_experiments, android.R.drawable.ic_menu_compass, "experiments"),
                DesktopShortcut(R.string.launcher_log_off, android.R.drawable.ic_lock_power_off, "switch_user")
            )
        }
    }
}
