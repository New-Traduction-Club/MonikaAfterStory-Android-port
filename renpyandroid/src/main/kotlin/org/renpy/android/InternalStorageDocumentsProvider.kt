package org.renpy.android

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

class InternalStorageDocumentsProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY_SUFFIX = ".internal.documents"
        const val ROOT_ID = "internal_storage"
        private const val ROOT_DOCUMENT_ID = "root"

        fun authorityForPackage(packageName: String): String = "$packageName$AUTHORITY_SUFFIX"
    }

    private val defaultRootProjection = arrayOf(
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_TITLE,
        Root.COLUMN_SUMMARY,
        Root.COLUMN_FLAGS,
        Root.COLUMN_MIME_TYPES,
        Root.COLUMN_ICON,
        Root.COLUMN_AVAILABLE_BYTES
    )

    private val defaultDocumentProjection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
        Document.COLUMN_SIZE,
        Document.COLUMN_ICON
    )

    override fun onCreate(): Boolean = context != null

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: defaultRootProjection)
        val rootDir = requireBaseDir()

        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
            add(Root.COLUMN_TITLE, context?.getString(R.string.documents_provider_root_title))
            add(Root.COLUMN_SUMMARY, context?.getString(R.string.documents_provider_root_summary))
            add(
                Root.COLUMN_FLAGS,
                Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_CREATE or
                    Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_IS_CHILD
            )
            add(Root.COLUMN_MIME_TYPES, "*/*")
            add(Root.COLUMN_ICON, R.drawable.ic_launcher_internal)
            add(Root.COLUMN_AVAILABLE_BYTES, rootDir.usableSpace)
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: defaultDocumentProjection)
        val file = resolveFileForDocumentId(documentId)
        includeFile(cursor, file)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parent = resolveFileForDocumentId(parentDocumentId)
        if (!parent.isDirectory) {
            throw FileNotFoundException("Document is not a directory: $parentDocumentId")
        }

        val cursor = MatrixCursor(projection ?: defaultDocumentProjection)
        val children = parent.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            ?: emptyList()

        for (child in children) {
            includeFile(cursor, child)
        }
        return cursor
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: defaultDocumentProjection)
        if (rootId != ROOT_ID) return cursor
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return cursor

        searchRecursively(requireBaseDir(), normalized, cursor, 100)
        return cursor
    }

    override fun getDocumentType(documentId: String): String {
        val file = resolveFileForDocumentId(documentId)
        return getMimeTypeForFile(file)
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = resolveFileForDocumentId(documentId)
        if (file.isDirectory) {
            throw FileNotFoundException("Cannot open directory: $documentId")
        }

        val accessMode = ParcelFileDescriptor.parseMode(mode)
        val writesContent = mode.contains('w') || mode.contains('a') || mode.contains('+')
        if (!writesContent) {
            return ParcelFileDescriptor.open(file, accessMode)
        }

        file.parentFile?.mkdirs()
        val parentDocumentId = file.parentFile?.let { parent ->
            runCatching { fileToDocumentId(parent) }.getOrNull()
        }

        return ParcelFileDescriptor.open(
            file,
            accessMode,
            Handler(Looper.getMainLooper())
        ) { _ ->
            notifyDocumentChanged(documentId)
            parentDocumentId?.let { notifyParentChanged(it) }
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = resolveFileForDocumentId(parentDocumentId)
        if (!parent.isDirectory) {
            throw FileNotFoundException("Parent is not a directory: $parentDocumentId")
        }

        val target = buildUniqueFile(
            parent = parent,
            requestedName = sanitizeDisplayName(displayName),
            isDirectory = mimeType == Document.MIME_TYPE_DIR
        )

        val created = if (mimeType == Document.MIME_TYPE_DIR) {
            target.mkdirs()
        } else {
            target.parentFile?.mkdirs()
            target.createNewFile()
        }

        if (!created) {
            throw IOException("Failed to create document: ${target.name}")
        }

        notifyParentChanged(parentDocumentId)
        return fileToDocumentId(target)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val source = resolveFileForDocumentId(documentId)
        val baseDir = requireBaseDir()
        if (source == baseDir) {
            throw FileNotFoundException("Root directory cannot be renamed")
        }

        val parent = source.parentFile ?: throw FileNotFoundException("Missing parent directory")
        val sanitizedName = sanitizeDisplayName(displayName)
        val directTarget = File(parent, sanitizedName).canonicalFile
        if (directTarget == source) return documentId

        val target = if (directTarget.exists()) {
            buildUniqueFile(
                parent = parent,
                requestedName = sanitizedName,
                isDirectory = source.isDirectory
            )
        } else {
            directTarget
        }

        if (!source.renameTo(target)) {
            throw IOException("Failed to rename ${source.name}")
        }

        notifyParentChanged(fileToDocumentId(parent))
        return fileToDocumentId(target)
    }

    override fun deleteDocument(documentId: String) {
        val target = resolveFileForDocumentId(documentId)
        val baseDir = requireBaseDir()
        if (target == baseDir) {
            throw FileNotFoundException("Root directory cannot be deleted")
        }

        val parent = target.parentFile
        if (!deleteRecursivelySafe(target)) {
            throw IOException("Failed to delete ${target.absolutePath}")
        }

        if (parent != null && isWithin(parent, baseDir)) {
            notifyParentChanged(fileToDocumentId(parent))
        }
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        if (sourceParentDocumentId == targetParentDocumentId) {
            return sourceDocumentId
        }

        val source = resolveFileForDocumentId(sourceDocumentId)
        val baseDir = requireBaseDir()
        if (source == baseDir) {
            throw FileNotFoundException("Root directory cannot be moved")
        }

        val targetParent = resolveFileForDocumentId(targetParentDocumentId)
        if (!targetParent.isDirectory) {
            throw FileNotFoundException("Target parent is not a directory: $targetParentDocumentId")
        }

        if (source.isDirectory) {
            val sourcePath = source.canonicalPath
            val targetParentPath = targetParent.canonicalPath
            if (targetParentPath == sourcePath || targetParentPath.startsWith("$sourcePath${File.separator}")) {
                throw IOException("Cannot move a folder inside itself")
            }
        }

        val target = buildUniqueFile(targetParent, source.name, source.isDirectory)

        val moved = if (source.renameTo(target)) {
            true
        } else {
            val copied = source.copyRecursively(target, overwrite = false)
            if (!copied) {
                false
            } else {
                deleteRecursivelySafe(source)
            }
        }

        if (!moved) {
            throw IOException("Failed to move ${source.absolutePath}")
        }

        notifyParentChanged(sourceParentDocumentId)
        notifyParentChanged(targetParentDocumentId)
        return fileToDocumentId(target)
    }

    override fun removeDocument(documentId: String, parentDocumentId: String) {
        deleteDocument(documentId)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = resolveFileForDocumentId(parentDocumentId)
        val child = resolveFileForDocumentId(documentId)
        return isWithin(child, parent)
    }

    private fun includeFile(cursor: MatrixCursor, file: File) {
        val baseDir = requireBaseDir()
        val isRoot = file == baseDir
        val flags = buildDocumentFlags(file, isRoot)

        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, fileToDocumentId(file))
            add(
                Document.COLUMN_DISPLAY_NAME,
                if (isRoot) context?.getString(R.string.documents_provider_root_title) else file.name
            )
            add(Document.COLUMN_SIZE, if (file.isFile) file.length() else null)
            add(Document.COLUMN_MIME_TYPE, getMimeTypeForFile(file))
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_ICON, if (isRoot) R.drawable.ic_launcher_internal else null)
        }
    }

    private fun buildDocumentFlags(file: File, isRoot: Boolean): Int {
        var flags = 0
        if (file.isDirectory) {
            flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        }

        if (!isRoot && file.canWrite()) {
            flags = flags or Document.FLAG_SUPPORTS_DELETE
            flags = flags or Document.FLAG_SUPPORTS_RENAME
            flags = flags or Document.FLAG_SUPPORTS_MOVE
            if (!file.isDirectory) {
                flags = flags or Document.FLAG_SUPPORTS_WRITE
            }
        }
        return flags
    }

    private fun searchRecursively(dir: File, query: String, cursor: MatrixCursor, maxResults: Int) {
        val children = dir.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            ?: return

        for (child in children) {
            if (cursor.count >= maxResults) return
            if (child.name.lowercase(Locale.ROOT).contains(query)) {
                includeFile(cursor, child)
            }
            if (child.isDirectory) {
                searchRecursively(child, query, cursor, maxResults)
            }
        }
    }

    private fun resolveFileForDocumentId(documentId: String): File {
        val baseDir = requireBaseDir()
        val resolved = when {
            documentId == ROOT_DOCUMENT_ID -> baseDir
            documentId.startsWith("$ROOT_DOCUMENT_ID:") -> {
                val relativePath = documentId.substringAfter(':').replace('/', File.separatorChar)
                if (relativePath.isBlank()) baseDir else File(baseDir, relativePath)
            }
            else -> throw FileNotFoundException("Unknown documentId: $documentId")
        }

        val canonical = resolved.canonicalFile
        if (!isWithin(canonical, baseDir)) {
            throw FileNotFoundException("Document outside allowed root")
        }
        if (!canonical.exists()) {
            throw FileNotFoundException("Document does not exist: $documentId")
        }
        return canonical
    }

    private fun fileToDocumentId(file: File): String {
        val baseDir = requireBaseDir()
        val canonical = file.canonicalFile
        if (!isWithin(canonical, baseDir)) {
            throw FileNotFoundException("File outside allowed root")
        }
        return if (canonical == baseDir) {
            ROOT_DOCUMENT_ID
        } else {
            val relative = canonical.absolutePath
                .removePrefix(baseDir.absolutePath + File.separator)
                .replace(File.separatorChar, '/')
            "$ROOT_DOCUMENT_ID:$relative"
        }
    }

    private fun buildUniqueFile(parent: File, requestedName: String, isDirectory: Boolean): File {
        val safeName = sanitizeDisplayName(requestedName)

        val (baseName, extension) = if (!isDirectory) {
            val dot = safeName.lastIndexOf('.')
            if (dot > 0 && dot < safeName.length - 1) {
                safeName.substring(0, dot) to safeName.substring(dot)
            } else {
                safeName to ""
            }
        } else {
            safeName to ""
        }

        var candidate = File(parent, safeName)
        var index = 1
        while (candidate.exists()) {
            val name = if (isDirectory) {
                "${baseName}_$index"
            } else {
                "${baseName}_$index$extension"
            }
            candidate = File(parent, name)
            index++
        }
        return candidate
    }

    private fun sanitizeDisplayName(displayName: String): String {
        return displayName.trim()
            .ifBlank { "untitled" }
            .replace('/', '_')
            .replace('\\', '_')
    }

    private fun getMimeTypeForFile(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR

        val extension = file.extension.lowercase(Locale.ROOT)
        if (extension.isEmpty()) return "application/octet-stream"

        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun notifyParentChanged(parentDocumentId: String) {
        val resolver = context?.contentResolver ?: return
        val authority = authorityForPackage(requireNotNull(context).packageName)
        resolver.notifyChange(DocumentsContract.buildDocumentUri(authority, parentDocumentId), null)
        resolver.notifyChange(DocumentsContract.buildChildDocumentsUri(authority, parentDocumentId), null)
    }

    private fun notifyDocumentChanged(documentId: String) {
        val resolver = context?.contentResolver ?: return
        val authority = authorityForPackage(requireNotNull(context).packageName)
        resolver.notifyChange(DocumentsContract.buildDocumentUri(authority, documentId), null)
    }

    private fun deleteRecursivelySafe(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            val children = file.listFiles() ?: return false
            for (child in children) {
                if (!deleteRecursivelySafe(child)) return false
            }
        }
        return file.delete() || !file.exists()
    }

    private fun isWithin(file: File, parent: File): Boolean {
        val filePath = file.canonicalPath
        val parentPath = parent.canonicalPath
        return filePath == parentPath || filePath.startsWith("$parentPath${File.separator}")
    }

    private fun requireBaseDir(): File {
        val ctx = context ?: throw IllegalStateException("Context unavailable")
        return ctx.filesDir.canonicalFile
    }
}
