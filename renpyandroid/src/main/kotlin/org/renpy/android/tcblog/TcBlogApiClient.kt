package org.renpy.android.tcblog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class TcBlogApiClient(private val context: Context) {

    companion object {
        private const val BASE_URL = "https://traduction-club.live/api/masl/"
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 15000
    }

    private val cacheDir = File(context.cacheDir, "tc_blog_cache").apply { mkdirs() }

    suspend fun fetchCategories(lang: String): Result<List<TcBlogCategory>> = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}categories/?lang=$lang"
        executeGet(url).mapCatching { jsonStr ->
            val jsonArray = JSONArray(jsonStr)
            val categories = mutableListOf<TcBlogCategory>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                categories.add(
                    TcBlogCategory(
                        id = obj.optString("id", ""),
                        slug = obj.optString("slug", obj.optString("id", "")),
                        name = obj.optString("name", ""),
                        order = obj.optInt("order", 0)
                    )
                )
            }
            categories.sortedBy { it.order }
        }
    }

    suspend fun fetchArticles(
        category: String? = null,
        page: Int = 1,
        limit: Int = 20,
        lang: String
    ): Result<TcBlogArticlePage> = withContext(Dispatchers.IO) {
        val urlBuilder = StringBuilder("${BASE_URL}articles/?page=$page&limit=$limit&lang=$lang")
        if (!category.isNullOrBlank() && category.lowercase() != "all") {
            urlBuilder.append("&category=").append(category)
        }
        val url = urlBuilder.toString()

        executeGet(url).mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            val count = obj.optInt("count", 0)
            val next = if (obj.isNull("next")) null else obj.optString("next")
            val previous = if (obj.isNull("previous")) null else obj.optString("previous")
            val resultsArray = obj.optJSONArray("results") ?: JSONArray()

            val articles = mutableListOf<TcBlogArticleSummary>()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val catString = parseCategoryField(item.opt("category"))

                articles.add(
                    TcBlogArticleSummary(
                        id = item.optInt("id", 0),
                        slug = item.optString("slug", item.optString("id", "")),
                        title = item.optString("title", ""),
                        summary = item.optString("summary", ""),
                        category = catString,
                        pinned = item.optBoolean("pinned", false),
                        publishedAt = item.optString("published_at", ""),
                        updatedAt = item.optString("updated_at", "")
                    )
                )
            }
            TcBlogArticlePage(count, next, previous, articles)
        }
    }

    suspend fun fetchArticleDetail(
        slugOrId: String,
        lang: String
    ): Result<TcBlogArticleDetail> = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}articles/$slugOrId/?lang=$lang"
        executeGet(url).mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            val catString = parseCategoryField(obj.opt("category"))

            val langList = mutableListOf<String>()
            val langsArray = obj.optJSONArray("available_languages")
            if (langsArray != null) {
                for (i in 0 until langsArray.length()) {
                    langList.add(langsArray.getString(i))
                }
            }

            TcBlogArticleDetail(
                id = obj.optInt("id", 0),
                slug = obj.optString("slug", obj.optString("id", "")),
                title = obj.optString("title", ""),
                category = catString,
                pinned = obj.optBoolean("pinned", false),
                publishedAt = obj.optString("published_at", ""),
                updatedAt = obj.optString("updated_at", ""),
                language = obj.optString("language", "en"),
                availableLanguages = langList,
                contentMarkdown = obj.optString("content_markdown", "")
            )
        }
    }

    private fun parseCategoryField(catObj: Any?): String {
        return when (catObj) {
            is JSONObject -> catObj.optString("name", catObj.optString("slug", catObj.optString("id", "")))
            is String -> catObj
            else -> ""
        }
    }

    private fun executeGet(urlString: String): Result<String> {
        val cacheKey = urlString.hashCode().toString()
        val cachedFile = File(cacheDir, "$cacheKey.json")
        val etagFile = File(cacheDir, "$cacheKey.etag")

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MonikaAfterStory-AndroidPort/1.0")

                if (etagFile.exists() && cachedFile.exists()) {
                    val savedEtag = etagFile.readText().trim()
                    if (savedEtag.isNotBlank()) {
                        setRequestProperty("If-None-Match", savedEtag)
                    }
                }
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED && cachedFile.exists()) {
                return Result.success(cachedFile.readText(StandardCharsets.UTF_8))
            }

            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                val responseText = reader.use { it.readText() }

                try {
                    cachedFile.writeText(responseText, StandardCharsets.UTF_8)
                    val newEtag = connection.getHeaderField("ETag")
                    if (!newEtag.isNullOrBlank()) {
                        etagFile.writeText(newEtag.trim())
                    }
                } catch (e: Exception) {
                }

                return Result.success(responseText)
            } else {
                if (cachedFile.exists()) {
                    return Result.success(cachedFile.readText(StandardCharsets.UTF_8))
                }
                return Result.failure(Exception("HTTP $responseCode: ${connection.responseMessage}"))
            }
        } catch (e: Exception) {
            if (cachedFile.exists()) {
                return Result.success(cachedFile.readText(StandardCharsets.UTF_8))
            }
            return Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
