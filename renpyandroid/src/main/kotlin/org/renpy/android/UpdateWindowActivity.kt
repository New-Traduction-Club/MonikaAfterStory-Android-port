package org.renpy.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateWindowActivity : GameWindowActivity() {

    private lateinit var txtUpdateStatus: TextView
    private lateinit var txtUpdateProgress: TextView
    private lateinit var progressBarUpdate: ProgressBar
    private lateinit var txtUpdateDescription: TextView

    private var isProcessing = false
    private var currentPackage: PackageInfo? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                DownloadService.ACTION_DOWNLOAD_PROGRESS -> {
                    val progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                    val currentBytes = intent.getLongExtra(DownloadService.EXTRA_CURRENT_BYTES, 0)
                    val totalBytes = intent.getLongExtra(DownloadService.EXTRA_TOTAL_BYTES, 0)
                    
                    updateProgressUI(progress, currentBytes, totalBytes)
                }
                DownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                    val success = intent.getBooleanExtra(DownloadService.EXTRA_SUCCESS, false)
                    val error = intent.getStringExtra(DownloadService.EXTRA_ERROR)

                    if (success) {
                        onDownloadComplete()
                    } else {
                        onDownloadError(error)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_window)
        setTitle(getString(R.string.update_window_title))

        txtUpdateStatus = findViewById(R.id.txtUpdateStatus)
        txtUpdateProgress = findViewById(R.id.txtUpdateProgress)
        progressBarUpdate = findViewById(R.id.progressBarUpdate)
        txtUpdateDescription = findViewById(R.id.txtUpdateDescription)

        currentPackage = intent.getParcelableExtra("package_info")
        
        setupReceiver()
        startUpdateProcess()
    }

    private fun startUpdateProcess() {
        val pkg = currentPackage ?: run {
            txtUpdateStatus.text = getString(R.string.update_error_no_package)
            return
        }

        isProcessing = true
        txtUpdateStatus.text = getString(R.string.update_status_downloading)
        
        GameUpdateManager.startDownload(this, pkg)
    }

    private fun updateProgressUI(progress: Int, currentBytes: Long, totalBytes: Long) {
        progressBarUpdate.progress = progress
        txtUpdateProgress.text = getString(R.string.update_progress_percent, progress)
    }

    private fun onDownloadComplete() {
        CoroutineScope(Dispatchers.Main).launch {
            txtUpdateStatus.text = getString(R.string.update_status_verifying)
            progressBarUpdate.isIndeterminate = true

            val success = withContext(Dispatchers.IO) {
                GameUpdateManager.verifyUpdateFile(this@UpdateWindowActivity, currentPackage?.sha256 ?: "")
            }

            if (success) {
                txtUpdateStatus.text = getString(R.string.update_status_installing)
                try {
                    withContext(Dispatchers.IO) {
                        GameUpdateManager.installUpdate(this@UpdateWindowActivity)
                    }
                    txtUpdateStatus.text = getString(R.string.update_status_complete)
                    progressBarUpdate.isIndeterminate = false
                    progressBarUpdate.progress = 100
                    txtUpdateProgress.text = getString(R.string.update_progress_percent, 100)
                    txtUpdateDescription.text = getString(R.string.update_desc_finished)
                    isProcessing = false
                    InAppNotifier.show(this@UpdateWindowActivity, getString(R.string.update_status_complete))
                } catch (e: Exception) {
                    onDownloadError("Installation failed: ${e.message}")
                }
            } else {
                onDownloadError(getString(R.string.update_error_checksum))
            }
        }
    }

    private fun onDownloadError(error: String?) {
        txtUpdateStatus.text = getString(R.string.update_status_failed)
        txtUpdateProgress.text = getString(R.string.update_progress_error)
        txtUpdateDescription.text = error ?: getString(R.string.update_status_failed)
        isProcessing = false
        InAppNotifier.show(this, error ?: getString(R.string.update_status_failed), isLong = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadReceiver)
    }

    override fun onBackPressed() {
        if (isProcessing) {
            GameDialogBuilder(this)
                .setTitle(getString(R.string.update_dialog_title))
                .setMessage(getString(R.string.update_dialog_warning))
                .setPositiveButton(getString(R.string.action_ok), null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
    private fun setupReceiver() {
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_DOWNLOAD_PROGRESS)
            addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }
}
