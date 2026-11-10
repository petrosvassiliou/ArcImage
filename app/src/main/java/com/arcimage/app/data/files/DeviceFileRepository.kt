package com.arcimage.app.data.files

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import com.arcimage.app.model.ArchiveFormat
import com.arcimage.app.model.ImageFormat
import java.io.File
import java.util.Locale

/** Classification used by the Files browser to pick icons, thumbnails and default actions. */
enum class FileKind { FOLDER, ARCHIVE, IMAGE, OTHER }

enum class FileSortOrder { NAME, DATE, SIZE }

/** Well-known public folders offered as quick-access chips. */
enum class QuickFolder(val environmentDirectory: String) {
    DOWNLOADS(Environment.DIRECTORY_DOWNLOADS),
    CAMERA(Environment.DIRECTORY_DCIM),
    PICTURES(Environment.DIRECTORY_PICTURES),
    DOCUMENTS(Environment.DIRECTORY_DOCUMENTS)
}

/** A single real file or folder on the device storage. */
data class DeviceFileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val kind: FileKind,
    val childCount: Int
)

/** A mounted storage volume (internal shared storage, SD card, USB drive ...). */
data class StorageRoot(
    val path: String,
    val description: String?,
    val isPrimary: Boolean
)

/**
 * Reads the real device file system (no sample / placeholder content).
 *
 * Listing is pure `java.io.File` so it works for both the primary shared storage
 * (`/storage/emulated/0`) and removable volumes on Android phones and Googlebook OS.
 */
object DeviceFileRepository {

    fun classify(name: String, isDirectory: Boolean): FileKind = when {
        isDirectory -> FileKind.FOLDER
        ArchiveFormat.detectFromFileName(name) != null -> FileKind.ARCHIVE
        ImageFormat.isSupportedImage(name, null) -> FileKind.IMAGE
        else -> FileKind.OTHER
    }

    /**
     * Lists [directory] with folders first, then files, each group ordered by [sortOrder].
     * Hidden dot-files are skipped unless [showHidden] is true.
     */
    fun listDirectory(
        directory: File,
        sortOrder: FileSortOrder = FileSortOrder.NAME,
        showHidden: Boolean = false
    ): List<DeviceFileEntry> {
        val children = directory.listFiles() ?: return emptyList()
        val entries = children
            .asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { file ->
                val isDir = file.isDirectory
                DeviceFileEntry(
                    path = file.absolutePath,
                    name = file.name,
                    isDirectory = isDir,
                    sizeBytes = if (isDir) 0L else file.length(),
                    lastModified = file.lastModified(),
                    kind = classify(file.name, isDir),
                    childCount = if (isDir) {
                        file.list()?.count { showHidden || !it.startsWith(".") } ?: 0
                    } else {
                        0
                    }
                )
            }
            .toList()

        val byName = compareBy<DeviceFileEntry> { it.name.lowercase(Locale.ROOT) }
        val withinGroup: Comparator<DeviceFileEntry> = when (sortOrder) {
            FileSortOrder.NAME -> byName
            FileSortOrder.DATE -> compareByDescending<DeviceFileEntry> { it.lastModified }.then(byName)
            FileSortOrder.SIZE -> compareByDescending<DeviceFileEntry> {
                if (it.isDirectory) it.childCount.toLong() else it.sizeBytes
            }.then(byName)
        }
        return entries.sortedWith(compareByDescending<DeviceFileEntry> { it.isDirectory }.then(withinGroup))
    }

    fun filter(entries: List<DeviceFileEntry>, query: String): List<DeviceFileEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return entries
        return entries.filter { it.name.contains(trimmed, ignoreCase = true) }
    }

    /** Returns the path segments between [root] and [current] for breadcrumb navigation. */
    fun breadcrumbs(root: String, current: String): List<Pair<String, String>> {
        val rootFile = File(root)
        val result = mutableListOf<Pair<String, String>>()
        var cursor: File? = File(current)
        while (cursor != null && cursor.absolutePath != rootFile.absolutePath) {
            result += cursor.name to cursor.absolutePath
            cursor = cursor.parentFile
        }
        return result.reversed()
    }

    fun primaryStorageRoot(): File = Environment.getExternalStorageDirectory()

    fun storageRoots(context: Context): List<StorageRoot> {
        val primary = primaryStorageRoot().absolutePath
        val roots = linkedMapOf(primary to StorageRoot(primary, null, isPrimary = true))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            storageManager?.storageVolumes?.forEach { volume ->
                val dir = volume.directory ?: return@forEach
                if (volume.isPrimary || dir.absolutePath == primary) return@forEach
                roots[dir.absolutePath] = StorageRoot(
                    path = dir.absolutePath,
                    description = runCatching { volume.getDescription(context) }.getOrNull(),
                    isPrimary = false
                )
            }
        } else {
            // API 28-29: derive volume roots from app-specific external dirs (…/Android/data/<pkg>/files)
            context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
                val volumeRoot = appDir.absolutePath.substringBefore("/Android/data/")
                if (volumeRoot.isNotBlank() && volumeRoot != primary && File(volumeRoot).canRead()) {
                    roots[volumeRoot] = StorageRoot(volumeRoot, null, isPrimary = false)
                }
            }
        }
        return roots.values.toList()
    }

    @Suppress("DEPRECATION")
    fun quickFolder(folder: QuickFolder): File =
        Environment.getExternalStoragePublicDirectory(folder.environmentDirectory)

    /** True if ArcImage may browse the shared device storage directly. */
    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
