package org.renpy.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import org.renpy.android.databinding.FileExplorerActivityBinding
import android.widget.TextView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import android.app.ProgressDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class FileExplorerActivity : GameWindowActivity() {

    companion object {
        private const val STATE_CURRENT_DIR_PATH = "state_current_dir_path"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private enum class ImportConflictAction {
        REPLACE,
        IGNORE,
        COPY_INCREMENT
    }

    private data class ImportConflictDecision(
        val action: ImportConflictAction,
        val applyToAll: Boolean
    )

    private lateinit var binding: FileExplorerActivityBinding
    private val viewModel: FileExplorerViewModel by viewModels()
    private lateinit var fileAdapter: FileAdapter

    private lateinit var rootDir: File
    private var isInternalRoot = false
    private var isSearchMode = false
    private var searchDebounceJob: Job? = null

    private val REQUEST_CODE_IMPORT_FILE = 1002
    private val REQUEST_CODE_IMPORT_FOLDER = 1003
    private val REQUEST_CODE_IMPORT_ZIP = 1004
    private val REQUEST_CODE_EXPORT_SELECTION = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FileExplorerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setTitle(getString(R.string.title_file_explorer).lowercase())

        SoundEffects.initialize(this)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        fileAdapter = FileAdapter(
            onItemClick = { file ->
                SoundEffects.playClick(this)
                if (file.isDirectory) {
                    if (isSearchMode) {
                        exitSearchMode(restoreDirectory = false)
                    }
                    viewModel.loadDirectory(file.absolutePath)
                } else {
                    val viewIntent = Intent(this, FileViewerActivity::class.java)
                    viewIntent.putExtra("file_path", file.absolutePath)
                    startActivity(viewIntent)
                }
            },
            onItemLongClick = { _ ->
                SoundEffects.playClick(this)
                updateActionUI()
            }
        )
        binding.recyclerView.adapter = fileAdapter

        viewModel.files.observe(this) { files ->
            fileAdapter.submitList(files)
            fileAdapter.clearSelection()
            updateActionUI()
        }
        
        viewModel.currentDir.observe(this) { dir ->
            setTitle(dir.name.lowercase())
            updateBreadcrumbs(dir)
            if (isSearchMode) {
                fileAdapter.setSearchContext(enabled = true, rootDir = dir)
                triggerSearch(binding.etSearchQuery.text?.toString().orEmpty(), debounce = false)
            }
        }

        viewModel.statusMessage.observe(this) { msg ->
            InAppNotifier.show(this, msg)
        }
        
        viewModel.hasClipboard.observe(this) { hasClip ->
            updateActionUI()
        }

        binding.btnDelete.setOnClickListener { SoundEffects.playClick(this); confirmDelete() }
        binding.btnCopy.setOnClickListener { SoundEffects.playClick(this); copyToClipboard(false) }
        binding.btnCut.setOnClickListener { SoundEffects.playClick(this); copyToClipboard(true) }
        binding.btnExport.setOnClickListener { SoundEffects.playClick(this); exportSelection() }
        binding.btnExtract.setOnClickListener { SoundEffects.playClick(this); extractRpaSelection() }
        binding.btnRename.setOnClickListener { SoundEffects.playClick(this); showRenameDialog() }
        
        binding.fabPaste.setOnClickListener { 
            SoundEffects.playClick(this)
            if (viewModel.hasClipboard.value == true) {
                viewModel.pasteToCurrentDir()
            } else {
                showImportDialog()
            }
        }

        val startPath = intent.getStringExtra("startPath") ?: filesDir.absolutePath
        rootDir = File(startPath)
        isInternalRoot = isInternalRootPath(rootDir)

        val restoredPath = savedInstanceState?.getString(STATE_CURRENT_DIR_PATH)
        val initialPath = restoredPath?.takeIf { isRestorablePath(it) } ?: rootDir.absolutePath
        viewModel.loadDirectory(initialPath)
        
        binding.btnQuickAdd.setOnClickListener { SoundEffects.playClick(this); showImportDialog() }
        binding.btnSearch.setOnClickListener {
            SoundEffects.playClick(this)
            if (isSearchMode) {
                exitSearchMode()
            } else {
                enterSearchMode()
            }
        }
        binding.btnSearchClose.setOnClickListener {
            SoundEffects.playClick(this)
            exitSearchMode()
        }
        binding.etSearchQuery.doOnTextChanged { text, _, _, _ ->
            if (!isSearchMode) return@doOnTextChanged
            triggerSearch(text?.toString().orEmpty(), debounce = true)
        }
        binding.btnSystemFiles.visibility = if (isInternalRoot) View.VISIBLE else View.GONE
        binding.btnSystemFiles.setOnClickListener {
            SoundEffects.playClick(this)
            openSystemInternalFiles()
        }

        binding.btnNavUp.setOnClickListener {
            SoundEffects.playClick(this)
            val current = viewModel.currentDir.value
            if (current != null && current.absolutePath != rootDir.absolutePath) {
                viewModel.navigateUp(rootDir.absolutePath)
            }
        }

        binding.btnMenu.setOnClickListener { view ->
            SoundEffects.playClick(this)
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.file_explorer_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_create_folder -> {
                        showCreateFolderDialog()
                        true
                    }
                    R.id.action_create_file -> {
                        showCreateFileDialog()
                        true
                    }
                    R.id.action_import -> {
                        showImportDialog()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
    
    private fun updateActionUI() {
        val selectionCount = fileAdapter.getSelectedCount()
        var showExtract = false
        if (selectionCount == 1) {
            val file = fileAdapter.selectedFiles.first()
            if (file.isFile && (file.name.endsWith(".rpa") || file.name.endsWith(".rpi"))) {
                showExtract = true
            }
        }

        if (isSearchMode) {
            val hasSelection = selectionCount > 0
            binding.actionContainer.visibility = if (hasSelection) View.VISIBLE else View.GONE
            binding.bottomAppBar.visibility = if (hasSelection) View.VISIBLE else View.GONE
            binding.btnMenu.visibility = View.GONE
            binding.btnExtract.visibility = if (showExtract) View.VISIBLE else View.GONE
            binding.btnRename.visibility = if (selectionCount == 1) View.VISIBLE else View.GONE
            binding.fabPaste.visibility = View.GONE
            return
        }

        val hasSelection = selectionCount > 0
        val hasClipboard = viewModel.hasClipboard.value == true
        
        binding.actionContainer.visibility = if (hasSelection) View.VISIBLE else View.GONE
        binding.btnMenu.visibility = View.VISIBLE
        
        if (hasClipboard) {
            binding.fabPaste.setImageResource(R.drawable.ic_paste)
            binding.fabPaste.visibility = View.VISIBLE
        } else if (hasSelection) {
            binding.fabPaste.visibility = View.GONE
        } else {
            binding.fabPaste.visibility = View.GONE
        }
        
        if (hasSelection || hasClipboard) {
            binding.bottomAppBar.visibility = View.VISIBLE
        } else {
            binding.bottomAppBar.visibility = View.GONE
        }
        
        binding.btnExtract.visibility = if (showExtract) View.VISIBLE else View.GONE
        binding.btnRename.visibility = if (selectionCount == 1) View.VISIBLE else View.GONE
    }

    private fun enterSearchMode() {
        if (isSearchMode) return
        isSearchMode = true
        fileAdapter.setSearchContext(enabled = true, rootDir = viewModel.currentDir.value)
        fileAdapter.clearSelection()
        updateActionUI()

        searchDebounceJob?.cancel()

        binding.etSearchQuery.setText("")
        binding.headerColumnsRow.animate()
            .alpha(0f)
            .setDuration(140)
            .withEndAction {
                binding.headerColumnsRow.visibility = View.GONE
                binding.headerColumnsRow.alpha = 1f
                binding.searchActionContainer.alpha = 0f
                binding.searchActionContainer.visibility = View.VISIBLE
                binding.searchActionContainer.animate()
                    .alpha(1f)
                    .setDuration(160)
                    .start()
            }
            .start()

        binding.recyclerView.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                viewModel.searchFromCurrentDir("")
                binding.recyclerView.animate().alpha(1f).setDuration(120).start()
            }
            .start()
    }

    private fun exitSearchMode(restoreDirectory: Boolean = true) {
        if (!isSearchMode) return
        isSearchMode = false
        fileAdapter.setSearchContext(enabled = false, rootDir = null)
        searchDebounceJob?.cancel()
        searchDebounceJob = null

        binding.etSearchQuery.setText("")
        binding.searchActionContainer.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                binding.searchActionContainer.visibility = View.GONE
                binding.searchActionContainer.alpha = 1f
                binding.headerColumnsRow.alpha = 0f
                binding.headerColumnsRow.visibility = View.VISIBLE
                binding.headerColumnsRow.animate().alpha(1f).setDuration(140).start()
            }
            .start()

        if (restoreDirectory) {
            viewModel.currentDir.value?.let { current ->
                viewModel.loadDirectory(current.absolutePath)
            }
        }
        updateActionUI()
    }

    private fun triggerSearch(query: String, debounce: Boolean) {
        searchDebounceJob?.cancel()
        searchDebounceJob = lifecycleScope.launch {
            if (debounce) {
                delay(SEARCH_DEBOUNCE_MS)
            }
            viewModel.searchFromCurrentDir(query)
        }
    }

    private fun showRenameDialog() {
        val selected = fileAdapter.selectedFiles.toList()
        if (selected.size != 1) return
        val file = selected.first()

        val editText = createDialogInput(
            hintRes = R.string.rename_hint,
            initialText = file.name
        )
        
        GameDialogBuilder(this)
            .setTitle(getString(R.string.rename_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.action_rename)) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != file.name) {
                    viewModel.renameFile(file, newName)
                    fileAdapter.clearSelection()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun copyToClipboard(isCut: Boolean) {
        val selected = fileAdapter.selectedFiles.toList()
        if (selected.isNotEmpty()) {
            viewModel.copyToClipboard(selected, isCut)
            fileAdapter.clearSelection()
            updateActionUI()
        }
    }

    private fun confirmDelete() {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.delete_files))
            .setMessage(getString(R.string.confirm_delete_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> 
                viewModel.deleteFiles(fileAdapter.selectedFiles.toList())
                fileAdapter.clearSelection() // UI update handles via observer
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun exportSelection() {
        val selected = fileAdapter.selectedFiles.toList()
        if (selected.isEmpty()) return
        
        if (selected.size == 1 && selected.first().isFile) {
            val file = selected.first()
            viewModel.prepareExport(selected)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_TITLE, file.name)
            }
            startActivityForResult(intent, REQUEST_CODE_EXPORT_SELECTION)
        } else {
            viewModel.prepareExport(selected)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "export.zip")
            }
            startActivityForResult(intent, REQUEST_CODE_EXPORT_SELECTION)
        }
    }

    private fun extractRpaSelection() {
        val selected = fileAdapter.selectedFiles.toList()
        if (selected.size != 1) return
        val rpaFile = selected.first()
        
        GameDialogBuilder(this)
            .setTitle(getString(R.string.extract_rpa_title))
            .setMessage(getString(R.string.confirm_extract_message, rpaFile.name))
            .setPositiveButton(getString(R.string.action_extract)) { _, _ ->
                performExtraction(rpaFile)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performExtraction(rpaFile: File) {
        val destDir = rpaFile.parentFile ?: filesDir // Fallback to filesDir
        val progressDialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.extracting))
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                RpaUtils.extractGameAssets(
                    rpaPath = rpaFile.absolutePath,
                    outputDir = destDir,
                    onProgress = { fileName, current, total ->
                        val progress = ((current.toFloat() / total.toFloat()) * 100).toInt()
                        runOnUiThread {
                            progressDialog.progress = progress
                            progressDialog.secondaryProgress = current
                            progressDialog.max = 100 
                        }
                    }
                )
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    InAppNotifier.show(this@FileExplorerActivity, getString(R.string.extraction_completed))
                    viewModel.loadDirectory(viewModel.currentDir.value?.absolutePath ?: rootDir.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    InAppNotifier.show(this@FileExplorerActivity, getString(R.string.extraction_error, e.message), true)
                }
            }
        }
    }
    
    private fun showImportDialog() {
        GameDialogBuilder(this)
            .setTitle(getString(R.string.import_title))
            .setItems(arrayOf(
                getString(R.string.import_files),
                getString(R.string.import_folder),
                getString(R.string.import_zip),
                getString(R.string.create_folder),
                getString(R.string.create_file)
            )) { _, which ->
                when (which) {
                    0 -> openImportSAFFile()
                    1 -> openImportSAFFolder()
                    2 -> openImportSAFZip()
                    3 -> showCreateFolderDialog()
                    4 -> showCreateFileDialog()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showCreateFolderDialog() {
        val editText = createDialogInput(hintRes = R.string.folder_name_hint)
        GameDialogBuilder(this)
            .setTitle(getString(R.string.create_folder))
            .setView(editText)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val folderName = editText.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    viewModel.createFolder(folderName)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showCreateFileDialog() {
        val editText = createDialogInput(hintRes = R.string.file_name_hint)
        GameDialogBuilder(this)
            .setTitle(getString(R.string.create_file))
            .setView(editText)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val fileName = editText.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    viewModel.createFile(fileName)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createDialogInput(hintRes: Int, initialText: String? = null): EditText {
        return EditText(this).apply {
            hint = getString(hintRes)
            setTextColor(ContextCompat.getColor(this@FileExplorerActivity, R.color.colorTextPrimary))
            setHintTextColor(ContextCompat.getColor(this@FileExplorerActivity, R.color.colorTextSecondary))
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this@FileExplorerActivity, R.color.colorPrimary)
            )
            initialText?.let { setText(it) }
        }
    }

    private fun openImportSAFFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        lockOrientationForImport()
        try {
            startActivityForResult(intent, REQUEST_CODE_IMPORT_FILE)
        } catch (e: ActivityNotFoundException) {
            unlockOrientationForImport()
            InAppNotifier.show(this, getString(R.string.import_failed, e.message ?: ""))
        } catch (e: SecurityException) {
            unlockOrientationForImport()
            InAppNotifier.show(this, getString(R.string.import_failed, e.message ?: ""))
        }
    }
    
    private fun openImportSAFZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORT_ZIP)
    }

    private fun openImportSAFFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            putExtra("android.content.extra.FANCY", true)
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORT_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMPORT_FILE && resultCode != android.app.Activity.RESULT_OK) {
            unlockOrientationForImport()
            return
        }
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return

        val currentDir = viewModel.currentDir.value
        if (currentDir == null) {
            if (requestCode == REQUEST_CODE_IMPORT_FILE) {
                unlockOrientationForImport()
            }
            return
        }

        when (requestCode) {
            REQUEST_CODE_IMPORT_FILE -> {
                val uris = mutableListOf<Uri>()
                if (data.clipData != null) {
                    for (i in 0 until data.clipData!!.itemCount) {
                        uris.add(data.clipData!!.getItemAt(i).uri)
                    }
                } else if (data.data != null) {
                    uris.add(data.data!!)
                }
                if (uris.isEmpty()) {
                    unlockOrientationForImport()
                    return
                }
                importUrisWithConflictResolution(uris, currentDir)
            }
            REQUEST_CODE_IMPORT_ZIP -> {
                data.data?.let { uri ->
                    viewModel.importZip(uri, applicationContext)
                }
            }
            REQUEST_CODE_IMPORT_FOLDER -> {
                data.data?.let { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                    }
                    viewModel.importFolderTree(uri, applicationContext)
                }
            }
            REQUEST_CODE_EXPORT_SELECTION -> {
                data.data?.let { uri ->
                    viewModel.finalizeExport(uri, applicationContext)
                }
            }
        }
    }
    
    private fun importUrisWithConflictResolution(uris: List<Uri>, destDir: File) {
        if (uris.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            var importedCount = 0
            var ignoredCount = 0
            var failedCount = 0
            var rememberedAction: ImportConflictAction? = null

            try {
                for (uri in uris) {
                    try {
                        val originalName = sanitizeImportName(getFileName(uri) ?: "imported_file")
                        var target = File(destDir, originalName)
                        if (target.exists()) {
                            val action = if (rememberedAction != null) {
                                rememberedAction!!
                            } else {
                                val decision = askImportConflictDecision(target.name)
                                if (decision == null) {
                                    ignoredCount++
                                    continue
                                }
                                if (decision.applyToAll) {
                                    rememberedAction = decision.action
                                }
                                decision.action
                            }

                            when (action) {
                                ImportConflictAction.REPLACE -> {
                                    if (!deleteExistingTarget(target)) {
                                        failedCount++
                                        continue
                                    }
                                }
                                ImportConflictAction.IGNORE -> {
                                    ignoredCount++
                                    continue
                                }
                                ImportConflictAction.COPY_INCREMENT -> {
                                    target = uniqueTargetFile(destDir, originalName)
                                }
                            }
                        }

                        copyUriToFile(uri, target)
                        importedCount++
                    } catch (e: IOException) {
                        e.printStackTrace()
                        failedCount++
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                        failedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    viewModel.loadDirectory(destDir.absolutePath)
                    val message = getString(
                        R.string.import_summary_message,
                        importedCount,
                        ignoredCount,
                        failedCount
                    )
                    InAppNotifier.show(this@FileExplorerActivity, message, failedCount > 0)
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    unlockOrientationForImport()
                }
            }
        }
    }

    private suspend fun askImportConflictDecision(fileName: String): ImportConflictDecision? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val checkBox = CheckBox(this@FileExplorerActivity).apply {
                    text = getString(R.string.import_conflict_apply_all)
                    setTextColor(ContextCompat.getColor(this@FileExplorerActivity, R.color.colorTextPrimary))
                }

                val dialog = GameDialogBuilder(this@FileExplorerActivity)
                    .setTitle(getString(R.string.existing_file))
                    .setMessage(getString(R.string.existing_file_message, fileName))
                    .setView(checkBox)
                    .setPositiveButton(getString(R.string.overwrite)) { _, _ ->
                        if (continuation.isActive) {
                            continuation.resume(
                                ImportConflictDecision(
                                    action = ImportConflictAction.REPLACE,
                                    applyToAll = checkBox.isChecked
                                )
                            )
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                        if (continuation.isActive) continuation.resume(null)
                    }
                    .setItems(
                        arrayOf(
                            getString(R.string.import_conflict_ignore),
                            getString(R.string.import_conflict_copy_increment)
                        )
                    ) { _, which ->
                        val action = when (which) {
                            0 -> ImportConflictAction.IGNORE
                            else -> ImportConflictAction.COPY_INCREMENT
                        }
                        if (continuation.isActive) {
                            continuation.resume(
                                ImportConflictDecision(
                                    action = action,
                                    applyToAll = checkBox.isChecked
                                )
                            )
                        }
                    }
                    .create()

                dialog.setOnCancelListener {
                    if (continuation.isActive) continuation.resume(null)
                }
                dialog.show()
                continuation.invokeOnCancellation { dialog.dismiss() }
            }
        }
    }

    private fun sanitizeImportName(name: String): String {
        return name.trim().ifBlank { "imported_file" }
            .replace('/', '_')
            .replace('\\', '_')
    }

    private fun lockOrientationForImport() = Unit

    private fun unlockOrientationForImport() = Unit

    @Throws(IOException::class)
    private fun copyUriToFile(uri: Uri, dest: File) {
        dest.parentFile?.mkdirs()
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output, 1024 * 1024)
            }
        } ?: throw IOException("Cannot open source stream")
    }

    private fun deleteExistingTarget(target: File): Boolean {
        if (!target.exists()) return true
        return if (target.isDirectory) {
            target.deleteRecursively()
        } else {
            target.delete()
        }
    }
    
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("_display_name")
                    if (idx >= 0) result = it.getString(idx)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun isRestorablePath(path: String): Boolean {
        val restoredDir = File(path)
        if (!restoredDir.exists() || !restoredDir.isDirectory) return false

        return try {
            val rootCanonicalPath = rootDir.canonicalPath
            val restoredCanonicalPath = restoredDir.canonicalPath
            restoredCanonicalPath == rootCanonicalPath ||
                restoredCanonicalPath.startsWith("$rootCanonicalPath${File.separator}")
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun uniqueTargetFile(parent: File, fileName: String): File {
        val sanitized = fileName.trim().ifBlank { "imported_file" }
            .replace('/', '_')
            .replace('\\', '_')
        val dot = sanitized.lastIndexOf('.')
        val hasExt = dot > 0 && dot < sanitized.length - 1
        val base = if (hasExt) sanitized.substring(0, dot) else sanitized
        val ext = if (hasExt) sanitized.substring(dot) else ""

        var candidate = File(parent, sanitized)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parent, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }

    private fun isInternalRootPath(candidateRoot: File): Boolean {
        return try {
            candidateRoot.canonicalFile == filesDir.canonicalFile
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun openSystemInternalFiles() {
        val authority = InternalStorageDocumentsProvider.authorityForPackage(packageName)
        val rootUri = DocumentsContract.buildRootUri(authority, InternalStorageDocumentsProvider.ROOT_ID)
        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, "root")

        val browseIntent = Intent(Intent.ACTION_VIEW).apply {
            data = rootUri
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        val fallbackIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }

        val canOpenBrowse = browseIntent.resolveActivity(packageManager) != null
        val canOpenFallback = fallbackIntent.resolveActivity(packageManager) != null
        if (!canOpenBrowse && !canOpenFallback) {
            InAppNotifier.show(this, R.string.documents_provider_open_unavailable)
            return
        }

        try {
            startActivity(browseIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(fallbackIntent)
            } catch (e: ActivityNotFoundException) {
                InAppNotifier.show(this, R.string.documents_provider_open_unavailable)
            } catch (e: SecurityException) {
                InAppNotifier.show(this, getString(R.string.documents_provider_open_failed, e.message ?: ""))
            }
        } catch (e: SecurityException) {
            InAppNotifier.show(this, getString(R.string.documents_provider_open_failed, e.message ?: ""))
        }
    }

    private fun updateBreadcrumbs(currentDir: File) {
        val container = binding.breadcrumbContainer
        container.removeAllViews()

        val pathParts = mutableListOf<File>()
        var temp: File? = currentDir
        while (temp != null) {
            pathParts.add(0, temp)
            if (temp.absolutePath == rootDir.absolutePath) break
            temp = temp.parentFile
        }

        pathParts.forEachIndexed { index, file ->
            val isLast = index == pathParts.size - 1
            
            val textView = TextView(this).apply {
                text = if (file.absolutePath == rootDir.absolutePath) {
                    "files"
                } else {
                    file.name.lowercase()
                }
                textSize = 14f
                setTextColor(if (isLast) 0xFFB45D85.toInt() else 0xCC7295B4.toInt())
                setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                if (!isLast) {
                    setOnClickListener {
                        SoundEffects.playClick(this@FileExplorerActivity)
                        viewModel.loadDirectory(file.absolutePath)
                    }
                }
            }
            container.addView(textView)

            if (!isLast) {
                val separator = TextView(this).apply {
                    text = ">"
                    textSize = 12f
                    setTextColor(0x88B45D85.toInt())
                }
                container.addView(separator)
            }
        }
        
        binding.breadcrumbScroll.post {
            binding.breadcrumbScroll.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private val Int.sp get() = this.toFloat()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.currentDir.value?.absolutePath?.let {
            outState.putString(STATE_CURRENT_DIR_PATH, it)
        }
    }

    override fun onDestroy() {
        searchDebounceJob?.cancel()
        searchDebounceJob = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (isSearchMode) {
            exitSearchMode()
        } else if (fileAdapter.getSelectedCount() > 0) {
            fileAdapter.clearSelection()
            updateActionUI()
        } else {
            super.onBackPressed()
        }
    }
}
