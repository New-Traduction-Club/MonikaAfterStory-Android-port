package org.renpy.android

import android.net.Uri
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.renpy.android.databinding.ActivitySpritepackInstallerBinding

class SpritepackInstallerActivity : GameWindowActivity() {

    private lateinit var binding: ActivitySpritepackInstallerBinding
    private var progressDialog: AlertDialog? = null
    private var progressText: TextView? = null
    private var progressIndicator: ProgressBar? = null

    private val pickArchiveLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        installSpritepack(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpritepackInstallerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTitle(R.string.spritepack_installer_title)
        binding.tvSpritepackInstallInfo.text = getString(R.string.spritepack_installer_info)

        binding.btnSelectArchive.setOnClickListener {
            SoundEffects.playClick(this)
            openArchivePicker()
        }
    }

    override fun onDestroy() {
        dismissProgressDialog()
        super.onDestroy()
    }

    private fun openArchivePicker() {
        pickArchiveLauncher.launch(
            arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/x-rar-compressed",
                "application/vnd.rar",
                "application/octet-stream"
            )
        )
    }

    private fun installSpritepack(uri: Uri) {
        showProgressDialog(getString(R.string.spritepack_progress_analyzing))

        lifecycleScope.launch {
            try {
                val detectedGiftFiles = withContext(Dispatchers.IO) {
                    val giftFiles = SpritepackInstallerUtils.findGiftFileNamesInArchive(
                        context = this@SpritepackInstallerActivity,
                        archiveUri = uri
                    )
                    SpritepackInstallerUtils.installFromSafUri(
                        context = this@SpritepackInstallerActivity,
                        archiveUri = uri,
                        onPhaseChanged = { phase ->
                            runOnUiThread {
                                when (phase) {
                                    SpritepackInstallerUtils.InstallPhase.EXTRACTING_ARCHIVE,
                                    SpritepackInstallerUtils.InstallPhase.ANALYZING_STRUCTURE -> {
                                        updateProgressText(getString(R.string.spritepack_progress_analyzing))
                                    }
                                    SpritepackInstallerUtils.InstallPhase.MERGING_FILES -> {
                                        updateProgressText(getString(R.string.spritepack_progress_merging))
                                    }
                                }
                            }
                        }
                    )
                    giftFiles
                }

                dismissProgressDialog()
                GameDialogBuilder(this@SpritepackInstallerActivity)
                    .setTitle(getString(R.string.spritepack_success_title))
                    .setMessage(getString(R.string.spritepack_success_message))
                    .setPositiveButton(getString(R.string.action_ok)) { _, _ ->
                        maybePromptGiftImport(uri, detectedGiftFiles)
                    }
                    .show()
                InAppNotifier.show(this@SpritepackInstallerActivity, getString(R.string.spritepack_install_success_toast))
            } catch (_: SpritepackInstallerUtils.UnrecognizedStructureException) {
                dismissProgressDialog()
                showRetryDialog(getString(R.string.spritepack_incompatible_message))
            } catch (_: SpritepackInstallerUtils.UnsupportedArchiveException) {
                dismissProgressDialog()
                showRetryDialog(getString(R.string.spritepack_incompatible_message))
            } catch (e: Exception) {
                dismissProgressDialog()
                InAppNotifier.show(
                    this@SpritepackInstallerActivity,
                    getString(R.string.spritepack_unexpected_error, e.message ?: "Unknown error"),
                    true
                )
                showRetryDialog(getString(R.string.spritepack_incompatible_message))
            }
        }
    }

    private fun maybePromptGiftImport(uri: Uri, giftFiles: List<String>) {
        if (giftFiles.isEmpty()) return

        GameDialogBuilder(this)
            .setTitle(getString(R.string.spritepack_gift_prompt_title))
            .setMessage(getString(R.string.spritepack_gift_prompt_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                importGiftFiles(uri)
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun importGiftFiles(uri: Uri) {
        showProgressDialog(getString(R.string.spritepack_gift_progress))

        lifecycleScope.launch {
            try {
                val importedCount = withContext(Dispatchers.IO) {
                    SpritepackInstallerUtils.importGiftFilesToCharacters(
                        context = this@SpritepackInstallerActivity,
                        archiveUri = uri
                    )
                }
                dismissProgressDialog()
                InAppNotifier.show(
                    this@SpritepackInstallerActivity,
                    getString(R.string.spritepack_gift_import_success, importedCount)
                )
            } catch (e: Exception) {
                dismissProgressDialog()
                InAppNotifier.show(
                    this@SpritepackInstallerActivity,
                    getString(R.string.spritepack_gift_import_error, e.message ?: "Unknown error"),
                    true
                )
            }
        }
    }

    private fun showRetryDialog(message: String) {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.spritepack_incompatible_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.spritepack_retry)) { _, _ ->
                openArchivePicker()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showProgressDialog(message: String) {
        if (progressDialog?.isShowing == true) {
            updateProgressText(message)
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        progressText = view.findViewById(R.id.progressText)
        progressIndicator = view.findViewById(R.id.progressBar)
        progressText?.text = message
        progressIndicator?.isIndeterminate = true

        progressDialog = GameDialogBuilder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun updateProgressText(message: String) {
        progressText?.text = message
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressText = null
        progressIndicator = null
    }
}
