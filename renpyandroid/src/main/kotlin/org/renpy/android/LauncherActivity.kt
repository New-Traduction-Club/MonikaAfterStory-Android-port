package org.renpy.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.os.SystemClock
import android.text.format.Formatter
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
import androidx.core.view.doOnPreDraw
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

    companion object {
        private const val STATE_BOOT_SEQUENCE_COMPLETED = "state_boot_sequence_completed"
        private const val STATE_DOWNLOAD_CENTER_CHECK_COMPLETED = "state_download_center_check_completed"
    }

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
    private var bootSequenceCompleted = false
    private var downloadCenterCheckCompleted = false
    
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
        bootSequenceCompleted = savedInstanceState?.getBoolean(STATE_BOOT_SEQUENCE_COMPLETED, false) ?: false
        downloadCenterCheckCompleted = savedInstanceState?.getBoolean(STATE_DOWNLOAD_CENTER_CHECK_COMPLETED, false) ?: false

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
        
        initializeDesktopGrid()
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_BOOT_SEQUENCE_COMPLETED, bootSequenceCompleted)
        outState.putBoolean(STATE_DOWNLOAD_CENTER_CHECK_COMPLETED, downloadCenterCheckCompleted)
        super.onSaveInstanceState(outState)
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

    private fun initializeDesktopGrid() {
        binding.desktopRecyclerView.layoutManager = LinearLayoutManager(this)

        updateStartMenuAdapter()

        if (!bootSequenceCompleted) {
            startBootSequence()
        } else {
            ensureStartMenuVisible()
            checkDownloadCenterUpdatesAfterBootIfNeeded()
        }
    }

    private fun startBootSequence() {
        bootSequenceCompleted = false
        binding.bootScreenLayout.alpha = 1f
        binding.bootScreenLayout.visibility = View.VISIBLE
        binding.startMenuPanel.visibility = View.GONE
        binding.txtBiosConsole.text = ""
        binding.txtBiosConsole.scrollTo(0, 0)
        
        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val androidVersion = Build.VERSION.RELEASE
        val kernelVersion = System.getProperty("os.version").orEmpty().ifBlank { "Unknown" }
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val (screenWidth, screenHeight) = resolveScreenResolution()
        val (totalStorageBytes, availableStorageBytes) = resolveInternalStorageStats()
        val totalStorage = Formatter.formatFileSize(this, totalStorageBytes)
        val availableStorage = Formatter.formatFileSize(this, availableStorageBytes)
        
        lifecycleScope.launch {
            delay(1_500)

            val consoleBuffer = StringBuilder()
            var cursorVisible = true

            fun renderBootConsole() {
                val output = if (cursorVisible) {
                    "${consoleBuffer}_"
                } else {
                    consoleBuffer.toString()
                }
                setBootConsoleText(output)
            }

            fun appendBootText(text: String) {
                consoleBuffer.append(text)
                renderBootConsole()
            }

            val cursorJob = launch {
                while (true) {
                    delay(280)
                    cursorVisible = !cursorVisible
                    renderBootConsole()
                }
            }

            val hexPhaseStart = SystemClock.elapsedRealtime()
            val hexDurationMs = Random.nextLong(2_400L, 5_200L)
            val hexLineIntervalMs = 100L

            appendBootText("HEX DUMP START\n")
            var offset = 0
            while (SystemClock.elapsedRealtime() - hexPhaseStart < hexDurationMs) {
                appendBootText("${generateHexDumpLine(offset)}\n")
                offset += 16
                delay(hexLineIntervalMs)
            }

            appendBootText("\nTraduction Club BIOS v0.2\n")
            appendBootText("Kernel: $kernelVersion\n")
            appendBootText("Board: $manufacturer $model\n")
            appendBootText("OS: Android $androidVersion\n")
            appendBootText("Architecture: $arch\n")
            appendBootText("CPU Cores: $cpuCores\n")
            appendBootText("Resolution: ${screenWidth}x${screenHeight}\n")
            appendBootText("Storage: $totalStorage total / $availableStorage free\n")
            appendBootText("Total RAM: ${totalRamMb}MB... OK\n\n")

            appendBootText("WAIT")
            val waitTargetEnd = hexPhaseStart + 8_000L
            val dotCount = 10
            repeat(dotCount) { index ->
                val dotsRemaining = dotCount - index
                val remainingTimeMs = (waitTargetEnd - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                val dotDelayMs = if (dotsRemaining > 0) remainingTimeMs / dotsRemaining else 0L
                delay(dotDelayMs)
                appendBootText(".")
            }

            cursorJob.cancel()
            cursorVisible = false
            setBootConsoleText(consoleBuffer.toString())
            delay(450)
            
            binding.bootScreenLayout.animate()
                .alpha(0f)
                .setDuration(600)
                .withEndAction {
                    bootSequenceCompleted = true
                    binding.bootScreenLayout.visibility = View.GONE
                    lifecycleScope.launch {
                        delay(1000)
                        showStartMenuAnimated()
                        checkDownloadCenterUpdatesAfterBootIfNeeded()
                    }
                }
                .start()
        }
    }

    private fun generateHexDumpLine(offset: Int, bytesPerLine: Int = 16): String {
        val values = IntArray(bytesPerLine) { Random.nextInt(0, 256) }
        val hexBytes = values.joinToString(" ") { String.format(Locale.US, "%02X", it) }
        val asciiPreview = values.joinToString(separator = "") { value ->
            if (value in 32..126) value.toChar().toString() else "."
        }
        return String.format(Locale.US, "%04X  %s  |%s|", offset, hexBytes, asciiPreview)
    }

    private fun resolveScreenResolution(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun resolveInternalStorageStats(): Pair<Long, Long> {
        val statFs = StatFs(filesDir.absolutePath)
        return statFs.totalBytes to statFs.availableBytes
    }

    private fun checkDownloadCenterUpdatesAfterBootIfNeeded() {
        if (downloadCenterCheckCompleted || !isUiInitialized) return
        downloadCenterCheckCompleted = true

        val prefs = getSharedPreferences(BaseActivity.PREFS_NAME, MODE_PRIVATE)
        val wifiOnly = prefs.getBoolean("wifi_only", false)
        if (!isNetworkConnected()) return
        if (wifiOnly && !isConnectedToWifi()) return

        lifecycleScope.launch {
            val updateManager = UpdateManager(this@LauncherActivity)
            val updates = updateManager.fetchUpdates(getString(R.string.manifest_url))
            val availableCount = updates.count { updateManager.isUpdateAvailable(it) }
            if (availableCount > 0 && !isFinishing && !isDestroyed) {
                showDownloadCenterUpdatePrompt(availableCount)
            }
        }
    }

    private fun showDownloadCenterUpdatePrompt(availableCount: Int) {
        val message = if (availableCount == 1) {
            getString(R.string.download_center_update_prompt_message_single)
        } else {
            getString(R.string.download_center_update_prompt_message_multiple, availableCount)
        }

        GameDialogBuilder(this)
            .setTitle(getString(R.string.download_center_update_prompt_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.launcher_download_center)) { _, _ ->
                returnFromWindow = true
                startActivity(Intent(this, DownloadCenterActivity::class.java))
            }
            .setNegativeButton(getString(R.string.import_conflict_ignore), null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    @Suppress("DEPRECATION")
    private fun isConnectedToWifi(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            val activeNetwork = connectivityManager.activeNetworkInfo
            activeNetwork?.isConnected == true && activeNetwork.type == ConnectivityManager.TYPE_WIFI
        }
    }

    private fun setBootConsoleText(text: String) {
        val console = binding.txtBiosConsole
        console.text = text
        console.doOnPreDraw {
            scrollBootConsoleIfNeeded()
        }
    }

    private fun scrollBootConsoleIfNeeded() {
        val console = binding.txtBiosConsole
        val layout = console.layout ?: return
        val lastLine = layout.lineCount - 1
        if (lastLine < 0) return

        val visibleHeight = console.height - console.paddingTop - console.paddingBottom
        if (visibleHeight <= 0) return

        val lastLineBottom = layout.getLineBottom(lastLine)
        val overflow = lastLineBottom - visibleHeight
        if (overflow > 0) {
            console.scrollTo(0, overflow)
        }
    }

    private fun ensureStartMenuVisible() {
        bootSequenceCompleted = true
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
