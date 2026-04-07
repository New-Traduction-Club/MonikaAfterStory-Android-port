package org.renpy.android

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient

class DiscordLoginActivity : BaseActivity() {

    override val preferredOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

    private var webView: WebView? = null
    private var extractionAttempt = 0
    private var extractionLoopStarted = false
    private var tokenDelivered = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.discord_rpc_login_title)

        val web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            if (Build.MANUFACTURER.equals(MOTOROLA_MANUFACTURER, ignoreCase = true)) {
                settings.userAgentString = MOTOROLA_FALLBACK_USER_AGENT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    maybeRequestTokenExtraction(view, url.orEmpty())
                }
            }
        }

        webView = web
        setContentView(web)
        web.loadUrl(DISCORD_LOGIN_URL)
    }

    private fun maybeRequestTokenExtraction(view: WebView, url: String) {
        if (tokenDelivered) return
        if (!url.contains("discord.com") || extractionLoopStarted) return
        extractionLoopStarted = true
        extractionAttempt = 0
        scheduleTokenExtraction(view)
    }

    private fun scheduleTokenExtraction(view: WebView) {
        if (tokenDelivered || webView !== view || isFinishing || isDestroyed) return
        extractionAttempt++
        val delayMillis = if (extractionAttempt == 1) 0L else EXTRACTION_RETRY_INTERVAL_MS
        view.postDelayed({
            if (tokenDelivered || webView !== view || isFinishing || isDestroyed) return@postDelayed
            view.evaluateJavascript(JS_TOKEN_EXTRACTION_SNIPPET) { jsResult ->
                if (tokenDelivered || webView !== view || isFinishing || isDestroyed) return@evaluateJavascript
                val token = normalizeJavascriptToken(jsResult)
                if (token.isNotBlank()) {
                    finishWithToken(token)
                } else {
                    scheduleTokenExtraction(view)
                }
            }
        }, delayMillis)
    }

    private fun finishWithToken(token: String) {
        if (tokenDelivered) return
        tokenDelivered = true
        extractionLoopStarted = false
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_DISCORD_TOKEN, token)
        )
        finish()
    }

    override fun onDestroy() {
        tokenDelivered = true
        extractionLoopStarted = false
        webView?.let { currentWebView ->
            (currentWebView.parent as? ViewGroup)?.removeView(currentWebView)
            currentWebView.stopLoading()
            currentWebView.destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun normalizeDiscordToken(raw: String): String {
        return raw
            .trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .removeSurrounding("\"")
            .removeSurrounding("'")
    }

    private fun normalizeJavascriptToken(raw: String?): String {
        val jsValue = raw.orEmpty().trim()
        if (jsValue.isBlank() || jsValue == "null" || jsValue == "\"\"") {
            return ""
        }
        val unescaped = jsValue
            .removeSurrounding("\"")
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
        return normalizeDiscordToken(unescaped)
    }

    companion object {
        const val EXTRA_DISCORD_TOKEN = "extra_discord_token"

        private const val DISCORD_LOGIN_URL = "https://discord.com/login"
        private const val EXTRACTION_RETRY_INTERVAL_MS = 120L
        private const val MOTOROLA_MANUFACTURER = "motorola"
        private const val JS_TOKEN_EXTRACTION_SNIPPET =
            "(function(){try{var token=window.localStorage.getItem('token');return token?token.slice(1,-1):'';}catch(e){return '';}})();"
        private const val MOTOROLA_FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
    }
}
