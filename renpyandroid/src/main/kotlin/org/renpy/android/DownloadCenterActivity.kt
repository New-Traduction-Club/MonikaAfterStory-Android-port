package org.renpy.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class DownloadCenterActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var updateManager: UpdateManager
    private lateinit var adapter: UpdateAdapter
    private var updatesList = listOf<UpdateItem>()
    
    // Track currently downloading item ID to map service broadcasts to UI
    private var currentDownloadItemId: String? = null
    private var currentDownloadItem: UpdateItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_center)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.download_center_title)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        updateManager = UpdateManager(this)

        loadUpdates()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || currentDownloadItemId == null) return
            
            when (intent.action) {
                DownloadService.ACTION_DOWNLOAD_PROGRESS -> {
                    val progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                    adapter.updateProgress(currentDownloadItemId!!, progress)
                }
                DownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                    val success = intent.getBooleanExtra(DownloadService.EXTRA_SUCCESS, false)
                    if (success) {
                        adapter.setProcessing(currentDownloadItemId!!)
                        installUpdate(currentDownloadItem!!)
                    } else {
                        val error = intent.getStringExtra(DownloadService.EXTRA_ERROR)
                        Toast.makeText(this@DownloadCenterActivity, getString(R.string.download_failed_error, error), Toast.LENGTH_SHORT).show()
                        adapter.setFailed(currentDownloadItemId!!)
                        currentDownloadItemId = null
                        currentDownloadItem = null
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
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

    override fun onStop() {
        super.onStop()
        unregisterReceiver(downloadReceiver)
    }

    private fun loadUpdates() {
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBarLoading)
        val recycler = findViewById<RecyclerView>(R.id.recyclerView)
        
        progressBar.visibility = android.view.View.VISIBLE
        recycler.visibility = android.view.View.GONE
        
        // Use manifest url from strings
        CoroutineScope(Dispatchers.Main).launch {
            val url = getString(R.string.manifest_url)
            
            val updates = updateManager.fetchUpdates(url)
            if (updates.isNotEmpty()) {
                updatesList = updates
                setupAdapter()
            } else {
                Toast.makeText(this@DownloadCenterActivity, getString(R.string.no_updates_found), Toast.LENGTH_LONG).show()
            }
            
            progressBar.visibility = android.view.View.GONE
            recycler.visibility = android.view.View.VISIBLE
        }
    }

    private fun setupAdapter() {
        adapter = UpdateAdapter(updatesList, updateManager) { item ->
            checkNetworkAndStartDownload(item)
        }
        recyclerView.adapter = adapter
    }

    private fun startDownload(item: UpdateItem) {
        if (currentDownloadItemId != null) {
            Toast.makeText(this, getString(R.string.download_in_progress), Toast.LENGTH_SHORT).show()
            return
        }

        currentDownloadItemId = item.id
        currentDownloadItem = item
        
        val destFile = File(filesDir, item.targetFile)
        if (destFile.exists()) destFile.delete()

        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, item.url)
            putExtra(DownloadService.EXTRA_DEST_PATH, destFile.absolutePath)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        adapter.updateProgress(item.id, 0)
    }

    private fun installUpdate(item: UpdateItem) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (item.type == "language") {
                    val updatesDir = File(filesDir, "LauncherUpdates")
                    if (!updatesDir.exists()) updatesDir.mkdirs()
                    
                    val sourceFile = File(filesDir, item.targetFile)
                    val destFile = File(updatesDir, item.targetFile)
                    
                    if (sourceFile.exists()) {
                        sourceFile.copyTo(destFile, overwrite = true)
                        sourceFile.delete()
                    }
                    
                } else {
                    val zipFile = File(filesDir, item.targetFile)
                    val gameDir = File(filesDir, "game")
                    
                    if (!gameDir.exists()) gameDir.mkdirs()
                    
                    ZipInputStream(zipFile.inputStream()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val file = File(gameDir, entry.name)
                            if (entry.isDirectory) {
                                file.mkdirs()
                            } else {
                                file.parentFile?.mkdirs()
                                FileOutputStream(file).use { out -> zip.copyTo(out) }
                            }
                            entry = zip.nextEntry
                        }
                    }
                    zipFile.delete()
                }

                updateManager.setLocalVersion(item.id, item.version)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DownloadCenterActivity, getString(R.string.update_installed, item.name), Toast.LENGTH_SHORT).show()
                    adapter.setCompleted(item.id)
                    currentDownloadItemId = null
                    currentDownloadItem = null
                    adapter.notifyDataSetChanged()
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DownloadCenterActivity, getString(R.string.installation_failed, e.message), Toast.LENGTH_LONG).show()
                    adapter.setFailed(item.id)
                    currentDownloadItemId = null
                    currentDownloadItem = null
                }
                e.printStackTrace()
            }
        }
    }

    private fun checkNetworkAndStartDownload(item: UpdateItem) {
        // Use app_prefs to match SettingsActivity
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val wifiOnly = prefs.getBoolean("wifi_only", false)
        
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connMgr.activeNetworkInfo
        val isWifi = activeNetwork?.type == android.net.ConnectivityManager.TYPE_WIFI
        
        if (wifiOnly && !isWifi && activeNetwork?.isConnected == true) {
            showDataWarningDialog(item)
        } else {
            startDownload(item)
        }
    }

    private fun showDataWarningDialog(item: UpdateItem) {
        val progressDialog = android.app.ProgressDialog(this)
        progressDialog.setMessage(getString(R.string.calculating_size))
        progressDialog.setCancelable(false)
        progressDialog.show()
        
        CoroutineScope(Dispatchers.Main).launch {
            val size = updateManager.getFileSize(item.url)
            progressDialog.dismiss()
            
            androidx.appcompat.app.AlertDialog.Builder(this@DownloadCenterActivity)
                .setTitle(getString(R.string.data_warning_title))
                .setMessage(getString(R.string.data_warning_message, size))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    startDownload(item)
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }
    }
}
