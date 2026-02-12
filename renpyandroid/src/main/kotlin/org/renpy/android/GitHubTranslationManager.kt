package org.renpy.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object GitHubTranslationManager {

    private const val REPO_OWNER = "New-Traduction-Club"
    private const val REPO_NAME = ""
    private const val BRANCH = "HEAD"
    
    // Commit info structure
    data class UpdateInfo(
        val sha: String,
        val message: String,
        val date: String
    )

    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/commits/$BRANCH?path=files")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val sha = json.getString("sha")
                val commit = json.getJSONObject("commit")
                val message = commit.getString("message")
                val date = commit.getJSONObject("author").getString("date")
                
                UpdateInfo(sha, message, date)
            } else {
                Log.e("GitHubTranslation", "Error fetching updates: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e("GitHubTranslation", "Exception checking updates", e)
            null
        }
    }

    suspend fun downloadAndInstallUpdate(
        context: Context, 
        sha: String, 
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/zipball/$sha")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val zipFile = File(context.cacheDir, "translation_update.zip")
                val contentLength = connection.contentLength
                
                // Download with progress
                connection.inputStream.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalBytesRead: Long = 0
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                val progress = ((totalBytesRead * 100) / contentLength).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }

                installUpdate(context, zipFile)
                
                zipFile.delete()
                true
            } else {
                Log.e("GitHubTranslation", "Error downloading update: ${connection.responseCode}")
                false
            }
        } catch (e: Exception) {
            Log.e("GitHubTranslation", "Exception downloading update", e)
            false
        }
    }

    private fun installUpdate(context: Context, zipFile: File) {
        val targetDir = File(context.filesDir, "game/tl/spanish")
        
        if (!targetDir.exists()) targetDir.mkdirs()

        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name
                    val filesIndex = entryName.indexOf("files/")
                    if (filesIndex != -1) {
                        val relativePath = entryName.substring(filesIndex + "files/".length)
                        if (relativePath.isNotEmpty()) {
                            val targetDir = File(context.filesDir, "game/tl/spanish")
                            val outFile = File(targetDir, relativePath)
                            
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
