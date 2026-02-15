package org.renpy.android

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.renpy.android.databinding.LauncherActivityBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.random.Random

class LauncherActivity : BaseActivity() {

    private lateinit var binding: LauncherActivityBinding
    private val viewModel: LauncherViewModel by viewModels()
    private var currentLanguage: String = ""
    
    private var progressDialog: AlertDialog? = null
    private var progressIndicator: android.widget.ProgressBar? = null
    private var progressText: android.widget.TextView? = null
    
    private var pendingExportUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        
        // Check if Setup is completed
        val isSetupCompleted = prefs.getBoolean("is_setup_completed", false)
        if (!isSetupCompleted) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        WorkManager.getInstance(applicationContext).cancelAllWork()
        currentLanguage = prefs.getString("language", "English") ?: "English"

        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        val setupConfirmed = prefs.getBoolean("setup_language_confirmed", false)
        
        if (isFirstLaunch && !setupConfirmed) {
            showLanguageSelectionDialog()
        } else {
            checkAndInstallLanguageScripts()
        }

        createLanguageFile(currentLanguage)

        binding = LauncherActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupObservers()
        
        setupListeners()
        
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
    
    private fun setupListeners() {
        binding.btnStartGame.setOnClickListener {
            showProgressDialog(getString(R.string.installing_language_data, currentLanguage))
            Thread {
                try {
                    installLogic(currentLanguage)
                    runOnUiThread {
                        dismissProgressDialog()
                        viewModel.handlePlayClick()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        dismissProgressDialog()
                        Toast.makeText(this, getString(R.string.install_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        binding.btnDownloadCenter.setOnClickListener {
            startActivity(Intent(this, DownloadCenterActivity::class.java))
        }
        
        binding.btnExport.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.launcher_export_title))
                .setMessage(getString(R.string.launcher_export_message))
                .setPositiveButton(getString(R.string.launcher_proceed)) { _, _ ->
                    val date = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
                    val fileName = "saves_backup_mas_$date.zip"
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "application/zip"
                    intent.putExtra(Intent.EXTRA_TITLE, fileName)
                    startActivityForResult(intent, 2001)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
        
        binding.btnImport.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.launcher_import_title))
                .setMessage(getString(R.string.launcher_import_message))
                .setPositiveButton(getString(R.string.launcher_proceed)) { _, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "application/zip"
                    startActivityForResult(intent, 2002)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
        
        binding.btnInternalExplorer.setOnClickListener {
            val intent = Intent(this, FileExplorerActivity::class.java)
            intent.putExtra("startPath", filesDir.absolutePath)
            startActivity(intent)
        }
        
        binding.btnExternalExplorer.setOnClickListener {
            val externalPath = getExternalFilesDir(null)?.absolutePath
            if (externalPath != null) {
                val intent = Intent(this, FileExplorerActivity::class.java)
                intent.putExtra("startPath", externalPath)
                startActivity(intent)
            }
        }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    private fun setupObservers() {
        viewModel.launchState.observe(this) { state ->
            when(state) {
                is LauncherViewModel.LaunchState.Idle -> {
                    dismissProgressDialog()
                }
                is LauncherViewModel.LaunchState.CheckingNetwork -> {
                    showProgressDialog(getString(R.string.translation_checking))
                }
                is LauncherViewModel.LaunchState.CheckingUpdates -> {
                    updateProgressText(getString(R.string.translation_checking))
                }
                is LauncherViewModel.LaunchState.UpdateAvailable -> {
                    dismissProgressDialog()
                    showUpdateConfirmationDialog(state.isMobileData)
                }
                is LauncherViewModel.LaunchState.Downloading -> {
                    if (progressDialog == null || !progressDialog!!.isShowing) {
                        showProgressDialog(getString(R.string.translation_updating))
                    }
                    progressIndicator?.isIndeterminate = false
                    progressIndicator?.progress = state.progress
                    updateProgressText("${getString(R.string.translation_updating)} ${state.progress}%")
                }
                is LauncherViewModel.LaunchState.LaunchGame -> {
                    dismissProgressDialog()
                    viewModel.consumeLaunchState()
                    startActivity(Intent(this, PythonSDLActivity::class.java))
                    finish()
                }
                is LauncherViewModel.LaunchState.Error -> {
                    dismissProgressDialog()
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.consumeLaunchState()
                    startActivity(Intent(this, PythonSDLActivity::class.java))
                    finish()
                }
            }
        }
        
        viewModel.operationStatus.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        
        viewModel.exportComplete.observe(this) { zipFile ->
            if (zipFile != null && pendingExportUri != null) {
                try {
                    contentResolver.openOutputStream(pendingExportUri!!)?.use { output ->
                        FileInputStream(zipFile).use { input ->
                            input.copyTo(output)
                        }
                    }
                    zipFile.delete()
                    Toast.makeText(this, getString(R.string.export_completed_toast), Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.export_failed_toast, e.message), Toast.LENGTH_LONG).show()
                } finally {
                    pendingExportUri = null
                }
            }
        }
    }
    
    private fun showProgressDialog(message: String) {
        if (progressDialog?.isShowing == true) {
            updateProgressText(message)
            return
        }
        
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null) // Assuming we create this layout
        progressIndicator = view.findViewById(R.id.progressBar)
        progressText = view.findViewById(R.id.progressText)
        progressText?.text = message
        progressIndicator?.isIndeterminate = true
        
        builder.setView(view)
        builder.setCancelable(false)
        progressDialog = builder.create()
        progressDialog?.show()
    }
    
    private fun updateProgressText(message: String) {
        progressText?.text = message
    }
    
    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressIndicator = null
        progressText = null
    }

    private fun showUpdateConfirmationDialog(isMobile: Boolean) {
        val title = getString(R.string.dialog_update_available_title)
        val msg = if (isMobile) {
            getString(R.string.dialog_update_available_mobile_message)
        } else {
            getString(R.string.dialog_update_available_wifi_message)
        }
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(getString(R.string.action_update)) { _, _ ->
                viewModel.confirmUpdate(useMobileData = true)
            }
            .setNegativeButton(getString(R.string.action_skip)) { _, _ ->
                viewModel.skipUpdate()
            }
            .setCancelable(false)
            .show()
    }

    private fun showLanguageSelectionDialog() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val languages = resources.getStringArray(R.array.languages)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_language_title))
            .setItems(languages) { _, which ->
                val selectedLang = languages[which]
                prefs.edit()
                    .putString("language", selectedLang)
                    .putBoolean("is_first_launch", false)
                    .apply()
                
                createLanguageFile(selectedLang)
                recreate()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedLang = prefs.getString("language", "English") ?: ""
        if (currentLanguage != savedLang) {
            recreate()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null || data.data == null) return
        
        val uri = data.data!!
        val savesDir = File(getExternalFilesDir(null), "saves")

        if (requestCode == 2001) {
            // Export
            pendingExportUri = uri
            viewModel.exportSaves(savesDir, cacheDir)
        } else if (requestCode == 2002) {
            // Import
            Thread { 
                try {
                    val tempZip = File.createTempFile("import_saves", ".zip", cacheDir)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempZip).use { output -> input.copyTo(output) }
                    }
                    runOnUiThread { 
                        viewModel.importSaves(tempZip, savesDir) 
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Import preparation failed", Toast.LENGTH_SHORT).show() }
                }
            }.start()
        }
    }

    private fun checkAndInstallLanguageScripts() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val selectedLang = prefs.getString("language", "English") ?: "English"
        val installedLang = prefs.getString("installed_language", null)
        val gameDir = File(filesDir, "game")

        if (selectedLang != installedLang || !gameDir.exists()) {
            showProgressDialog(getString(R.string.installing_language_data, selectedLang))
            
            Thread {
                try {
                    installLogic(selectedLang)
                    
                    prefs.edit().putString("installed_language", selectedLang).apply()
                    
                    runOnUiThread {
                        dismissProgressDialog()
                        createLanguageFile(selectedLang)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        dismissProgressDialog()
                        Toast.makeText(this, getString(R.string.install_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    private fun installLogic(language: String) {
        val zipName = when(language) {
            "Español" -> "es.zip"
            "Português" -> "pt.zip"
            else -> "en.zip"
        }
        val gameDir = File(filesDir, "game")
        
        if (gameDir.exists()) {
            gameDir.listFiles()?.forEach { 
                if (it.extension == "rpyc") it.delete() 
            }
        } else {
            gameDir.mkdirs()
        }

        val updateFile = File(filesDir, "LauncherUpdates/$zipName")
        val inputStream = if (updateFile.exists()) {
            FileInputStream(updateFile)
        } else {
            assets.open(zipName)
        }

        inputStream.use { stream ->
            ZipInputStream(stream).use { zip ->
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
        }
    }

    private fun createLanguageFile(language: String) {
        try {
            val gameDir = File(filesDir, "game")
            if (!gameDir.exists()) {
                gameDir.mkdirs()
            }

            gameDir.listFiles { file -> file.name.startsWith("language_") && file.name.endsWith(".txt") }
                ?.forEach { it.delete() }

            val langParam = when(language) {
                "Español" -> "spanish"
                "Português" -> "portuguese"
                else -> "english"
            }
            val langFile = File(gameDir, "language_$langParam.txt")
            langFile.createNewFile()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}