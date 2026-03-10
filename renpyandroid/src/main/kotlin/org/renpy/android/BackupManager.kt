package org.renpy.android

import android.content.Context
import android.util.Log
import java.io.*
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val TAG = "BackupManager"

    // interface for tracking progress
    interface ProgressListener {
        fun onProgress(percentage: Int, currentFile: String)
    }

    /**
     * Creates a backup ZIP file recursively containing all internal files, except 'LauncherUpdates'.
     * The backup is placed in external_storage/backups/.
     *
     * @return The absolute path of the created backup file, or null if it failed.
     */
    fun createBackup(context: Context, listener: ProgressListener? = null): String? {
        return try {
            val externalDir = context.getExternalFilesDir(null) ?: return null
            val backupsDir = File(externalDir, "backups")
            if (!backupsDir.exists()) backupsDir.mkdirs()

            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val backupFileName = "mas_backup_$timestamp.zip"
            val backupFile = File(backupsDir, backupFileName)

            val fos = FileOutputStream(backupFile)
            val zos = ZipOutputStream(BufferedOutputStream(fos))
            // Maximum compression
            zos.setLevel(Deflater.BEST_COMPRESSION)

            val sourceDir = context.filesDir
            val excludeDirName = "LauncherUpdates"
            
            // Calculate total size for progress tracking
            val totalSize = calculateTotalSize(sourceDir, sourceDir.absolutePath, excludeDirName)
            var currentProcessedSize = 0L

            zipDirectory(sourceDir, sourceDir.absolutePath, excludeDirName, zos) { file ->
                currentProcessedSize += file.length()
                if (totalSize > 0) {
                    val progress = ((currentProcessedSize.toDouble() / totalSize) * 100).toInt()
                    listener?.onProgress(progress, file.name)
                }
            }

            zos.close()
            fos.close()

            backupFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error creating backup: ${e.message}", e)
            null
        }
    }

    private fun calculateTotalSize(dir: File, basePath: String, excludeName: String): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            if (file.name == excludeName && file.parentFile?.absolutePath == basePath) {
                continue
            }
            size += if (file.isDirectory) {
                calculateTotalSize(file, basePath, excludeName)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun zipDirectory(
        dir: File, 
        basePath: String, 
        excludeName: String, 
        zos: ZipOutputStream,
        onFileZipped: (File) -> Unit
    ) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.name == excludeName && file.parentFile?.absolutePath == basePath) {
                // Skip the root LauncherUpdates folder completely
                continue
            }

            if (file.isDirectory) {
                zipDirectory(file, basePath, excludeName, zos, onFileZipped)
            } else {
                try {
                    val filePath = file.absolutePath.substring(basePath.length + 1)
                    val entry = ZipEntry(filePath)
                    zos.putNextEntry(entry)

                    val fis = FileInputStream(file)
                    fis.copyTo(zos)
                    fis.close()
                    zos.closeEntry()
                    onFileZipped(file)
                } catch (e: Exception) {
                    Log.e(TAG, "Error zipping file ${file.name}", e)
                }
            }
        }
    }

    /**
     * Restores a backup from the given ZIP file.
     * Deletes all current contents in filesDir except 'LauncherUpdates', then extracts the archive.
     *
     * @return true if successful, false otherwise.
     */
    fun restoreBackup(context: Context, backupFile: File, listener: ProgressListener? = null): Boolean {
        return try {
            if (!backupFile.exists()) return false

            val rootDir = context.filesDir
            val excludeName = "LauncherUpdates"

            // Delete everything except LauncherUpdates
            deleteDirectoryContentsExcluding(rootDir, excludeName)

            // Calculate total compressed size for progress approximation
            val totalZipSize = backupFile.length()
            var currentProcessedZipSize = 0L

            // Unzip the backup
            val fis = FileInputStream(backupFile)
            val zis = ZipInputStream(BufferedInputStream(fis))

            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val extractedFile = File(rootDir, entry.name)

                // Protect against Path Traversal
                if (!extractedFile.canonicalPath.startsWith(rootDir.canonicalPath)) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                if (entry.isDirectory) {
                    extractedFile.mkdirs()
                } else {
                    extractedFile.parentFile?.mkdirs()
                    val fos = FileOutputStream(extractedFile)
                    val out = BufferedOutputStream(fos)
                    
                    val buffer = ByteArray(8192)
                    var count: Int
                    while (zis.read(buffer).also { count = it } != -1) {
                        out.write(buffer, 0, count)
                        currentProcessedZipSize += count
                        // Safe approximation since zip size is smaller than uncompressed, progress will jump but guarantees finish
                        if (totalZipSize > 0) {
                            var progress = ((currentProcessedZipSize.toDouble() / totalZipSize) * 100).toInt()
                            if (progress > 100) progress = 100
                            listener?.onProgress(progress, entry.name)
                        }
                    }
                    
                    out.flush()
                    out.close()
                    fos.close()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
            fis.close()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring backup: ${e.message}", e)
            false
        }
    }

    private fun deleteDirectoryContentsExcluding(dir: File, excludeName: String) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name == excludeName && file.parentFile == dir) {
                    // Skip 'LauncherUpdates' folder at the root level completely
                    continue
                }
                deleteDirectoryRecursive(file)
            } else {
                file.delete()
            }
        }
    }

    private fun deleteDirectoryRecursive(dir: File) {
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    deleteDirectoryRecursive(file)
                } else {
                    file.delete()
                }
            }
        }
        dir.delete()
    }

    /**
     * Lists all existing backups in the external backups directory, sorted by last modified (newest first).
     */
    fun listBackups(context: Context): List<File> {
        val externalDir = context.getExternalFilesDir(null) ?: return emptyList()
        val backupsDir = File(externalDir, "backups")
        if (!backupsDir.exists()) return emptyList()

        return backupsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".zip") && file.name.startsWith("mas_backup_")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
