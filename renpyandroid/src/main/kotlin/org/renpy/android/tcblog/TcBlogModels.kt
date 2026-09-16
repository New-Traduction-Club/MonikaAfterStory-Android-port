package org.renpy.android.tcblog

data class TcBlogCategory(
    val id: String,
    val slug: String,
    val name: String,
    val order: Int = 0
)

data class TcBlogArticleSummary(
    val id: Int,
    val slug: String,
    val title: String,
    val summary: String,
    val category: String,
    val pinned: Boolean,
    val publishedAt: String,
    val updatedAt: String
)

data class TcBlogArticlePage(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<TcBlogArticleSummary>
)

data class TcBlogArticleDetail(
    val id: Int,
    val slug: String,
    val title: String,
    val category: String,
    val pinned: Boolean,
    val publishedAt: String,
    val updatedAt: String,
    val language: String,
    val availableLanguages: List<String>,
    val contentMarkdown: String
)
