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
import java.util.zip.ZipInputStream

class SetupActivity : BaseActivity() {

    private var ddlcUri: Uri? = null
    private var masUri: Uri? = null

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

    companion object {
        private const val REQUEST_CODE_DDLC = 1001
        private const val REQUEST_CODE_MAS = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Bind Views
        val btnDownloadDDLC = findViewById<Button>(R.id.btnDownloadDDLC)
        val btnSelectDDLC = findViewById<Button>(R.id.btnSelectDDLC)
        tvSelectedDDLC = findViewById(R.id.tvSelectedDDLC)

        val btnDownloadMAS = findViewById<Button>(R.id.btnDownloadMAS)
        val btnSelectMAS = findViewById<Button>(R.id.btnSelectMAS)
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

        // Install
        btnInstall.setOnClickListener {
            if (ddlcUri != null && masUri != null) {
                startInstallation()
            } else {
                Toast.makeText(this, getString(R.string.setup_error_missing_files), Toast.LENGTH_LONG).show()
            }
        }
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
            } else if (requestCode == REQUEST_CODE_MAS) {
                masUri = uri
                tvSelectedMAS.text = getString(R.string.setup_file_selected, fileName)
                tvSelectedMAS.visibility = View.VISIBLE
            }
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
        findViewById<View>(R.id.btnLanguage).isEnabled = enabled
        
        findViewById<View>(R.id.btnDownloadDDLC).alpha = alpha
        findViewById<View>(R.id.btnSelectDDLC).alpha = alpha
        findViewById<View>(R.id.btnDownloadMAS).alpha = alpha
        findViewById<View>(R.id.btnSelectMAS).alpha = alpha
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
        
        val ddlcZipStream = contentResolver.openInputStream(ddlcUri!!) ?: throw Exception("Cannot open DDLC zip")
        ZipInputStream(ddlcZipStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && (
                        name.endsWith("game/audio.rpa") ||
                        name.endsWith("game/fonts.rpa") ||
                        name.endsWith("game/images.rpa")
                    )) {
                    
                    val fileName = File(name).name 
                    val targetFile = File(gameDir, fileName)
                    FileOutputStream(targetFile).use { out -> zip.copyTo(out) }
                }
                entry = zip.nextEntry
            }
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

        // Extract MAS
        updateStatus(getString(R.string.setup_progress_extracting_mas))
        val masZipStream = contentResolver.openInputStream(masUri!!) ?: throw Exception("Cannot open MAS zip")
        ZipInputStream(masZipStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
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
                        FileOutputStream(targetFile).use { out -> zip.copyTo(out) }
                    }
                }
                entry = zip.nextEntry
            }
        }
        
        // Extract py_scripts_and_other_stuff.zip
        updateStatus(getString(R.string.setup_progress_finalizing))
        try {
            assets.open("py_scripts_and_other_stuff.zip").use { ais ->
                ZipInputStream(ais).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val targetFile = File(gameDir, entry.name)
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { out -> zip.copyTo(out) }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("Setup", "py_scripts zip missing or error: ${e.message}")
        }
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
