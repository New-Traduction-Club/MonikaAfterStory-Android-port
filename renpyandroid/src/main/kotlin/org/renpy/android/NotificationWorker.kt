package org.renpy.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import kotlin.math.max

/**
 * A robust Worker for handling Game Notifications.
 *
 * Can be called from Ren'Py via JNIUS.
 *
 * **Python (Ren'Py) Usage Example:**
 * ```python
 * from jnius import autoclass
 *
 * # Get Context (usually via PythonActivity)
 * PythonActivity = autoclass('org.renpy.android.PythonSDLActivity')
 * context = PythonActivity.mActivity
 *
 * # Reference this Worker
 * NotificationWorker = autoclass('org.renpy.android.NotificationWorker')
 *
 * # Show Immediate Notification
 * NotificationWorker.showNotification(
 *     context,
 *     "Monika",
 *     "Yahallo!",
 *     "/storage/emulated/0/Android/data/our.package.name/files/game/images/monika_icon.png" // or relative path to internal files
 * )
 *
 * # Schedule Notification (e.g., in 5 hours)
 * # delaySeconds: Long, title: String, message: String, imagePath: String
 * NotificationWorker.scheduleNotification(
 *     context,
 *     5 * 3600, 
 *     "Monika",
 *     "Yahallo!",
 *     None 
 * )
 * ```
 */
class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val CHANNEL_ID = "mas_game_channel_high"
        private const val CHANNEL_NAME = "MAS Notifications"
        private const val CHANNEL_DESC = "Notifications from Monika"
        
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_IMAGE_PATH = "image_path"

        const val WORK_TAG = "renpy_notification"

        /**
         * Cancels all scheduled notifications tagged with WORK_TAG.
         * Callable from JNIUS.
         */
        @JvmStatic
        fun cancelAllNotifications(context: Context) {
            NotificationSchedulerReceiver.cancelAll(context.applicationContext)
        }

        /**
         * Schedules a notification to be shown after a delay.
         * Callable from JNIUS.
         *
         * @param context Android Context.
         * @param delaySeconds Delay in seconds before functionality runs.
         * @param title Notification Title.
         * @param message Notification Body.
         * @param imagePath Absolute path to an image file to use as the Large Icon (optional).
         */
        @JvmStatic
        fun scheduleNotification(
            context: Context,
            delaySeconds: Long,
            title: String,
            message: String,
            imagePath: String?
        ) {
            NotificationSchedulerReceiver.schedule(
                context = context.applicationContext,
                delaySeconds = delaySeconds,
                title = title,
                message = message,
                imagePath = imagePath
            )
        }

        /**
         * Shows a notification immediately.
         * Callable from JNIUS.
         *
         * @param context Android Context.
         * @param title Notification Title.
         * @param message Notification Body.
         * @param imagePath Absolute path to an image file to use as the Large Icon (optional).
         */
        @JvmStatic
        fun showNotification(
            context: Context,
            title: String,
            message: String,
            imagePath: String?
        ) {
            triggerNotification(context, title, message, imagePath)
        }

        private fun triggerNotification(context: Context, title: String, message: String, imagePath: String?) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }

            // Intent to launch the game when notification is clicked
            // We use LauncherActivity as the entry point
            val intent = Intent(context, LauncherActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            // Handle Custom Image (Large Icon)
            if (!imagePath.isNullOrEmpty()) {
                val file = File(imagePath)
                if (file.exists()) {
                    try {
                        val bitmap = decodeSampledBitmap(file, 1024, 1024)
                        if (bitmap != null) {
                            builder.setLargeIcon(bitmap)
                            builder.setStyle(NotificationCompat.BigPictureStyle()
                                .bigPicture(bitmap)
                                .bigLargeIcon(null as Bitmap?)) // Hide large icon when expanded if desired
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Use a unique ID based on time to allow multiple notifications
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        }

        private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return BitmapFactory.decodeFile(file.absolutePath)
            }

            var sampleSize = 1
            while ((bounds.outWidth / (sampleSize * 2)) >= reqWidth && (bounds.outHeight / (sampleSize * 2)) >= reqHeight) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = max(1, sampleSize)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        }
    }

    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Monika After Story"
        val message = inputData.getString(KEY_MESSAGE) ?: "Monika is waiting..."
        val imagePath = inputData.getString(KEY_IMAGE_PATH)

        triggerNotification(applicationContext, title, message, imagePath)

        return Result.success()
    }
}
