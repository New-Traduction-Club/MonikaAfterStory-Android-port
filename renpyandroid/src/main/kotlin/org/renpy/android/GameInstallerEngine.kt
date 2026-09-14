package org.renpy.android

import com.github.junrar.Archive
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream

object GameInstallerEngine {

    const val EXPECTED_DDLC_SHA256 = "2A3DD7969A06729A32ACE0A6ECE5F2327E29BDF460B8B39E6A8B0875E545632E"
    private const val BUFFER_SIZE = 1024 * 1024

    @Volatile
    private var isSevenZipInitialized = false

    fun ensureSevenZipInitialized() {
        if (isSevenZipInitialized) return
        synchronized(this) {
            if (isSevenZipInitialized) return
            try {
                if (!SevenZip.isInitializedSuccessfully()) {
                    try {
                        System.loadLibrary("7-Zip-JBinding")
                        SevenZip.initLoadedLibraries()
                    } catch (_: Throwable) {
                        try {
                            SevenZip.initSevenZipFromPlatformJAR()
                        } catch (_: Throwable) {
                        }
                    }
                }
                isSevenZipInitialized = SevenZip.isInitializedSuccessfully()
            } catch (_: Throwable) {
            }
        }
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    fun resolveUniqueFolder(baseDir: File, desiredName: String): File {
        val cleanName = sanitizeFolderName(desiredName)
        val initialFolder = File(baseDir, cleanName)
        if (!initialFolder.exists()) {
            return initialFolder
        }

        var counter = 1
        while (true) {
            val candidate = File(baseDir, "$cleanName ($counter)")
            if (!candidate.exists()) {
                return candidate
            }
            counter++
        }
    }

    fun sanitizeFolderName(name: String): String {
        val trimmed = name.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
        return if (trimmed.isEmpty()) "imported_game" else trimmed
    }

    fun isRarArchive(file: File): Boolean {
        if (file.name.lowercase().endsWith(".rar")) {
            return true
        }
        if (!file.exists() || file.length() < 4) {
            return false
        }
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                read == 4 &&
                        header[0] == 0x52.toByte() && // 'R'
                        header[1] == 0x61.toByte() && // 'a'
                        header[2] == 0x72.toByte() && // 'r'
                        header[3] == 0x21.toByte()    // '!'
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isRar5Archive(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(8)
                val read = fis.read(header)
                read >= 8 &&
                        header[0] == 0x52.toByte() &&
                        header[1] == 0x61.toByte() &&
                        header[2] == 0x72.toByte() &&
                        header[3] == 0x21.toByte() &&
                        header[4] == 0x1A.toByte() &&
                        header[5] == 0x07.toByte() &&
                        header[6] == 0x01.toByte() &&
                        header[7] == 0x00.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    @Throws(IOException::class, CancellationException::class)
    fun extractArchive(
        archiveFile: File,
        destinationDir: File,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null
    ) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        if (isRarArchive(archiveFile)) {
            val extractedWithSevenZip = try {
                extractArchiveWithSevenZip(archiveFile, destinationDir, isCancelled, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isRar5Archive(archiveFile)) {
                    throw e
                }
                false
            }

            if (extractedWithSevenZip) {
                return
            }

            extractRarArchive(archiveFile, destinationDir, isCancelled, onProgress)
        } else {
            try {
                extractZipArchive(archiveFile, destinationDir, isCancelled, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val extractedWithSevenZip = try {
                    extractArchiveWithSevenZip(archiveFile, destinationDir, isCancelled, onProgress)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    false
                }
                if (!extractedWithSevenZip) {
                    throw e
                }
            }
        }
    }

    @Throws(IOException::class, CancellationException::class)
    fun extractArchiveWithSevenZip(
        archiveFile: File,
        destinationDir: File,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        ensureSevenZipInitialized()
        if (!SevenZip.isInitializedSuccessfully()) {
            return false
        }

        var randomAccessFile: RandomAccessFile? = null
        var inStream: RandomAccessFileInStream? = null
        var inArchive: IInArchive? = null

        try {
            randomAccessFile = RandomAccessFile(archiveFile, "r")
            inStream = RandomAccessFileInStream(randomAccessFile)
            inArchive = SevenZip.openInArchive(null, inStream) ?: return false

            val totalBytes = archiveFile.length().coerceAtLeast(1L)
            var extractedBytes = 0L

            class ExtractCallback : IArchiveExtractCallback {
                private var currentFos: FileOutputStream? = null

                override fun setTotal(total: Long) {}

                override fun setCompleted(complete: Long) {}

                override fun getStream(index: Int, extractAskMode: ExtractAskMode?): ISequentialOutStream? {
                    if (isCancelled()) throw CancellationException("Extraction cancelled")
                    if (extractAskMode != ExtractAskMode.EXTRACT) return null

                    val path = inArchive?.getStringProperty(index, PropID.PATH) ?: return null
                    val isFolder = inArchive?.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false

                    val safeFile = resolveSafeEntryFile(destinationDir, path) ?: return null

                    if (isFolder) {
                        safeFile.mkdirs()
                        return null
                    } else {
                        safeFile.parentFile?.mkdirs()
                        val fos = FileOutputStream(safeFile)
                        currentFos = fos
                        return ISequentialOutStream { data ->
                            if (isCancelled()) throw CancellationException("Extraction cancelled")
                            fos.write(data)
                            extractedBytes += data.size
                            onProgress?.invoke((extractedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                            data.size
                        }
                    }
                }

                override fun prepareOperation(extractAskMode: ExtractAskMode?) {}

                override fun setOperationResult(extractOperationResult: ExtractOperationResult?) {
                    try {
                        currentFos?.close()
                    } catch (_: Exception) {
                    }
                    currentFos = null

                    if (isCancelled()) throw CancellationException("Extraction cancelled")
                    if (extractOperationResult != ExtractOperationResult.OK) {
                        throw IOException("7-Zip extraction item failed: $extractOperationResult")
                    }
                }
            }

            inArchive.extract(null, false, ExtractCallback())
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: SevenZipException) {
            throw IOException("7-Zip extraction error: ${e.message}", e)
        } finally {
            try {
                inArchive?.close()
            } catch (_: Exception) {
            }
            try {
                inStream?.close()
            } catch (_: Exception) {
            }
            try {
                randomAccessFile?.close()
            } catch (_: Exception) {
            }
        }
    }

    @Throws(IOException::class, CancellationException::class)
    private fun extractZipArchive(
        zipFile: File,
        destinationDir: File,
        isCancelled: () -> Boolean,
        onProgress: ((Float) -> Unit)?
    ) {
        val totalBytes = zipFile.length().coerceAtLeast(1L)
        var extractedBytes = 0L

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (isCancelled()) {
                    throw CancellationException("Extraction cancelled")
                }

                val safeFile = resolveSafeEntryFile(destinationDir, entry.name)
                if (safeFile != null) {
                    if (entry.isDirectory) {
                        safeFile.mkdirs()
                    } else {
                        safeFile.parentFile?.mkdirs()
                        FileOutputStream(safeFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } != -1) {
                                if (isCancelled()) {
                                    throw CancellationException("Extraction cancelled")
                                }
                                fos.write(buffer, 0, len)
                                extractedBytes += len
                            }
                        }
                    }
                }

                onProgress?.invoke((extractedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    @Throws(IOException::class, CancellationException::class)
    private fun extractRarArchive(
        rarFile: File,
        destinationDir: File,
        isCancelled: () -> Boolean,
        onProgress: ((Float) -> Unit)?
    ) {
        val totalBytes = rarFile.length().coerceAtLeast(1L)
        var extractedBytes = 0L

        Archive(rarFile).use { arch ->
            var header = arch.nextFileHeader()
            while (header != null) {
                if (isCancelled()) {
                    throw CancellationException("Extraction cancelled")
                }

                val entryName = header.fileName
                val safeFile = resolveSafeEntryFile(destinationDir, entryName)
                if (safeFile != null) {
                    if (header.isDirectory) {
                        safeFile.mkdirs()
                    } else {
                        safeFile.parentFile?.mkdirs()
                        FileOutputStream(safeFile).use { fos ->
                            arch.extractFile(header, fos)
                            extractedBytes += header.fullPackSize
                        }
                    }
                }

                onProgress?.invoke((extractedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                header = arch.nextFileHeader()
            }
        }
    }

    fun resolveSafeEntryFile(destDir: File, entryName: String): File? {
        val normalized = entryName.replace('\\', '/').trimStart('/')
        if (normalized.isEmpty()) return null

        val target = File(destDir, normalized)
        val canonicalDest = destDir.canonicalPath
        val canonicalTarget = target.canonicalPath

        if (!canonicalTarget.startsWith(canonicalDest + File.separator) && canonicalTarget != canonicalDest) {
            return null
        }
        return target
    }

    fun hasGameDirectory(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val children = dir.listFiles() ?: return false
        return children.any { it.isDirectory && it.name.equals("game", ignoreCase = true) }
    }

    fun findRenpyGameRoot(dir: File, maxDepth: Int = 4): File? {
        if (hasGameDirectory(dir)) {
            return dir
        }
        if (maxDepth <= 0) return null

        val subdirs = dir.listFiles()?.filter {
            it.isDirectory &&
                    !it.name.startsWith(".") &&
                    !it.name.equals("__MACOSX", ignoreCase = true)
        } ?: return null

        val matchingDirectChildren = subdirs.filter { hasGameDirectory(it) }
        if (matchingDirectChildren.size == 1) {
            return matchingDirectChildren[0]
        } else if (matchingDirectChildren.size > 1) {
            val withRenpy = matchingDirectChildren.find { child ->
                child.listFiles()?.any { it.isDirectory && it.name.equals("renpy", ignoreCase = true) } == true
            }
            return withRenpy ?: matchingDirectChildren[0]
        }

        for (subdir in subdirs) {
            val deepFound = findRenpyGameRoot(subdir, maxDepth - 1)
            if (deepFound != null) {
                return deepFound
            }
        }

        return null
    }

    fun resolveEffectiveSourceRoot(extractedDir: File): File {
        val renpyRoot = findRenpyGameRoot(extractedDir)
        if (renpyRoot != null) {
            return renpyRoot
        }

        val entries = extractedDir.listFiles() ?: return extractedDir
        val meaningfulDirs = entries.filter {
            it.isDirectory &&
                    !it.name.startsWith(".") &&
                    !it.name.equals("__MACOSX", ignoreCase = true)
        }

        if (meaningfulDirs.size == 1) {
            val singleDir = meaningfulDirs[0]
            val dirName = singleDir.name.lowercase()
            if (dirName != "game" && dirName != "lib" && dirName != "renpy" && dirName != "characters") {
                return resolveEffectiveSourceRoot(singleDir)
            }
        }

        return extractedDir
    }

    fun resolveBaseGameDir(tempDdlcDir: File): File {
        if (File(tempDdlcDir, "game").isDirectory) {
            return tempDdlcDir
        }

        val subdirs = tempDdlcDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (subdirs.size == 1 && File(subdirs[0], "game").isDirectory) {
            return subdirs[0]
        }

        for (subdir in subdirs) {
            if (File(subdir, "game").isDirectory) {
                return subdir
            }
        }

        return tempDdlcDir
    }

    fun recursiveCopy(
        source: File,
        destination: File,
        isCancelled: () -> Boolean = { false }
    ) {
        if (isCancelled()) throw CancellationException("Cancelled")

        if (source.isFile) {
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
            return
        }

        if (!destination.exists()) {
            destination.mkdirs()
        }

        val children = source.listFiles() ?: return
        for (child in children) {
            if (isCancelled()) throw CancellationException("Cancelled")
            val target = File(destination, child.name)
            recursiveCopy(child, target, isCancelled)
        }
    }

    @Throws(Exception::class)
    fun installGenericGame(
        archiveFile: File,
        targetDir: File,
        title: String?,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null
    ): File {
        val tempExtractDir = File(targetDir.parentFile, ".temp_extract_${System.currentTimeMillis()}")
        try {
            if (tempExtractDir.exists()) tempExtractDir.deleteRecursively()
            tempExtractDir.mkdirs()

            extractArchive(archiveFile, tempExtractDir, isCancelled, onProgress)

            if (isCancelled()) throw CancellationException("Cancelled")

            val effectiveRoot = resolveEffectiveSourceRoot(tempExtractDir)

            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val items = effectiveRoot.listFiles() ?: emptyArray()
            for (item in items) {
                if (isCancelled()) throw CancellationException("Cancelled")
                val targetName = when {
                    item.isDirectory && item.name.equals("game", ignoreCase = true) -> "game"
                    item.isDirectory && item.name.equals("renpy", ignoreCase = true) -> "renpy"
                    else -> item.name
                }
                val dest = File(targetDir, targetName)
                recursiveCopy(item, dest, isCancelled)
            }

            if (effectiveRoot != tempExtractDir) {
                var ancestor: File? = effectiveRoot.parentFile
                while (ancestor != null) {
                    val rootFiles = ancestor.listFiles()?.filter { it.isFile } ?: emptyList()
                    for (file in rootFiles) {
                        if (isCancelled()) throw CancellationException("Cancelled")
                        val dest = File(targetDir, file.name)
                        if (!dest.exists()) {
                            file.copyTo(dest, overwrite = false)
                        }
                    }
                    if (ancestor == tempExtractDir || ancestor.canonicalPath == tempExtractDir.canonicalPath) {
                        break
                    }
                    ancestor = ancestor.parentFile
                }
            }

            MineLauncherConfigHelper.setGameTitle(targetDir, title)
            RenpyVersionDetector.detectAndSave(targetDir)

            return targetDir
        } catch (e: Exception) {
            if (targetDir.exists()) targetDir.deleteRecursively()
            throw e
        } finally {
            if (tempExtractDir.exists()) {
                tempExtractDir.deleteRecursively()
            }
        }
    }

    @Throws(Exception::class)
    fun installDdlcMod(
        modArchiveFile: File,
        baseDdlcZipFile: File,
        targetDir: File,
        title: String?,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null
    ): File {
        val baseHash = computeSha256(baseDdlcZipFile)
        if (!baseHash.equals(EXPECTED_DDLC_SHA256, ignoreCase = true)) {
            throw IllegalArgumentException("Base DDLC archive SHA-256 mismatch: $baseHash")
        }

        if (isCancelled()) throw CancellationException("Cancelled")

        val tempDdlc = File(targetDir, "temp_ddlc")
        val tempMod = File(targetDir, "temp_mod")

        try {
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            tempDdlc.mkdirs()
            tempMod.mkdirs()

            extractArchive(baseDdlcZipFile, tempDdlc, isCancelled) { p ->
                onProgress?.invoke(p * 0.4f)
            }

            if (isCancelled()) throw CancellationException("Cancelled")

            extractArchive(modArchiveFile, tempMod, isCancelled) { p ->
                onProgress?.invoke(0.4f + p * 0.3f)
            }

            if (isCancelled()) throw CancellationException("Cancelled")

            val baseRoot = resolveBaseGameDir(tempDdlc)
            val baseEntries = baseRoot.listFiles() ?: emptyArray()
            for (entry in baseEntries) {
                if (entry.name == "temp_ddlc" || entry.name == "temp_mod") continue
                val dest = File(targetDir, entry.name)
                recursiveCopy(entry, dest, isCancelled)
            }

            if (isCancelled()) throw CancellationException("Cancelled")

            val modRoot = resolveEffectiveSourceRoot(tempMod)
            val hasGameDir = File(modRoot, "game").isDirectory || File(modRoot, "Game").isDirectory

            if (hasGameDir) {
                val modEntries = modRoot.listFiles() ?: emptyArray()
                for (entry in modEntries) {
                    if (entry.name == "temp_ddlc" || entry.name == "temp_mod") continue
                    val dest = File(targetDir, entry.name)
                    recursiveCopy(entry, dest, isCancelled)
                }
            } else {
                val targetGameDir = File(targetDir, "game")
                targetGameDir.mkdirs()
                val modEntries = modRoot.listFiles() ?: emptyArray()
                for (entry in modEntries) {
                    if (entry.name == "temp_ddlc" || entry.name == "temp_mod") continue
                    val dest = File(targetGameDir, entry.name)
                    recursiveCopy(entry, dest, isCancelled)
                }
            }

            tempDdlc.deleteRecursively()
            tempMod.deleteRecursively()

            onProgress?.invoke(1.0f)

            MineLauncherConfigHelper.setGameTitle(targetDir, title)
            RenpyVersionDetector.detectAndSave(targetDir)

            return targetDir
        } catch (e: Exception) {
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            throw e
        } finally {
            if (tempDdlc.exists()) tempDdlc.deleteRecursively()
            if (tempMod.exists()) tempMod.deleteRecursively()
        }
    }
}
