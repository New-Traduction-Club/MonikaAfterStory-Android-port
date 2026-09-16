package org.renpy.android.tcblog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.renpy.android.R
import org.renpy.android.SoundEffects
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TcBlogArticlesAdapter(
    private val onArticleClick: (TcBlogArticleSummary) -> Unit
) : RecyclerView.Adapter<TcBlogArticlesAdapter.ArticleViewHolder>() {

    private val articles = mutableListOf<TcBlogArticleSummary>()

    fun submitList(newList: List<TcBlogArticleSummary>) {
        articles.clear()
        articles.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tc_blog_article, parent, false)
        return ArticleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        val item = articles[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = articles.size

    inner class ArticleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvPublishDate: TextView = itemView.findViewById(R.id.tvPublishDate)
        private val badgePinned: TextView = itemView.findViewById(R.id.badgePinned)
        private val tvArticleTitle: TextView = itemView.findViewById(R.id.tvArticleTitle)
        private val tvArticleSummary: TextView = itemView.findViewById(R.id.tvArticleSummary)

        fun bind(item: TcBlogArticleSummary) {
            tvCategoryName.text = item.category.ifBlank { "BLOG" }
            tvPublishDate.text = formatDate(item.publishedAt)
            badgePinned.visibility = if (item.pinned) View.VISIBLE else View.GONE
            tvArticleTitle.text = item.title
            tvArticleSummary.text = item.summary

            itemView.setOnClickListener {
                SoundEffects.playClick(itemView.context)
                onArticleClick(item)
            }
        }

        private fun formatDate(rawDate: String): String {
            if (rawDate.isBlank()) return ""
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val cleaned = if (rawDate.length >= 19) rawDate.substring(0, 19) else rawDate
                val date = inputFormat.parse(cleaned)
                if (date != null) {
                    val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    outputFormat.format(date)
                } else {
                    rawDate.take(10)
                }
            } catch (e: Exception) {
                rawDate.take(10)
            }
        }
    }
}
