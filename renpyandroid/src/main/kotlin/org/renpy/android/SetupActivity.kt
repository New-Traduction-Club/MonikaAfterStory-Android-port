package org.renpy.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.io.InputStream
import java.io.BufferedOutputStream
import java.io.BufferedInputStream
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SetupActivity : BaseActivity() {

    private var ddlcUri: Uri? = null
    private var masUri: Uri? = null
    
    private var deleteDdlcAfterInstall = false
    private var deleteMasAfterInstall = false

    // Download State
    private var isMasDownloadInProgress = false
    private var masDownloadTotalBytes = 0L
    private var masDownloadBytesRead = 0L
    private var masDownloadStartTime = 0L


    private lateinit var tvSelectedDDLC: TextView
    private lateinit var tvSelectedMAS: TextView
    private lateinit var layoutProgress: LinearLayout
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnInstall: Button
    
    private lateinit var btnLanguage: LinearLayout
    private lateinit var tvCurrentLanguage: TextView

    private val CHECKSUM_DDLC = "2a3dd7969a06729a32ace0a6ece5f2327e29bdf460b8b39e6a8b0875e545632e"
    private val CHECKSUM_MAS = "80925bccce56cc83f5a95fd2a196d1b26c755c771a7b5f853dd65b6c23caf1a1"
    private val MAS_DOWNLOAD_URL = "https://github.com/Monika-After-Story/MonikaModDev/releases/download/v0.12.15/Monika_After_Story-0.12.15-Mod-Dlx.zip"

    companion object {
        private const val REQUEST_CODE_DDLC = 1001
        private const val REQUEST_CODE_MAS = 1002
        private const val REQUEST_PERMISSION_NOTIFICATIONS = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        setContentView(R.layout.activity_setup)

        if (savedInstanceState != null) {
            ddlcUri = savedInstanceState.getParcelable("ddlcUri")
            masUri = savedInstanceState.getParcelable("masUri")
            deleteDdlcAfterInstall = savedInstanceState.getBoolean("deleteDdlcAfterInstall")
            deleteMasAfterInstall = savedInstanceState.getBoolean("deleteMasAfterInstall")
            isMasDownloadInProgress = savedInstanceState.getBoolean("isMasDownloadInProgress")
        }


        // Bind Views
        val btnDownloadDDLC = findViewById<Button>(R.id.btnDownloadDDLC)
        val btnSelectDDLC = findViewById<Button>(R.id.btnSelectDDLC)
        tvSelectedDDLC = findViewById(R.id.tvSelectedDDLC)

        val btnDownloadMAS = findViewById<Button>(R.id.btnDownloadMAS)
        val btnSelectMAS = findViewById<Button>(R.id.btnSelectMAS)
        val btnAutoDownloadMAS = findViewById<Button>(R.id.btnAutoDownloadMAS)
        tvSelectedMAS = findViewById(R.id.tvSelectedMAS)

        btnInstall = findViewById(R.id.btnInstall)
        layoutProgress = findViewById(R.id.layoutProgress)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)
        
        btnLanguage = findViewById(R.id.btnLanguage)
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)

        setupLanguageUI()

        // DDLC
        btnDownloadDDLC.setOnClickListener {
            openUrl(getString(R.string.setup_step_1_url))
        }

        btnSelectDDLC.setOnClickListener {
            selectFile(REQUEST_CODE_DDLC)
        }

        // MAS
        btnDownloadMAS.setOnClickListener {
            openUrl(getString(R.string.setup_step_2_url))
        }

        btnSelectMAS.setOnClickListener {
            selectFile(REQUEST_CODE_MAS)
        }
        
        btnAutoDownloadMAS.setOnClickListener {
            checkAndStartDownload()
        }

        // Install
        btnInstall.setOnClickListener {
            if (ddlcUri != null && masUri != null) {
                startInstallation()
            } else {
                Toast.makeText(this, getString(R.string.setup_error_missing_files), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("ddlcUri", ddlcUri)
        outState.putParcelable("masUri", masUri)
        outState.putBoolean("deleteDdlcAfterInstall", deleteDdlcAfterInstall)
        outState.putBoolean("deleteMasAfterInstall", deleteMasAfterInstall)
        outState.putBoolean("isMasDownloadInProgress", isMasDownloadInProgress)
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            
            when (intent.action) {
                DownloadService.ACTION_DOWNLOAD_PROGRESS -> {
                    val progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                    val speed = intent.getStringExtra(DownloadService.EXTRA_SPEED) ?: ""
                    val eta = intent.getStringExtra(DownloadService.EXTRA_ETA) ?: ""
                    
                    val currentBytes = intent.getLongExtra(DownloadService.EXTRA_CURRENT_BYTES, 0)
                    val totalBytes = intent.getLongExtra(DownloadService.EXTRA_TOTAL_BYTES, 0)
                    
                    updateDownloadProgressUI(progress, speed, eta, currentBytes, totalBytes)
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

    override fun onResume() {
        super.onResume()
        
        // check shared prefs for status
        val prefs = getSharedPreferences(DownloadService.PREFS_NAME, MODE_PRIVATE)
        val status = prefs.getInt(DownloadService.KEY_STATUS, DownloadService.STATUS_IDLE)
        val error = prefs.getString(DownloadService.KEY_ERROR, null)
        
        if (status == DownloadService.STATUS_RUNNING) {
             isMasDownloadInProgress = true
             layoutProgress.visibility = View.VISIBLE
             setUiEnabled(false)
             // Receiver will update ui
        } else if (status == DownloadService.STATUS_COMPLETE && isMasDownloadInProgress) {
             onDownloadComplete()
        } else if (status == DownloadService.STATUS_ERROR && isMasDownloadInProgress) {
             onDownloadError(error)
        } else {
            // Idle or completed previously
            if (masUri != null) {
                val fileName = getFileName(masUri!!)
                tvSelectedMAS.text = getString(R.string.setup_file_selected, fileName)
                tvSelectedMAS.visibility = View.VISIBLE
                layoutProgress.visibility = View.GONE
                setUiEnabled(true)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }


    private fun setupLanguageUI() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentLang = prefs.getString("language", "English") ?: "English"
        tvCurrentLanguage.text = currentLang

        if (!prefs.getBoolean("setup_language_confirmed", false)) {
            showLanguageDialog(false)
        }

        btnLanguage.setOnClickListener {
            showLanguageDialog(true)
        }
    }

    private fun showLanguageDialog(cancelable: Boolean) {
        val languages = resources.getStringArray(R.array.languages)
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_language_title))
            .setItems(languages) { _, which ->
                val selectedLang = languages[which]
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                
                prefs.edit()
                    .putBoolean("setup_language_confirmed", true)
                    .putString("language", selectedLang)
                    .apply()
                
                recreate()
            }
            .setCancelable(cancelable)
            .apply {
                if (cancelable) {
                    setNegativeButton(getString(R.string.cancel), null)
                }
            }
            .show()
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    private fun selectFile(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/zip"
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            val uri = data.data!!
            val fileName = getFileName(uri)

            if (requestCode == REQUEST_CODE_DDLC) {
                ddlcUri = uri
                tvSelectedDDLC.text = getString(R.string.setup_file_selected, fileName)
                tvSelectedDDLC.visibility = View.VISIBLE
                confirmDeleteZip(REQUEST_CODE_DDLC)
            } else if (requestCode == REQUEST_CODE_MAS) {
                masUri = uri
                tvSelectedMAS.text = getString(R.string.setup_file_selected, fileName)
                tvSelectedMAS.visibility = View.VISIBLE
                confirmDeleteZip(REQUEST_CODE_MAS)
            }
            if (requestCode == REQUEST_CODE_MAS) deleteMasAfterInstall = false 
            if (requestCode == REQUEST_CODE_DDLC) deleteDdlcAfterInstall = false
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown.zip"
    }

    private fun startInstallation() {
        layoutProgress.visibility = View.VISIBLE
        
        // Disable UI
        setUiEnabled(false)
        
        progressBar.isIndeterminate = true
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    processInstallation()
                }
                
                tvProgress.text = getString(R.string.setup_complete)
                progressBar.progress = 100
                progressBar.isIndeterminate = false
                
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                prefs.edit().putBoolean("is_setup_completed", true).apply()

                Toast.makeText(this@SetupActivity, getString(R.string.setup_complete), Toast.LENGTH_SHORT).show()
                
                kotlinx.coroutines.delay(1000)
                
                val intent = Intent(this@SetupActivity, LauncherActivity::class.java)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                layoutProgress.visibility = View.GONE
                setUiEnabled(true)
                Toast.makeText(this@SetupActivity, getString(R.string.setup_error, e.message), Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f
        
        btnInstall.isEnabled = enabled
        btnInstall.alpha = alpha
        
        findViewById<View>(R.id.btnDownloadDDLC).isEnabled = enabled
        findViewById<View>(R.id.btnSelectDDLC).isEnabled = enabled
        findViewById<View>(R.id.btnDownloadMAS).isEnabled = enabled
        findViewById<View>(R.id.btnSelectMAS).isEnabled = enabled
        findViewById<View>(R.id.btnAutoDownloadMAS).isEnabled = enabled
        findViewById<View>(R.id.btnLanguage).isEnabled = enabled
        
        findViewById<View>(R.id.btnDownloadDDLC).alpha = alpha
        findViewById<View>(R.id.btnSelectDDLC).alpha = alpha
        findViewById<View>(R.id.btnDownloadMAS).alpha = alpha
        findViewById<View>(R.id.btnSelectMAS).alpha = alpha
        findViewById<View>(R.id.btnAutoDownloadMAS).alpha = alpha
        findViewById<View>(R.id.btnLanguage).alpha = alpha
    }

    private suspend fun processInstallation() {
        val gameDir = File(filesDir, "game")
        if (!gameDir.exists()) gameDir.mkdirs()

        // Verify and Extract DDLC
        updateStatus(getString(R.string.setup_progress_verifying))
        
        if (!verifyChecksum(ddlcUri!!, CHECKSUM_DDLC)) {
            throw Exception(getString(R.string.setup_error_checksum, "DDLC"))
        }
        if (!verifyChecksum(masUri!!, CHECKSUM_MAS)) {
            throw Exception(getString(R.string.setup_error_checksum, "MAS"))
        }

        // Extract DDLC relevant files
        updateStatus(getString(R.string.setup_progress_extracting_ddlc))
        
        var ddlcTempFile: File? = null
        try {
            ddlcTempFile = File(cacheDir, "ddlc_temp.zip")
            copyUriToFile(ddlcUri!!, ddlcTempFile)
            
            ZipFile(ddlcTempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (!entry.isDirectory && (
                            name.endsWith("game/audio.rpa") ||
                            name.endsWith("game/fonts.rpa") ||
                            name.endsWith("game/images.rpa")
                        )) {
                        
                        val fileName = File(name).name 
                        val targetFile = File(gameDir, fileName)
                        
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } finally {
            ddlcTempFile?.delete()
        }

        // Unrpa files
        updateStatus(getString(R.string.setup_progress_extracting_rpa))
        val rpaFiles = listOf("audio.rpa", "fonts.rpa", "images.rpa")
        for (rpaName in rpaFiles) {
            val rpaFile = File(gameDir, rpaName)
            if (rpaFile.exists()) {
                RpaUtils.extractGameAssets(rpaFile.absolutePath, gameDir) { file, current, total ->
                }
                rpaFile.delete()
            }
        }

        if (deleteDdlcAfterInstall && ddlcUri != null) {
            deleteFile(ddlcUri!!)
        }

        // Extract MAS
        updateStatus(getString(R.string.setup_progress_extracting_mas))
        
        var masTempFile: File? = null
        try {
            // Check if masUri is already a file we created (downloaded)
            if (masUri!!.scheme == "file") {
                masTempFile = File(masUri!!.path!!)
            } else {
                masTempFile = File(cacheDir, "mas_temp_install.zip")
                copyUriToFile(masUri!!, masTempFile)
            }

            ZipFile(masTempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    
                    var relativePath: String? = null
                    
                    if (name.startsWith("game/")) {
                        relativePath = name.substring("game/".length)
                    } 
                    else if (name.contains("/game/")) {
                        val index = name.indexOf("/game/")
                        relativePath = name.substring(index + "/game/".length)
                    }
                    
                    if (!relativePath.isNullOrEmpty()) {
                        val targetFile = File(gameDir, relativePath)
                        
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
             if (masUri!!.scheme != "file") {
                 masTempFile?.delete()
             }
        }

        if (deleteMasAfterInstall && masUri != null) {
            deleteFile(masUri!!)
        }
        
        // Extract py_scripts_and_other_stuff.zip
        updateStatus(getString(R.string.setup_progress_finalizing))
        try {
            val assetTemp = File(cacheDir, "py_scripts_temp.zip")
            assets.open("py_scripts_and_other_stuff.zip").use { ais ->
                FileOutputStream(assetTemp).use { fos -> ais.copyTo(fos) }
            }
            
            ZipFile(assetTemp).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory) {
                        val targetFile = File(gameDir, entry.name)
                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                             FileOutputStream(targetFile).use { output ->
                                 input.copyTo(output)
                             }
                        }
                    }
                }
            }
            assetTemp.delete()
            
        } catch (e: Exception) {
        } catch (e: Exception) {
            android.util.Log.w("Setup", "py_scripts zip missing or error: ${e.message}")
        }
    }

    private fun confirmDeleteZip(requestCode: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setup_confirm_delete_zip_title))
            .setMessage(getString(R.string.setup_confirm_delete_zip_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                if (requestCode == REQUEST_CODE_DDLC) deleteDdlcAfterInstall = true
                else if (requestCode == REQUEST_CODE_MAS) deleteMasAfterInstall = true
            }
            .setNegativeButton(getString(R.string.no)) { _, _ ->
                if (requestCode == REQUEST_CODE_DDLC) deleteDdlcAfterInstall = false
                else if (requestCode == REQUEST_CODE_MAS) deleteMasAfterInstall = false
            }
            .show()
    }

    private fun checkAndStartDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.permission_notification_title))
                    .setMessage(getString(R.string.permission_notification_message))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_PERMISSION_NOTIFICATIONS)
                    }
                    .setNegativeButton(getString(R.string.no)) { _, _ ->
                        downloadMAS() // Proceed without notifications
                    }
                    .show()
                return
            }
        }
        downloadMAS()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_NOTIFICATIONS) {
            downloadMAS()
        }
    }

    private fun updateDownloadProgressUI(progress: Int, speed: String, eta: String, currentBytes: Long, totalBytes: Long) {
        layoutProgress.visibility = View.VISIBLE
        setUiEnabled(false)
        if (totalBytes > 0) {
            progressBar.isIndeterminate = false
            progressBar.progress = progress
            
            val currentSize = formatBytes(currentBytes)
            val totalSize = formatBytes(totalBytes)
            
            tvProgress.text = getString(R.string.setup_download_progress_full, progress, currentSize, totalSize, speed, eta)
        } else {
             progressBar.isIndeterminate = true
             val currentSize = formatBytes(currentBytes)
             tvProgress.text = getString(R.string.setup_download_progress_indeterminate, currentSize, speed)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes > 1024 * 1024) {
             String.format("%.1f MB", bytes / (1024f * 1024f))
        } else {
             String.format("%.1f KB", bytes / 1024f)
        }
    }

    private fun onDownloadComplete() {
        isMasDownloadInProgress = false
        val destFile = File(filesDir, "mas_temp.zip")
        masUri = Uri.fromFile(destFile)
        tvSelectedMAS.text = getString(R.string.setup_file_selected, destFile.name)
        tvSelectedMAS.visibility = View.VISIBLE
        deleteMasAfterInstall = true
        
        layoutProgress.visibility = View.GONE
        setUiEnabled(true)
        
        Toast.makeText(this, getString(R.string.notification_download_complete), Toast.LENGTH_SHORT).show()
    }

    private fun onDownloadError(error: String?) {
        isMasDownloadInProgress = false
        layoutProgress.visibility = View.GONE
        setUiEnabled(true)
        Toast.makeText(this, getString(R.string.setup_download_error, error ?: "Unknown"), Toast.LENGTH_LONG).show()
    }

    private fun downloadMAS() {
        if (isMasDownloadInProgress) return
        
        isMasDownloadInProgress = true
        layoutProgress.visibility = View.VISIBLE
        setUiEnabled(false)
        tvProgress.text = getString(R.string.setup_downloading_mas)
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        
        val destFile = File(filesDir, "mas_temp.zip")
        
        // Reset status
        val prefs = getSharedPreferences(DownloadService.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putInt(DownloadService.KEY_STATUS, DownloadService.STATUS_IDLE).apply()
        
        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, MAS_DOWNLOAD_URL)
            putExtra(DownloadService.EXTRA_DEST_PATH, destFile.absolutePath)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun deleteFile(uri: Uri) {
        try {
            if (uri.scheme == "file") {
                val file = File(uri.path!!)
                if (file.exists()) file.delete()
            } else if (uri.scheme == "content") {
                try {
                    android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyUriToFile(uri: Uri, destFile: File) {
        contentResolver.openInputStream(uri)?.use { input ->
             FileOutputStream(destFile).use { output ->
                 input.copyTo(output)
             }
        } ?: throw Exception("Cannot open input stream for URI: $uri")
    }

    private suspend fun updateStatus(message: String) {
        withContext(Dispatchers.Main) {
            tvProgress.text = message
        }
    }

    private fun verifyChecksum(uri: Uri, excpectedHash: String): Boolean {
        val inputStream = contentResolver.openInputStream(uri) ?: return false
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        inputStream.close()
        
        val hashBytes = digest.digest()
        val sb = StringBuilder()
        for (b in hashBytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString().equals(excpectedHash, ignoreCase = true)
    }
}
