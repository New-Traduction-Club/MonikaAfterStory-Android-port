package org.renpy.android

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class MigrationActivity : GameWindowActivity() {

    private lateinit var cardDdlc: MaterialCardView
    private lateinit var btnDownloadDDLC: MaterialButton
    private lateinit var btnSelectDDLC: MaterialButton
    private lateinit var tvSelectedDDLC: TextView

    private lateinit var layoutMigrationProgress: View
    private lateinit var txtMigrationStatus: TextView
    private lateinit var progressBarMigration: ProgressBar
    private lateinit var txtMigrationProgress: TextView
    private lateinit var txtMigrationDescription: TextView

    private lateinit var btnStartMigration: MaterialButton
    private lateinit var btnMigrationOkay: MaterialButton

    private var ddlcUri: Uri? = null
    private var isProcessing = false
    private var isReceiverRegistered = false
    private var targetPackage: PackageInfo? = null

    companion object {
        private const val REQUEST_CODE_DDLC = 1001
        private const val CHECKSUM_DDLC = "2a3dd7969a06729a32ace0a6ece5f2327e29bdf460b8b39e6a8b0875e545632e"
        private const val TARGET_BASE_VERSION = "0.12.18-r1"
        private const val PACKAGES_JSON_URL =
            "https://raw.githubusercontent.com/New-Traduction-Club/MonikaAfterStory-Android-port/refs/heads/main/.utilityfiles/packages_list.json"
        private const val TEMP_MOD_FILE = "mod_migration_temp.zip"

        private val MAS_CORE_RPYC_BLACKLIST = hashSetOf(
            "0config.rpyc", "0imports.rpyc", "0statements.rpyc", "0utils.rpyc",
            "cgs.rpyc", "chess.rpyc", "console.rpyc", "credits.rpyc", "definitions.rpyc",
            "effects.rpyc", "event-handler.rpyc", "event-rules.rpyc", "glitchtext.rpyc",
            "gui.rpyc", "import_ddlc.rpyc", "main_menu.rpyc", "migration.rpyc",
            "options.rpyc", "overrides.rpyc", "poems.rpyc", "poems_special.rpyc",
            "pong.rpyc", "progression.rpyc", "screens.rpyc", "script-affection.rpyc",
            "script-android-notifs.rpyc", "script-anniversary.rpyc", "script-apologies.rpyc",
            "script-brbs.rpyc", "script-ch0.rpyc", "script-ch10.rpyc", "script-ch1.rpyc",
            "script-ch20.rpyc", "script-ch21.rpyc", "script-ch22.rpyc", "script-ch23.rpyc",
            "script-ch2.rpyc", "script-ch30.rpyc", "script-ch3.rpyc", "script-ch40.rpyc",
            "script-ch4.rpyc", "script-ch5.rpyc", "script-compliments.rpyc",
            "script-easter-eggs.rpyc", "script-exclusives2-natsuki.rpyc",
            "script-exclusives2-yuri.rpyc", "script-exclusives-natsuki.rpyc",
            "script-exclusives-sayori.rpyc", "script-exclusives-yuri.rpyc",
            "script-farewells.rpyc", "script-fun-facts.rpyc", "script-grammar.rpyc",
            "script-greetings.rpyc", "script-holidays.rpyc", "script-introduction.rpyc",
            "script-islands-event.rpyc", "script-moods.rpyc", "script-poemgame.rpyc",
            "script-poemresponses2.rpyc", "script-poemresponses.rpyc", "script-python.rpyc",
            "script.rpyc", "script-songs.rpyc", "script-stories.rpyc",
            "script-story-events.rpyc", "script-topics.rpyc", "script-windowreacts.rpyc",
            "shake.rpyc", "special-effects.rpyc", "splash.rpyc", "sprite-chart-matrix.rpyc",
            "sprite-chart.rpyc", "sprite-decoder.rpyc", "sprite-generator.rpyc",
            "sprites.rpyc", "styles.rpyc", "traduction-club_stuff.rpyc", "transforms.rpyc",
            "updates.rpyc", "updates_topics.rpyc", "zz_apikeys.rpyc", "zz_backgrounds.rpyc",
            "zz_backup.rpyc", "zz_calendar.rpyc", "zz_cardgames.rpyc", "zz_consumables.rpyc",
            "zz_dm.rpyc", "zz_dockingstation.rpyc", "zz_dump.rpyc", "zz_extrasmenu.rpyc",
            "zz_games.rpyc", "zz_graphicsmenu.rpyc", "zz_hangman.rpyc", "zz_history.rpyc",
            "zz_hotkey_buttons.rpyc", "zz_hotkeys.rpyc", "zz_interactions.rpyc",
            "zz_layout_translation.rpyc", "zz_monikamovie.rpyc", "zz_music_selector.rpyc",
            "zz_overlays.rpyc", "zz_pianokeys.rpyc", "zz_poemgame.rpyc", "zz_poems.rpyc",
            "zz_reactions.rpyc", "zz_seasons.rpyc", "zz_selector.rpyc", "zz_shields.rpyc",
            "zz_spritedeco.rpyc", "zz_spritejsons.rpyc", "zz_spriteobjects.rpyc",
            "zz_submods.rpyc", "zz_threading.rpyc", "zz_time.rpyc", "zz_transforms.rpyc",
            "zz_weather.rpyc", "zz_windowutils.rpyc"
        )

        private val EXCLUDED_GAME_DIRS = hashSetOf("aa_early", "tl")
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                DownloadService.ACTION_DOWNLOAD_PROGRESS -> {
                    val progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                    val speed = intent.getStringExtra(DownloadService.EXTRA_SPEED) ?: ""
                    val eta = intent.getStringExtra(DownloadService.EXTRA_ETA) ?: ""
                    val currentBytes = intent.getLongExtra(DownloadService.EXTRA_CURRENT_BYTES, 0L)
                    val totalBytes = intent.getLongExtra(DownloadService.EXTRA_TOTAL_BYTES, 0L)

                    updateDownloadProgressUI(progress, speed, eta, currentBytes, totalBytes)
                }

                DownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                    val success = intent.getBooleanExtra(DownloadService.EXTRA_SUCCESS, false)
                    val error = intent.getStringExtra(DownloadService.EXTRA_ERROR)

                    if (success) {
                        onDownloadCompleted()
                    } else {
                        onMigrationError(error ?: getString(R.string.update_status_failed))
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_migration)
        setTitle(getString(R.string.migration_window_title))

        bindViews()
        setupListeners()
        setupReceiver()

        if (savedInstanceState != null) {
            @Suppress("DEPRECATION")
            ddlcUri = savedInstanceState.getParcelable("ddlcUri")
            val selectedName = savedInstanceState.getString("ddlcName")
            if (ddlcUri != null && !selectedName.isNullOrBlank()) {
                tvSelectedDDLC.text = getString(R.string.setup_file_selected, selectedName)
                tvSelectedDDLC.visibility = View.VISIBLE
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("ddlcUri", ddlcUri)
        outState.putString("ddlcName", tvSelectedDDLC.text.toString())
    }

    private fun bindViews() {
        cardDdlc = findViewById(R.id.cardDdlc)
        btnDownloadDDLC = findViewById(R.id.btnDownloadDDLC)
        btnSelectDDLC = findViewById(R.id.btnSelectDDLC)
        tvSelectedDDLC = findViewById(R.id.tvSelectedDDLC)

        layoutMigrationProgress = findViewById(R.id.layoutMigrationProgress)
        txtMigrationStatus = findViewById(R.id.txtMigrationStatus)
        progressBarMigration = findViewById(R.id.progressBarMigration)
        txtMigrationProgress = findViewById(R.id.txtMigrationProgress)
        txtMigrationDescription = findViewById(R.id.txtMigrationDescription)

        btnStartMigration = findViewById(R.id.btnStartMigration)
        btnMigrationOkay = findViewById(R.id.btnMigrationOkay)
    }

    private fun setupListeners() {
        btnDownloadDDLC.setOnClickListener {
            openUrl(getString(R.string.setup_step_1_url))
        }

        btnSelectDDLC.setOnClickListener {
            selectDdlcFile()
        }

        btnStartMigration.setOnClickListener {
            startMigrationProcess()
        }

        btnMigrationOkay.setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btnWindowClose)?.setOnClickListener {
            if (isProcessing) {
                showWarningDialog()
            } else {
                finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun selectDdlcFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQUEST_CODE_DDLC)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_DDLC && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            val uri = data.data!!
            ddlcUri = uri
            val fileName = getFileName(uri)
            tvSelectedDDLC.text = getString(R.string.setup_file_selected, fileName)
            tvSelectedDDLC.visibility = View.VISIBLE
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return uri.lastPathSegment ?: "ddlc-win.zip"
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun startMigrationProcess() {
        if (ddlcUri == null) {
            GameDialogBuilder(this)
                .setTitle(getString(R.string.migration_step_ddlc_title))
                .setMessage(getString(R.string.migration_step_ddlc_desc))
                .setPositiveButton(getString(R.string.action_ok), null)
                .show()
            return
        }

        if (!isNetworkConnected()) {
            GameDialogBuilder(this)
                .setTitle(getString(R.string.migration_window_title))
                .setMessage(getString(R.string.migration_error_offline))
                .setPositiveButton(getString(R.string.action_ok), null)
                .show()
            return
        }

        isProcessing = true
        cardDdlc.visibility = View.GONE
        btnStartMigration.visibility = View.GONE
        layoutMigrationProgress.visibility = View.VISIBLE

        txtMigrationStatus.text = getString(R.string.migration_status_fetching)
        progressBarMigration.isIndeterminate = true
        txtMigrationProgress.text = ""
        txtMigrationDescription.text = getString(R.string.migration_desc_wait)

        CoroutineScope(Dispatchers.Main).launch {
            val packages = withContext(Dispatchers.IO) {
                PackageHelper.fetchPackages(PACKAGES_JSON_URL)
            }

            val pkg = packages.find { it.version == TARGET_BASE_VERSION } ?: packages.firstOrNull()
            if (pkg == null) {
                onMigrationError(getString(R.string.migration_error_package_not_found))
                return@launch
            }

            targetPackage = pkg

            txtMigrationStatus.text = getString(R.string.setup_progress_verifying)
            val ddlcValid = withContext(Dispatchers.IO) {
                verifyUriChecksum(ddlcUri!!, CHECKSUM_DDLC)
            }

            if (!ddlcValid) {
                onMigrationError(getString(R.string.setup_error_checksum, "DDLC"))
                return@launch
            }

            txtMigrationStatus.text = getString(R.string.migration_status_downloading)
            progressBarMigration.isIndeterminate = false
            progressBarMigration.progress = 0

            val destFile = File(filesDir, TEMP_MOD_FILE)
            val intent = Intent(this@MigrationActivity, DownloadService::class.java).apply {
                action = DownloadService.ACTION_START_DOWNLOAD
                putExtra(DownloadService.EXTRA_URL, pkg.download_url)
                putExtra(DownloadService.EXTRA_DEST_PATH, destFile.absolutePath)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
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
        if (totalBytes > 0) {
            progressBarMigration.isIndeterminate = false
            progressBarMigration.progress = progress
            val cur = formatBytes(currentBytes)
            val tot = formatBytes(totalBytes)
            txtMigrationProgress.text = getString(R.string.setup_download_progress_full, progress, cur, tot, speed, eta)
        } else {
            progressBarMigration.isIndeterminate = true
            val cur = formatBytes(currentBytes)
            txtMigrationProgress.text = getString(R.string.setup_download_progress_indeterminate, cur, speed)
        }
    }

    private fun onDownloadCompleted() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                txtMigrationStatus.text = getString(R.string.migration_status_verifying_download)
                progressBarMigration.isIndeterminate = true
                txtMigrationProgress.text = ""

                val modFile = File(filesDir, TEMP_MOD_FILE)
                val isModValid = withContext(Dispatchers.IO) {
                    verifyFileChecksum(modFile, targetPackage?.sha256 ?: "")
                }

                if (!isModValid) {
                    throw Exception(getString(R.string.setup_error_checksum, "Mod"))
                }

                txtMigrationStatus.text = getString(R.string.migration_status_extracting_base)
                withContext(Dispatchers.IO) {
                    extractBaseGameAndDdlc(modFile)
                }

                txtMigrationStatus.text = getString(R.string.migration_status_transferring_assets)
                withContext(Dispatchers.IO) {
                    val targetMaslDir = File(filesDir, "monikaafterstory-masl-edition")
                    migrateLegacyAssets(filesDir, targetMaslDir)
                }

                txtMigrationStatus.text = getString(R.string.migration_status_updating_saves)
                withContext(Dispatchers.IO) {
                    migrateSaves()
                }

                onMigrationSuccess()

            } catch (e: Exception) {
                onMigrationError(e.message ?: "Unknown error")
            }
        }
    }

    private fun extractBaseGameAndDdlc(modFile: File) {
        val installDir = File(filesDir, "monikaafterstory-masl-edition")
        val gameDir = File(installDir, "game")
        if (!gameDir.exists()) gameDir.mkdirs()

        var ddlcTempFile: File? = null
        try {
            ddlcTempFile = File(cacheDir, "ddlc_migration_temp.zip")
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

        ZipFile(modFile).use { zip ->
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
        modFile.delete()
    }

    private fun migrateLegacyAssets(sourceDir: File, targetDir: File) {
        val excludedRootDirs = setOf("renpy", "lib", "LauncherUpdates", "monikaafterstory-masl-edition")
        val rootItems = sourceDir.listFiles() ?: return

        for (item in rootItems) {
            if (item.isFile) {
                continue
            }
            if (item.isDirectory && excludedRootDirs.contains(item.name)) {
                continue
            }

            if (item.isDirectory) {
                val destSubDir = File(targetDir, item.name)
                copyDirectoryRecursive(item, destSubDir, item.name == "game")
            }
        }
    }

    private fun copyDirectoryRecursive(source: File, dest: File, isInsideGame: Boolean) {
        if (!dest.exists()) {
            dest.mkdirs()
        }

        val files = source.listFiles() ?: return
        for (file in files) {
            val targetFile = File(dest, file.name)
            if (file.isDirectory) {
                if (isInsideGame && EXCLUDED_GAME_DIRS.contains(file.name.lowercase())) {
                    continue
                }
                copyDirectoryRecursive(file, targetFile, isInsideGame)
            } else {
                if (isInsideGame && file.parentFile?.name == "game" && MAS_CORE_RPYC_BLACKLIST.contains(file.name.lowercase())) {
                    continue
                }
                file.copyTo(targetFile, overwrite = true)
            }
        }
    }

    private fun migrateSaves() {
        val externalSaves = File(getExternalFilesDir(null), "saves")
        val internalSaves = File(filesDir.parentFile, "saves")
        val savesDir = when {
            externalSaves.exists() -> externalSaves
            internalSaves.exists() -> internalSaves
            else -> externalSaves
        }

        if (!savesDir.exists()) {
            throw Exception(getString(R.string.migration_error_missing_persistent_699))
        }

        val p699 = File(savesDir, "persistent_699")
        if (!p699.exists()) {
            throw Exception(getString(R.string.migration_error_missing_persistent_699))
        }

        savesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".save")) {
                file.delete()
            }
        }

        val currentPersistent = File(savesDir, "persistent")
        if (currentPersistent.exists()) {
            val backupPersistent = File(savesDir, "persistent784.oldmasl")
            if (backupPersistent.exists()) backupPersistent.delete()
            currentPersistent.renameTo(backupPersistent)
        }

        val destPersistent = File(savesDir, "persistent")
        p699.renameTo(destPersistent)
    }

    private fun onMigrationSuccess() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_migrated_masl", true).apply()

        txtMigrationStatus.text = getString(R.string.migration_status_complete)
        progressBarMigration.isIndeterminate = false
        progressBarMigration.progress = 100
        txtMigrationProgress.text = ""
        txtMigrationDescription.text = getString(R.string.migration_desc_finished)
        isProcessing = false

        btnStartMigration.visibility = View.GONE
        btnMigrationOkay.visibility = View.VISIBLE

        GameDialogBuilder(this)
            .setTitle(getString(R.string.migration_cleanup_title))
            .setMessage(getString(R.string.migration_cleanup_message))
            .setPositiveButton(getString(R.string.migration_cleanup_btn_delete)) { _, _ ->
                deleteLegacyFolders()
            }
            .setNegativeButton(getString(R.string.migration_cleanup_btn_keep), null)
            .show()
    }

    private fun deleteLegacyFolders() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val legacyFolders = listOf("game", "renpy", "lib", "characters", "LauncherUpdates", "log")
                for (folderName in legacyFolders) {
                    val folder = File(filesDir, folderName)
                    if (folder.exists()) {
                        folder.deleteRecursively()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun onMigrationError(errorMessage: String) {
        isProcessing = false
        txtMigrationStatus.text = getString(R.string.migration_status_idle)
        txtMigrationProgress.text = ""
        txtMigrationDescription.text = getString(R.string.migration_error_generic, errorMessage)
        progressBarMigration.isIndeterminate = false
        progressBarMigration.progress = 0

        cardDdlc.visibility = View.VISIBLE
        btnStartMigration.visibility = View.VISIBLE
        btnStartMigration.isEnabled = true
        layoutMigrationProgress.visibility = View.GONE

        GameDialogBuilder(this)
            .setTitle(getString(R.string.migration_window_title))
            .setMessage(getString(R.string.migration_error_generic, errorMessage))
            .setPositiveButton(getString(R.string.action_ok), null)
            .show()
    }

    private fun showWarningDialog() {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.migration_window_title))
            .setMessage(getString(R.string.migration_dialog_warning))
            .setPositiveButton(getString(R.string.action_ok), null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (isProcessing) {
            showWarningDialog()
        } else {
            super.onBackPressed()
        }
    }

    private fun copyUriToFile(uri: Uri, destFile: File) {
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Cannot open input stream for URI: $uri")
    }

    private fun verifyUriChecksum(uri: Uri, expectedHash: String): Boolean {
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
        return sb.toString().equals(expectedHash, ignoreCase = true)
    }

    private fun verifyFileChecksum(file: File, expectedHash: String): Boolean {
        if (!file.exists()) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            file.inputStream().use { input ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hashBytes = digest.digest()
            val sb = StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString().equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes > 1024 * 1024) {
            String.format("%.1f MB", bytes / (1024f * 1024f))
        } else {
            String.format("%.1f KB", bytes / 1024f)
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
        isReceiverRegistered = true
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(downloadReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isReceiverRegistered = false
        }
        super.onDestroy()
    }
}
