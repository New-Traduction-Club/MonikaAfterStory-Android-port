package org.renpy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.renpy.android.tcblog.TcBlogArticleDetail
import org.renpy.android.tcblog.TcBlogArticlePage
import org.renpy.android.tcblog.TcBlogArticleSummary
import org.renpy.android.tcblog.TcBlogCategory
import java.util.regex.Pattern

class TcBlogTest {

    @Test
    fun testButtonDirectiveRegex() {
        val pattern = Pattern.compile("^:::button\\[(.*?)\\]\\((.*?)\\)")
        val input = ":::button[Download Update](https://traduction-club.live/download)"
        val matcher = pattern.matcher(input)

        assertTrue(matcher.find())
        assertEquals("Download Update", matcher.group(1))
        assertEquals("https://traduction-club.live/download", matcher.group(2))
    }

    @Test
    fun testButtonDirectiveWithActionUrl() {
        val pattern = Pattern.compile("^:::button\\[(.*?)\\]\\((.*?)\\)")
        val input = ":::button[Open Settings](app://settings)"
        val matcher = pattern.matcher(input)

        assertTrue(matcher.find())
        assertEquals("Open Settings", matcher.group(1))
        assertEquals("app://settings", matcher.group(2))
    }

    @Test
    fun testButtonDirectiveWithWhitespaces() {
        val pattern = Pattern.compile("^:::button\\[(.*?)\\]\\((.*?)\\)")
        val input = ":::button[Read Guide](https://traduction-club.live/guides/mas-guide/)"
        val matcher = pattern.matcher(input)

        assertTrue(matcher.find())
        assertEquals("Read Guide", matcher.group(1))
        assertEquals("https://traduction-club.live/guides/mas-guide/", matcher.group(2))
    }

    @Test
    fun testCategoryModel() {
        val category = TcBlogCategory(
            id = "testing",
            slug = "testing",
            name = "pruebas",
            order = 1
        )

        assertEquals("testing", category.id)
        assertEquals("testing", category.slug)
        assertEquals("pruebas", category.name)
        assertEquals(1, category.order)
    }

    @Test
    fun testArticleSummaryModel() {
        val article = TcBlogArticleSummary(
            id = 1,
            slug = "article-1",
            title = "Testing Feature",
            summary = "Summary text",
            category = "testing",
            pinned = true,
            publishedAt = "2026-09-15T02:56:47+00:00",
            updatedAt = "2026-09-15T02:58:31.498478+00:00"
        )

        assertEquals(1, article.id)
        assertEquals("article-1", article.slug)
        assertEquals("Testing Feature", article.title)
        assertEquals("Summary text", article.summary)
        assertEquals("testing", article.category)
        assertTrue(article.pinned)
    }

    @Test
    fun testArticlePageModel() {
        val article = TcBlogArticleSummary(
            id = 1,
            slug = "article-1",
            title = "Testing Feature",
            summary = "Summary text",
            category = "testing",
            pinned = false,
            publishedAt = "2026-09-15T02:56:47+00:00",
            updatedAt = "2026-09-15T02:58:31.498478+00:00"
        )

        val page = TcBlogArticlePage(
            count = 1,
            next = null,
            previous = null,
            results = listOf(article)
        )

        assertEquals(1, page.count)
        assertEquals(1, page.results.size)
        assertEquals("article-1", page.results[0].slug)
    }

    @Test
    fun testArticleDetailModel() {
        val detail = TcBlogArticleDetail(
            id = 1,
            slug = "article-1",
            title = "Testing Feature",
            category = "testing",
            pinned = false,
            publishedAt = "2026-09-15T02:56:47+00:00",
            updatedAt = "2026-09-15T02:58:31.498478+00:00",
            language = "en",
            availableLanguages = listOf("en", "es", "pt"),
            contentMarkdown = "# Heading\n:::button[Action](https://traduction-club.live)"
        )

        assertEquals(1, detail.id)
        assertEquals("en", detail.language)
        assertEquals(3, detail.availableLanguages.size)
        assertTrue(detail.contentMarkdown.contains(":::button[Action]"))
    }

    @Test
    fun testInlineImageRegex() {
        val pattern = Regex("""^!\[(.*?)\]\((https?://\S+?)(?:\s+"(.*?)")?\)""")
        val input = "![Minion](https://octodex.github.com/images/minion.png)"
        val match = pattern.find(input)

        assertTrue(match != null)
        assertEquals("Minion", match!!.groupValues[1])
        assertEquals("https://octodex.github.com/images/minion.png", match.groupValues[2])
    }

    @Test
    fun testImageWithTitleRegex() {
        val pattern = Regex("""^!\[(.*?)\]\((https?://\S+?)(?:\s+"(.*?)")?\)""")
        val input = "![Stormtroopocat](https://octodex.github.com/images/stormtroopocat.jpg \"The Stormtroopocat\")"
        val match = pattern.find(input)

        assertTrue(match != null)
        assertEquals("Stormtroopocat", match!!.groupValues[1])
        assertEquals("https://octodex.github.com/images/stormtroopocat.jpg", match.groupValues[2])
        assertEquals("The Stormtroopocat", match.groupValues[3])
    }

    @Test
    fun testReferenceImageRegexAndDefinition() {
        val refImagePattern = Regex("""^!\[(.*?)\]\[(.*?)\]""")
        val refDefPattern = Regex("""^\[(.*?)\]:\s*(https?://\S+)(?:\s+"(.*?)")?""")

        val imgInput = "![Alt text][id]"
        val defInput = "[id]: https://octodex.github.com/images/dojocat.jpg \"The Dojocat\""

        val imgMatch = refImagePattern.find(imgInput)
        val defMatch = refDefPattern.find(defInput)

        assertTrue(imgMatch != null)
        assertEquals("Alt text", imgMatch!!.groupValues[1])
        assertEquals("id", imgMatch.groupValues[2])

        assertTrue(defMatch != null)
        assertEquals("id", defMatch!!.groupValues[1])
        assertEquals("https://octodex.github.com/images/dojocat.jpg", defMatch.groupValues[2])
    }
}
