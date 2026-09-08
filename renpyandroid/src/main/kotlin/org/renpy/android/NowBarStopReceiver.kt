package org.renpy.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

class NowBarStopReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_NOWBAR = "org.renpy.android.ACTION_STOP_NOWBAR"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val DEFAULT_NOWBAR_ID = 2026
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, DEFAULT_NOWBAR_ID)
        NotificationManagerCompat.from(context).cancel(notificationId)
        Toast.makeText(context, R.string.nowbar_demo_stopped, Toast.LENGTH_SHORT).show()
    }
}
