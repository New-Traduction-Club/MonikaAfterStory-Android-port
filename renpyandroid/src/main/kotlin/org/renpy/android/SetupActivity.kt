package org.renpy.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.WindowManager
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import android.content.BroadcastReceiver

class SetupActivity : BaseActivity() {

    override val preferredOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

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

    private var ddlcUri: Uri? = null
    private var masUri: Uri? = null
    private var selectedPackage: PackageInfo? = null

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
    private lateinit var setupContentScroll: View
    private lateinit var notificationPermissionScreen: View
    private lateinit var btnGrantNotificationPermission: Button
    private lateinit var btnSkipNotificationPermission: Button
    private lateinit var btnContinueAfterNotifications: Button
    private lateinit var tvNotificationPermissionStatus: TextView
    private lateinit var notificationPermissionBottomActions: View

    private lateinit var btnLanguage: LinearLayout
    private lateinit var tvCurrentLanguage: TextView

    private val CHECKSUM_DDLC = "2a3dd7969a06729a32ace0a6ece5f2327e29bdf460b8b39e6a8b0875e545632e"
    private val PACKAGES_JSON_URL =
        "https://raw.githubusercontent.com/New-Traduction-Club/MonikaAfterStory-Android-port/refs/heads/main/.utilityfiles/packages_list.json"

    companion object {
        private const val TAG = "SetupActivity"
        private const val REQUEST_CODE_DDLC = 1001
        private const val REQUEST_CODE_MAS = 1002
        private const val REQUEST_PERMISSION_NOTIFICATIONS_DOWNLOAD = 1003
        private const val REQUEST_PERMISSION_NOTIFICATIONS_SETUP = 1004
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyIfAvailable(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !isChromeOsDevice()) {
            try {
                window.attributes = window.attributes.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        @Suppress("DEPRECATION")
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to apply display cutout mode", e)
            }
        }
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

        val btnAutoDownloadMAS = findViewById<Button>(R.id.btnAutoDownloadMAS)
        tvSelectedMAS = findViewById(R.id.tvSelectedMAS)

        btnInstall = findViewById(R.id.btnInstall)
        layoutProgress = findViewById(R.id.layoutProgress)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)
        setupContentScroll = findViewById(R.id.setupContentScroll)
        notificationPermissionScreen = findViewById(R.id.notificationPermissionScreen)
        btnGrantNotificationPermission = findViewById(R.id.btnGrantNotificationPermission)
        btnSkipNotificationPermission = findViewById(R.id.btnSkipNotificationPermission)
        btnContinueAfterNotifications = findViewById(R.id.btnContinueAfterNotifications)
        tvNotificationPermissionStatus = findViewById(R.id.tvNotificationPermissionStatus)
        notificationPermissionBottomActions = findViewById(R.id.notificationPermissionBottomActions)

