package org.renpy.android

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import org.renpy.android.databinding.ActivityBackupsBinding
import java.io.File

class BackupsActivity : GameWindowActivity() {

    private lateinit var binding: ActivityBackupsBinding
    private lateinit var adapter: BackupsAdapter
    
    private var progressDialog: AlertDialog? = null
    private var progressIndicator: android.widget.ProgressBar? = null
    private var progressText: android.widget.TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityBackupsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setTitle(R.string.title_backups)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
        
        setupRecyclerView()
        setupFab()
        
        loadBackups()
    }

    private fun setupRecyclerView() {
        adapter = BackupsAdapter(emptyList()) { backupFile ->
            showBackupOptionsDialog(backupFile)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddBackup.setOnClickListener {
            binding.fabAddBackup.isEnabled = false
            showProgressDialog("Creating backup...")
            
            Thread {
                val resultPath = BackupManager.createBackup(this, object : BackupManager.ProgressListener {
                    override fun onProgress(percentage: Int, currentFile: String) {
                        runOnUiThread {
                            updateProgress(percentage, "Zipping: $currentFile")
                        }
                    }
                })
                runOnUiThread {
                    dismissProgressDialog()
                    binding.fabAddBackup.isEnabled = true
                    if (resultPath != null) {
                        Toast.makeText(this, getString(R.string.backup_created_toast), Toast.LENGTH_SHORT).show()
                        loadBackups()
                    } else {
                        Toast.makeText(this, getString(R.string.backup_failed_toast, "Unknown error"), Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun loadBackups() {
        val backups = BackupManager.listBackups(this)
        if (backups.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            adapter.updateData(backups)
        }
    }

    private fun showBackupOptionsDialog(backupFile: File) {
        val options = arrayOf(
            getString(R.string.restore_backup),
            getString(R.string.delete_backup)
        )
        
        GameDialogBuilder(this)
            .setTitle(backupFile.name)
            .setItems(options) { _, which ->
                when(which) {
                    0 -> confirmRestore(backupFile)
                    1 -> confirmDelete(backupFile)
                }
            }
            .show()
    }

    private fun confirmRestore(backupFile: File) {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.confirm_restore_title))
            .setMessage(getString(R.string.confirm_restore_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                performRestore(backupFile)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performRestore(backupFile: File) {
        showProgressDialog(getString(R.string.unzipping))
        Thread {
            val success = BackupManager.restoreBackup(this, backupFile, object : BackupManager.ProgressListener {
                override fun onProgress(percentage: Int, currentFile: String) {
                    runOnUiThread {
                        updateProgress(percentage, "Extracting: $currentFile")
                    }
                }
            })
            runOnUiThread {
                dismissProgressDialog()
                if (success) {
                    Toast.makeText(this, getString(R.string.backup_restored_toast), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.backup_restore_failed_toast, "I/O Error"), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun confirmDelete(backupFile: File) {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.confirm_delete_backup_title))
            .setMessage(getString(R.string.confirm_delete_backup_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                if (backupFile.delete()) {
                    Toast.makeText(this, getString(R.string.backup_deleted_toast), Toast.LENGTH_SHORT).show()
                    loadBackups()
                } else {
                    Toast.makeText(this, getString(R.string.backup_delete_failed_toast), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showProgressDialog(message: String) {
        if (progressDialog?.isShowing == true) return
        
        val builder = GameDialogBuilder(this)
        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        progressText = view.findViewById(R.id.progressText)
        progressIndicator = view.findViewById(R.id.progressBar)
        
        progressText?.text = message
        progressIndicator?.isIndeterminate = false
        progressIndicator?.progress = 0
        
        builder.setView(view)
        builder.setCancelable(false)
        progressDialog = builder.create()
        progressDialog?.show()
    }
    
    private fun updateProgress(percentage: Int, message: String) {
        progressIndicator?.progress = percentage
        progressText?.text = "$percentage% - $message"
    }
    
    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressIndicator = null
        progressText = null
    }
}
