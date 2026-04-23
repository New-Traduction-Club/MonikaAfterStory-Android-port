package org.renpy.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipInputStream

object SpritepackInstallerUtils {

    private val MAS_FOLDERS = setOf("a", "b", "c", "ch", "f", "h", "j", "t")
    private val MAS_ASSET_EXTENSIONS = setOf("png", "json")

    enum class ArchiveFormat(val extension: String) {
        ZIP("zip"),
        RAR("rar")
    }

    enum class VirtualRootLevel {
        ABSOLUTE_GAME_PATH,
        MONIKA_FOLDER,
        LOOSE_MAS_FOLDERS
    }

    enum class InstallPhase {
        EXTRACTING_ARCHIVE,
        ANALYZING_STRUCTURE,
        MERGING_FILES
    }

    data class InstallReport(
        val archiveFormat: ArchiveFormat,
        val virtualRootLevel: VirtualRootLevel,
        val filesMerged: Int,
        val directoriesCreated: Int,
        val destinationDir: File
    )

    class UnsupportedArchiveException(message: String) : IOException(message)

    class UnrecognizedStructureException(message: String) : IOException(message)

    interface RarExtractor {
        @Throws(IOException::class)
        fun extract(archiveFile: File, outputDir: File)
    }

    @Volatile
    private var rarExtractor: RarExtractor? = null

    fun registerRarExtractor(extractor: RarExtractor) {
        rarExtractor = extractor
    }

    fun clearRarExtractor() {
        rarExtractor = null
    }

