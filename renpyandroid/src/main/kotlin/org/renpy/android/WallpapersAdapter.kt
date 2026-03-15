package org.renpy.android

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WallpapersAdapter(
    private var items: List<String>,
    private val activeId: String,
    private val onItemClick: (String) -> Unit,
    private val onItemLongClick: (String) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val TYPE_WALLPAPER = 0
        private const val TYPE_ADD = 1
    }

    private var currentActive = activeId

    override fun getItemViewType(position: Int): Int {
        return if (position < items.size) TYPE_WALLPAPER else TYPE_ADD
    }

    override fun getItemCount(): Int = items.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wallpaper, parent, false)
        return WallpaperViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val vh = holder as WallpaperViewHolder
        val context = holder.itemView.context

        if (position < items.size) {
            val id = items[position]
            val isActive = id == currentActive

            vh.checkOverlay.visibility = if (isActive) View.VISIBLE else View.GONE

            if (id == "default") {
                // Render default gradient as thumbnail
                val gradient = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        Color.parseColor("#2A406B"),
                        Color.parseColor("#1E2F52"),
                        Color.parseColor("#111C34")
                    )
                )
                vh.thumbnail.setImageDrawable(gradient)
                vh.thumbnail.scaleType = ImageView.ScaleType.FIT_XY
                vh.label.text = context.getString(R.string.wallpaper_default)
            } else {
                val bitmap = WallpaperManager.loadThumbnail(context, id, 200)
                if (bitmap != null) {
                    vh.thumbnail.setImageBitmap(bitmap)
                    vh.thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    vh.thumbnail.setImageResource(R.drawable.ic_file)
                }
                vh.label.text = id
            }

            vh.itemView.setOnClickListener {
                SoundEffects.playClick(context)
                onItemClick(id)
            }
            vh.itemView.setOnLongClickListener {
                if (id != "default") {
                    SoundEffects.playClick(context)
                    onItemLongClick(id)
                }
                true
            }
        } else {
            // "+" add tile
            vh.checkOverlay.visibility = View.GONE
            vh.thumbnail.setImageResource(android.R.drawable.ic_input_add)
            vh.thumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
            vh.thumbnail.setBackgroundColor(Color.parseColor("#F5EEF0"))
            vh.thumbnail.setColorFilter(Color.parseColor("#B45D85"))
            vh.label.text = context.getString(R.string.wallpaper_add)
            vh.itemView.setOnClickListener {
                SoundEffects.playClick(context)
                onAddClick()
            }
            vh.itemView.setOnLongClickListener { false }
        }
    }

    fun updateActive(newId: String) {
        currentActive = newId
        notifyDataSetChanged()
    }

    fun updateItems(newItems: List<String>, activeId: String) {
        items = newItems
        currentActive = activeId
        notifyDataSetChanged()
    }

    class WallpaperViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.wallpaperThumbnail)
        val checkOverlay: ImageView = view.findViewById(R.id.checkOverlay)
        val label: TextView = view.findViewById(R.id.wallpaperLabel)
    }
}
