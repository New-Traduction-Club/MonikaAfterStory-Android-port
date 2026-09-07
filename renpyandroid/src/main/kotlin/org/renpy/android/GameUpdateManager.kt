package org.renpy.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

object GameUpdateManager {
    private const val TAG = "GameUpdateManager"
    private const val PACKAGES_JSON_URL = "https://raw.githubusercontent.com/New-Traduction-Club/MonikaAfterStory-Android-port/refs/heads/main/.utilityfiles/packages_list.json"
    private const val UPDATE_TEMP_FILE = "update_temp.zip"

    fun fetchPackages(): List<PackageInfo> {
        return PackageHelper.fetchPackages(PACKAGES_JSON_URL)
    }

    fun startDownload(context: Context, packageInfo: PackageInfo) {
        val destFile = File(context.filesDir, UPDATE_TEMP_FILE)
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, packageInfo.download_url)
            putExtra(DownloadService.EXTRA_DEST_PATH, destFile.absolutePath)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun verifyUpdateFile(context: Context, expectedHash: String): Boolean {
        val file = File(context.filesDir, UPDATE_TEMP_FILE)
        if (!file.exists()) return false

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hashBytes = digest.digest()
            val sb = StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString().equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Checksum verification failed", e)
            false
        }
    }

    fun installUpdate(context: Context) {
        val tempFile = File(context.filesDir, UPDATE_TEMP_FILE)
        if (!tempFile.exists()) throw Exception(context.getString(R.string.update_error_file_not_found))

        val maslOutputFolder = File(context.filesDir, "monikaafterstory-masl-edition")

        val gameDir = File(maslOutputFolder, "game")
        if (!gameDir.exists()) gameDir.mkdirs()

        ZipFile(tempFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                if (name.startsWith("game/") || name.startsWith("characters/")) {
                    val targetFile = File(maslOutputFolder, name)

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }
        tempFile.delete()
    }
}
