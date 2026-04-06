package org.renpy.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.WorkManager
import org.renpy.android.databinding.LauncherActivityBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.random.Random
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import android.app.ActivityManager


import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class LauncherActivity : BaseActivity() {

    // Fixed virtual DPI and font scale to keep the Taskbar consistent across all devices
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val language = prefs.getString("language", "English") ?: "English"
        val locale = when (language) {
            "Español" -> Locale("es")
            "Português" -> Locale("pt")
            else -> Locale.ENGLISH
        }
        Locale.setDefault(locale)

        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val localeContext = newBase.createConfigurationContext(config)

        val metrics = localeContext.resources.displayMetrics
        val virtualHeight = 500f
        val rawHeight = Math.min(metrics.widthPixels, metrics.heightPixels)
        val targetDensity = rawHeight / virtualHeight
        val targetDensityDpi = (targetDensity * DisplayMetrics.DENSITY_DEFAULT).toInt()

        val dpiConfig = Configuration(localeContext.resources.configuration)
        dpiConfig.densityDpi = targetDensityDpi
        dpiConfig.fontScale = 1.0f

        val finalContext = localeContext.createConfigurationContext(dpiConfig)
        super.attachBaseContext(finalContext)
    }

    private lateinit var binding: LauncherActivityBinding
    private val viewModel: LauncherViewModel by viewModels()
    private var currentLanguage: String = ""
    private var isUiInitialized = false
    
    private var progressDialog: AlertDialog? = null
    private var progressIndicator: android.widget.ProgressBar? = null
    private var progressText: android.widget.TextView? = null
    
    private var pendingExportUri: Uri? = null
    private var wallpaperRotationJob: Job? = null

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

        WorkManager.getInstance(applicationContext).cancelAllWorkByTag(NotificationWorker.WORK_TAG)
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
        setupEdgeToEdgeInsets()
        isUiInitialized = true

        SoundEffects.initialize(this)
        
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

    private fun setupEdgeToEdgeInsets() {
        val initialLeft = binding.root.paddingLeft
        val initialTop = binding.root.paddingTop
        val initialRight = binding.root.paddingRight
        val initialBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft,
                initialTop,
                initialRight,
                initialBottom + insets.bottom
            )
            windowInsets
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }
    
    private var returnFromWindow = false

    override fun onResume() {
        super.onResume()
        if (!isUiInitialized) return
        
        WallpaperManager.advanceOnAppToggle(this)
        WallpaperManager.maybeAdvanceByTime(this)
        WallpaperManager.applyWallpaper(this, binding.root)
        startWallpaperRotation()
        
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedLang = prefs.getString("language", "English") ?: ""
        if (currentLanguage != savedLang) {
            recreate()
            return
        }

        SoundEffects.initialize(this)
        
        if (returnFromWindow) {
            returnFromWindow = false
            lifecycleScope.launch {
                delay(200)
                ensureStartMenuVisible()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isUiInitialized) return
        stopWallpaperRotation()
        WallpaperManager.advanceOnAppToggle(this)
    }

    override fun onDestroy() {
        if (isUiInitialized) {
            WallpaperManager.clearVideoWallpaper(binding.root)
        }
        super.onDestroy()
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
            DesktopShortcut(R.string.launcher_wallpapers, R.drawable.ic_launcher_wallpaper, "wallpapers"),
            DesktopShortcut(R.string.title_app_info, android.R.drawable.ic_menu_info_details, "app_info")
        )
    }

    private fun updateStartMenuAdapter() {
        binding.desktopRecyclerView.adapter = DesktopItemAdapter(getPinnedItems()) { clickedItem ->
            SoundEffects.playClick(this)
            handleShortcutExecution(clickedItem)
        }
        
        binding.expandedRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.expandedRecyclerView.adapter = DesktopItemAdapter(getExpandedItems()) { clickedItem ->
            SoundEffects.playClick(this)
            handleShortcutExecution(clickedItem)
        }
    }

    private fun initializeDesktopGrid(isFirstStart: Boolean) {
        binding.desktopRecyclerView.layoutManager = LinearLayoutManager(this)

        updateStartMenuAdapter()

        if (isFirstStart) {
            startBootSequence()
        } else {
            ensureStartMenuVisible()
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
                        delay(1000)
                        showStartMenuAnimated()
                    }
                }
                .start()
        }
    }

    private fun appendBiosText(text: String) {
        binding.txtBiosConsole.append(text)
    }

    private fun ensureStartMenuVisible() {
        binding.startMenuPanel.visibility = View.VISIBLE
        binding.startMenuPanel.translationY = 0f
        binding.startMenuPanel.bringToFront()

        if (isStartMenuExpanded) {
            binding.expandedProgramsPanel.visibility = View.VISIBLE
            binding.expandedProgramsPanel.translationX = 0f
        } else {
            binding.expandedProgramsPanel.visibility = View.GONE
            binding.expandedProgramsPanel.translationX = 0f
        }
    }

    private fun showStartMenuAnimated() {
        binding.expandedProgramsPanel.clearAnimation()
        binding.expandedProgramsPanel.visibility = View.GONE
        binding.expandedProgramsPanel.translationX = 0f
        binding.startMenuPanel.clearAnimation()
        binding.startMenuPanel.visibility = View.INVISIBLE
        binding.startMenuPanel.translationY = 0f
        binding.startMenuPanel.alpha = 1f

        binding.startMenuPanel.post {
            val startHeight = binding.startMenuPanel.height.toFloat()
            binding.startMenuPanel.translationY = startHeight
            binding.startMenuPanel.visibility = View.VISIBLE
            binding.startMenuPanel.animate()
                .translationY(0f)
                .setDuration(520)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun showExpandedMenuAnimated() {
        val panel = binding.expandedProgramsPanel
        panel.clearAnimation()
        panel.visibility = View.INVISIBLE
        panel.alpha = 1f
        panel.post {
            val width = panel.width.takeIf { it > 0 }?.toFloat() ?: binding.startMenuPanel.width.toFloat()
            panel.translationX = -width
            binding.startMenuPanel.bringToFront()
            panel.visibility = View.VISIBLE
            panel.animate()
                .translationX(0f)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun hideExpandedMenuAnimated() {
        val panel = binding.expandedProgramsPanel
        if (panel.visibility != View.VISIBLE) {
            panel.translationX = 0f
            panel.visibility = View.GONE
            return
        }

        panel.clearAnimation()
        val width = panel.width.takeIf { it > 0 }?.toFloat() ?: binding.startMenuPanel.width.toFloat()
        panel.animate()
            .translationX(-width)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                panel.visibility = View.GONE
                panel.translationX = 0f
            }
            .start()
    }

    private fun handleShortcutExecution(shortcut: DesktopShortcut) {
        if (shortcut.actionId == "toggle_expand") {
            isStartMenuExpanded = !isStartMenuExpanded
            if (isStartMenuExpanded) {
                showExpandedMenuAnimated()
            } else {
                hideExpandedMenuAnimated()
            }
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
                            InAppNotifier.show(this@LauncherActivity, getString(R.string.install_error, e.message), true)
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
            "wallpapers" -> {
                returnFromWindow = true
                startActivity(Intent(this, WallpapersActivity::class.java))
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

    private fun startWallpaperRotation() {
        wallpaperRotationJob?.cancel()

        val config = WallpaperManager.getSlideshowConfig(this)
        val intervalMinutes = config.intervalMinutes
        if (!config.enabled || intervalMinutes == null || intervalMinutes <= 0) return

        val intervalMs = TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
        wallpaperRotationJob = lifecycleScope.launch {
            while (true) {
                delay(intervalMs)
                val changed = WallpaperManager.advanceWallpaper(this@LauncherActivity) != null
                if (changed) {
                    WallpaperManager.applyWallpaper(this@LauncherActivity, binding.root)
                }
            }
        }
    }

    private fun stopWallpaperRotation() {
        wallpaperRotationJob?.cancel()
        wallpaperRotationJob = null
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
                    InAppNotifier.show(this, state.message, true)
                    viewModel.consumeLaunchState()
                    startActivity(Intent(this, PythonSDLActivity::class.java))
                }
            }
        }
        
        viewModel.operationStatus.observe(this) { msg ->
            InAppNotifier.show(this, msg)
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
                    InAppNotifier.show(this, getString(R.string.export_completed_toast), true)
                } catch (e: Exception) {
                    InAppNotifier.show(this, getString(R.string.export_failed_toast, e.message), true)
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
                    runOnUiThread { InAppNotifier.show(this, "Import preparation failed") }
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
                        InAppNotifier.show(this, getString(R.string.install_error, e.message), true)
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
