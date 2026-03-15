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
            if (selectedFiles.isEmpty()) {
                isSelectionMode = false
            }
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
            
            val dateFormat = SimpleDateFormat("MM-dd-yyyy HH:mm", Locale.getDefault())
            val lastModified = dateFormat.format(Date(file.lastModified()))
            binding.textDate.text = lastModified
            
            if (file.isDirectory) {
                binding.icon.setImageResource(R.drawable.ic_folder) 
                binding.icon.setColorFilter(android.graphics.Color.parseColor("#FFC107"))
                binding.textType.text = itemView.context.getString(R.string.file_type_folder)
                binding.textSize.text = "--"
            } else {
                val ext = file.extension.lowercase()
                val iconRes = when (ext) {
                    "rpy", "py", "json", "txt", "log", "xml" -> R.drawable.ic_file_document
                    "png", "jpg", "jpeg", "webp" -> R.drawable.ic_file_image
                    "mp3", "ogg", "wav" -> R.drawable.ic_file_audio
                    "zip", "rpa", "rpi" -> R.drawable.ic_file_archive
                    else -> R.drawable.ic_file
                }
                binding.icon.setImageResource(iconRes)
                binding.icon.setColorFilter(android.graphics.Color.parseColor("#DCEEFA"))
                
                binding.textType.text = when (ext) {
                    "txt" -> itemView.context.getString(R.string.file_type_text)
                    "rpy", "py" -> itemView.context.getString(R.string.file_type_script)
                    "png", "jpg", "jpeg", "webp" -> itemView.context.getString(R.string.file_type_image)
                    "mp3", "ogg", "wav" -> itemView.context.getString(R.string.file_type_audio)
                    "zip", "rpa", "rpi" -> itemView.context.getString(R.string.file_type_archive)
                    "sh" -> itemView.context.getString(R.string.file_type_generic)
                    else -> itemView.context.getString(R.string.file_type_generic)
                }
                
                val size = file.length()
                if (size < 1024) {
                    binding.textSize.text = "$size B"
                } else if (size < 1024 * 1024) {
                    binding.textSize.text = "${size / 1024} KB"
                } else {
                    binding.textSize.text = "${size / (1024 * 1024)} MB"
                }
            }

            val isSelected = selectedFiles.contains(file)
            
            if (isSelected) {
                binding.cardView.setBackgroundColor(android.graphics.Color.parseColor("#BEB5B6"))
            } else {
                binding.cardView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
