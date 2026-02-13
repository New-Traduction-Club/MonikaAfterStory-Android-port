package org.renpy.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DownloadService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "org.renpy.android.action.START_DOWNLOAD"
        const val ACTION_DOWNLOAD_PROGRESS = "org.renpy.android.action.DOWNLOAD_PROGRESS"
        const val ACTION_DOWNLOAD_COMPLETE = "org.renpy.android.action.DOWNLOAD_COMPLETE"
        
        const val EXTRA_URL = "extra_url"
        const val EXTRA_DEST_PATH = "extra_dest_path"
        
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_ETA = "extra_eta"
        const val EXTRA_SUCCESS = "extra_success"
        const val EXTRA_ERROR = "extra_error"

        private const val NOTIFICATION_CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var isDownloading = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val url = intent.getStringExtra(EXTRA_URL)
            val destPath = intent.getStringExtra(EXTRA_DEST_PATH)
            
            if (url != null && destPath != null && !isDownloading) {
                startForegroundService()
                startDownload(url, destPath)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_download_title))
            .setContentText(getString(R.string.setup_downloading_mas))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(progress: Int, speed: String, eta: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_download_title))
            .setContentText(getString(R.string.notification_download_progress, speed, eta))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startDownload(urlString: String, destPath: String) {
        isDownloading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connect()

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                val fileLength = connection.contentLength.toLong()
                val destFile = File(destPath)
                val input = connection.inputStream
                val output = FileOutputStream(destFile)

                val data = ByteArray(4096)
                var count: Int
                var total: Long = 0
                var lastUpdate = 0L
                val startTime = System.currentTimeMillis()

                // Initial notification
                updateNotification(0, "...", "...")

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 1000 && fileLength > 0) {
                        lastUpdate = now
                        val progress = (total * 100 / fileLength).toInt()
                        
                        val elapsedMs = now - startTime
                        val speedBytesPerSec = if (elapsedMs > 0) total * 1000 / elapsedMs else 0
                        val speed = formatSpeed(speedBytesPerSec)
                        val remainingBytes = fileLength - total
                        val etaSec = if (speedBytesPerSec > 0) remainingBytes / speedBytesPerSec else 0
                        val eta = formatEta(etaSec)

                        updateNotification(progress, speed, eta)
                        sendProgressBroadcast(progress, speed, eta)
                    }
                }

                output.close()
                input.close()

                sendCompleteBroadcast(true)
                showCompleteNotification(true) 

            } catch (e: Exception) {
                e.printStackTrace()
                sendCompleteBroadcast(false, e.message)
                showCompleteNotification(false)
            } finally {
                stopForeground(true)
                stopSelf()
                isDownloading = false
            }
        }
    }

    private fun showCompleteNotification(success: Boolean) {
        val title = if (success) getString(R.string.notification_download_complete) else getString(R.string.notification_download_failed)
        val icon = if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error
        
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(icon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendProgressBroadcast(progress: Int, speed: String, eta: String) {
        val intent = Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_SPEED, speed)
            putExtra(EXTRA_ETA, eta)
        }
        sendBroadcast(intent)
    }

    private fun sendCompleteBroadcast(success: Boolean, errorMessage: String? = null) {
        val intent = Intent(ACTION_DOWNLOAD_COMPLETE).apply {
            putExtra(EXTRA_SUCCESS, success)
            if (errorMessage != null) putExtra(EXTRA_ERROR, errorMessage)
        }
        sendBroadcast(intent)
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return if (bytesPerSec > 1024 * 1024) {
            String.format("%.1f MB/s", bytesPerSec / (1024f * 1024f))
        } else {
            String.format("%.1f KB/s", bytesPerSec / 1024f)
        }
    }

    private fun formatEta(seconds: Long): String {
        return if (seconds > 60) {
            String.format("%d min %d s", seconds / 60, seconds % 60)
        } else {
            String.format("%d s", seconds)
        }
    }
}
