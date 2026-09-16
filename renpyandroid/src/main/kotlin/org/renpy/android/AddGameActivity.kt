package org.renpy.android

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.renpy.android.databinding.ActivityAddGameBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException

class AddGameActivity : GameWindowActivity() {

    enum class InstallMode {
        RENPY,
        DDLC
    }

    private lateinit var binding: ActivityAddGameBinding

    private var currentMode = InstallMode.RENPY

    private var gameArchiveUri: Uri? = null
    private var gameArchiveName: String? = null

    private var modArchiveUri: Uri? = null
    private var modArchiveName: String? = null

    private var baseDdlcUri: Uri? = null
    private var baseDdlcName: String? = null

    private var isTitleAutoDerived = true
    private var isFolderAutoDerived = true

    private var installJob: Job? = null
    @Volatile
    private var isCancelled = false

    private val archiveMimeTypes = arrayOf(
        "application/zip",
        "application/x-zip",
        "application/x-zip-compressed",
        "application/x-rar",
        "application/x-rar-compressed",
        "application/vnd.rar",
        "application/rar",
        "application/x-compressed",
        "application/octet-stream",
        "*/*"
    )

    private val baseDdlcMimeTypes = arrayOf(
        "application/zip",
        "application/x-zip",
        "application/x-zip-compressed",
        "application/octet-stream",
        "*/*"
    )

    private fun isSupportedArchive(uri: Uri, name: String): Boolean {
        val lower = name.lowercase().trim()
        if (lower.endsWith(".zip") || lower.endsWith(".rar")) {
            return true
        }
        val type = try {
            contentResolver.getType(uri)?.lowercase()
        } catch (_: Exception) {
            null
        }
        if (type != null && (type.contains("zip") || type.contains("rar"))) {
            return true
        }
        return false
    }

