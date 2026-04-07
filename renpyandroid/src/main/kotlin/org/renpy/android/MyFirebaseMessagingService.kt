package org.renpy.android

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.Locale

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: ${remoteMessage.from}")

        val content = resolveNotificationContent(remoteMessage) ?: return
        sendNotification(content.first, content.second)
    }

    private fun resolveNotificationContent(remoteMessage: RemoteMessage): Pair<String, String>? {
        val languageCode = getPreferredLanguageCode()
        val localizedContext = getLocalizedContext(languageCode)

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            val data = remoteMessage.data
            val title = pickLocalizedPayloadValue(data, "title", languageCode)
                ?: localizedContext.getString(R.string.firebase_notification_default_title)
            val body = pickLocalizedPayloadValue(data, "body", languageCode)
                ?: localizedContext.getString(R.string.firebase_notification_default_body)
            return title to body
        }

        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            val title = it.title?.takeIf(String::isNotBlank)
                ?: localizedContext.getString(R.string.firebase_notification_default_title)
            val body = it.body?.takeIf(String::isNotBlank)
                ?: localizedContext.getString(R.string.firebase_notification_default_body)
            return title to body
        }

        return null
    }

    private fun pickLocalizedPayloadValue(
        payload: Map<String, String>,
        baseKey: String,
        languageCode: String
    ): String? {
        val candidates = listOf(
            "${baseKey}_${languageCode.lowercase(Locale.ROOT)}",
            "${baseKey}_${languageCode.uppercase(Locale.ROOT)}",
            baseKey
        )
        for (key in candidates) {
            val value = payload[key]?.trim()
            if (!value.isNullOrEmpty()) {
                return value
            }
        }
        return null
    }

    private fun getPreferredLanguageCode(): String {
        val prefs = getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString("language", "English")) {
            "Español" -> "es"
            "Português" -> "pt"
            else -> "en"
        }
    }

    private fun getLocalizedContext(languageCode: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale(languageCode))
        return createConfigurationContext(config)
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed FCM token: $token")
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, DownloadCenterActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "updates_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_download)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
