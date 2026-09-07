package org.renpy.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    // Launch Flow State
    sealed class LaunchState {
        object Idle : LaunchState()
        object LaunchGame : LaunchState()
        data class Error(val message: String) : LaunchState()
    }

    private val _launchState = MutableLiveData<LaunchState>(LaunchState.Idle)
    val launchState: LiveData<LaunchState> = _launchState
    
    // Export/Import State
    private val _operationStatus = MutableLiveData<String>()
    val operationStatus: LiveData<String> = _operationStatus
    
    private val _exportComplete = MutableLiveData<File?>()
    val exportComplete: LiveData<File?> = _exportComplete

    private var latestUpdateInfo: GitHubTranslationManager.UpdateInfo? = null
    
    fun handlePlayClick() {
        _launchState.value = LaunchState.LaunchGame
    }
    
    fun consumeLaunchState() {
        _launchState.value = LaunchState.Idle
    }

    fun exportSaves(savesDir: File, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val zipFile = File.createTempFile("saves_backup", ".zip", cacheDir)
                val tempExportDir = createTempExportDir(cacheDir)

                try {
                    copyDirectoryForExport(savesDir, tempExportDir)
                    zipDirectory(tempExportDir, zipFile)
                } finally {
                    if (tempExportDir.exists()) {
                        tempExportDir.deleteRecursively()
                    }
                }

                _exportComplete.postValue(zipFile)
                _operationStatus.postValue(getApplication<Application>().getString(R.string.status_export_ready))
            } catch (e: Exception) {
                _operationStatus.postValue(getApplication<Application>().getString(R.string.export_failed_toast, e.message))
            }
        }
    }
    
    fun importSaves(sourceZip: File, savesDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                unzipToDirectory(sourceZip, savesDir)
                _operationStatus.postValue(getApplication<Application>().getString(R.string.status_import_completed))
            } catch (e: Exception) {
                _operationStatus.postValue(getApplication<Application>().getString(R.string.launcher_import_failed, e.message))
            }
        }
    }

    private fun createTempExportDir(cacheDir: File): File {
        val tempFile = File.createTempFile("saves_export_tmp", "", cacheDir)
        if (!tempFile.delete()) {
            throw IOException("Failed to prepare temporary export directory")
        }
        if (!tempFile.mkdirs()) {
            throw IOException("Failed to create temporary export directory")
        }
        return tempFile
    }

    private fun copyDirectoryForExport(sourceDir: File, tempDir: File) {
        sourceDir.walkTopDown().forEach { file ->
            if (!file.isFile) {
                return@forEach
            }

            val relPath = file.relativeTo(sourceDir).path.replace("\\", "/")
            if (shouldIgnoreForZip(relPath, file)) {
                return@forEach
            }

            val outputFile = File(tempDir, relPath)
            outputFile.parentFile?.mkdirs()
            file.copyTo(outputFile, overwrite = true)
        }
    }

    private fun shouldIgnoreForZip(relPath: String, file: File): Boolean {
        return relPath.startsWith("android/") ||
            relPath.startsWith("sync/") ||
            file.name == "persistent.migrated" ||
            file.name.endsWith(".save")
    }

    private fun zipDirectory(folder: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            folder.walkTopDown().forEach { file ->
                if (!file.isFile) {
                    return@forEach
                }

                val relPath = file.relativeTo(folder).path.replace("\\", "/")
                if (shouldIgnoreForZip(relPath, file)) {
                    return@forEach
                }

                zos.putNextEntry(ZipEntry(relPath))
                FileInputStream(file).use { fis -> fis.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun unzipToDirectory(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        
        destDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name == "persistent.migrated") {
                file.delete()
            }
        }
    }
}
