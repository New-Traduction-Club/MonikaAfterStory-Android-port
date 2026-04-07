package org.renpy.android

import android.content.Context
import android.util.Log
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DiscordRpcManager {

    private const val TAG = "DiscordRpcManager"
    private const val APP_ICON_URL =
        "https://raw.githubusercontent.com/New-Traduction-Club/MonikaAfterStory-Android-port/c064f77/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
    private const val DEFAULT_RPC_APPLICATION_ID = "962990036020756480"
    private const val PLAY_STORE_URL =
        "https://play.google.com/store/apps/details?id=com.z.mas.portby.just6889"
    private const val GITHUB_REPO_URL =
        "https://github.com/New-Traduction-Club/MonikaAfterStory-Android-port"
    const val PREF_DISCORD_RPC_ENABLED = "discord_rpc_enabled"
    const val PREF_DISCORD_RPC_TOKEN = "discord_rpc_token"
    const val PREF_DISCORD_RPC_WARNING_ACCEPTED = "discord_rpc_warning_accepted"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var rpc: KizzyRPC? = null

    @Volatile
    private var startJob: Job? = null

    @JvmStatic
    fun startIfEnabled(context: Context) {
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(PREF_DISCORD_RPC_ENABLED, false)
        val token = normalizeToken(prefs.getString(PREF_DISCORD_RPC_TOKEN, null).orEmpty())
        if (!enabled || token.isBlank()) return

        if (startJob?.isActive == true) return
        if (rpc?.isRpcRunning() == true) return

        val appContext = context.applicationContext
        startJob = scope.launch {
            val newRpc = KizzyRPC(token)
            try {
                newRpc.setActivity(
                    name = appContext.getString(R.string.discord_rpc_presence_name),
                    state = appContext.getString(R.string.discord_rpc_presence_state),
                    details = appContext.getString(R.string.discord_rpc_presence_details),
                    largeImage = RpcImage.ExternalImage(APP_ICON_URL),
                    smallImage = null,
                    largeText = appContext.getString(R.string.discord_rpc_presence_name),
                    buttons = listOf(
                        appContext.getString(R.string.discord_rpc_button_play_store) to PLAY_STORE_URL,
                        appContext.getString(R.string.discord_rpc_button_github_repo) to GITHUB_REPO_URL
                    ),
                    applicationId = DEFAULT_RPC_APPLICATION_ID,
                    type = KizzyRPC.Type.PLAYING
                )
                rpc = newRpc
            } catch (e: Exception) {
                try {
                    newRpc.closeRPC()
                } catch (closeError: Exception) {
                    Log.e(TAG, "Failed to close RPC after start error", closeError)
                }
                Log.e(TAG, "Failed to start Discord RPC", e)
            }
        }
    }

    @JvmStatic
    fun stop() {
        startJob?.cancel()
        startJob = null
        val currentRpc = rpc ?: return
        rpc = null

        scope.launch {
            try {
                if (currentRpc.isRpcRunning()) {
                    currentRpc.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear Discord RPC presence", e)
            } finally {
                try {
                    currentRpc.closeRPC()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to close Discord RPC websocket", e)
                }
            }
        }
    }

    private fun normalizeToken(raw: String): String {
        return raw
            .trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }
}
