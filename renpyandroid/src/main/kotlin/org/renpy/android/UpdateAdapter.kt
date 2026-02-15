package org.renpy.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.renpy.android.R

class UpdateAdapter(
    private val updates: List<UpdateItem>,
    private val updateManager: UpdateManager,
    private val onUpdateClick: (UpdateItem) -> Unit
) : RecyclerView.Adapter<UpdateAdapter.UpdateViewHolder>() {

    private val downloadingItems = mutableMapOf<String, Int>() // Item ID -> Progress
    private val processingItems = mutableSetOf<String>()

    class UpdateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.textName)
        val textDescription: TextView = view.findViewById(R.id.textDescription)
        val textVersion: TextView = view.findViewById(R.id.textVersion)
        val buttonAction: Button = view.findViewById(R.id.buttonAction)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpdateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_update, parent, false)
        return UpdateViewHolder(view)
    }

    override fun onBindViewHolder(holder: UpdateViewHolder, position: Int) {
        val item = updates[position]
        holder.textName.text = item.name
        holder.textDescription.text = item.description
        
        val localVersion = updateManager.getLocalVersion(item.id)
        holder.textVersion.text = holder.itemView.context.getString(R.string.update_current_version, localVersion.toString(), item.version.toString(), item.versionName)

        val isDownloading = downloadingItems.containsKey(item.id)
        val isProcessing = processingItems.contains(item.id)

        if (isDownloading) {
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = downloadingItems[item.id] ?: 0
            holder.textStatus.visibility = View.VISIBLE
            holder.textStatus.text = holder.itemView.context.getString(R.string.status_downloading)
            holder.buttonAction.isEnabled = false
        } else if (isProcessing) {
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.isIndeterminate = true
            holder.textStatus.visibility = View.VISIBLE
            holder.textStatus.text = holder.itemView.context.getString(R.string.status_installing)
            holder.buttonAction.isEnabled = false
        } else {
            holder.progressBar.visibility = View.GONE
            holder.textStatus.visibility = View.GONE
            holder.buttonAction.isEnabled = true
            
            if (updateManager.isUpdateAvailable(item)) {
                holder.buttonAction.text = holder.itemView.context.getString(R.string.btn_update)
                holder.buttonAction.setOnClickListener { onUpdateClick(item) }
            } else {
                holder.buttonAction.text = holder.itemView.context.getString(R.string.btn_reinstall)
                holder.buttonAction.setOnClickListener { onUpdateClick(item) }
            }
        }
    }

    override fun getItemCount() = updates.size

    fun updateProgress(itemId: String, progress: Int) {
        downloadingItems[itemId] = progress
        processingItems.remove(itemId)
        notifyDataSetChanged() // efficient updates would be better but this is simple...
    }

    fun setProcessing(itemId: String) {
        processingItems.add(itemId)
        downloadingItems.remove(itemId)
        notifyDataSetChanged()
    }

    fun setCompleted(itemId: String) {
        downloadingItems.remove(itemId)
        processingItems.remove(itemId)
        notifyDataSetChanged()
    }
    
    fun setFailed(itemId: String) {
        downloadingItems.remove(itemId)
        processingItems.remove(itemId)
        notifyDataSetChanged()
    }
}
