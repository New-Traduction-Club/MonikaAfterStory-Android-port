package org.renpy.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return getApplication<Application>().getString(resId, *formatArgs)
    }

    private val _currentDir = MutableLiveData<File>()
    val currentDir: LiveData<File> = _currentDir

    private val _files = MutableLiveData<List<File>>()
    val files: LiveData<List<File>> = _files

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage
    
    private var clipboardFiles: List<File> = emptyList()
    private var isCutOperation: Boolean = false
    private val _hasClipboard = MutableLiveData<Boolean>(false)
    val hasClipboard: LiveData<Boolean> = _hasClipboard

    fun loadDirectory(path: String) {
        val file = File(path)
        if (file.exists() && file.isDirectory) {
            _currentDir.postValue(file)
            viewModelScope.launch(Dispatchers.IO) {
                val fileList = file.listFiles()?.sortedWith(
                    compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })
                ) ?: emptyList()
                _files.postValue(fileList)
            }
        }
    }

    fun navigateUp(rootDirPath: String) {
        val current = _currentDir.value ?: return
        if (current.absolutePath != rootDirPath) {
            current.parentFile?.let { loadDirectory(it.absolutePath) }
        }
    }
    
    fun createFolder(name: String) {
        val current = _currentDir.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val newFolder = File(current, name)
            if (newFolder.mkdirs()) {
                refreshCurrentDir()
                postMessage(getString(R.string.folder_created))
            } else {
                postMessage(getString(R.string.failed_to_create_folder))
            }
        }
    }

    fun createFile(name: String) {
        val current = _currentDir.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newFile = File(current, name)
                if (newFile.exists()) {
                    postMessage(getString(R.string.item_already_exists))
                } else if (newFile.createNewFile()) {
                    refreshCurrentDir()
                    postMessage(getString(R.string.file_created))
                } else {
                    postMessage(getString(R.string.failed_to_create_file))
                }
            } catch (e: Exception) {
                postMessage(getString(R.string.failed_to_create_file) + ": ${e.message}")
            }
        }
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newFile = File(file.parentFile, newName)
                if (newFile.exists()) {
                    postMessage(getString(R.string.item_already_exists))
                    return@launch
                }
                
                if (file.renameTo(newFile)) {
                    refreshCurrentDir()
                    postMessage(getString(R.string.renamed_successfully))
                } else {
                    postMessage(getString(R.string.failed_to_rename))
                }
            } catch (e: Exception) {
                postMessage(getString(R.string.error_renaming, e.message ?: ""))
            }
        }
    }

    fun deleteFiles(filesToDelete: List<File>) {
        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            
            val sortedFiles = filesToDelete.sortedByDescending { it.absolutePath.length }

            sortedFiles.forEach { file ->
                if (file.exists()) {
                    try {
                        if (file.deleteRecursively()) {
                            successCount++
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        failCount++
                    }
                }
            }
            
            refreshCurrentDir()
            
            if (failCount > 0) {
                postMessage(getString(R.string.deleted_items_mixed, successCount, failCount))
            } else {
                postMessage(getString(R.string.deleted_items, successCount))
            }
        }
    }

    fun copyToClipboard(files: List<File>, isCut: Boolean) {
        clipboardFiles = files
        isCutOperation = isCut
        _hasClipboard.value = true
        postMessage(if (isCut) getString(R.string.items_cut, files.size) else getString(R.string.items_copied, files.size))
    }

    fun pasteToCurrentDir() {
        val destDir = _currentDir.value ?: return
        val filesToPaste = clipboardFiles.toList()
        if (filesToPaste.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                filesToPaste.forEach { file ->
                    val dest = File(destDir, file.name)
                    if (isCutOperation) {
                        if (file.renameTo(dest)) {
                        } else {
                            if (!dest.exists()) {
                                file.copyRecursively(dest, overwrite = true)
                                file.deleteRecursively()
                            }
                        }
                    } else {
                        file.copyRecursively(dest, overwrite = true)
                    }
                }
                
                if (isCutOperation) {
                    clearClipboard()
                }
                
                refreshCurrentDir()
                postMessage(getString(R.string.paste_success))
            } catch (e: Exception) {
                postMessage(getString(R.string.error_pasting, e.message ?: ""))
            }
        }
    }
    
    private fun clearClipboard() {
        clipboardFiles = emptyList()
        isCutOperation = false
        _hasClipboard.postValue(false)
    }

    private fun refreshCurrentDir() {
        _currentDir.value?.let { loadDirectory(it.absolutePath) }
    }
    
    private fun postMessage(msg: String) {
        _statusMessage.postValue(msg)
    }

    private var filesToExport: List<File> = emptyList()

    fun prepareExport(files: List<File>) {
        filesToExport = files
    }

    fun finalizeExport(uri: android.net.Uri, context: android.content.Context) {
        val files = filesToExport
        if (files.isEmpty()) {
            postMessage(getString(R.string.no_files_export))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    if (files.size == 1 && files.first().isFile) {
                        FileInputStream(files.first()).use { input ->
                            input.copyTo(output)
                        }
                    } else {
                        ZipOutputStream(output).use { zos ->
                            files.forEach { file ->
                                zipFileRecursively(file, file.name, zos)
                            }
                        }
                    }
                }
                postMessage(getString(R.string.export_completed))
            } catch (e: Exception) {
                postMessage(getString(R.string.export_error, e.message ?: ""))
            } finally {
                filesToExport = emptyList()
            }
        }
    }

    private fun zipFileRecursively(file: File, fileName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                zipFileRecursively(child, "$fileName/${child.name}", zos)
            }
        } else {
            val entry = ZipEntry(fileName)
            zos.putNextEntry(entry)
            FileInputStream(file).use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
    }
    fun importZip(uri: android.net.Uri, context: android.content.Context) {
        val destDir = _currentDir.value ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            val tempZipObj = File.createTempFile("import_temp", ".zip", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempZipObj).use { output ->
                        input.copyTo(output)
                    }
                }
                
                java.util.zip.ZipFile(tempZipObj).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val outFile = File(destDir, entry.name)
                        
                        // Prevent Zip Slip
                        if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                             // Skip malicious entry
                        } else {
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(outFile).use { fos ->
                                        input.copyTo(fos)
                                    }
                                }
                            }
                        }
                    }
                }
                
                refreshCurrentDir()
                postMessage(getString(R.string.status_import_completed))
                
            } catch (e: Exception) {
                postMessage(getString(R.string.import_failed, e.message ?: ""))
            } finally {
                if (tempZipObj.exists()) tempZipObj.delete()
            }
        }
    }
}
