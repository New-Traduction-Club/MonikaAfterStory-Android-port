package org.renpy.android

import android.content.Context

object AutoLoginHelper {
    const val PREFS_KEY_AUTO_LOGIN = "auto_login_mode"
    const val PREFS_KEY_LAST_USED = "last_used_profile"

    const val MODE_DISABLED = "disabled"
    const val MODE_MAS = "mas"
    const val MODE_MINE = "mine"
    const val MODE_LAST_USED = "last_used"

    val MODES = arrayOf(
        MODE_DISABLED,
        MODE_MAS,
        MODE_MINE,
        MODE_LAST_USED
    )

    fun getAutoLoginMode(context: Context): String {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString(PREFS_KEY_AUTO_LOGIN, MODE_DISABLED) ?: MODE_DISABLED
    }

    fun setAutoLoginMode(context: Context, mode: String) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY_AUTO_LOGIN, mode)
            .apply()
    }

    fun recordLastUsedProfile(context: Context, profile: String) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY_LAST_USED, profile)
            .apply()
    }

    fun resolveAutoLoginProfile(context: Context): String? {
        val mode = getAutoLoginMode(context)
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastUsed = prefs.getString(PREFS_KEY_LAST_USED, null)
            ?: prefs.getString("active_user_profile", ProfileNavigationHelper.PROFILE_MAS)
        return resolveProfileForMode(mode, lastUsed)
    }

    fun resolveProfileForMode(mode: String, lastUsedProfile: String?): String? {
        return when (mode) {
            MODE_MAS -> ProfileNavigationHelper.PROFILE_MAS
            MODE_MINE -> ProfileNavigationHelper.PROFILE_RENPY_LAUNCHER
            MODE_LAST_USED -> lastUsedProfile ?: ProfileNavigationHelper.PROFILE_MAS
            else -> null
        }
    }

    fun getModeLabel(context: Context, mode: String): String {
        val resId = when (mode) {
            MODE_MAS -> R.string.settings_auto_login_mas
            MODE_MINE -> R.string.settings_auto_login_mine
            MODE_LAST_USED -> R.string.settings_auto_login_last_used
            else -> R.string.settings_auto_login_disabled
        }
        return context.getString(resId)
    }
}
