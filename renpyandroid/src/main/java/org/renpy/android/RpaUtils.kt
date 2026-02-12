package org.renpy.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RpaUtils {

    /**
    * Extracts all files from an RPA file to a destination directory.
    * This function must be called from a Coroutine or a background thread to avoid freezes
    *
    * @param rpaPath Absolute path to the .rpa file
    * @param outputDir Directory where the files will be extracted
    * @param onProgress Optional callback to report progress (current file, total files)
    */
    suspend fun extractGameAssets(
        rpaPath: String, 
        outputDir: File,
        onProgress: ((String, Int, Int) -> Unit)? = null
    ) {
        withContext(Dispatchers.IO) {
            val rpaFile = File(rpaPath)
            if (!rpaFile.exists()) {
                throw java.io.FileNotFoundException("RPA file not found: $rpaPath")
            }

            RpaReader(rpaFile).use { reader ->
                val fileList = reader.getFileList()
                val totalFiles = fileList.size
                
                for ((index, fileName) in fileList.withIndex()) {
                    // Report progress
                    onProgress?.invoke(fileName, index + 1, totalFiles)
                    
                    val destFile = File(outputDir, fileName)
                    
                    // Ensure the parent directory exists
                    destFile.parentFile?.mkdirs()
                    
                    destFile.outputStream().use { fos ->
                        reader.extractFile(fileName, fos)
                    }
                }
            }
        }
    }
}
