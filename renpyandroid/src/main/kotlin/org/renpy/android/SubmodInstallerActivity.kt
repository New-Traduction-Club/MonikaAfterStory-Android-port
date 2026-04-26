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
import org.renpy.android.databinding.ActivitySubmodInstallerBinding

class SubmodInstallerActivity : GameWindowActivity() {

    private lateinit var binding: ActivitySubmodInstallerBinding
    private var progressDialog: AlertDialog? = null
    private var progressText: TextView? = null
    private var progressIndicator: ProgressBar? = null

    private val pickArchiveLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        installSubmod(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubmodInstallerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTitle(R.string.submod_installer_title)
        binding.tvSubmodInstallInfo.text = getString(R.string.submod_installer_info)

        binding.btnSelectSubmodArchive.setOnClickListener {
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

    private fun installSubmod(uri: Uri) {
        showProgressDialog(getString(R.string.submod_progress_analyzing))

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SubmodInstallerUtils.installFromSafUri(
                        context = this@SubmodInstallerActivity,
                        archiveUri = uri,
                        onPhaseChanged = { phase ->
                            runOnUiThread {
                                when (phase) {
                                    SubmodInstallerUtils.InstallPhase.EXTRACTING_ARCHIVE,
                                    SubmodInstallerUtils.InstallPhase.ANALYZING_STRUCTURE -> {
                                        updateProgressText(getString(R.string.submod_progress_analyzing))
                                    }

                                    SubmodInstallerUtils.InstallPhase.MERGING_FILES -> {
                                        updateProgressText(getString(R.string.submod_progress_merging))
                                    }
                                }
                            }
                        }
                    )
                }

                dismissProgressDialog()
                GameDialogBuilder(this@SubmodInstallerActivity)
                    .setTitle(getString(R.string.submod_success_title))
                    .setMessage(getString(R.string.submod_success_message))
                    .setPositiveButton(getString(R.string.action_ok), null)
                    .show()
                InAppNotifier.show(this@SubmodInstallerActivity, getString(R.string.submod_install_success_toast))
            } catch (_: SubmodInstallerUtils.UnrecognizedStructureException) {
                dismissProgressDialog()
                showRetryDialog(getString(R.string.submod_incompatible_message))
            } catch (_: SubmodInstallerUtils.UnsupportedArchiveException) {
                dismissProgressDialog()
                showRetryDialog(getString(R.string.submod_incompatible_message))
            } catch (e: Exception) {
                dismissProgressDialog()
                InAppNotifier.show(
                    this@SubmodInstallerActivity,
                    getString(R.string.submod_unexpected_error, e.message ?: "Unknown error"),
                    true
                )
                showRetryDialog(getString(R.string.submod_incompatible_message))
            }
        }
    }

    private fun showRetryDialog(message: String) {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.submod_incompatible_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.submod_retry)) { _, _ ->
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
