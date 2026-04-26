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

object SubmodInstallerUtils {

    enum class ArchiveFormat(val extension: String) {
        ZIP("zip"),
        RAR("rar")
    }

    enum class InstallPhase {
        EXTRACTING_ARCHIVE,
        ANALYZING_STRUCTURE,
        MERGING_FILES
    }

    enum class InstallFlow {
        CORE_PATCH,
        ORDERED_SUBMOD,
        LOOSE_SUBMOD
    }

    data class InstallReport(
        val archiveFormat: ArchiveFormat,
        val installFlow: InstallFlow,
        val filesMerged: Int,
        val directoriesCreated: Int,
        val destinationRoot: File
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
        smartLooseRouting: Boolean = true,
        onPhaseChanged: ((InstallPhase) -> Unit)? = null
    ): InstallReport {
        val displayName = fileNameHint ?: resolveDisplayName(context, archiveUri)
        val archiveFormat = detectArchiveFormat(context, archiveUri, displayName)
        val workspaceDir = createWorkspace(context.filesDir)
        val archiveCopy = File(workspaceDir, "submod_source.${archiveFormat.extension}")
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
            validateSubmodSignature(extractedDir)
            val effectiveSourceRoot = resolveEffectiveSourceRoot(extractedDir)
            val installFlow = resolveInstallFlow(effectiveSourceRoot)

            onPhaseChanged?.invoke(InstallPhase.MERGING_FILES)
            val destinationRoot = context.filesDir
            val mergeStats = when (installFlow) {
                InstallFlow.CORE_PATCH -> mergeDirectoryContents(
                    sourceRoot = effectiveSourceRoot,
                    destinationRoot = destinationRoot
                )

                InstallFlow.ORDERED_SUBMOD -> mergeOrderedSubmodLayout(
                    extractedDir = effectiveSourceRoot,
                    filesDir = destinationRoot
                )

                InstallFlow.LOOSE_SUBMOD -> {
                    val looseSourceRoot = resolveLooseSourceRoot(effectiveSourceRoot)
                    if (!smartLooseRouting) {
                        val destinationSubmods = File(destinationRoot, "game/Submods")
                        ensureDirectoryExists(destinationSubmods)
                        mergeDirectoryContents(
                            sourceRoot = looseSourceRoot,
                            destinationRoot = destinationSubmods
                        )
                    } else {
                        val routingDecision = resolveLooseRouting(looseSourceRoot)
                        val destination = if (routingDecision.installToGameRoot) {
                            File(destinationRoot, "game")
                        } else {
                            File(destinationRoot, "game/Submods")
                        }
                        ensureDirectoryExists(destination)
                        mergeDirectoryContents(
                            sourceRoot = routingDecision.sourceRoot,
                            destinationRoot = destination
                        )
                    }
                }
            }

            return InstallReport(
                archiveFormat = archiveFormat,
                installFlow = installFlow,
                filesMerged = mergeStats.filesMerged,
                directoriesCreated = mergeStats.directoriesCreated,
                destinationRoot = destinationRoot
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
                val normalizedName = normalizeArchivePath(entry.name)
                if (normalizedName != null && !shouldIgnoreArchivePath(normalizedName)) {
                    val outFile = resolveSafeEntryFile(outputDir, normalizedName)
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
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun validateSubmodSignature(extractedDir: File) {
        if (collectRenPyScriptFiles(extractedDir).isEmpty()) {
            throw UnrecognizedStructureException(
                "This file does not look like a valid Submod. If you are trying to install clothes or accessories, use the Sprite Manager."
            )
        }
    }

    private fun resolveEffectiveSourceRoot(extractedDir: File): File {
        val topLevelEntries = extractedDir.listFiles()
            ?.filter { !shouldIgnoreArchivePath(it.name) }
            .orEmpty()

        if (topLevelEntries.size == 1 && topLevelEntries.first().isDirectory) {
            val candidate = topLevelEntries.first()
            val entries = candidate.listFiles().orEmpty()

            // check for [any]/game/Submods or [any]/game/submods
            val gameDir = entries.find { it.isDirectory && it.name.equals("game", ignoreCase = true) }
            if (gameDir != null) {
                val hasSubmodsInside = gameDir.listFiles()?.any {
                    it.isDirectory && (it.name == "Submods" || it.name == "submods")
                } == true
                if (hasSubmodsInside) {
                    return candidate
                }
            }

            // check for [any]/Submods or [any]/submods
            val hasSubmodsAtRoot = entries.any {
                it.isDirectory && (it.name == "Submods" || it.name == "submods")
            }
            if (hasSubmodsAtRoot) {
                return candidate
            }
        }
        return extractedDir
    }

    private fun resolveInstallFlow(extractedDir: File): InstallFlow {
        val topLevelEntries = extractedDir.listFiles()
            ?.filter { !shouldIgnoreArchivePath(it.name) }
            .orEmpty()

        val hasGameAtRoot = topLevelEntries.any { item ->
            item.isDirectory && item.name.equals("game", ignoreCase = true)
        }
        if (hasGameAtRoot) {
            return InstallFlow.CORE_PATCH
        }

        val hasSubmodsAtRoot = topLevelEntries.any { item ->
            item.isDirectory && (item.name == "Submods" || item.name == "submods")
        }
        if (hasSubmodsAtRoot) {
            return InstallFlow.ORDERED_SUBMOD
        }

        return InstallFlow.LOOSE_SUBMOD
    }

    private fun mergeOrderedSubmodLayout(extractedDir: File, filesDir: File): MergeStats {
        val submodsSource = findSubmodsDirectory(extractedDir)
            ?: throw UnrecognizedStructureException("Submods folder was not found in archive root.")
        val submodsDestination = File(filesDir, "game/Submods")
        ensureDirectoryExists(submodsDestination)

        val submodsMerge = mergeDirectoryContents(
            sourceRoot = submodsSource,
            destinationRoot = submodsDestination
        )

        val modAssetsSource = findTopLevelDirectory(extractedDir, "mod_assets")
        if (modAssetsSource == null) {
            return submodsMerge
        }

        val modAssetsDestination = File(filesDir, "game/mod_assets")
        ensureDirectoryExists(modAssetsDestination)
        val assetsMerge = mergeDirectoryContents(
            sourceRoot = modAssetsSource,
            destinationRoot = modAssetsDestination
        )

        return MergeStats(
            filesMerged = submodsMerge.filesMerged + assetsMerge.filesMerged,
            directoriesCreated = submodsMerge.directoriesCreated + assetsMerge.directoriesCreated
        )
    }

    private fun findSubmodsDirectory(root: File): File? {
        return root.listFiles()
            ?.firstOrNull { item ->
                item.isDirectory &&
                    !shouldIgnoreArchivePath(item.name) &&
                    (item.name == "Submods" || item.name == "submods")
            }
    }

    private fun resolveLooseSourceRoot(extractedDir: File): File {
        return extractedDir
    }

    private data class LooseRoutingDecision(
        val sourceRoot: File,
        val installToGameRoot: Boolean
    )

    private fun resolveLooseRouting(looseSourceRoot: File): LooseRoutingDecision {
        val scripts = collectRenPyScriptFiles(looseSourceRoot)
            .sortedWith(
                compareBy<File>(
                    { pathDepth(it.relativeTo(looseSourceRoot).path) },
                    { it.relativeTo(looseSourceRoot).path.length }
                )
            )
        if (scripts.isEmpty()) {
            throw UnrecognizedStructureException(
                "This file does not look like a valid Submod. If you are trying to install clothes or accessories, use the Sprite Manager."
            )
        }

        val primaryScript = scripts.first()
        val pythonPackagesSourceRoot = resolvePythonPackagesSourceRoot(primaryScript, looseSourceRoot)
        if (pythonPackagesSourceRoot != null) {
            return LooseRoutingDecision(
                sourceRoot = pythonPackagesSourceRoot,
                installToGameRoot = true
            )
        }

        return LooseRoutingDecision(
            sourceRoot = looseSourceRoot,
            installToGameRoot = false
        )
    }

    private fun collectRenPyScriptFiles(root: File): List<File> {
        return root.walkTopDown()
            .filter { item ->
                if (!item.isFile) {
                    return@filter false
                }
                val relativePath = item.relativeTo(root).path
                if (shouldIgnoreArchivePath(relativePath)) {
                    return@filter false
                }
                val extension = item.extension.lowercase(Locale.US)
                extension == "rpy" || extension == "rpyc"
            }
            .toList()
    }

    private fun resolvePythonPackagesSourceRoot(scriptFile: File, looseSourceRoot: File): File? {
        val scriptParent = scriptFile.parentFile ?: looseSourceRoot
        val candidates = buildList {
            add(scriptParent)
            scriptParent.parentFile?.let { add(it) }
            if (looseSourceRoot != scriptParent) {
                add(looseSourceRoot)
            }
        }

        return candidates.firstOrNull { candidate ->
            candidate.exists() &&
                candidate.isDirectory &&
                isInsideOrEqual(candidate, looseSourceRoot) &&
                containsDirectoryNamed(candidate, "python-packages")
        }
    }

    private fun containsDirectoryNamed(directory: File, expectedName: String): Boolean {
        return directory.listFiles()
            ?.any { item ->
                item.isDirectory &&
                    !shouldIgnoreArchivePath(item.name) &&
                    item.name.equals(expectedName, ignoreCase = true)
            }
            ?: false
    }

    private fun pathDepth(relativePath: String): Int {
        return relativePath.replace('\\', '/').count { it == '/' }
    }

    private fun isInsideOrEqual(candidate: File, root: File): Boolean {
        val candidatePath = candidate.canonicalPath
        val rootPath = root.canonicalPath
        if (candidatePath == rootPath) {
            return true
        }
        val rootPrefix = if (rootPath.endsWith(File.separator)) rootPath else "$rootPath${File.separator}"
        return candidatePath.startsWith(rootPrefix)
    }

    private data class MergeStats(
        val filesMerged: Int,
        val directoriesCreated: Int
    )

    private fun mergeDirectoryContents(sourceRoot: File, destinationRoot: File): MergeStats {
        var filesMerged = 0
        var directoriesCreated = 0

        sourceRoot.walkTopDown()
            .onEnter { directory ->
                if (directory == sourceRoot) {
                    return@onEnter true
                }
                val relativePath = directory.relativeTo(sourceRoot).path
                !shouldIgnoreArchivePath(relativePath)
            }
            .forEach { item ->
                if (item == sourceRoot) {
                    return@forEach
                }

                val relativePath = item.relativeTo(sourceRoot).path
                if (shouldIgnoreArchivePath(relativePath)) {
                    return@forEach
                }

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

    private fun ensureDirectoryExists(directory: File) {
        if (directory.exists() && !directory.isDirectory) {
            throw IOException("Destination path is not a directory: ${directory.absolutePath}")
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create destination directory: ${directory.absolutePath}")
        }
    }

    private fun findTopLevelDirectory(root: File, expectedName: String): File? {
        return root.listFiles()
            ?.firstOrNull { item ->
                item.isDirectory &&
                    !shouldIgnoreArchivePath(item.name) &&
                    item.name.equals(expectedName, ignoreCase = true)
            }
    }

    private fun createWorkspace(filesDir: File): File {
        val workspace = File(
            filesDir,
            "submod_install_tmp_${System.currentTimeMillis()}_${(1000..9999).random()}"
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

    private fun normalizeArchivePath(path: String): String? {
        val normalized = path.replace('\\', '/').trimStart('/').trim()
        return normalized.ifBlank { null }
    }

    private fun shouldIgnoreArchivePath(path: String): Boolean {
        val normalized = normalizeArchivePath(path) ?: return true
        val segments = normalized.split('/')
        if (segments.any { it.equals("__MACOSX", ignoreCase = true) }) {
            return true
        }
        val fileName = segments.lastOrNull().orEmpty()
        return fileName.equals(".DS_Store", ignoreCase = true)
    }

    private fun resolveSafeEntryFile(rootDir: File, entryName: String): File? {
        val cleanName = normalizeArchivePath(entryName) ?: return null

        val targetFile = File(rootDir, cleanName)
        val rootPath = rootDir.canonicalPath
        val targetPath = targetFile.canonicalPath
        val rootPrefix = if (rootPath.endsWith(File.separator)) rootPath else "$rootPath${File.separator}"

        if (targetPath != rootPath && !targetPath.startsWith(rootPrefix)) {
            throw IOException("Unsafe archive entry path: $entryName")
        }
        return targetFile
    }
}
