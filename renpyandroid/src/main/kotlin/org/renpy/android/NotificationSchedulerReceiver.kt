package org.renpy.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlin.random.Random
import java.util.concurrent.TimeUnit

class NotificationSchedulerReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_TRIGGER = "org.renpy.android.action.TRIGGER_NOTIFICATION"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_MESSAGE = "extra_message"
        private const val EXTRA_IMAGE_PATH = "extra_image_path"
        private const val EXTRA_REQUEST_CODE = "extra_request_code"

        private const val PREFS_NAME = "notification_scheduler_prefs"
        private const val PREF_SCHEDULED_REQUEST_CODES = "scheduled_request_codes"

        private fun triggerIntent(context: Context): Intent {
            return Intent(context, NotificationSchedulerReceiver::class.java).apply {
                action = ACTION_TRIGGER
                `package` = context.packageName
            }
        }

        private fun readScheduledRequestCodes(context: Context): MutableSet<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(PREF_SCHEDULED_REQUEST_CODES, emptySet())?.toMutableSet()
                ?: mutableSetOf()
        }

        private fun writeScheduledRequestCodes(context: Context, requestCodes: Set<String>) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_SCHEDULED_REQUEST_CODES, requestCodes)
                .commit()
        }

        private fun generateRequestCode(existing: Set<String>): Int {
            var requestCode: Int
            do {
                requestCode = Random.nextInt(1, Int.MAX_VALUE)
            } while (existing.contains(requestCode.toString()))
            return requestCode
        }

        private fun scheduleAlarm(
            context: Context,
            requestCode: Int,
            delaySeconds: Long,
            title: String,
            message: String,
            imagePath: String?
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAtMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delaySeconds.coerceAtLeast(0L))

            val intent = triggerIntent(context).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_REQUEST_CODE, requestCode)
                if (!imagePath.isNullOrEmpty()) {
                    putExtra(EXTRA_IMAGE_PATH, imagePath)
                }
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                else -> {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            }
        }

        private fun removeScheduledRequestCode(context: Context, requestCode: Int) {
            val requestCodes = readScheduledRequestCodes(context)
            if (requestCodes.remove(requestCode.toString())) {
                writeScheduledRequestCodes(context, requestCodes)
            }
        }

        private fun cancelScheduledAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val requestCodes = readScheduledRequestCodes(context)

            requestCodes.forEach { value ->
                val requestCode = value.toIntOrNull() ?: return@forEach
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    triggerIntent(context),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                ) ?: return@forEach

                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }

            if (requestCodes.isNotEmpty()) {
                writeScheduledRequestCodes(context, emptySet())
            }
        }

        fun schedule(
            context: Context,
            delaySeconds: Long,
            title: String,
            message: String,
            imagePath: String?
        ) {
            val appContext = context.applicationContext
            val requestCodes = readScheduledRequestCodes(appContext)
            val requestCode = generateRequestCode(requestCodes)
            requestCodes.add(requestCode.toString())
            writeScheduledRequestCodes(appContext, requestCodes)
            scheduleAlarm(appContext, requestCode, delaySeconds, title, message, imagePath)
        }

        fun cancelAll(context: Context) {
            val appContext = context.applicationContext
            cancelScheduledAlarms(appContext)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext

        if (intent?.action != ACTION_TRIGGER) {
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            triggerIntent(appContext),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.cancel()

        NotificationWorker.showNotification(appContext, title, message, imagePath)

        if (requestCode > 0) {
            removeScheduledRequestCode(appContext, requestCode)
        }
    }
}