    private val pickGameArchiveLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = resolveDisplayName(uri)
            if (!isSupportedArchive(uri, name)) {
                showErrorDialog(getString(R.string.add_game_error_invalid_archive))
                return@registerForActivityResult
            }
            gameArchiveUri = uri
            gameArchiveName = name
            binding.tvSelectedGameArchive.text = name
            deriveDefaultNames(name)
        }
    }

    private val pickModArchiveLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = resolveDisplayName(uri)
            if (!isSupportedArchive(uri, name)) {
                showErrorDialog(getString(R.string.add_game_error_invalid_archive))
                return@registerForActivityResult
            }
            modArchiveUri = uri
            modArchiveName = name
            binding.tvSelectedModArchive.text = name
            deriveDefaultNames(name)
        }
    }

    private val pickBaseDdlcLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = resolveDisplayName(uri)
            if (!name.lowercase().endsWith(".zip")) {
                showErrorDialog(getString(R.string.add_game_error_invalid_base_ddlc))
                return@registerForActivityResult
            }
            baseDdlcUri = uri
            baseDdlcName = name
            binding.tvSelectedBaseDdlc.text = name
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTitle(R.string.add_game_title)

        setupListeners()
        showModeSelect()
    }

    private fun setupListeners() {
        binding.cardModeRenpy.setOnClickListener {
            SoundEffects.playClick(this)
            selectMode(InstallMode.RENPY)
        }

        binding.cardModeDdlc.setOnClickListener {
            SoundEffects.playClick(this)
            selectMode(InstallMode.DDLC)
        }

        binding.btnSelectGameArchive.setOnClickListener {
            SoundEffects.playClick(this)
            pickGameArchiveLauncher.launch(archiveMimeTypes)
        }

        binding.btnSelectModArchive.setOnClickListener {
            SoundEffects.playClick(this)
            pickModArchiveLauncher.launch(archiveMimeTypes)
        }

        binding.btnSelectBaseDdlc.setOnClickListener {
            SoundEffects.playClick(this)
            pickBaseDdlcLauncher.launch(baseDdlcMimeTypes)
        }

        binding.btnBackToMode.setOnClickListener {
            SoundEffects.playClick(this)
            showModeSelect()
        }

        binding.btnStartInstall.setOnClickListener {
            SoundEffects.playClick(this)
            startInstallation()
        }

        binding.btnCancelInstall.setOnClickListener {
            SoundEffects.playClick(this)
            cancelInstallation()
        }

        binding.etGameTitle.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) isTitleAutoDerived = false
        }

        binding.etFolderName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) isFolderAutoDerived = false
        }
    }

    private fun showModeSelect() {
        binding.layoutModeSelect.visibility = View.VISIBLE
        binding.layoutConfigure.visibility = View.GONE
        binding.layoutProgress.visibility = View.GONE
    }

    private fun selectMode(mode: InstallMode) {
        currentMode = mode
        binding.layoutModeSelect.visibility = View.GONE
        binding.layoutConfigure.visibility = View.VISIBLE
        binding.layoutProgress.visibility = View.GONE

        if (mode == InstallMode.RENPY) {
            binding.tvConfigureTitle.setText(R.string.add_game_mode_renpy)
            binding.containerRenpyForm.visibility = View.VISIBLE
            binding.containerDdlcForm.visibility = View.GONE
        } else {
            binding.tvConfigureTitle.setText(R.string.add_game_mode_ddlc)
            binding.containerRenpyForm.visibility = View.GONE
            binding.containerDdlcForm.visibility = View.VISIBLE
        }
    }

    private fun deriveDefaultNames(fileName: String) {
        val baseName = fileName.substringBeforeLast('.')
        val cleanTitle = baseName
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val cleanFolder = baseName.trim().lowercase().replace(Regex("""[^a-z0-9_]"""), "_").trim('_')

        if (binding.etGameTitle.text.isNullOrBlank() || isTitleAutoDerived) {
            binding.etGameTitle.setText(cleanTitle)
            isTitleAutoDerived = true
        }
        if (binding.etFolderName.text.isNullOrBlank() || isFolderAutoDerived) {
            binding.etFolderName.setText(cleanFolder)
            isFolderAutoDerived = true
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        return cursor.getString(idx)
                    }
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment?.substringAfterLast('/') ?: "archive.zip"
    }

    private fun copyUriToTempFile(uri: Uri, tempFile: File) {
        tempFile.parentFile?.mkdirs()
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Cannot read file from storage")
    }

    private fun startInstallation() {
        val title = binding.etGameTitle.text?.toString()?.trim()
        val rawFolderName = binding.etFolderName.text?.toString()?.trim()

        if (rawFolderName.isNullOrBlank()) {
            showErrorDialog(getString(R.string.add_game_error_no_folder))
            return
        }

        if (currentMode == InstallMode.RENPY) {
            val uri = gameArchiveUri
            if (uri == null) {
                showErrorDialog(getString(R.string.add_game_error_no_archive))
                return
            }
            executeInstallGeneric(uri, rawFolderName, title)
        } else {
            val modUri = modArchiveUri
            val baseUri = baseDdlcUri
            if (modUri == null) {
                showErrorDialog(getString(R.string.add_game_error_no_archive))
                return
            }
            if (baseUri == null) {
                showErrorDialog(getString(R.string.add_game_error_no_base))
                return
            }
            executeInstallDdlc(modUri, baseUri, rawFolderName, title)
        }
    }

    private fun executeInstallGeneric(archiveUri: Uri, folderName: String, title: String?) {
        showProgressScreen()
        isCancelled = false

        installJob = lifecycleScope.launch {
            val cacheDir = externalCacheDir ?: cacheDir
            val isRar = gameArchiveName?.lowercase()?.endsWith(".rar") == true
            val suffix = if (isRar) ".rar" else ".zip"
            val tempSource = File(cacheDir, "import_src_${System.currentTimeMillis()}$suffix")
            val baseGamesDir = filesDir ?: return@launch
            val targetDir = GameInstallerEngine.resolveUniqueFolder(baseGamesDir, folderName)

            try {
                withContext(Dispatchers.IO) {
                    copyUriToTempFile(archiveUri, tempSource)
                    if (isCancelled) throw CancellationException("Cancelled")

                    GameInstallerEngine.installGenericGame(
                        archiveFile = tempSource,
                        targetDir = targetDir,
                        title = title,
                        isCancelled = { isCancelled },
                        onProgress = { progress ->
                            runOnUiThread {
                                binding.progressBar.isIndeterminate = false
                                binding.progressBar.progress = (progress * 100).toInt()
                            }
                        }
                    )
                }

                InAppNotifier.show(this@AddGameActivity, getString(R.string.add_game_success))
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: CancellationException) {
                showConfigureScreen()
            } catch (e: Exception) {
                showConfigureScreen()
                showErrorDialog(getString(R.string.add_game_error_generic, e.localizedMessage ?: e.message))
            } finally {
                if (tempSource.exists()) tempSource.delete()
            }
        }
    }

    private fun executeInstallDdlc(modUri: Uri, baseUri: Uri, folderName: String, title: String?) {
        showProgressScreen()
        isCancelled = false

        installJob = lifecycleScope.launch {
            val cacheDir = externalCacheDir ?: cacheDir
            val isModRar = modArchiveName?.lowercase()?.endsWith(".rar") == true
            val modSuffix = if (isModRar) ".rar" else ".zip"
            val tempMod = File(cacheDir, "import_mod_${System.currentTimeMillis()}$modSuffix")
            val tempBase = File(cacheDir, "import_base_${System.currentTimeMillis()}.zip")
            val baseGamesDir = filesDir ?: return@launch
            val targetDir = GameInstallerEngine.resolveUniqueFolder(baseGamesDir, folderName)

            try {
                withContext(Dispatchers.IO) {
                    copyUriToTempFile(modUri, tempMod)
                    if (isCancelled) throw CancellationException("Cancelled")

                    copyUriToTempFile(baseUri, tempBase)
                    if (isCancelled) throw CancellationException("Cancelled")

                    GameInstallerEngine.installDdlcMod(
                        modArchiveFile = tempMod,
                        baseDdlcZipFile = tempBase,
                        targetDir = targetDir,
                        title = title,
                        isCancelled = { isCancelled },
                        onProgress = { progress ->
                            runOnUiThread {
                                binding.progressBar.isIndeterminate = false
                                binding.progressBar.progress = (progress * 100).toInt()
                            }
                        }
                    )
                }

                InAppNotifier.show(this@AddGameActivity, getString(R.string.add_game_success))
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: CancellationException) {
                showConfigureScreen()
            } catch (e: IllegalArgumentException) {
                showConfigureScreen()
                showErrorDialog(getString(R.string.add_game_error_hash))
            } catch (e: Exception) {
                showConfigureScreen()
                showErrorDialog(getString(R.string.add_game_error_generic, e.localizedMessage ?: e.message))
            } finally {
                if (tempMod.exists()) tempMod.delete()
                if (tempBase.exists()) tempBase.delete()
            }
        }
    }

    private fun showProgressScreen() {
        binding.layoutModeSelect.visibility = View.GONE
        binding.layoutConfigure.visibility = View.GONE
        binding.layoutProgress.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true
    }

    private fun showConfigureScreen() {
        binding.layoutModeSelect.visibility = View.GONE
        binding.layoutConfigure.visibility = View.VISIBLE
        binding.layoutProgress.visibility = View.GONE
    }

    private fun cancelInstallation() {
        isCancelled = true
        installJob?.cancel()
        showConfigureScreen()
    }

    private fun showErrorDialog(message: String) {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.dialog_error_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.action_ok), null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.layoutProgress.visibility == View.VISIBLE) {
            cancelInstallation()
            return
        }
        if (binding.layoutConfigure.visibility == View.VISIBLE) {
            showModeSelect()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        isCancelled = true
        installJob?.cancel()
        super.onDestroy()
    }
}
