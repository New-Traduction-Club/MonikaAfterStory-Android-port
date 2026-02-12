package org.renpy.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.renpy.android.databinding.ItemFileBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: (File) -> Unit
) : ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    val selectedFiles = mutableSetOf<File>()
    private var isSelectionMode = false

    override fun submitList(list: List<File>?) {
        super.submitList(list?.let { ArrayList(it) })
    }

    fun toggleSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        val index = currentList.indexOf(file)
        if (index != -1) {
            notifyItemChanged(index)
        } else {
            notifyDataSetChanged()
        }
    }

    fun clearSelection() {
        selectedFiles.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }
    
    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) clearSelection()
    }
    
    fun getSelectedCount(): Int = selectedFiles.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(file: File) {
            binding.textName.text = file.name
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val lastModified = dateFormat.format(Date(file.lastModified()))
            
            if (file.isDirectory) {
                binding.icon.setImageResource(R.drawable.ic_folder) 
                val childCount = file.listFiles()?.size ?: 0
                binding.textDetails.text = "$lastModified • $childCount items"
            } else {
                binding.icon.setImageResource(R.drawable.ic_file)
                val size = file.length() / 1024
                binding.textDetails.text = "$lastModified • ${size} KB"
            }

            val isSelected = selectedFiles.contains(file)
            binding.cardView.isChecked = isSelected
            
            if (isSelected) {
                binding.cardView.setCardBackgroundColor(binding.root.context.resources.getColor(android.R.color.darker_gray))
                binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = true
            } else {
                binding.cardView.setCardBackgroundColor(binding.root.context.resources.getColor(android.R.color.transparent))
                binding.checkbox.visibility = View.GONE
                binding.checkbox.isChecked = false
            }


            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(file)
                    onItemLongClick(file) // Notify parent to update UI
                } else {
                    onItemClick(file)
                }
            }

            itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleSelection(file)
                    onItemLongClick(file)
                }
                true
            }
        }
    }
    
    class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.lastModified() == newItem.lastModified() && 
                oldItem.length() == newItem.length()
        }
    }
}
