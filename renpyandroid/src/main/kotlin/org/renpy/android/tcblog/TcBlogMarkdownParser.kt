package org.renpy.android.tcblog

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import org.renpy.android.R
import org.renpy.android.SoundEffects
import java.util.regex.Pattern

class TcBlogMarkdownParser(
    private val context: Context,
    private val onActionClick: ((String) -> Unit)? = null
) {

    private val primaryColor by lazy { ContextCompat.getColor(context, R.color.colorPrimary) }
    private val textPrimaryColor by lazy { ContextCompat.getColor(context, R.color.colorTextPrimary) }
    private val textSecondaryColor by lazy { ContextCompat.getColor(context, R.color.colorTextSecondary) }
    private val dividerColor by lazy { ContextCompat.getColor(context, R.color.colorDivider) }

    private val buttonDirectivePattern = Pattern.compile("^:::button\\[(.*?)\\]\\((.*?)\\)")
    private val inlineImagePattern = Regex("""^!\[(.*?)\]\((https?://\S+?)(?:\s+"(.*?)")?\)""")
    private val refImagePattern = Regex("""^!\[(.*?)\]\[(.*?)\]""")
    private val refDefinitionPattern = Regex("""^\[(.*?)\]:\s*(https?://\S+)(?:\s+"(.*?)")?""")

    fun renderMarkdown(markdown: String, container: ViewGroup) {
        container.removeAllViews()

        val normalized = markdown.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split("\n")

        val references = mutableMapOf<String, String>()
        for (line in lines) {
            val trimmed = line.trim()
            val match = refDefinitionPattern.find(trimmed)
            if (match != null) {
                val refId = match.groupValues[1].trim().lowercase()
                val refUrl = match.groupValues[2].trim()
                references[refId] = refUrl
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            if (refDefinitionPattern.matches(trimmed)) {
                i++
                continue
            }

            val btnMatcher = buttonDirectivePattern.matcher(trimmed)
            if (btnMatcher.find()) {
                val label = btnMatcher.group(1).orEmpty()
                val target = btnMatcher.group(2).orEmpty()
                renderCustomButton(label, target, container)
                i++
                continue
            }

            val inlineImgMatch = inlineImagePattern.find(trimmed)
            if (inlineImgMatch != null) {
                val alt = inlineImgMatch.groupValues[1]
                val url = inlineImgMatch.groupValues[2]
                renderImageBlock(url, alt, container)
                i++
                continue
            }

            val refImgMatch = refImagePattern.find(trimmed)
            if (refImgMatch != null) {
                val alt = refImgMatch.groupValues[1]
                val refId = refImgMatch.groupValues[2].trim().lowercase()
                val resolvedUrl = references[refId]
                if (!resolvedUrl.isNullOrBlank()) {
                    renderImageBlock(resolvedUrl, alt, container)
                }
                i++
                continue
            }

            if (trimmed.startsWith("```")) {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++
                renderCodeBlock(codeLines.joinToString("\n"), container)
                continue
            }

            if (trimmed.matches(Regex("^([\\-*_])\\s*\\1\\s*\\1[\\s\\-*_]*$"))) {
                renderDivider(container)
                i++
                continue
            }

            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2]
                renderHeading(text, level, container)
                i++
                continue
            }

            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().replaceFirst(Regex("^>+\\s?"), ""))
                    i++
                }
                renderBlockquote(quoteLines.joinToString("\n"), container)
                continue
            }

            if (trimmed.startsWith(":::")) {
                val containerLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(":::")) {
                    containerLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++
                renderBlockquote(containerLines.joinToString("\n"), container)
                continue
            }

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                renderTable(tableLines, container)
                continue
            }

            val bulletMatch = Regex("^([*+\\-]\\s+|\\d+\\.\\s+)(.+)$").find(trimmed)
            if (bulletMatch != null) {
                val isNumbered = Character.isDigit(trimmed[0])
                val prefix = if (isNumbered) bulletMatch.groupValues[1].trim() else "•"
                val content = bulletMatch.groupValues[2]
                renderListItem(prefix, content, container)
                i++
                continue
            }

            val paragraphLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().isNotEmpty() &&
                !lines[i].trim().startsWith("#") &&
                !lines[i].trim().startsWith("```") &&
                !lines[i].trim().startsWith(">") &&
                !lines[i].trim().startsWith(":::") &&
                !lines[i].trim().startsWith("|") &&
                !inlineImagePattern.containsMatchIn(lines[i].trim()) &&
                !refImagePattern.containsMatchIn(lines[i].trim()) &&
                !refDefinitionPattern.matches(lines[i].trim()) &&
                !lines[i].trim().matches(Regex("^([\\-*_])\\s*\\1\\s*\\1[\\s\\-*_]*$")) &&
                !Regex("^([*+\\-]\\s+|\\d+\\.\\s+)").containsMatchIn(lines[i].trim())
            ) {
                paragraphLines.add(lines[i].trim())
                i++
            }
            if (paragraphLines.isNotEmpty()) {
                renderParagraph(paragraphLines.joinToString(" "), container)
            }
        }
    }

    private fun renderImageBlock(imageUrl: String, alt: String, container: ViewGroup) {
        val inflater = LayoutInflater.from(context)
        val imageViewLayout = inflater.inflate(R.layout.item_tc_blog_image, container, false)

        val iv = imageViewLayout.findViewById<ImageView>(R.id.ivArticleImage)
        val pb = imageViewLayout.findViewById<ProgressBar>(R.id.pbImageLoading)
        val tvCaption = imageViewLayout.findViewById<TextView>(R.id.tvImageCaption)

        if (alt.isNotBlank()) {
            tvCaption.text = alt
            tvCaption.visibility = View.VISIBLE
        } else {
            tvCaption.visibility = View.GONE
        }

        TcBlogImageLoader.loadImage(context, imageUrl, iv, pb)

        iv.setOnClickListener {
            SoundEffects.playClick(context)
            showImageViewerDialog(imageUrl)
        }

        container.addView(imageViewLayout)
    }

    private fun showImageViewerDialog(imageUrl: String) {
        try {
            val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(R.layout.dialog_tc_blog_image_viewer)

            val ivFull = dialog.findViewById<ImageView>(R.id.ivFullImage)
            val pbFull = dialog.findViewById<ProgressBar>(R.id.pbFullLoading)
            val btnClose = dialog.findViewById<ImageView>(R.id.btnViewerClose)
            val root = dialog.findViewById<View>(R.id.dialogImageRoot)

            TcBlogImageLoader.loadImage(context, imageUrl, ivFull, pbFull, reqWidth = 1920, reqHeight = 1080)

            btnClose?.setOnClickListener {
                SoundEffects.playClick(context)
                dialog.dismiss()
            }
            root?.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun renderCustomButton(label: String, target: String, container: ViewGroup) {
        val inflater = LayoutInflater.from(context)
        val buttonView = inflater.inflate(R.layout.item_tc_blog_button, container, false)
        val btn = buttonView.findViewById<TextView>(R.id.btnCustomAction)
        btn.text = label

        val fallbackMinWidth = (context.resources.displayMetrics.widthPixels * 0.20f).toInt()
        btn.minWidth = fallbackMinWidth

        container.post {
            if (container.width > 0) {
                btn.minWidth = (container.width * 0.25f).toInt()
            }
        }

        btn.setOnClickListener {
            SoundEffects.playClick(context)
            if (onActionClick != null) {
                onActionClick.invoke(target)
            } else {
                handleDefaultAction(target)
            }
        }
        container.addView(buttonView)
    }

    private fun handleDefaultAction(target: String) {
        val trimmed = target.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun renderHeading(text: String, level: Int, container: ViewGroup) {
        val tv = TextView(context).apply {
            val html = inlineMarkdownToHtml(text)
            this.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            setTextColor(textPrimaryColor)
            setTypeface(typeface, Typeface.BOLD)

            val (sizeSp, topMarginDp, bottomMarginDp) = when (level) {
                1 -> Triple(22f, 18, 8)
                2 -> Triple(18f, 14, 6)
                3 -> Triple(16f, 12, 4)
                else -> Triple(14f, 10, 4)
            }

            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(topMarginDp)
                bottomMargin = dpToPx(bottomMarginDp)
            }
            layoutParams = params
            movementMethod = LinkMovementMethod.getInstance()
        }
        container.addView(tv)
    }

    private fun renderParagraph(text: String, container: ViewGroup) {
        val tv = TextView(context).apply {
            val html = inlineMarkdownToHtml(text)
            this.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            setTextColor(textPrimaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.25f)
            movementMethod = LinkMovementMethod.getInstance()

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
            layoutParams = params
        }
        container.addView(tv)
    }

    private fun renderListItem(bullet: String, content: String, container: ViewGroup) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(4)
                marginStart = dpToPx(8)
            }
            layoutParams = params
        }

        val bulletView = TextView(context).apply {
            text = bullet
            setTextColor(primaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(8)
            }
            layoutParams = params
        }

        val contentView = TextView(context).apply {
            val html = inlineMarkdownToHtml(content)
            this.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            setTextColor(textPrimaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.2f)
            movementMethod = LinkMovementMethod.getInstance()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        row.addView(bulletView)
        row.addView(contentView)
        container.addView(row)
    }

    private fun renderBlockquote(text: String, container: ViewGroup) {
        val quoteContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6)
                bottomMargin = dpToPx(10)
                marginStart = dpToPx(4)
            }
            layoutParams = params
        }

        val strip = View(context).apply {
            setBackgroundColor(primaryColor)
            val params = LinearLayout.LayoutParams(dpToPx(4), ViewGroup.LayoutParams.MATCH_PARENT)
            layoutParams = params
        }

        val tv = TextView(context).apply {
            val html = inlineMarkdownToHtml(text)
            this.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            setTextColor(textSecondaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.ITALIC)
            setLineSpacing(0f, 1.2f)
            setPadding(dpToPx(10), dpToPx(4), dpToPx(8), dpToPx(4))
            movementMethod = LinkMovementMethod.getInstance()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        quoteContainer.addView(strip)
        quoteContainer.addView(tv)
        container.addView(quoteContainer)
    }

    private fun renderCodeBlock(code: String, container: ViewGroup) {
        val tv = TextView(context).apply {
            text = code
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#E0E0E0"))
            setBackgroundColor(Color.parseColor("#231C28"))
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6)
                bottomMargin = dpToPx(10)
            }
            layoutParams = params
        }
        container.addView(tv)
    }

    private fun renderDivider(container: ViewGroup) {
        val divider = View(context).apply {
            setBackgroundColor(dividerColor)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply {
                topMargin = dpToPx(12)
                bottomMargin = dpToPx(12)
            }
            layoutParams = params
        }
        container.addView(divider)
    }

    private fun renderTable(tableLines: List<String>, container: ViewGroup) {
        if (tableLines.isEmpty()) return

        val tableLayout = TableLayout(context).apply {
            isStretchAllColumns = true
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6)
                bottomMargin = dpToPx(10)
            }
            layoutParams = params
        }

        for (line in tableLines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^\\|[\\s\\-:]+\\|+$"))) {
                continue
            }

            val cells = trimmed.trim('|').split("|").map { it.trim() }
            val tableRow = TableRow(context).apply {
                val params = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams = params
            }

            for (cell in cells) {
                val cellTv = TextView(context).apply {
                    val html = inlineMarkdownToHtml(cell)
                    this.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    setTextColor(textPrimaryColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                }
                tableRow.addView(cellTv)
            }

            tableLayout.addView(tableRow)
        }

        container.addView(tableLayout)
    }

    private fun inlineMarkdownToHtml(input: String): String {
        var res = input

        res = res.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        res = res.replace(Regex("__(.+?)__"), "<b>$1</b>")

        res = res.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<i>$1</i>")
        res = res.replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"), "<i>$1</i>")

        res = res.replace(Regex("~~(.+?)~~"), "<s>$1</s>")

        res = res.replace(Regex("`(.+?)`"), "<tt><font color=\"#D25C7E\">$1</font></tt>")

        res =
            res.replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\"><font color=\"#D25C7E\"><b>$1</b></font></a>")

        return res
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
