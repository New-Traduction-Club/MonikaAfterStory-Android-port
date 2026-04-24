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

    companion object {
        private val CODE_EXTENSIONS = setOf(
            "rpy", "py", "kt", "kts", "java", "js", "ts", "tsx", "jsx", "json", "xml", "html", "htm",
            "css", "scss", "sass", "less", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
            "gradle", "md", "markdown", "txt", "csv", "log", "sh", "bat", "ps1", "lua", "rb", "php",
            "go", "rs", "c", "h", "cpp", "hpp", "cc", "cs", "swift", "sql"
        )
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif")
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "webm", "mkv", "mov", "avi", "3gp", "3gpp", "mpeg", "mpg", "ts", "m2ts", "mts"
        )
        private val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "wav", "m4a", "flac", "aac", "opus")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rpa", "rpi", "rar", "7z", "tar", "gz", "bz2")
    }

    val selectedFiles = mutableSetOf<File>()
    private var isSelectionMode = false
    private var showSearchLocation = false
    private var searchRootDir: File? = null

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

    fun setSearchContext(enabled: Boolean, rootDir: File?) {
        showSearchLocation = enabled
        searchRootDir = rootDir
        notifyDataSetChanged()
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
            if (showSearchLocation) {
                binding.textPath.visibility = View.VISIBLE
                binding.textPath.text = buildLocationSubtitle(file)
            } else {
                binding.textPath.visibility = View.GONE
            }
            
            val dateFormat = SimpleDateFormat("MM-dd-yyyy HH:mm", Locale.getDefault())
            val lastModified = dateFormat.format(Date(file.lastModified()))
            binding.textDate.text = lastModified
            
            if (file.isDirectory) {
                binding.icon.setImageResource(R.drawable.ic_folder) 
                binding.icon.setColorFilter(android.graphics.Color.parseColor("#FFC107"))
                binding.textType.text = itemView.context.getString(R.string.file_type_folder)
                binding.textSize.text = "--"
            } else {
                val ext = file.extension.lowercase(Locale.US)
                val iconRes = when {
                    ext in CODE_EXTENSIONS -> R.drawable.ic_file_document
                    ext in IMAGE_EXTENSIONS -> R.drawable.ic_file_image
                    ext in VIDEO_EXTENSIONS -> R.drawable.ic_file_image
                    ext in AUDIO_EXTENSIONS -> R.drawable.ic_file_audio
                    ext in ARCHIVE_EXTENSIONS -> R.drawable.ic_file_archive
                    else -> R.drawable.ic_file
                }
                binding.icon.setImageResource(iconRes)
                binding.icon.setColorFilter(android.graphics.Color.parseColor("#DCEEFA"))
                
                binding.textType.text = when {
                    ext in CODE_EXTENSIONS -> itemView.context.getString(R.string.file_type_script)
                    ext in IMAGE_EXTENSIONS -> itemView.context.getString(R.string.file_type_image)
                    ext in VIDEO_EXTENSIONS -> itemView.context.getString(R.string.file_type_video)
                    ext in AUDIO_EXTENSIONS -> itemView.context.getString(R.string.file_type_audio)
                    ext in ARCHIVE_EXTENSIONS -> itemView.context.getString(R.string.file_type_archive)
                    else -> itemView.context.getString(R.string.file_type_generic)
                }
                
                binding.textSize.text = formatSize(file.length())
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

    private fun formatSize(size: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L
        return when {
            size < kb -> "$size B"
            size < mb -> "${size / kb} KB"
            size < gb -> String.format(Locale.US, "%.1f MB", size / mb.toDouble())
            else -> String.format(Locale.US, "%.1f GB", size / gb.toDouble())
        }
    }

    private fun buildLocationSubtitle(file: File): String {
        val parentPath = file.parentFile?.absolutePath ?: file.absolutePath
        val rootPath = searchRootDir?.absolutePath ?: return parentPath
        if (!parentPath.startsWith(rootPath)) return parentPath

        val relative = parentPath
            .removePrefix(rootPath)
            .trimStart(File.separatorChar)
        return if (relative.isEmpty()) File.separator else "${File.separator}$relative"
    }
}
