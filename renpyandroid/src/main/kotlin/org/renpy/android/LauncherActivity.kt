package org.renpy.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import android.view.animation.AccelerateDecelerateInterpolator
import android.app.ActivityManager


import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        
        initializeDesktopGrid(savedInstanceState == null)
        startSystemClockWorker()
        setupDynamicShortcuts(prefs.getBoolean("is_setup_completed", false))
        
        createNotificationChannel()

        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        
        handleShortcutIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
        }
    }

    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Update Notifications"
            val descriptionText = "Notifications for updates and features"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("updates_channel", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }
    
    private var returnFromWindow = false

    override fun onResume() {
        super.onResume()
        
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedLang = prefs.getString("language", "English") ?: ""
        if (currentLanguage != savedLang) {
            recreate()
            return
        }
        
        if (returnFromWindow) {
            returnFromWindow = false
            lifecycleScope.launch {
                delay(200)
                if (binding.startMenuPanel.visibility != View.VISIBLE) {
                    toggleStartMenu()
                }
            }
        }
    }

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.getStringExtra("shortcut_action")
        if (action == "start_game") {
            handleShortcutExecution(DesktopShortcut(R.string.launcher_start_game, android.R.drawable.ic_media_play, "start_game"))
        } else if (action == "export_persistent") {
            handleShortcutExecution(DesktopShortcut(R.string.launcher_export_button, R.drawable.ic_launcher_export, "export"))
        }
    }
    
    private fun setupDynamicShortcuts(isSetupCompleted: Boolean) {
        if (!isSetupCompleted) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(this)
            return
        }

        // Start Game Shortcut
        val startGameIntent = Intent(this, LauncherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("shortcut_action", "start_game")
        }
        val startGameShortcut = ShortcutInfoCompat.Builder(this, "shortcut_start_game")
            .setShortLabel(getString(R.string.launcher_start_game))
            .setIcon(IconCompat.createWithResource(this, android.R.drawable.ic_media_play))
            .setIntent(startGameIntent)
            .build()
            
        // Export Persistent Shortcut
        val exportIntent = Intent(this, LauncherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("shortcut_action", "export_persistent")
        }
        val exportShortcut = ShortcutInfoCompat.Builder(this, "shortcut_export")
            .setShortLabel(getString(R.string.launcher_export_button))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_launcher_export)) // uses our new modern SVG
            .setIntent(exportIntent)
            .build()
            
        ShortcutManagerCompat.pushDynamicShortcut(this, startGameShortcut)
        ShortcutManagerCompat.pushDynamicShortcut(this, exportShortcut)
    }
    
    private var isStartMenuExpanded = false

    private fun getPinnedItems(): List<DesktopShortcut> {
        return listOf(
            DesktopShortcut(R.string.launcher_start_game, android.R.drawable.ic_media_play, "start_game"),
            DesktopShortcut(R.string.label_internal_files, R.drawable.ic_launcher_internal, "internal_files"),
            DesktopShortcut(R.string.launcher_import_button, R.drawable.ic_launcher_import, "import"),
            DesktopShortcut(R.string.launcher_export_button, R.drawable.ic_launcher_export, "export"),
            DesktopShortcut(R.string.launcher_settings, R.drawable.ic_launcher_settings, "settings"),
            DesktopShortcut(R.string.launcher_all_programs, android.R.drawable.ic_menu_sort_by_size, "toggle_expand")
        )
    }

    private fun getExpandedItems(): List<DesktopShortcut> {
        return listOf(
            DesktopShortcut(R.string.launcher_browse_external, R.drawable.ic_launcher_external, "external_files"),
            DesktopShortcut(R.string.launcher_download_center, R.drawable.ic_launcher_download, "download_center"),
            DesktopShortcut(R.string.launcher_backups, R.drawable.ic_launcher_backup, "backups"),
            DesktopShortcut(R.string.title_app_info, android.R.drawable.ic_menu_info_details, "app_info")
        )
    }

    private fun updateStartMenuAdapter() {
        binding.desktopRecyclerView.adapter = DesktopItemAdapter(getPinnedItems()) { clickedItem ->
            handleShortcutExecution(clickedItem)
        }
        
        binding.expandedRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.expandedRecyclerView.adapter = DesktopItemAdapter(getExpandedItems()) { clickedItem ->
            handleShortcutExecution(clickedItem)
        }
        
        val layoutParams = binding.startMenuPanel.layoutParams
        layoutParams.height = resources.getDimensionPixelSize(R.dimen.start_menu_collapsed_height)
        binding.startMenuPanel.layoutParams = layoutParams
    }

    private fun initializeDesktopGrid(isFirstStart: Boolean) {
        binding.desktopRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.btnStartMenu.setOnClickListener {
            toggleStartMenu()
        }

        updateStartMenuAdapter()

        if (isFirstStart) {
            startBootSequence()
        } else {
            lifecycleScope.launch {
                delay(500)
                if (binding.startMenuPanel.visibility != View.VISIBLE) {
                    toggleStartMenu()
                }
            }
        }
    }

    private fun startBootSequence() {
        binding.bootScreenLayout.visibility = View.VISIBLE
        binding.startMenuPanel.visibility = View.GONE
        binding.txtBiosConsole.text = ""
        
        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val androidVersion = Build.VERSION.RELEASE
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
        
        lifecycleScope.launch {
            delay(800)
            
            appendBiosText("Traduction Club BIOS v0.1\n\n")
            delay(500)
            
            appendBiosText("Board: $manufacturer $model\n")
            appendBiosText("OS: Android $androidVersion\n")
            appendBiosText("Architecture: $arch\n")
            appendBiosText("Total RAM: ${totalRamMb}MB... OK\n\n")
            
            delay(800)
            appendBiosText("WAIT")
            for (i in 1..10) {
                delay(400)
                appendBiosText(".")
            }
            delay(500)
            
            binding.bootScreenLayout.animate()
                .alpha(0f)
                .setDuration(600)
                .withEndAction {
                    binding.bootScreenLayout.visibility = View.GONE
                    lifecycleScope.launch {
                        delay(200)
                        toggleStartMenu()
                    }
                }
                .start()
        }
    }

    private fun appendBiosText(text: String) {
        binding.txtBiosConsole.append(text)
    }

    private fun toggleStartMenu() {
        if (binding.startMenuPanel.visibility == View.VISIBLE) {
            // Hide expanded panel immediately
            binding.expandedProgramsPanel.visibility = View.GONE
            isStartMenuExpanded = false
            
            // Slide down to hide
            binding.startMenuPanel.animate()
                .translationY(binding.startMenuPanel.height.toFloat())
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setDuration(300)
                .withEndAction {
                    binding.startMenuPanel.visibility = View.GONE
                }
                .start()
        } else {
            // Slide up to show
            binding.startMenuPanel.visibility = View.INVISIBLE
            binding.startMenuPanel.post {
                val height = binding.startMenuPanel.height.toFloat()
                binding.startMenuPanel.translationY = height
                binding.startMenuPanel.visibility = View.VISIBLE
                binding.startMenuPanel.animate()
                    .translationY(0f)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .setDuration(300)
                    .start()
            }
        }
    }

    private fun handleShortcutExecution(shortcut: DesktopShortcut) {
        if (shortcut.actionId == "toggle_expand") {
            isStartMenuExpanded = !isStartMenuExpanded
            binding.expandedProgramsPanel.visibility = if (isStartMenuExpanded) View.VISIBLE else View.GONE
            return
        }
        
        when (shortcut.actionId) {
            "start_game" -> {
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
                            Toast.makeText(this@LauncherActivity, getString(R.string.install_error, e.message), Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
            "import" -> {
                GameDialogBuilder(this)
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
            "export" -> {
                GameDialogBuilder(this)
                    .setTitle(getString(R.string.launcher_export_title))
                    .setMessage(getString(R.string.launcher_export_message))
                    .setPositiveButton(getString(R.string.launcher_proceed)) { _, _ ->
                        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
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
            "internal_files" -> {
                returnFromWindow = true
                val intent = Intent(this, FileExplorerActivity::class.java)
                intent.putExtra("startPath", filesDir.absolutePath)
                startActivity(intent)
            }
            "settings" -> {
                returnFromWindow = true
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            "download_center" -> {
                returnFromWindow = true
                startActivity(Intent(this, DownloadCenterActivity::class.java))
            }
            "backups" -> {
                returnFromWindow = true
                startActivity(Intent(this, BackupsActivity::class.java))
            }
            "external_files" -> {
                returnFromWindow = true
                val externalPath = getExternalFilesDir(null)?.absolutePath
                if (externalPath != null) {
                    val intent = Intent(this, FileExplorerActivity::class.java)
                    intent.putExtra("startPath", externalPath)
                    startActivity(intent)
                }
            }
            "app_info" -> {
                returnFromWindow = true
                startActivity(Intent(this, AppInfoActivity::class.java))
            }
        }
    }

    private fun startSystemClockWorker() {
        lifecycleScope.launch {
            val updateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            while (true) {
                binding.txtSystemClock.text = updateFormat.format(Date())
                delay(60000)
            }
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
                }
                is LauncherViewModel.LaunchState.Error -> {
                    dismissProgressDialog()
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.consumeLaunchState()
                    startActivity(Intent(this, PythonSDLActivity::class.java))
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
        
        val builder = GameDialogBuilder(this)
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
        
        GameDialogBuilder(this)
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

        GameDialogBuilder(this)
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