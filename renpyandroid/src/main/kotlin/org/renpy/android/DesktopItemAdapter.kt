package org.renpy.android

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.renpy.android.databinding.ItemDesktopShortcutBinding

data class DesktopShortcut(val titleResId: Int, val iconResId: Int, val actionId: String)

class DesktopItemAdapter(
    private val items: List<DesktopShortcut>,
    private val onItemClick: (DesktopShortcut) -> Unit
) : RecyclerView.Adapter<DesktopItemAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemDesktopShortcutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DesktopShortcut) {
            binding.textDesktop.setText(item.titleResId)
            binding.iconDesktop.setImageResource(item.iconResId)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDesktopShortcutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
