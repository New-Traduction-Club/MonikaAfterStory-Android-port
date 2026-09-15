package org.renpy.android.tcblog

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.renpy.android.BaseActivity
import org.renpy.android.GameWindowActivity
import org.renpy.android.R
import org.renpy.android.SoundEffects
import org.renpy.android.databinding.ActivityTcBlogBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TcBlogActivity : GameWindowActivity() {

    private lateinit var binding: ActivityTcBlogBinding
    private lateinit var apiClient: TcBlogApiClient
    private lateinit var markdownParser: TcBlogMarkdownParser
    private lateinit var articlesAdapter: TcBlogArticlesAdapter

    private var activeLanguage: String = "en"
    private var currentCategorySlug: String? = null
    private var isReaderOpen = false
    private var currentArticleSlug: String? = null
    private var articlesJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTcBlogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTitle(R.string.title_tc_blog)

        apiClient = TcBlogApiClient(this)
        markdownParser = TcBlogMarkdownParser(this)
        activeLanguage = resolveAppLanguage()

        setupRecyclerView()
        setupListeners()
        loadFeedData()
    }

    private fun resolveAppLanguage(): String {
        val prefs = getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString("language", "English")) {
            "Español" -> "es"
            "Português" -> "pt"
            else -> "en"
        }
    }

    private fun setupRecyclerView() {
        articlesAdapter = TcBlogArticlesAdapter { article ->
            openArticle(article.slug)
        }

        val initialSpan = getTargetSpanCount()
        binding.rvArticles.layoutManager = GridLayoutManager(this, initialSpan)
        binding.rvArticles.adapter = articlesAdapter

        binding.rvArticles.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val widthDp = (right - left) / resources.displayMetrics.density
            if (widthDp > 0) {
                val targetSpan = if (widthDp >= 600 || getWindowMode() == WindowMode.MAXIMIZED) 2 else 1
                val currentManager = binding.rvArticles.layoutManager as? GridLayoutManager
                if (currentManager != null && currentManager.spanCount != targetSpan) {
                    binding.rvArticles.post {
                        currentManager.spanCount = targetSpan
                    }
                }
            }
        }
    }

    private fun getTargetSpanCount(): Int {
        val realMetrics = resources.displayMetrics
        val widthDp = realMetrics.widthPixels / realMetrics.density
        return if (getWindowMode() == WindowMode.MAXIMIZED || widthDp >= 600) 2 else 1
    }

    private fun setupListeners() {
        binding.btnFeedRetry.setOnClickListener {
            SoundEffects.playClick(this)
            loadFeedData()
        }

        binding.btnReaderBack.setOnClickListener {
            SoundEffects.playClick(this)
            closeReader()
        }

        binding.btnReaderRetry.setOnClickListener {
            SoundEffects.playClick(this)
            currentArticleSlug?.let { slug -> loadArticleDetail(slug, activeLanguage) }
        }
    }

    private fun loadFeedData() {
        buildCategoryTabs(emptyList())

        lifecycleScope.launch {
            val categoriesResult = apiClient.fetchCategories(activeLanguage)
            val categories = categoriesResult.getOrDefault(emptyList())
            buildCategoryTabs(categories)
        }

        fetchArticles(currentCategorySlug)
    }

    private fun buildCategoryTabs(categories: List<TcBlogCategory>) {
        binding.llCategoryTabs.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val allTab = inflater.inflate(R.layout.item_tc_blog_category_tab, binding.llCategoryTabs, false) as TextView
        allTab.text = getString(R.string.tc_blog_tab_all)
        allTab.isSelected = (currentCategorySlug == null || currentCategorySlug == "all")
        allTab.setOnClickListener {
            SoundEffects.playClick(this)
            if (currentCategorySlug != null) {
                currentCategorySlug = null
                highlightSelectedTab(allTab)
            }
            fetchArticles(null)
        }
        binding.llCategoryTabs.addView(allTab)

        for (cat in categories) {
            val tabView =
                inflater.inflate(R.layout.item_tc_blog_category_tab, binding.llCategoryTabs, false) as TextView
            tabView.text = cat.name.ifBlank { cat.slug }
            tabView.isSelected = (currentCategorySlug == cat.slug)
            tabView.setOnClickListener {
                SoundEffects.playClick(this)
                if (currentCategorySlug != cat.slug) {
                    currentCategorySlug = cat.slug
                    highlightSelectedTab(tabView)
                    fetchArticles(cat.slug)
                }
            }
            binding.llCategoryTabs.addView(tabView)
        }
    }

    private fun highlightSelectedTab(selectedTab: TextView) {
        for (i in 0 until binding.llCategoryTabs.childCount) {
            val child = binding.llCategoryTabs.getChildAt(i) as? TextView
            child?.isSelected = (child === selectedTab)
        }
    }

    private fun fetchArticles(categorySlug: String?) {
        articlesJob?.cancel()
        articlesJob = lifecycleScope.launch {
            binding.loadingFeed.visibility = View.VISIBLE
            binding.errorFeed.visibility = View.GONE
            binding.emptyFeed.visibility = View.GONE

            val result = apiClient.fetchArticles(
                category = categorySlug,
                page = 1,
                limit = 30,
                lang = activeLanguage
            )

            binding.loadingFeed.visibility = View.GONE

            result.onSuccess { page ->
                if (page.results.isEmpty()) {
                    binding.emptyFeed.visibility = View.VISIBLE
                    binding.rvArticles.visibility = View.GONE
                    articlesAdapter.submitList(emptyList())
                } else {
                    binding.emptyFeed.visibility = View.GONE
                    binding.rvArticles.visibility = View.VISIBLE
                    articlesAdapter.submitList(page.results)
                }
            }.onFailure { error ->
                binding.errorFeed.visibility = View.VISIBLE
                binding.rvArticles.visibility = View.GONE
                binding.tvFeedErrorMessage.text = getString(R.string.tc_blog_error_loading)
            }
        }
    }

    private fun openArticle(slug: String) {
        currentArticleSlug = slug
        isReaderOpen = true

        binding.layoutFeedView.visibility = View.GONE
        binding.layoutReaderView.visibility = View.VISIBLE
        binding.nsvReader.scrollTo(0, 0)

        loadArticleDetail(slug, activeLanguage)
    }

    private fun loadArticleDetail(slug: String, lang: String) {
        lifecycleScope.launch {
            binding.loadingReader.visibility = View.VISIBLE
            binding.errorReader.visibility = View.GONE
            binding.nsvReader.visibility = View.GONE
            binding.llReaderLanguages.removeAllViews()

            val result = apiClient.fetchArticleDetail(slug, lang)
            binding.loadingReader.visibility = View.GONE

            result.onSuccess { detail ->
                binding.nsvReader.visibility = View.VISIBLE
                binding.tvReaderTitle.text = detail.title
                binding.tvReaderCategory.text = detail.category.ifBlank { "BLOG" }
                binding.tvReaderDate.text = formatDetailDate(detail.publishedAt)

                buildLanguageSelector(detail)
                markdownParser.renderMarkdown(detail.contentMarkdown, binding.llMarkdownBody)
            }.onFailure {
                binding.errorReader.visibility = View.VISIBLE
                binding.tvReaderErrorMessage.text = getString(R.string.tc_blog_error_loading)
            }
        }
    }

    private fun buildLanguageSelector(detail: TcBlogArticleDetail) {
        binding.llReaderLanguages.removeAllViews()
        val available = detail.availableLanguages
        if (available.size <= 1) return

        val inflater = LayoutInflater.from(this)
        for (code in available) {
            val chip =
                inflater.inflate(R.layout.item_tc_blog_category_tab, binding.llReaderLanguages, false) as TextView
            chip.text = code.uppercase(Locale.ROOT)
            chip.isSelected = code.equals(detail.language, ignoreCase = true)
            chip.setOnClickListener {
                SoundEffects.playClick(this)
                if (!code.equals(detail.language, ignoreCase = true)) {
                    loadArticleDetail(detail.slug, code)
                }
            }
            binding.llReaderLanguages.addView(chip)
        }
    }

    private fun closeReader() {
        isReaderOpen = false
        currentArticleSlug = null
        binding.layoutReaderView.visibility = View.GONE
        binding.layoutFeedView.visibility = View.VISIBLE
    }

    private fun formatDetailDate(rawDate: String): String {
        if (rawDate.isBlank()) return ""
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val cleaned = if (rawDate.length >= 19) rawDate.substring(0, 19) else rawDate
            val date = inputFormat.parse(cleaned)
            if (date != null) {
                val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                outputFormat.format(date)
            } else {
                rawDate.take(10)
            }
        } catch (e: Exception) {
            rawDate.take(10)
        }
    }

    override fun onBackPressed() {
        if (isReaderOpen) {
            closeReader()
        } else {
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val targetSpan = getTargetSpanCount()
        val currentManager = binding.rvArticles.layoutManager as? GridLayoutManager
        if (currentManager != null && currentManager.spanCount != targetSpan) {
            currentManager.spanCount = targetSpan
        }
    }
}
