package org.renpy.android

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("updates_prefs", Context.MODE_PRIVATE)

    suspend fun fetchUpdates(manifestUrl: String): List<UpdateItem> = withContext(Dispatchers.IO) {
        val updates = mutableListOf<UpdateItem>()
        try {
            val url = URL(manifestUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonObject = JSONObject(response.toString())
                val jsonArray = jsonObject.getJSONArray("updates")

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    updates.add(
                        UpdateItem(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            description = item.getString("description"),
                            version = item.getInt("version"),
                            versionName = item.getString("version_name"),
                            url = item.getString("url"),
                            type = item.getString("type"),
                            targetFile = item.getString("target_file")
                        )
                    )
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext updates
    }

    companion object {
        private val DEFAULT_VERSIONS = mapOf(
            "lang_en" to 100,
            "lang_es" to 100,
            "lang_pt" to 100,
            "py_scripts" to 100
        )
    }

    fun getLocalVersion(id: String): Int {
        val defaultVersion = DEFAULT_VERSIONS[id] ?: 0
        return prefs.getInt("version_$id", defaultVersion)
    }

    fun setLocalVersion(id: String, version: Int) {
        prefs.edit().putInt("version_$id", version).apply()
    }
    
    fun isUpdateAvailable(item: UpdateItem): Boolean {
        val localVersion = getLocalVersion(item.id)
        return item.version > localVersion
    }

    suspend fun getFileSize(fileUrl: String): String = withContext(Dispatchers.IO) {
        var sizeStr = "Unknown size"
        try {
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val length = connection.contentLengthLong
                if (length > 0) {
                    val sizeInMb = length.toDouble() / (1024 * 1024)
                    sizeStr = String.format("%.2f MB", sizeInMb)
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext sizeStr
    }
}