    @Throws(IOException::class)
    fun installFromSafUri(
        context: Context,
        archiveUri: Uri,
        fileNameHint: String? = null,
        onPhaseChanged: ((InstallPhase) -> Unit)? = null
    ): InstallReport {
        val displayName = fileNameHint ?: resolveDisplayName(context, archiveUri)
        val archiveFormat = detectArchiveFormat(context, archiveUri, displayName)
        val workspaceDir = createWorkspace(context.filesDir)
        val archiveCopy = File(workspaceDir, "spritepack_source.${archiveFormat.extension}")
        val extractedDir = File(workspaceDir, "extracted")

        if (!extractedDir.mkdirs()) {
            workspaceDir.deleteRecursively()
            throw IOException("Cannot create temporary extraction directory")
        }

        try {
            onPhaseChanged?.invoke(InstallPhase.EXTRACTING_ARCHIVE)
            copyUriToFile(context, archiveUri, archiveCopy)
            extractArchive(archiveCopy, extractedDir, archiveFormat)

            onPhaseChanged?.invoke(InstallPhase.ANALYZING_STRUCTURE)
            val virtualRoot = findVirtualRoot(extractedDir)
                ?: throw UnrecognizedStructureException(
                    "Unrecognized Spritepack structure. Please verify that the file is compatible with MAS."
                )

            val destinationDir = File(context.filesDir, "game/mod_assets/monika")
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                throw IOException("Cannot create destination directory: ${destinationDir.absolutePath}")
            }

            onPhaseChanged?.invoke(InstallPhase.MERGING_FILES)
            val mergeStats = when (virtualRoot) {
                is VirtualRoot.MonikaRoot -> {
                    mergeDirectoryContents(virtualRoot.rootDir, destinationDir)
                }
                is VirtualRoot.LooseFoldersRoot -> {
                    mergeLooseFolders(virtualRoot, destinationDir)
                }
            }

            return InstallReport(
                archiveFormat = archiveFormat,
                virtualRootLevel = virtualRoot.level,
                filesMerged = mergeStats.filesMerged,
                directoriesCreated = mergeStats.directoriesCreated,
                destinationDir = destinationDir
            )
        } finally {
            workspaceDir.deleteRecursively()
        }
    }

    @Throws(IOException::class)
    private fun extractArchive(archiveFile: File, outputDir: File, format: ArchiveFormat) {
        when (format) {
            ArchiveFormat.ZIP -> extractZip(archiveFile, outputDir)
            ArchiveFormat.RAR -> {
                val extractor = rarExtractor
                    ?: throw UnsupportedArchiveException(
                        ".rar extraction problem"
                    )
                extractor.extract(archiveFile, outputDir)
            }
        }
    }

    @Throws(IOException::class)
    private fun extractZip(archiveFile: File, outputDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = resolveSafeEntryFile(outputDir, entry.name)
                if (outFile != null) {
                    if (entry.isDirectory) {
                        if (outFile.exists() && !outFile.isDirectory) {
                            throw IOException("Directory entry collides with a file: ${outFile.absolutePath}")
                        }
                        if (!outFile.exists() && !outFile.mkdirs()) {
                            throw IOException("Cannot create directory: ${outFile.absolutePath}")
                        }
                    } else {
                        val parent = outFile.parentFile
                        if (parent != null) {
                            if (parent.exists() && !parent.isDirectory) {
                                throw IOException("Parent path is not a directory: ${parent.absolutePath}")
                            }
                            if (!parent.exists() && !parent.mkdirs()) {
                                throw IOException("Cannot create directory: ${parent.absolutePath}")
                            }
                        }
                        if (outFile.exists() && outFile.isDirectory) {
                            throw IOException("File entry collides with a directory: ${outFile.absolutePath}")
                        }
                        FileOutputStream(outFile).use { out ->
                            zip.copyTo(out, DEFAULT_BUFFER_SIZE)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private sealed class VirtualRoot(val level: VirtualRootLevel) {
        class MonikaRoot(level: VirtualRootLevel, val rootDir: File) : VirtualRoot(level)
        class LooseFoldersRoot(val folders: List<File>) :
            VirtualRoot(VirtualRootLevel.LOOSE_MAS_FOLDERS)
    }

    private data class MergeStats(
        val filesMerged: Int,
        val directoriesCreated: Int
    )

    private fun findVirtualRoot(extractedDir: File): VirtualRoot? {
        val directories = dfsDirectories(extractedDir)

        directories.firstOrNull { isAbsoluteMonikaPath(it, extractedDir) && hasMasSignature(it) }?.let {
            return VirtualRoot.MonikaRoot(VirtualRootLevel.ABSOLUTE_GAME_PATH, it)
        }

        directories.firstOrNull { it.name.equals("monika", ignoreCase = true) && hasMasSignature(it) }?.let {
            return VirtualRoot.MonikaRoot(VirtualRootLevel.MONIKA_FOLDER, it)
        }

        directories.forEach { candidate ->
            val validFolders = candidate.listFiles()
                ?.filter { it.isDirectory && MAS_FOLDERS.contains(it.name.lowercase(Locale.US)) }
                ?.filter { folderContainsSpriteAssets(it) }
                ?.sortedBy { it.name.lowercase(Locale.US) }
                .orEmpty()

            if (validFolders.isNotEmpty()) {
                return VirtualRoot.LooseFoldersRoot(validFolders)
            }
        }

        return null
    }

    private fun hasMasSignature(monikaDir: File): Boolean {
        val hasKnownFolders = monikaDir.listFiles()
            ?.any { it.isDirectory && MAS_FOLDERS.contains(it.name.lowercase(Locale.US)) }
            ?: false
        return hasKnownFolders || folderContainsSpriteAssets(monikaDir)
    }

    private fun folderContainsSpriteAssets(folder: File): Boolean {
        return folder.walkTopDown().any { item ->
            item.isFile && hasSupportedAssetExtension(item.name)
        }
    }

    private fun hasSupportedAssetExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "")
        return MAS_ASSET_EXTENSIONS.contains(ext.lowercase(Locale.US))
    }

    private fun isAbsoluteMonikaPath(directory: File, extractedRoot: File): Boolean {
        val relative = directory.relativeTo(extractedRoot).path.replace('\\', '/').lowercase(Locale.US)
        return relative == "game/mod_assets/monika" ||
            relative.endsWith("/game/mod_assets/monika") ||
            relative == "mod_assets/monika" ||
            relative.endsWith("/mod_assets/monika")
    }

    private fun mergeLooseFolders(virtualRoot: VirtualRoot.LooseFoldersRoot, destinationDir: File): MergeStats {
        var filesMerged = 0
        var directoriesCreated = 0
        virtualRoot.folders.forEach { sourceFolder ->
            val destinationFolder = File(destinationDir, sourceFolder.name)
            val createdNow = !destinationFolder.exists()
            if (destinationFolder.exists() && !destinationFolder.isDirectory) {
                throw IOException("Destination path is not a directory: ${destinationFolder.absolutePath}")
            }
            if (!destinationFolder.exists() && !destinationFolder.mkdirs()) {
                throw IOException("Cannot create directory: ${destinationFolder.absolutePath}")
            }
            if (createdNow) {
                directoriesCreated++
            }
            val nested = mergeDirectoryContents(sourceFolder, destinationFolder)
            filesMerged += nested.filesMerged
            directoriesCreated += nested.directoriesCreated
        }
        return MergeStats(filesMerged = filesMerged, directoriesCreated = directoriesCreated)
    }

    private fun mergeDirectoryContents(sourceRoot: File, destinationRoot: File): MergeStats {
        var filesMerged = 0
        var directoriesCreated = 0

        sourceRoot.walkTopDown().forEach { item ->
            if (item == sourceRoot) {
                return@forEach
            }

            val relativePath = item.relativeTo(sourceRoot).path
            val target = File(destinationRoot, relativePath)

            if (item.isDirectory) {
                val createdNow = !target.exists()
                if (target.exists() && !target.isDirectory) {
                    throw IOException("Directory path collides with a file: ${target.absolutePath}")
                }
                if (!target.exists() && !target.mkdirs()) {
                    throw IOException("Cannot create directory: ${target.absolutePath}")
                }
                if (createdNow) {
                    directoriesCreated++
                }
            } else {
                val parent = target.parentFile
                if (parent != null) {
                    if (parent.exists() && !parent.isDirectory) {
                        throw IOException("Parent path is not a directory: ${parent.absolutePath}")
                    }
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IOException("Cannot create directory: ${parent.absolutePath}")
                    }
                }
                if (target.exists() && target.isDirectory) {
                    throw IOException("File path collides with a directory: ${target.absolutePath}")
                }
                item.copyTo(target, overwrite = true)
                filesMerged++
            }
        }

        return MergeStats(filesMerged = filesMerged, directoriesCreated = directoriesCreated)
    }

    private fun createWorkspace(filesDir: File): File {
        val workspace = File(
            filesDir,
            "spritepack_install_tmp_${System.currentTimeMillis()}_${(1000..9999).random()}"
        )
        if (!workspace.mkdirs()) {
            throw IOException("Cannot create temporary workspace")
        }
        return workspace
    }

    private fun copyUriToFile(context: Context, uri: Uri, target: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        } ?: throw IOException("Cannot open source stream for URI: $uri")
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx < 0 || cursor.isNull(nameIdx)) {
                    null
                } else {
                    cursor.getString(nameIdx)
                }
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun detectArchiveFormat(context: Context, uri: Uri, nameHint: String?): ArchiveFormat {
        inferFormatFromFileName(nameHint)?.let { return it }
        inferFormatFromFileName(uri.lastPathSegment)?.let { return it }

        val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.US)
        return when (mimeType) {
            "application/zip",
            "application/x-zip",
            "application/x-zip-compressed",
            "multipart/x-zip" -> ArchiveFormat.ZIP

            "application/x-rar",
            "application/x-rar-compressed",
            "application/vnd.rar" -> ArchiveFormat.RAR

            else -> throw UnsupportedArchiveException("Unsupported format. Only .zip or .rar files are allowed.")
        }
    }

    private fun inferFormatFromFileName(name: String?): ArchiveFormat? {
        if (name.isNullOrBlank()) return null
        val normalized = name.substringAfterLast('/')
        val extension = normalized.substringAfterLast('.', "").lowercase(Locale.US)
        return when (extension) {
            "zip" -> ArchiveFormat.ZIP
            "rar" -> ArchiveFormat.RAR
            else -> null
        }
    }

    private fun resolveSafeEntryFile(rootDir: File, entryName: String): File? {
        val cleanName = entryName.replace('\\', '/').trimStart('/').trim()
        if (cleanName.isEmpty()) {
            return null
        }

        val targetFile = File(rootDir, cleanName)
        val rootPath = rootDir.canonicalPath
        val targetPath = targetFile.canonicalPath
        val rootPrefix = if (rootPath.endsWith(File.separator)) rootPath else "$rootPath${File.separator}"

        if (targetPath != rootPath && !targetPath.startsWith(rootPrefix)) {
            throw IOException("Unsafe archive entry path: $entryName")
        }
        return targetFile
    }

    private fun dfsDirectories(root: File): List<File> {
        val result = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            result.add(current)

            val children = current.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name.lowercase(Locale.US) }
                .orEmpty()

            children.forEach { child ->
                stack.addLast(child)
            }
        }

        return result
    }
}
