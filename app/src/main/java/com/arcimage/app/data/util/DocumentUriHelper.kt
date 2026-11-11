package com.arcimage.app.data.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.arcimage.app.data.files.DeviceFileRepository
import com.arcimage.app.model.SourceItem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

data class ResolvedInputEntry(
    val relativeArchivePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val openStream: () -> InputStream?
)

object DocumentUriHelper {

    fun safeUriForFile(context: Context, file: File): Uri {
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrElse { Uri.fromFile(file) }
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path?.substringAfterLast('/') ?: "file"
        }
        if (isTreeUriSafe(uri)) {
            val docDir = DocumentFile.fromTreeUri(context, uri)
            if (!docDir?.name.isNullOrBlank()) {
                return docDir!!.name!!
            }
        }
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && !cursor.isNull(idx)) {
                    return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "item"
    }

    fun resolveFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return File(uri.path ?: return 0L).length()
        }
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) {
                    return cursor.getLong(idx)
                }
            }
        }
        return 0L
    }

    fun resolveMimeType(context: Context, uri: Uri): String? {
        val resolverType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (!resolverType.isNullOrBlank() && resolverType != "application/octet-stream") {
            return resolverType
        }
        val name = resolveDisplayName(context, uri)
        return guessMimeTypeFromFileName(name) ?: resolverType
    }

    fun guessMimeTypeFromFileName(fileName: String): String? {
        val lower = fileName.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") || lower.endsWith(".gz") -> "application/gzip"
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") || lower.endsWith(".bz2") -> "application/x-bzip2"
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") || lower.endsWith(".xz") -> "application/x-xz"
            lower.endsWith(".tar") -> "application/x-tar"
            lower.endsWith(".7z") -> "application/x-7z-compressed"
            lower.endsWith(".rar") || lower.endsWith(".cbr") -> "application/vnd.rar"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heic"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".bmp") -> "image/bmp"
            else -> {
                val ext = lower.substringAfterLast('.', "")
                if (ext.isNotEmpty()) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                } else {
                    null
                }
            }
        }
    }

    fun isDirectoryUri(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") {
            return File(uri.path ?: return false).isDirectory
        }
        if (isTreeUriSafe(uri)) return true
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) return true
        val singleDoc = DocumentFile.fromSingleUri(context, uri)
        return singleDoc?.isDirectory == true
    }

    fun buildSourceItem(context: Context, uri: Uri): SourceItem {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.isDirectory) {
                val stats = computeLocalDirStats(file)
                return SourceItem(
                    id = uri.toString(),
                    uri = uri,
                    name = file.name.ifBlank { "Folder" },
                    sizeBytes = stats.first,
                    isDirectory = true,
                    childFileCount = stats.second
                )
            }
            return SourceItem(
                id = uri.toString(),
                uri = uri,
                name = file.name.ifBlank { "File" },
                sizeBytes = file.length(),
                isDirectory = false,
                childFileCount = 1
            )
        }

        if (isTreeUriSafe(uri)) {
            val treeDoc = DocumentFile.fromTreeUri(context, uri)
            val stats = treeDoc?.let { computeDocDirStats(it) } ?: (0L to 0)
            return SourceItem(
                id = uri.toString(),
                uri = uri,
                name = treeDoc?.name ?: resolveDisplayName(context, uri),
                sizeBytes = stats.first,
                isDirectory = true,
                childFileCount = stats.second
            )
        }

        val singleDoc = DocumentFile.fromSingleUri(context, uri)
        if (singleDoc != null && singleDoc.isDirectory) {
            val stats = computeDocDirStats(singleDoc)
            return SourceItem(
                id = uri.toString(),
                uri = uri,
                name = singleDoc.name ?: resolveDisplayName(context, uri),
                sizeBytes = stats.first,
                isDirectory = true,
                childFileCount = stats.second
            )
        }

        return SourceItem(
            id = uri.toString(),
            uri = uri,
            name = resolveDisplayName(context, uri),
            sizeBytes = resolveFileSize(context, uri),
            isDirectory = false,
            childFileCount = 1
        )
    }

    fun flattenSourceItems(context: Context, items: List<SourceItem>): List<ResolvedInputEntry> {
        val result = mutableListOf<ResolvedInputEntry>()
        for (item in items) {
            val uri = item.uri
            if (uri.scheme == "file") {
                val file = File(uri.path ?: continue)
                if (file.isDirectory) {
                    collectLocalDirEntries(file, sanitizeSegment(file.name), result)
                } else if (file.exists()) {
                    result += ResolvedInputEntry(
                        relativeArchivePath = sanitizeSegment(file.name),
                        isDirectory = false,
                        sizeBytes = file.length(),
                        openStream = { file.inputStream() }
                    )
                }
            } else if (isTreeUriSafe(uri)) {
                val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: continue
                val rootPrefix = sanitizeSegment(rootDoc.name ?: item.name)
                collectDocumentDirEntries(context, rootDoc, rootPrefix, result)
            } else {
                val singleDoc = DocumentFile.fromSingleUri(context, uri)
                if (singleDoc != null && singleDoc.isDirectory) {
                    val rootPrefix = sanitizeSegment(singleDoc.name ?: item.name)
                    collectDocumentDirEntries(context, singleDoc, rootPrefix, result)
                } else {
                    result += ResolvedInputEntry(
                        relativeArchivePath = sanitizeSegment(item.name),
                        isDirectory = false,
                        sizeBytes = item.sizeBytes,
                        openStream = { context.contentResolver.openInputStream(uri) }
                    )
                }
            }
        }
        return result
    }

    fun copyUriToTempFile(context: Context, uri: Uri, extensionHint: String = "tmp"): File {
        if (uri.scheme == "file") {
            val direct = File(uri.path ?: "")
            if (direct.exists() && direct.canRead()) {
                return direct
            }
        }
        val workDir = File(context.cacheDir, "staging").apply { mkdirs() }
        val safeExt = extensionHint.trimStart('.').ifBlank { "tmp" }
        val stagedFile = File(workDir, "staged_${UUID.randomUUID()}.$safeExt")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(stagedFile).use { output ->
                input.copyTo(output, bufferSize = DEFAULT_BUFFER_SIZE)
            }
        } ?: throw IllegalArgumentException("Unable to open input stream for $uri")
        return stagedFile
    }

    fun exportFileToDestination(
        context: Context,
        localSourceFile: File,
        desiredFileName: String,
        mimeType: String,
        destinationTreeUri: Uri?
    ): Pair<Uri, String> {
        if (destinationTreeUri != null) {
            if (destinationTreeUri.scheme == "file") {
                val targetDir = File(destinationTreeUri.path ?: "")
                if ((targetDir.isDirectory || targetDir.mkdirs()) && targetDir.canWrite()) {
                    val targetFile = uniqueFile(targetDir, desiredFileName)
                    if (localSourceFile.absolutePath != targetFile.absolutePath) {
                        localSourceFile.copyTo(targetFile, overwrite = true)
                    }
                    val contentUri = safeUriForFile(context, targetFile)
                    return contentUri to targetFile.name
                }
            } else {
                val treeRoot = DocumentFile.fromTreeUri(context, destinationTreeUri)
                if (treeRoot != null && treeRoot.canWrite()) {
                    treeRoot.findFile(desiredFileName)?.delete()
                    val created = treeRoot.createFile(mimeType, desiredFileName)
                    if (created != null) {
                        context.contentResolver.openOutputStream(created.uri)?.use { out ->
                            localSourceFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        return created.uri to (created.name ?: desiredFileName)
                    }
                }
            }
        }

        val exportDir = getDefaultExportsDir(context)
        val targetFile = uniqueFile(exportDir, desiredFileName)
        if (localSourceFile.absolutePath != targetFile.absolutePath) {
            localSourceFile.copyTo(targetFile, overwrite = true)
        }
        val contentUri = safeUriForFile(context, targetFile)
        return contentUri to targetFile.name
    }

    fun copyDirectoryToSafTree(
        context: Context,
        localRootDir: File,
        destinationTreeUri: Uri
    ): String {
        val treeRoot = DocumentFile.fromTreeUri(context, destinationTreeUri)
            ?: return localRootDir.absolutePath
        val containerName = localRootDir.name.ifBlank { "Extracted" }
        val targetContainer = treeRoot.findFile(containerName)?.takeIf { it.isDirectory }
            ?: treeRoot.createDirectory(containerName)
            ?: treeRoot

        copyLocalFolderRecursiveToDoc(context, localRootDir, targetContainer)
        return targetContainer.name ?: containerName
    }

    /**
     * Results are written to the public `Download/ArcImage` folder when ArcImage has been granted
     * device file access, so they immediately show up in the device file system (and in the
     * ArcImage Files browser). Falls back to the private app sandbox otherwise.
     */
    fun getDefaultExportsDir(context: Context): File {
        if (DeviceFileRepository.hasAllFilesAccess(context)) {
            @Suppress("DEPRECATION")
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "ArcImage"
            )
            if ((publicDir.isDirectory || publicDir.mkdirs()) && publicDir.canWrite()) {
                return publicDir
            }
        }
        return File(context.filesDir, "exports").apply { mkdirs() }
    }

    fun openOutputStreamForNewExport(
        context: Context,
        fileName: String,
        mimeType: String,
        destinationTreeUri: Uri?
    ): Triple<OutputStream, Uri, File?> {
        if (destinationTreeUri != null) {
            if (destinationTreeUri.scheme == "file") {
                val targetDir = File(destinationTreeUri.path ?: "")
                if ((targetDir.isDirectory || targetDir.mkdirs()) && targetDir.canWrite()) {
                    val outFile = uniqueFile(targetDir, fileName)
                    val uri = safeUriForFile(context, outFile)
                    return Triple(FileOutputStream(outFile), uri, outFile)
                }
            } else {
                val treeRoot = DocumentFile.fromTreeUri(context, destinationTreeUri)
                if (treeRoot != null && treeRoot.canWrite()) {
                    treeRoot.findFile(fileName)?.delete()
                    val docFile = treeRoot.createFile(mimeType, fileName)
                    if (docFile != null) {
                        val out = context.contentResolver.openOutputStream(docFile.uri)
                        if (out != null) {
                            return Triple(out, docFile.uri, null)
                        }
                    }
                }
            }
        }
        val exportDir = getDefaultExportsDir(context)
        val outFile = uniqueFile(exportDir, fileName)
        val uri = safeUriForFile(context, outFile)
        return Triple(FileOutputStream(outFile), uri, outFile)
    }

    private fun copyLocalFolderRecursiveToDoc(
        context: Context,
        sourceDir: File,
        targetDocDir: DocumentFile
    ) {
        val children = sourceDir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                val subDoc = targetDocDir.findFile(child.name)?.takeIf { it.isDirectory }
                    ?: targetDocDir.createDirectory(child.name)
                    ?: continue
                copyLocalFolderRecursiveToDoc(context, child, subDoc)
            } else {
                targetDocDir.findFile(child.name)?.delete()
                val mime = guessMimeTypeFromFileName(child.name) ?: "application/octet-stream"
                val created = targetDocDir.createFile(mime, child.name) ?: continue
                context.contentResolver.openOutputStream(created.uri)?.use { out ->
                    child.inputStream().use { input -> input.copyTo(out) }
                }
            }
        }
    }

    private fun uniqueFile(dir: File, desiredName: String): File {
        var candidate = File(dir, desiredName)
        if (!candidate.exists()) return candidate
        val dotIdx = desiredName.lastIndexOf('.')
        val stem = if (dotIdx > 0) desiredName.substring(0, dotIdx) else desiredName
        val ext = if (dotIdx > 0) desiredName.substring(dotIdx) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_$counter$ext")
            counter++
        }
        return candidate
    }

    private fun isTreeUriSafe(uri: Uri): Boolean {
        return runCatching { DocumentsContract.isTreeUri(uri) }.getOrDefault(false)
    }

    private fun sanitizeSegment(name: String): String {
        return name.replace('\\', '_').replace('/', '_').trim().ifBlank { "item" }
    }

    private fun computeLocalDirStats(dir: File): Pair<Long, Int> {
        var bytes = 0L
        var files = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                bytes += f.length()
                files += 1
            }
        }
        return bytes to files
    }

    private fun computeDocDirStats(dir: DocumentFile): Pair<Long, Int> {
        var bytes = 0L
        var files = 0
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                val sub = computeDocDirStats(child)
                bytes += sub.first
                files += sub.second
            } else {
                bytes += child.length()
                files += 1
            }
        }
        return bytes to files
    }

    private fun collectLocalDirEntries(
        dir: File,
        currentPrefix: String,
        sink: MutableList<ResolvedInputEntry>
    ) {
        val children = dir.listFiles()
        if (children.isNullOrEmpty()) {
            sink += ResolvedInputEntry(
                relativeArchivePath = "$currentPrefix/",
                isDirectory = true,
                sizeBytes = 0L,
                openStream = { null }
            )
            return
        }
        for (child in children) {
            val childPath = "$currentPrefix/${sanitizeSegment(child.name)}"
            if (child.isDirectory) {
                collectLocalDirEntries(child, childPath, sink)
            } else {
                sink += ResolvedInputEntry(
                    relativeArchivePath = childPath,
                    isDirectory = false,
                    sizeBytes = child.length(),
                    openStream = { child.inputStream() }
                )
            }
        }
    }

    private fun collectDocumentDirEntries(
        context: Context,
        dir: DocumentFile,
        currentPrefix: String,
        sink: MutableList<ResolvedInputEntry>
    ) {
        val children = dir.listFiles()
        if (children.isEmpty()) {
            sink += ResolvedInputEntry(
                relativeArchivePath = "$currentPrefix/",
                isDirectory = true,
                sizeBytes = 0L,
                openStream = { null }
            )
            return
        }
        for (child in children) {
            val seg = sanitizeSegment(child.name ?: "item")
            val childPath = "$currentPrefix/$seg"
            if (child.isDirectory) {
                collectDocumentDirEntries(context, child, childPath, sink)
            } else {
                val childUri = child.uri
                sink += ResolvedInputEntry(
                    relativeArchivePath = childPath,
                    isDirectory = false,
                    sizeBytes = child.length(),
                    openStream = { context.contentResolver.openInputStream(childUri) }
                )
            }
        }
    }
}
