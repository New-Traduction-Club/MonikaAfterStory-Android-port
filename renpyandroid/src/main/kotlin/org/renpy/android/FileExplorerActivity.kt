package org.renpy.android

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import org.renpy.android.databinding.FileExplorerActivityBinding
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import android.app.ProgressDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileExplorerActivity : BaseActivity() {

    private lateinit var binding: FileExplorerActivityBinding
    private val viewModel: FileExplorerViewModel by viewModels()
    private lateinit var fileAdapter: FileAdapter

    private lateinit var rootDir: File

    private val REQUEST_CODE_IMPORT_FILE = 1002
    private val REQUEST_CODE_IMPORT_FOLDER = 1003
    private val REQUEST_CODE_IMPORT_ZIP = 1004
    private val REQUEST_CODE_EXPORT_SELECTION = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FileExplorerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        fileAdapter = FileAdapter(
            onItemClick = { file ->
                if (file.isDirectory) {
                    viewModel.loadDirectory(file.absolutePath)
                } else {
                    
                }
            },
            onItemLongClick = { _ ->
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
            supportActionBar?.title = dir.name
        }

        viewModel.statusMessage.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        
        viewModel.hasClipboard.observe(this) { hasClip ->
            updateActionUI()
        }

        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnCopy.setOnClickListener { copyToClipboard(false) }
        binding.btnCut.setOnClickListener { copyToClipboard(true) }
        binding.btnExport.setOnClickListener { exportSelection() }
        binding.btnExtract.setOnClickListener { extractRpaSelection() }
        
        binding.fabPaste.setOnClickListener { 
            if (viewModel.hasClipboard.value == true) {
                viewModel.pasteToCurrentDir()
            } else {
                showImportDialog()
            }
        }

        val startPath = intent.getStringExtra("startPath") ?: filesDir.absolutePath
        rootDir = File(startPath)
        
        viewModel.loadDirectory(rootDir.absolutePath)
        
        binding.toolbar.inflateMenu(R.menu.file_explorer_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_create_folder -> {
                    showCreateFolderDialog()
                    true
                }
                R.id.action_import -> {
                    showImportDialog()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun updateActionUI() {
        val selectionCount = fileAdapter.getSelectedCount()
        val hasSelection = selectionCount > 0
        val hasClipboard = viewModel.hasClipboard.value == true
        
        binding.actionContainer.visibility = if (hasSelection) View.VISIBLE else View.GONE
        
        if (hasClipboard) {
            binding.fabPaste.setImageResource(R.drawable.ic_paste)
            binding.fabPaste.visibility = View.VISIBLE
        } else {
            binding.fabPaste.setImageResource(android.R.drawable.ic_menu_add)
            binding.fabPaste.visibility = View.VISIBLE
        }
        
        var showExtract = false
        if (selectionCount == 1) {
            val file = fileAdapter.selectedFiles.first()
            if (file.isFile && (file.name.endsWith(".rpa") || file.name.endsWith(".rpi"))) {
                showExtract = true
            }
        }
        binding.btnExtract.visibility = if (showExtract) View.VISIBLE else View.GONE
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
        AlertDialog.Builder(this)
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
        
        AlertDialog.Builder(this)
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
                    Toast.makeText(this@FileExplorerActivity, getString(R.string.extraction_completed), Toast.LENGTH_SHORT).show()
                    viewModel.loadDirectory(viewModel.currentDir.value?.absolutePath ?: rootDir.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@FileExplorerActivity, getString(R.string.extraction_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showImportDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_title))
            .setItems(arrayOf(
                getString(R.string.import_files),
                getString(R.string.import_zip),
                getString(R.string.create_folder)
            )) { _, which ->
                when (which) {
                    0 -> openImportSAFFile()
                    1 -> openImportSAFZip()
                    2 -> showCreateFolderDialog()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showCreateFolderDialog() {
        val editText = EditText(this)
        editText.hint = getString(R.string.folder_name_hint)
        AlertDialog.Builder(this)
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

    private fun openImportSAFFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORT_FILE)
    }
    
    private fun openImportSAFZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORT_ZIP)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return
        
        val currentDir = viewModel.currentDir.value ?: return

        when (requestCode) {
            REQUEST_CODE_IMPORT_FILE -> {
                if (data.clipData != null) {
                    for (i in 0 until data.clipData!!.itemCount) {
                        importUri(data.clipData!!.getItemAt(i).uri, currentDir)
                    }
                } else if (data.data != null) {
                    importUri(data.data!!, currentDir)
                }
                viewModel.loadDirectory(currentDir.absolutePath)
            }
            REQUEST_CODE_IMPORT_ZIP -> {
                data.data?.let { uri ->
                    viewModel.importZip(uri, applicationContext)
                }
            }
            REQUEST_CODE_EXPORT_SELECTION -> {
                data.data?.let { uri ->
                    viewModel.finalizeExport(uri, applicationContext)
                }
            }
        }
    }
    
    private fun importUri(uri: Uri, destDir: File) {
        val name = getFileName(uri) ?: "imported_file"
        val dest = File(destDir, name)
        Thread {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                runOnUiThread { viewModel.loadDirectory(destDir.absolutePath) }
            } catch(e: Exception) { e.printStackTrace() }
        }.start()
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

    override fun onBackPressed() {
        if (fileAdapter.getSelectedCount() > 0) {
            fileAdapter.clearSelection()
            updateActionUI()
        } else {
            val current = viewModel.currentDir.value
            if (current != null && current.absolutePath != rootDir.absolutePath) {
                viewModel.navigateUp(rootDir.absolutePath)
            } else {
                super.onBackPressed()
            }
        }
    }
}