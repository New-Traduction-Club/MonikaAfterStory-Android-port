package org.renpy.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.nowbar.api.NowBarConfig
import com.nowbar.api.NowBarManager
import com.nowbar.api.cards.CustomCard
import com.nowbar.api.notification.ActionConfig
import com.nowbar.api.util.AppIconHelper

/**
 * Class providing static JNI/PyJNIus callable methods for Ren'Py / Python scripts.
 *
 * ```python
 * from jnius import autoclass
 *
 * NowBarBridge = autoclass(b'org.renpy.android.NowBarBridge')
 *
 * # start default test counter
 * NowBarBridge.startTest()
 *
 * # stop test counter
 * NowBarBridge.stopTest()
 *
 * # start custom live counter
 * NowBarBridge.startLiveCounter("MASL", "Monika", "text")
 * ```
 */
object NowBarBridge {

    private const val TAG = "NowBarBridge"
    const val DEFAULT_NOWBAR_ID = 2026
    const val DEFAULT_CHANNEL_ID = "mas_nowbar_channel"

    @JvmStatic
    fun startTest(): Boolean {
        val context = PythonSDLActivity.mActivity
        if (context == null) {
            Log.w(TAG, "Cannot start NowBar test: PythonSDLActivity.mActivity is null")
            return false
        }
        return startTest(context)
    }

    @JvmStatic
    fun startTest(context: Context): Boolean {
        return startLiveCounter(
            context = context,
            title = "MASL",
            primaryText = "Hello World",
            secondaryText = "Just counting"
        )
    }

    @JvmStatic
    @JvmOverloads
    fun startLiveCounter(
        title: String,
        primaryText: String,
        secondaryText: String = ""
    ): Boolean {
        val context = PythonSDLActivity.mActivity
        if (context == null) {
            Log.w(TAG, "Cannot start live counter: PythonSDLActivity.mActivity is null")
            return false
        }
        return startLiveCounter(context, title, primaryText, secondaryText)
    }

    @JvmStatic
    fun startLiveCounter(
        context: Context,
        title: String,
        primaryText: String,
        secondaryText: String = ""
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(TAG, "Now Bar requires Android 16+")
            return false
        }

        val appIcon = AppIconHelper.getAppIconCompat(context, context.packageName)
            ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        val config = NowBarConfig(
            channelId = DEFAULT_CHANNEL_ID,
            channelName = "Monika Live Updates",
            channelDescription = "MASL live status and updates",
            notificationId = DEFAULT_NOWBAR_ID,
            samsungStyle = NowBarConfig.STYLE_BOTH
        )

        val targetClass = if (PythonSDLActivity.mActivity != null) {
            PythonSDLActivity::class.java
        } else {
            LauncherActivity::class.java
        }
        val tapIntent = Intent(context, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, NowBarStopReceiver::class.java).apply {
            action = NowBarStopReceiver.ACTION_STOP_NOWBAR
            putExtra(NowBarStopReceiver.EXTRA_NOTIFICATION_ID, DEFAULT_NOWBAR_ID)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            DEFAULT_NOWBAR_ID,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopLabel = try {
            context.getString(R.string.nowbar_action_stop)
        } catch (e: Exception) {
            "Stop"
        }

        val cardBuilder = CustomCard.Builder.create(title, appIcon, primaryText)
            .chipWhenTimeMillis(System.currentTimeMillis(), countDown = false)
            .tapAction(tapPendingIntent)
            .action(
                ActionConfig.textOnly(
                    "stop_demo",
                    stopLabel,
                    stopPendingIntent
                )
            )

        if (secondaryText.isNotBlank()) {
            cardBuilder.secondaryText(secondaryText)
        }

        return NowBarManager.notify(context, config, cardBuilder.build())
    }

    @JvmStatic
    fun stopTest(): Boolean {
        val context = PythonSDLActivity.mActivity
        if (context == null) {
            Log.w(TAG, "Cannot stop NowBar test: PythonSDLActivity.mActivity is null")
            return false
        }
        return stopTest(context)
    }

    @JvmStatic
    fun stopTest(context: Context): Boolean {
        return stopLiveCounter(context, DEFAULT_NOWBAR_ID)
    }

    @JvmStatic
    @JvmOverloads
    fun stopLiveCounter(
        context: Context,
        notificationId: Int = DEFAULT_NOWBAR_ID
    ): Boolean {
        NotificationManagerCompat.from(context).cancel(notificationId)
        return true
    }

    @JvmStatic
    fun isSupported(): Boolean {
        val context = PythonSDLActivity.mActivity ?: return false
        return isSupported(context)
    }

    @JvmStatic
    fun isSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                NowBarManager.isSupported(context)
    }
}