        btnLanguage = findViewById(R.id.btnLanguage)
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)

        setupLanguageUI()
        setupEdgeToEdgeInsets()
        setupNotificationPermissionScreenInsets()

        // DDLC
        btnDownloadDDLC.setOnClickListener {
            openUrl(getString(R.string.setup_step_1_url))
        }

        btnSelectDDLC.setOnClickListener {
            selectFile(REQUEST_CODE_DDLC)
        }

        // MAS
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

        enableImmersiveFullscreen()
        window.decorView.post {
            enableImmersiveFullscreen()
        }
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
            findViewById<View>(R.id.setupRoot)?.let { ViewCompat.requestApplyInsets(it) }
        }
    }

    private fun enableImmersiveFullscreen() {
        if (isChromeOsDevice()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
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
        enableImmersiveFullscreen()
        findViewById<View>(R.id.setupRoot)?.let { ViewCompat.requestApplyInsets(it) }

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

        refreshNotificationPermissionUi()
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

        val builder = GameDialogBuilder(this)
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

        if (cancelable) {
            builder.setNegativeButton(getString(R.string.cancel), null)
        }
        builder.show()
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
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            val displayName = cursor.getString(index)
                            if (!displayName.isNullOrBlank()) {
                                return displayName
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "No permission to query filename for uri: $uri", e)
            }
        }

        val lastPathSegment = uri.lastPathSegment
        if (!lastPathSegment.isNullOrBlank()) {
            return lastPathSegment
        }

        val path = uri.path
        if (!path.isNullOrBlank()) {
            return path.substringAfterLast('/')
        }

        return "unknown.zip"
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
                prefs.edit()
                    .putBoolean("is_setup_completed", true)
                    .putBoolean("user_migrated_masl", true)
                    .apply()

                Toast.makeText(this@SetupActivity, getString(R.string.setup_complete), Toast.LENGTH_SHORT).show()

                kotlinx.coroutines.delay(1000)

                proceedAfterSetupInstall()

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
        findViewById<View>(R.id.btnAutoDownloadMAS).isEnabled = enabled
        findViewById<View>(R.id.btnLanguage).isEnabled = enabled

        findViewById<View>(R.id.btnDownloadDDLC).alpha = alpha
        findViewById<View>(R.id.btnSelectDDLC).alpha = alpha
        findViewById<View>(R.id.btnAutoDownloadMAS).alpha = alpha
        findViewById<View>(R.id.btnLanguage).alpha = alpha
    }

    private fun setupEdgeToEdgeInsets() {
        val titlebar = findViewById<View>(R.id.setupTitlebar)
        val initialTitlebarStart = titlebar.paddingStart
        val initialTitlebarEnd = titlebar.paddingEnd
        val initialTitlebarTop = titlebar.paddingTop
        val initialTitlebarBottom = titlebar.paddingBottom

        val scroll = setupContentScroll
        val initialScrollLeft = scroll.paddingLeft
        val initialScrollTop = scroll.paddingTop
        val initialScrollRight = scroll.paddingRight
        val initialScrollBottom = scroll.paddingBottom

        val permissionScreen = notificationPermissionScreen
        val initialPermLeft = permissionScreen.paddingLeft
        val initialPermTop = permissionScreen.paddingTop
        val initialPermRight = permissionScreen.paddingRight
        val initialPermBottom = permissionScreen.paddingBottom

        val root = findViewById<View>(R.id.setupRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val cutout = windowInsets.displayCutout

            val safeLeft = Math.max(cutoutInsets.left, cutout?.safeInsetLeft ?: 0)
            val safeRight = Math.max(cutoutInsets.right, cutout?.safeInsetRight ?: 0)

            val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val cutoutStart = if (isRtl) safeRight else safeLeft
            val cutoutEnd = if (isRtl) safeLeft else safeRight

            titlebar.setPaddingRelative(
                initialTitlebarStart + cutoutStart,
                initialTitlebarTop,
                initialTitlebarEnd + cutoutEnd,
                initialTitlebarBottom
            )

            scroll.setPadding(
                initialScrollLeft + safeLeft,
                initialScrollTop,
                initialScrollRight + safeRight,
                initialScrollBottom
            )

            permissionScreen.setPadding(
                initialPermLeft + safeLeft,
                initialPermTop,
                initialPermRight + safeRight,
                initialPermBottom
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupNotificationPermissionScreenInsets() {}

    private fun proceedAfterSetupInstall() {
        if (hasNotificationPermission()) {
            launchLauncher()
            return
        }

        setupContentScroll.visibility = View.GONE
        notificationPermissionScreen.visibility = View.VISIBLE
        refreshNotificationPermissionUi()

        btnGrantNotificationPermission.setOnClickListener {
            requestNotificationPermissionFromSetup()
        }

        btnSkipNotificationPermission.setOnClickListener {
            launchLauncher()
        }

        btnContinueAfterNotifications.setOnClickListener {
            launchLauncher()
        }
    }

    private fun refreshNotificationPermissionUi() {
        val notificationsGranted = hasNotificationPermission()
        if (notificationPermissionScreen.visibility != View.VISIBLE) {
            return
        }

        btnContinueAfterNotifications.isEnabled = notificationsGranted
        tvNotificationPermissionStatus.visibility = if (notificationsGranted) View.VISIBLE else View.GONE
        tvNotificationPermissionStatus.text = getString(
            if (notificationsGranted) R.string.setup_notifications_enabled
            else R.string.setup_notifications_denied
        )
    }

    private fun requestNotificationPermissionFromSetup() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            refreshNotificationPermissionUi()
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_PERMISSION_NOTIFICATIONS_SETUP
        )
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchLauncher() {
        val intent = Intent(this@SetupActivity, LauncherActivity::class.java)
        startActivity(intent)
        finish()
    }

    private suspend fun processInstallation() {
        val installDir = File(filesDir, "monikaafterstory-masl-edition")
        val gameDir = File(installDir, "game")
        if (!gameDir.exists()) gameDir.mkdirs()

        // Verify and Extract DDLC
        updateStatus(getString(R.string.setup_progress_verifying))

        if (!verifyChecksum(ddlcUri!!, CHECKSUM_DDLC)) {
            throw Exception(getString(R.string.setup_error_checksum, "DDLC"))
        }

        val modChecksum = selectedPackage?.sha256
        if (modChecksum != null) {
            if (!verifyChecksum(masUri!!, modChecksum)) {
                throw Exception(getString(R.string.setup_error_checksum, "Mod"))
            }
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
                    if (!entry.isDirectory && name.contains("game/") && name.endsWith(".rpa")) {
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

        if (deleteDdlcAfterInstall && ddlcUri != null) {
            deleteFile(ddlcUri!!)
        }

        // Extract Mod
        updateStatus(getString(R.string.setup_progress_extracting_mas))

        var modTempFile: File? = null
        try {
            // Check if masUri is already a file we created (downloaded)
            if (masUri!!.scheme == "file") {
                modTempFile = File(masUri!!.path!!)
            } else {
                modTempFile = File(cacheDir, "mod_temp_install.zip")
                copyUriToFile(masUri!!, modTempFile)
            }

            ZipFile(modTempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (name.startsWith("game/") || name.startsWith("characters/")) {
                        val targetFile = File(installDir, name)

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
                modTempFile?.delete()
            }
        }

        if (deleteMasAfterInstall && masUri != null) {
            deleteFile(masUri!!)
        }

        // Finalizing
        updateStatus(getString(R.string.setup_progress_finalizing))
    }

    private fun confirmDeleteZip(requestCode: Int) {
        GameDialogBuilder(this)
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
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                GameDialogBuilder(this)
                    .setTitle(getString(R.string.permission_notification_title))
                    .setMessage(getString(R.string.permission_notification_message))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_PERMISSION_NOTIFICATIONS_DOWNLOAD
                        )
                    }
                    .setNegativeButton(getString(R.string.no)) { _, _ ->
                        fetchAndSelectVersion() // Proceed without notifications
                    }
                    .show()
                return
            }
        }
        fetchAndSelectVersion()
    }

    private fun fetchAndSelectVersion() {
        layoutProgress.visibility = View.VISIBLE
        setUiEnabled(false)
        tvProgress.text = getString(R.string.setup_fetching_packages)
        progressBar.isIndeterminate = true

        CoroutineScope(Dispatchers.Main).launch {
            val packages = withContext(Dispatchers.IO) {
                PackageHelper.fetchPackages(PACKAGES_JSON_URL)
            }

            layoutProgress.visibility = View.GONE
            setUiEnabled(true)

            if (packages.isEmpty()) {
                Toast.makeText(this@SetupActivity, getString(R.string.setup_error_fetching_packages), Toast.LENGTH_LONG)
                    .show()
                return@launch
            }

            showVersionSelectionDialog(packages)
        }
    }

    private fun showVersionSelectionDialog(packages: List<PackageInfo>) {
        val versions = packages.map { it.version }.toTypedArray()

        GameDialogBuilder(this)
            .setTitle(getString(R.string.setup_select_version_title))
            .setItems(versions) { _, which ->
                val selected = packages[which]
                checkCompatibilityAndDownload(selected)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun checkCompatibilityAndDownload(packageInfo: PackageInfo) {
        val currentAppVersionCode = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }

        if (currentAppVersionCode < packageInfo.min_app_version) {
            GameDialogBuilder(this)
                .setTitle(getString(R.string.setup_error))
                .setMessage(getString(R.string.setup_error_incompatible_version, packageInfo.min_app_version))
                .setPositiveButton(getString(R.string.action_ok), null)
                .show()
            return
        }

        selectedPackage = packageInfo
        downloadMod()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSION_NOTIFICATIONS_DOWNLOAD -> {
                fetchAndSelectVersion()
            }

            REQUEST_PERMISSION_NOTIFICATIONS_SETUP -> {
                refreshNotificationPermissionUi()
            }
        }
    }

    private fun updateDownloadProgressUI(
        progress: Int,
        speed: String,
        eta: String,
        currentBytes: Long,
        totalBytes: Long
    ) {
        layoutProgress.visibility = View.VISIBLE
        setUiEnabled(false)
        if (totalBytes > 0) {
            progressBar.isIndeterminate = false
            progressBar.progress = progress

            val currentSize = formatBytes(currentBytes)
            val totalSize = formatBytes(totalBytes)

            tvProgress.text =
                getString(R.string.setup_download_progress_full, progress, currentSize, totalSize, speed, eta)
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
        val destFile = File(filesDir, "mod_temp.zip")
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

    private fun downloadMod() {
        if (isMasDownloadInProgress) return
        val downloadUrl = selectedPackage?.download_url ?: return

        isMasDownloadInProgress = true
        layoutProgress.visibility = View.VISIBLE
        setUiEnabled(false)
        tvProgress.text = getString(R.string.setup_downloading_mod)
        progressBar.progress = 0
        progressBar.isIndeterminate = true

        val destFile = File(filesDir, "mod_temp.zip")

        // Reset status
        val prefs = getSharedPreferences(DownloadService.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putInt(DownloadService.KEY_STATUS, DownloadService.STATUS_IDLE).apply()

        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, downloadUrl)
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
