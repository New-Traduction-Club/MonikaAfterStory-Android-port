package org.renpy.android

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class DiscordRpcActivity : GameWindowActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var loginStatusView: TextView
    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var sessionValidButton: MaterialButton
    private lateinit var logoutButton: MaterialButton
    private var updatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discord_rpc)
        setTitle(R.string.discord_rpc_dialog_title)

        prefs = getSharedPreferences(BaseActivity.PREFS_NAME, MODE_PRIVATE)
        loginStatusView = findViewById(R.id.txtDiscordLoginStatus)
        enabledSwitch = findViewById(R.id.switchDiscordRpcEnabled)
        val loginButton: MaterialButton = findViewById(R.id.btnDiscordLogin)
        sessionValidButton = findViewById(R.id.btnDiscordSessionValid)
        logoutButton = findViewById(R.id.btnDiscordLogout)

        val enabled = prefs.getBoolean(DiscordRpcManager.PREF_DISCORD_RPC_ENABLED, false)
        updatingSwitch = true
        enabledSwitch.isChecked = enabled
        updatingSwitch = false

        refreshLoginStatus()

        loginButton.setOnClickListener {
            startActivityForResult(
                Intent(this, DiscordLoginActivity::class.java),
                REQUEST_CODE_DISCORD_LOGIN
            )
        }
        logoutButton.setOnClickListener {
            showLogoutDialog()
        }

        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            if (isChecked && readToken().isBlank()) {
                updatingSwitch = true
                enabledSwitch.isChecked = false
                updatingSwitch = false
                InAppNotifier.show(this, getString(R.string.discord_rpc_login_required), true)
                return@setOnCheckedChangeListener
            }

            prefs.edit()
                .putBoolean(DiscordRpcManager.PREF_DISCORD_RPC_ENABLED, isChecked)
                .apply()

            if (isChecked) {
                InAppNotifier.show(this, getString(R.string.discord_rpc_saved_enabled))
            } else {
                DiscordRpcManager.stop()
                InAppNotifier.show(this, getString(R.string.discord_rpc_saved_disabled))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE_DISCORD_LOGIN) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        if (resultCode != RESULT_OK || data == null) return

        val token = normalizeDiscordToken(
            data.getStringExtra(DiscordLoginActivity.EXTRA_DISCORD_TOKEN).orEmpty()
        )
        if (token.isBlank()) {
            InAppNotifier.show(this, getString(R.string.discord_rpc_login_failed), true)
            return
        }

        prefs.edit()
            .putString(DiscordRpcManager.PREF_DISCORD_RPC_TOKEN, token)
            .apply()
        refreshLoginStatus()
        InAppNotifier.show(this, getString(R.string.discord_rpc_login_success))
    }

    private fun refreshLoginStatus() {
        val hasValidSession = readToken().isNotBlank()
        loginStatusView.text = if (hasValidSession) {
            getString(R.string.discord_rpc_login_status_connected)
        } else {
            getString(R.string.discord_rpc_login_status_disconnected)
        }
        sessionValidButton.visibility = if (hasValidSession) View.VISIBLE else View.GONE
        logoutButton.visibility = if (hasValidSession) View.VISIBLE else View.GONE
    }

    private fun readToken(): String {
        return normalizeDiscordToken(
            prefs.getString(DiscordRpcManager.PREF_DISCORD_RPC_TOKEN, "").orEmpty()
        )
    }

    private fun normalizeDiscordToken(raw: String): String {
        return raw
            .trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }

    private fun showLogoutDialog() {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.discord_rpc_logout_confirm_title))
            .setMessage(getString(R.string.discord_rpc_logout_confirm_message))
            .setPositiveButton(getString(R.string.launcher_proceed)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performLogout() {
        prefs.edit()
            .remove(DiscordRpcManager.PREF_DISCORD_RPC_TOKEN)
            .putBoolean(DiscordRpcManager.PREF_DISCORD_RPC_ENABLED, false)
            .apply()
        DiscordRpcManager.stop()

        updatingSwitch = true
        enabledSwitch.isChecked = false
        updatingSwitch = false
        refreshLoginStatus()

        InAppNotifier.show(this, getString(R.string.discord_rpc_logout_success))
    }

    companion object {
        private const val REQUEST_CODE_DISCORD_LOGIN = 3001
    }
}
