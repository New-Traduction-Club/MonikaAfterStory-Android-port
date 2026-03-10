package org.renpy.android

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.renpy.android.databinding.ItemBackupBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupsAdapter(
    private var backups: List<File>,
    private val onBackupClick: (File) -> Unit
) : RecyclerView.Adapter<BackupsAdapter.BackupViewHolder>() {

    class BackupViewHolder(val binding: ItemBackupBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackupViewHolder {
        val binding = ItemBackupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BackupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BackupViewHolder, position: Int) {
        val file = backups[position]
        holder.binding.tvFileName.text = file.name
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
        val dateString = dateFormat.format(Date(file.lastModified()))
        val sizeInMb = file.length() / (1024.0 * 1024.0)
        val sizeString = String.format(Locale.getDefault(), "%.2f MB", sizeInMb)
        
        holder.binding.tvFileInfo.text = "$dateString • $sizeString"
        
        holder.itemView.setOnClickListener {
            onBackupClick(file)
        }
    }

    override fun getItemCount(): Int = backups.size

    fun updateData(newBackups: List<File>) {
        backups = newBackups
        notifyDataSetChanged()
    }
}
