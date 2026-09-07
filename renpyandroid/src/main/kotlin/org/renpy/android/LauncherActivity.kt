package org.renpy.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.LinearLayout
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
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.random.Random
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import android.app.ActivityManager
import android.animation.ValueAnimator
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.annotation.SuppressLint
import android.view.animation.LinearInterpolator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable


import android.graphics.RectF
import android.view.MotionEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class LauncherActivity : BaseActivity() {

    companion object {
        private const val STATE_BOOT_SEQUENCE_COMPLETED = "state_boot_sequence_completed"
        private const val REQUEST_CODE_EXPORT_SAVES = 2001
        private const val REQUEST_CODE_IMPORT_SAVES = 2002
        private const val MAX_EXPANDED_ITEMS_PER_COLUMN = 6
        private const val EXPANDED_MENU_COLUMN_WIDTH_DP = 240
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
    
    private var progressDialog: AlertDialog? = null
    private var progressIndicator: android.widget.ProgressBar? = null
    private var progressText: android.widget.TextView? = null
    
    private var pendingExportUri: Uri? = null
    private var wallpaperRotationJob: Job? = null

    private var selectionStartX = 0f
    private var selectionStartY = 0f

    private val runningApps = mutableMapOf<String, RunningAppInfo>()
    private var lastFocusedAppId: String? = null
    private var renpyMonitorJob: kotlinx.coroutines.Job? = null

    data class RunningAppInfo(
        val id: String,
        val name: String,
        var state: String
    )

    private var notificationAdapter: NotificationAdapter? = null

    private val desktopNotificationReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "org.renpy.android.ACTION_NEW_DESKTOP_NOTIFICATION") {
                val title = intent.getStringExtra("title") ?: "Monika"
                val message = intent.getStringExtra("message") ?: ""
                val imagePath = intent.getStringExtra("image_path")

                val notification = NotificationHistoryManager.addNotification(context, title, message, imagePath)

                updateNotificationBadge()
                notificationAdapter?.updateItems(NotificationHistoryManager.getNotifications(context))

                showNotificationToast(title, message, imagePath)
            }
        }
    }

    private val windowStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            if (intent.action == DesktopWindowManager.ACTION_WINDOW_STATE_CHANGED) {
                val id = intent.getStringExtra(DesktopWindowManager.EXTRA_ACTIVITY_ID) ?: return
                val name = intent.getStringExtra(DesktopWindowManager.EXTRA_ACTIVITY_NAME) ?: "App"
                val state = intent.getStringExtra(DesktopWindowManager.EXTRA_STATE) ?: return

                if (state == "DESTROYED") {
                    runningApps.remove(id)
                    if (lastFocusedAppId == id) {
                        lastFocusedAppId = runningApps.keys.lastOrNull { runningApps[it]?.state == "RUNNING" }
                    }
                } else {
                    runningApps[id] = RunningAppInfo(id, name, state)
                    if (state == "RUNNING") {
                        lastFocusedAppId = id
                    } else if (lastFocusedAppId == id) {
                        lastFocusedAppId = runningApps.keys.lastOrNull { runningApps[it]?.state == "RUNNING" }
                    }
                    if (id.startsWith("org.renpy.android.PythonSDLActivity")) {
                        startRenpyProcessMonitoring(id)
                    }
                }
                updateTaskbarApps()
            }
        }
    }

    private val renpyMonitorJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    private fun startRenpyProcessMonitoring(activityId: String) {
        if (renpyMonitorJobs[activityId]?.isActive == true) return
        val suffix = when (activityId) {
            "org.renpy.android.PythonSDLActivity" -> "renpy"
            "org.renpy.android.PythonSDLActivity2" -> "renpy2"
            "org.renpy.android.PythonSDLActivity3" -> "renpy3"
            else -> "renpy"
        }
        val renpyProcessName = "$packageName:$suffix"
        renpyMonitorJobs[activityId] = lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (!isProcessRunning(renpyProcessName)) {
                    if (runningApps.containsKey(activityId)) {
                        runningApps.remove(activityId)
                        if (lastFocusedAppId == activityId) {
                            lastFocusedAppId = runningApps.keys.lastOrNull { runningApps[it]?.state == "RUNNING" }
                        }
                        updateTaskbarApps()
                    }
                    break
                }
            }
        }
    }

    private fun isProcessRunning(processName: String): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        val runningProcesses = manager.runningAppProcesses ?: return false
        for (processInfo in runningProcesses) {
            if (processInfo.processName == processName) {
                return true
            }
        }
        return false
    }

    private fun bringRunningActivitiesToFront() {
        val processesToCheck = listOf(
            "org.renpy.android.PythonSDLActivity" to "renpy",
            "org.renpy.android.PythonSDLActivity2" to "renpy2",
            "org.renpy.android.PythonSDLActivity3" to "renpy3"
        )
        var anyRemoved = false
        for ((actId, suffix) in processesToCheck) {
            val processName = "$packageName:$suffix"
            if (runningApps.containsKey(actId) && !isProcessRunning(processName)) {
                runningApps.remove(actId)
                if (lastFocusedAppId == actId) {
                    lastFocusedAppId = runningApps.keys.lastOrNull { runningApps[it]?.state == "RUNNING" }
                }
                anyRemoved = true
            }
        }
        if (anyRemoved) {
            updateTaskbarApps()
        }

        val runningIds = runningApps.values
            .filter { it.state == "RUNNING" }
            .map { it.id }

        if (runningIds.isEmpty()) return

        for (id in runningIds) {
            if (id != lastFocusedAppId) {
                if (id.startsWith("org.renpy.android.PythonSDLActivity") || ActiveActivityRegistry.activeActivities.contains(id)) {
                    bringToFront(id)
                }
            }
        }

        lastFocusedAppId?.let { id ->
            if (runningIds.contains(id)) {
                if (id.startsWith("org.renpy.android.PythonSDLActivity") || ActiveActivityRegistry.activeActivities.contains(id)) {
                    bringToFront(id)
                }
            }
        }
    }

    private fun bringToFront(id: String) {
        try {
            val activityClass = Class.forName(id)
            val intent = Intent(this, activityClass).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Failed to bring app to front: $id", e)
        }
    }

    private fun launchActivityWindow(intent: Intent, classId: String) {
        returnFromWindow = true
        lastFocusedAppId = classId
        bringRunningActivitiesToFront()
        startActivity(intent)
    }

    private fun updateTaskbarApps() {
        binding.runningAppsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val paddingHorizontal = (12 * density).toInt()
        val paddingVertical = (4 * density).toInt()

        for (app in runningApps.values) {
            val appButton = TextView(this).apply {
                text = app.name
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                
                if (app.state == "MINIMIZED") {
                    setTextColor(androidx.core.content.ContextCompat.getColor(this@LauncherActivity, R.color.colorTaskbarTint))
                    setBackgroundResource(R.drawable.bg_taskbar)
                    alpha = 0.5f
                } else {
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_taskbar_button_press)
                    alpha = 1.0f
                }

                setOnClickListener {
                    SoundEffects.playClick(this@LauncherActivity)
                    if (app.state == "MINIMIZED") {
                        lastFocusedAppId = app.id
                        DesktopWindowManager.sendCommand(this@LauncherActivity, app.id, "RESTORE")
                    } else {
                        DesktopWindowManager.sendCommand(this@LauncherActivity, app.id, "MINIMIZE")
                    }
                }

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginStart = (4 * density).toInt()
                    marginEnd = (4 * density).toInt()
                }
                layoutParams = params
            }
            binding.runningAppsContainer.addView(appButton)
        }
    }

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

        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        val setupConfirmed = prefs.getBoolean("setup_language_confirmed", false)
        
        if (isFirstLaunch && !setupConfirmed) {
            showLanguageSelectionDialog()
        }

        createLanguageFile(currentLanguage)

        binding = LauncherActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets()
        isUiInitialized = true

        (applicationContext as android.app.Application).registerActivityLifecycleCallbacks(
            object : android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: android.app.Activity) {}
                override fun onActivityResumed(activity: android.app.Activity) {
                    ActiveActivityRegistry.currentActivity = activity
                }
                override fun onActivityPaused(activity: android.app.Activity) {
                    if (ActiveActivityRegistry.currentActivity === activity) {
                        ActiveActivityRegistry.currentActivity = null
                    }
                }
                override fun onActivityStopped(activity: android.app.Activity) {}
                override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: android.app.Activity) {}
            }
        )

        SoundEffects.initialize(this)
        
        setupObservers()
        
        initializeDesktopGrid()
        startSystemClockWorker()
        setupDynamicShortcuts(prefs.getBoolean("is_setup_completed", false))
        setupDesktopSelection()
        
        startBootCrtAnimations()
        
        createNotificationChannel()

        // Register window state broadcast receiver
        val filter = android.content.IntentFilter(DesktopWindowManager.ACTION_WINDOW_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(windowStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(windowStateReceiver, filter)
        }

        val notifFilter = android.content.IntentFilter("org.renpy.android.ACTION_NEW_DESKTOP_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(desktopNotificationReceiver, notifFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(desktopNotificationReceiver, notifFilter)
        }

        val rvLayoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvNotifications.layoutManager = rvLayoutManager
        notificationAdapter = NotificationAdapter(emptyList()) { item ->
            NotificationHistoryManager.deleteNotification(this, item.id)
            notificationAdapter?.updateItems(NotificationHistoryManager.getNotifications(this))
            updateNotificationBadge()
        }
        binding.rvNotifications.adapter = notificationAdapter

        binding.btnClearAllNotifications.setOnClickListener {
            SoundEffects.playClick(this)
            NotificationHistoryManager.clearAll(this)
            notificationAdapter?.updateItems(emptyList())
            updateNotificationBadge()
        }

        binding.btnNotificationCenter.setOnClickListener {
            SoundEffects.playClick(this)
            toggleNotificationCenter()
        }

        updateNotificationBadge()

        binding.btnStartMenu.setOnClickListener {
            SoundEffects.playClick(this)
            isStartMenuExpanded = false
            hideExpandedMenuAnimated()
            if (binding.startMenuPanel.visibility == View.VISIBLE) {
                binding.startMenuPanel.animate()
                    .translationY(binding.startMenuPanel.height.toFloat())
                    .setDuration(220)
                    .withEndAction { binding.startMenuPanel.visibility = View.GONE }
                    .start()
            } else {
                showStartMenuAnimated()
            }
        }

        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        
        handleShortcutIntent(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDesktopSelection() {
        binding.root.setOnTouchListener { _, event ->
            if (!bootSequenceCompleted) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    selectionStartX = event.x
                    selectionStartY = event.y
                    binding.desktopSelectionView.updateSelection(null)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val rect = RectF(
                        Math.min(selectionStartX, event.x),
                        Math.min(selectionStartY, event.y),
                        Math.max(selectionStartX, event.x),
                        Math.max(selectionStartY, event.y)
                    )
                    binding.desktopSelectionView.updateSelection(rect)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.desktopSelectionView.updateSelection(null)
                    true
                }
                else -> false
            }
        }
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
        if (isChromeOsDevice() || !window.decorView.isAttachedToWindow) return
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

        lifecycleScope.launch {
            delay(150)
            bringRunningActivitiesToFront()
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
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isUiInitialized) {
            WallpaperManager.clearVideoWallpaper(binding.root)
        }
        try {
            unregisterReceiver(windowStateReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
        try {
            unregisterReceiver(desktopNotificationReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
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
            DesktopShortcut(R.string.launcher_update_game, R.drawable.ic_launcher_export, "update_game"),
            // TODO: maybe edit this to support JY submods
            // DesktopShortcut(R.string.launcher_add_extra_content, android.R.drawable.ic_input_add, "extra_content"),
            // DesktopShortcut(R.string.launcher_discord_rpc, android.R.drawable.stat_notify_chat, "discord_rpc"),
            DesktopShortcut(R.string.launcher_backups, R.drawable.ic_launcher_backup, "backups"),
            DesktopShortcut(R.string.launcher_wallpapers, R.drawable.ic_launcher_wallpaper, "wallpapers"),
            DesktopShortcut(R.string.title_app_info, android.R.drawable.ic_menu_info_details, "app_info"),
            DesktopShortcut(R.string.title_experiments, android.R.drawable.ic_menu_compass, "experiments")
        )
    }

    private fun updateStartMenuAdapter() {
        binding.desktopRecyclerView.adapter = DesktopItemAdapter(getPinnedItems()) { clickedItem ->
            SoundEffects.playClick(this)
            handleShortcutExecution(clickedItem)
        }

        val expandedItems = getExpandedItems()
        val columnWidthPx = dpToPx(EXPANDED_MENU_COLUMN_WIDTH_DP)
        binding.expandedRecyclerView.layoutManager = GridLayoutManager(
            this,
            MAX_EXPANDED_ITEMS_PER_COLUMN,
            GridLayoutManager.HORIZONTAL,
            false
        )
        binding.expandedRecyclerView.adapter = DesktopItemAdapter(
            expandedItems,
            itemWidthPx = columnWidthPx
        ) { clickedItem ->
                SoundEffects.playClick(this)
                handleShortcutExecution(clickedItem)
            }
        updateExpandedPanelWidth(expandedItems.size, columnWidthPx)
    }

    private fun updateExpandedPanelWidth(itemsCount: Int, columnWidthPx: Int) {
        val columns = ((itemsCount + MAX_EXPANDED_ITEMS_PER_COLUMN - 1) / MAX_EXPANDED_ITEMS_PER_COLUMN)
            .coerceAtLeast(1)
        binding.expandedProgramsPanel.layoutParams = binding.expandedProgramsPanel.layoutParams.apply {
            width = columns * columnWidthPx
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun initializeDesktopGrid() {
        binding.desktopRecyclerView.layoutManager = LinearLayoutManager(this)

        updateStartMenuAdapter()

        if (!bootSequenceCompleted) {
            startBootSequence()
        } else {
            ensureStartMenuVisible()
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

            appendBootText("\nTraduction Club BIOS v0.3\n")
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
            val slideDistance = binding.startMenuPanel.width
                .takeIf { it > 0 }
                ?.toFloat()
                ?: dpToPx(EXPANDED_MENU_COLUMN_WIDTH_DP).toFloat()
            panel.translationX = -slideDistance
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
        val slideDistance = binding.startMenuPanel.width
            .takeIf { it > 0 }
            ?.toFloat()
            ?: dpToPx(EXPANDED_MENU_COLUMN_WIDTH_DP).toFloat()
        panel.animate()
            .translationX(-slideDistance)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                panel.visibility = View.GONE
                panel.translationX = 0f
            }
            .start()
    }

    private fun openDiscordRpcWindow() {
        val prefs = getSharedPreferences(BaseActivity.PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(DiscordRpcManager.PREF_DISCORD_RPC_WARNING_ACCEPTED, false)) {
            showDiscordRpcWarningDialog(prefs)
            return
        }
        val intent = Intent(this, DiscordRpcActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        launchActivityWindow(intent, DiscordRpcActivity::class.java.name)
    }

    private fun showDiscordRpcWarningDialog(prefs: SharedPreferences) {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.discord_rpc_warning_title))
            .setMessage(getString(R.string.discord_rpc_warning_message))
            .setPositiveButton(getString(R.string.launcher_proceed)) { _, _ ->
                prefs.edit()
                    .putBoolean(DiscordRpcManager.PREF_DISCORD_RPC_WARNING_ACCEPTED, true)
                    .apply()
                openDiscordRpcWindow()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                InAppNotifier.show(this, getString(R.string.discord_rpc_warning_denied), true)
            }
            .show()
    }

    private fun handleUpdateGame() {
        lifecycleScope.launch {
            val packages = withContext(Dispatchers.IO) {
                GameUpdateManager.fetchPackages()
            }

            if (packages.isEmpty()) {
                InAppNotifier.show(this@LauncherActivity, getString(R.string.update_error_fetch_failed))
                return@launch
            }

            val versions = packages.map { it.version }.toTypedArray()
            GameDialogBuilder(this@LauncherActivity)
                .setTitle(getString(R.string.update_select_version_title))
                .setItems(versions) { _, which ->
                    val selected = packages[which]
                    val intent = Intent(this@LauncherActivity, UpdateWindowActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("package_info", selected)
                    }
                    launchActivityWindow(intent, UpdateWindowActivity::class.java.name)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun checkLanguageAndStartGame() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val language = prefs.getString("language", "English") ?: "English"
        val skipWarning = prefs.getBoolean("skip_language_warning", false)

        if (language == "English" || skipWarning) {
            viewModel.handlePlayClick()
            return
        }

        val titleText = when (language) {
            "Español" -> "Aviso de idioma"
            "Português" -> "Aviso de idioma"
            else -> "Language Warning"
        }
        
        val messageText = when (language) {
            "Español" -> "Aviso: El soporte para español en MAS se limita a lo básico, si encuentras contenido en inglés, favor de no reportarlo. Usar la versión de MASL de la Play Store si tu prioridad es la traducción y no el uso de Ren'Py 6.99 de esta edición de MASL.\n\nPodrás cambiar el idioma dentro del juego en el apartado de Ajustes."
            "Português" -> "Aviso: O MASL 6.99 não possui suporte nativo para português na sua versão do MAS. Por favor, considere mudar para o MASL da Play Store ou instalar a tradução em português do MAS Brasil usando a função Experimentos."
            else -> ""
        }
        
        val checkBoxText = when (language) {
            "Español" -> "No volver a mostrar"
            "Português" -> "Não mostrar novamente"
            else -> "Don't show again"
        }
        
        val okButtonText = when (language) {
            "Español" -> "Vale"
            "Português" -> "Entendido"
            else -> "OK"
        }

        val scrollContainer = android.widget.ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val textView = TextView(this).apply {
            text = messageText
            setTextColor(androidx.core.content.ContextCompat.getColor(this@LauncherActivity, R.color.colorTextPrimary))
            textSize = 14f
        }
        container.addView(textView)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(12)
            )
        }
        container.addView(spacer)

        val checkBox = android.widget.CheckBox(this).apply {
            text = checkBoxText
            setTextColor(androidx.core.content.ContextCompat.getColor(this@LauncherActivity, R.color.colorTextPrimary))
            textSize = 14f
            val tintColor = androidx.core.content.ContextCompat.getColor(this@LauncherActivity, R.color.colorPrimary)
            androidx.core.widget.CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(tintColor))
        }
        container.addView(checkBox)

        scrollContainer.addView(container)

        GameDialogBuilder(this)
            .setTitle(titleText)
            .setView(scrollContainer)
            .setPositiveButton(okButtonText) { _, _ ->
                if (checkBox.isChecked) {
                    prefs.edit().putBoolean("skip_language_warning", true).apply()
                }
                viewModel.handlePlayClick()
            }
            .setCancelable(false)
            .show()
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
                checkLanguageAndStartGame()
            }
            "update_game" -> {
                handleUpdateGame()
            }
            "import" -> {
                GameDialogBuilder(this)
                    .setTitle(getString(R.string.launcher_import_title))
                    .setMessage(getString(R.string.launcher_import_message))
                    .setPositiveButton(getString(R.string.launcher_proceed)) { _, _ ->
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        intent.addCategory(Intent.CATEGORY_OPENABLE)
                        intent.type = "application/zip"
                        startActivityForResult(intent, REQUEST_CODE_IMPORT_SAVES)
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
                        startActivityForResult(intent, REQUEST_CODE_EXPORT_SAVES)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            "internal_files" -> {
                val intent = Intent(this, FileExplorerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra("startPath", filesDir.absolutePath)
                }
                launchActivityWindow(intent, FileExplorerActivity::class.java.name)
            }
            "settings" -> {
                val intent = Intent(this, SettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                launchActivityWindow(intent, SettingsActivity::class.java.name)
            }
            "extra_content" -> {
                val intent = Intent(this, ExtraContentActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                launchActivityWindow(intent, ExtraContentActivity::class.java.name)
            }
            "discord_rpc" -> {
                openDiscordRpcWindow()
            }
            "backups" -> {
                val intent = Intent(this, BackupsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                launchActivityWindow(intent, BackupsActivity::class.java.name)
            }
            "wallpapers" -> {
                val intent = Intent(this, WallpapersActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                launchActivityWindow(intent, WallpapersActivity::class.java.name)
            }
            "external_files" -> {
                val externalPath = getExternalFilesDir(null)?.absolutePath
                if (externalPath != null) {
                    val intent = Intent(this, FileExplorerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("startPath", externalPath)
                    }
                    launchActivityWindow(intent, FileExplorerActivity::class.java.name)
                }
            }
            "app_info" -> {
                val intent = Intent(this, AppInfoActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                launchActivityWindow(intent, AppInfoActivity::class.java.name)
            }
            "experiments" -> {
                val intent = Intent(this, ExperimentsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                launchActivityWindow(intent, ExperimentsActivity::class.java.name)
            }
        }
    }

    private fun startBootCrtAnimations() {
        val scanlineBitmap = Bitmap.createBitmap(1, 2, Bitmap.Config.ARGB_8888)
        scanlineBitmap.setPixel(0, 0, Color.TRANSPARENT)
        scanlineBitmap.setPixel(0, 1, Color.argb(45, 0, 0, 0))
        
        val scanlineDrawable = BitmapDrawable(resources, scanlineBitmap)
        scanlineDrawable.tileModeY = Shader.TileMode.REPEAT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.crtOverlay.foreground = scanlineDrawable
        } else {}

        val rollingLine = binding.bootRollingLine
        rollingLine.post {
            val parentHeight = binding.bootScreenLayout.height.toFloat()
            val lineAnimator = ValueAnimator.ofFloat(-200f, parentHeight + 200f)
            lineAnimator.duration = 4000
            lineAnimator.repeatCount = ValueAnimator.INFINITE
            lineAnimator.interpolator = LinearInterpolator()
            lineAnimator.addUpdateListener { animator ->
                rollingLine.translationY = animator.animatedValue as Float
            }
            lineAnimator.start()
        }

        val overlay = binding.crtOverlay
        val flickerAnimator = ValueAnimator.ofFloat(0.6f, 0.8f)
        flickerAnimator.duration = 60
        flickerAnimator.repeatCount = ValueAnimator.INFINITE
        flickerAnimator.repeatMode = ValueAnimator.REVERSE
        flickerAnimator.addUpdateListener { animator ->
            overlay.alpha = animator.animatedValue as Float
        }
        flickerAnimator.start()
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
                is LauncherViewModel.LaunchState.LaunchGame -> {
                    dismissProgressDialog()
                    viewModel.consumeLaunchState()
                    launchPythonActivityWithSanitizedPackages()
                }
                is LauncherViewModel.LaunchState.Error -> {
                    dismissProgressDialog()
                    InAppNotifier.show(this, state.message, true)
                    viewModel.consumeLaunchState()
                    launchPythonActivityWithSanitizedPackages()
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
    
    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressIndicator = null
        progressText = null
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

        if (requestCode == REQUEST_CODE_EXPORT_SAVES) {
            // Export
            pendingExportUri = uri
            viewModel.exportSaves(savesDir, cacheDir)
        } else if (requestCode == REQUEST_CODE_IMPORT_SAVES) {
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

    private fun removeUtf8CodingDeclarationsInPythonPackages() {
        val pythonPackagesDir = File(filesDir, "monikaafterstory-masl-edition/game/python-packages")
        if (!pythonPackagesDir.isDirectory) return

        pythonPackagesDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("py", ignoreCase = true) }
            .forEach { removeUtf8CodingDeclarationLine(it) }
    }

    private fun removeUtf8CodingDeclarationLine(file: File) {
        val originalText = file.readText(Charsets.UTF_8)
        val lineSeparator = if (originalText.contains("\r\n")) "\r\n" else "\n"
        val hadTrailingLineBreak = originalText.endsWith("\n") || originalText.endsWith("\r\n")
        val originalLines = originalText.lineSequence().toList()
        val sanitizedLines = originalLines.filterNot { isUtf8CodingDeclaration(it) }

        if (sanitizedLines.size == originalLines.size) return

        val sanitizedText = buildString {
            append(sanitizedLines.joinToString(lineSeparator))
            if (hadTrailingLineBreak && sanitizedLines.isNotEmpty()) {
                append(lineSeparator)
            }
        }

        file.writeText(sanitizedText, Charsets.UTF_8)
    }

    private fun isUtf8CodingDeclaration(line: String): Boolean {
        val normalized = line
            .removePrefix("\uFEFF")
            .trimStart()
            .lowercase(Locale.US)
        if (!normalized.startsWith("#")) return false
        if (!normalized.contains("coding")) return false
        if (!normalized.contains("utf8") && !normalized.contains("utf-8") && !normalized.contains("utf_8")) {
            return false
        }
        return normalized.contains("coding:") || normalized.contains("coding=")
    }

    private fun launchPythonActivityWithSanitizedPackages() {
        Thread {
            var sanitizeError: IOException? = null
            try {
                // ensureAndroidMasbaseBootstrapScript()
                removeUtf8CodingDeclarationsInPythonPackages()
                ensureCaCertBundle()
            } catch (e: IOException) {
                sanitizeError = e
            }

            runOnUiThread {
                sanitizeError?.let { error ->
                    InAppNotifier.show(this@LauncherActivity, getString(R.string.install_error, error.message), true)
                }
                val intent = Intent(this@LauncherActivity, PythonSDLActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra("base_dir", "monikaafterstory-masl-edition")
                }
                launchActivityWindow(intent, PythonSDLActivity::class.java.name)
            }
        }.start()
    }

    private fun ensureCaCertBundle() {
        try {
            val certFile = File(filesDir, "monikaafterstory-masl-edition/game/python-packages/certifi/cacert.pem")
            certFile.parentFile?.mkdirs()
            
            assets.open("cacert.pem").use { input ->
                FileOutputStream(certFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherActivity", "Failed to copy cacert.pem: ${e.message}")
        }
    }

    @Throws(IOException::class)
    private fun ensureAndroidMasbaseBootstrapScript() {
        val installDir = File(filesDir, "monikaafterstory-masl-edition")
        val gameDir = File(installDir, "game")
        if (!gameDir.exists() && !gameDir.mkdirs()) {
            throw IOException("Unable to create game directory")
        }

        val escapedBasePath = installDir.absolutePath
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val bootstrapScript = """
            init -1000 python:
                import os
                ANDROID_MASBASE = os.environ.get("ANDROID_MASBASE", os.environ.get("ANDROID_PRIVATE", "$escapedBasePath"))
        """.trimIndent() + "\n"

        val bootstrapFile = File(gameDir, "zz_android_masbase_bootstrap.rpy")
        if (!bootstrapFile.exists() || bootstrapFile.readText(Charsets.UTF_8) != bootstrapScript) {
            bootstrapFile.writeText(bootstrapScript, Charsets.UTF_8)
        }
    }

    private fun createLanguageFile(language: String) {
        try {
            val gameDir = File(filesDir, "monikaafterstory-masl-edition/game")
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

    private fun updateNotificationBadge() {
        val unreadCount = NotificationHistoryManager.getUnreadCount(this)
        if (unreadCount > 0) {
            binding.txtNotificationBadge.text = unreadCount.toString()
            binding.txtNotificationBadge.visibility = View.VISIBLE
        } else {
            binding.txtNotificationBadge.visibility = View.GONE
        }

        // Also update the empty state text inside the panel
        val notifications = NotificationHistoryManager.getNotifications(this)
        if (notifications.isEmpty()) {
            binding.txtNoNotifications.visibility = View.VISIBLE
            binding.rvNotifications.visibility = View.GONE
        } else {
            binding.txtNoNotifications.visibility = View.GONE
            binding.rvNotifications.visibility = View.VISIBLE
        }
    }

    private fun toggleNotificationCenter() {
        val adapter = notificationAdapter ?: return
        if (binding.notificationCenterPanel.visibility == View.VISIBLE) {
            binding.notificationCenterPanel.animate()
                .translationY(binding.notificationCenterPanel.height.toFloat())
                .alpha(0f)
                .setDuration(200)
                .withEndAction { binding.notificationCenterPanel.visibility = View.GONE }
                .start()
        } else {
            NotificationHistoryManager.markAllAsRead(this)
            updateNotificationBadge()
            adapter.updateItems(NotificationHistoryManager.getNotifications(this))

            binding.notificationCenterPanel.visibility = View.VISIBLE
            binding.notificationCenterPanel.alpha = 0f
            binding.notificationCenterPanel.post {
                binding.notificationCenterPanel.translationY = binding.notificationCenterPanel.height.toFloat()
                binding.notificationCenterPanel.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun showNotificationToast(title: String, message: String, imagePath: String?) {
        val activeActivity = ActiveActivityRegistry.currentActivity
        val targetActivity: android.app.Activity = if (activeActivity != null && !activeActivity.isFinishing && 
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activeActivity.isDestroyed)) {
            activeActivity
        } else {
            this
        }
        DesktopNotificationUI.showNotificationToast(targetActivity, title, message, imagePath)
    }

    private inner class NotificationAdapter(
        private var items: List<DesktopNotification>,
        private val onDismiss: (DesktopNotification) -> Unit
    ) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgAvatar: ImageView = view.findViewById(R.id.imgNotifAvatar)
            val txtTitle: TextView = view.findViewById(R.id.txtNotifTitle)
            val txtMessage: TextView = view.findViewById(R.id.txtNotifMessage)
            val txtTime: TextView = view.findViewById(R.id.txtNotifTime)
            val btnDismiss: ImageButton = view.findViewById(R.id.btnDismissNotif)
            val divider: View = view.findViewById(R.id.dividerLine)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_desktop_notification, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtTitle.text = item.title
            holder.txtMessage.text = item.message
            
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.txtTime.text = sdf.format(Date(item.timestamp))

            if (!item.imagePath.isNullOrEmpty()) {
                val file = File(item.imagePath)
                if (file.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        holder.imgAvatar.setImageBitmap(bitmap)
                    } else {
                        holder.imgAvatar.setImageResource(R.drawable.ic_notifications)
                    }
                } else {
                    holder.imgAvatar.setImageResource(R.drawable.ic_notifications)
                }
            } else {
                holder.imgAvatar.setImageResource(R.drawable.ic_notifications)
            }

            holder.btnDismiss.setOnClickListener {
                onDismiss(item)
            }

            holder.divider.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE
        }

        override fun getItemCount(): Int = items.size

        fun updateItems(newItems: List<DesktopNotification>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

}
