package com.arcimage.app.model

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.StringRes
import com.arcimage.app.R
import kotlinx.parcelize.Parcelize
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

enum class ArchiveWorkspaceMode {
    COMPRESS,
    EXPLORE
}

enum class ArchiveFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val supportsCompression: Boolean,
    val supportsEncryption: Boolean,
    val singleFileOnly: Boolean = false
) {
    ZIP(
        displayName = "ZIP (.zip)",
        extension = "zip",
        mimeType = "application/zip",
        supportsCompression = true,
        supportsEncryption = true
    ),
    SEVEN_Z(
        displayName = "7-Zip (.7z)",
        extension = "7z",
        mimeType = "application/x-7z-compressed",
        supportsCompression = true,
        supportsEncryption = false
    ),
    TAR(
        displayName = "TAR (.tar)",
        extension = "tar",
        mimeType = "application/x-tar",
        supportsCompression = true,
        supportsEncryption = false
    ),
    TAR_GZ(
        displayName = "TAR.GZ (.tar.gz)",
        extension = "tar.gz",
        mimeType = "application/gzip",
        supportsCompression = true,
        supportsEncryption = false
    ),
    TAR_BZ2(
        displayName = "TAR.BZ2 (.tar.bz2)",
        extension = "tar.bz2",
        mimeType = "application/x-bzip2",
        supportsCompression = true,
        supportsEncryption = false
    ),
    TAR_XZ(
        displayName = "TAR.XZ (.tar.xz)",
        extension = "tar.xz",
        mimeType = "application/x-xz",
        supportsCompression = true,
        supportsEncryption = false
    ),
    GZ(
        displayName = "GZIP (.gz)",
        extension = "gz",
        mimeType = "application/gzip",
        supportsCompression = true,
        supportsEncryption = false,
        singleFileOnly = true
    ),
    BZ2(
        displayName = "BZIP2 (.bz2)",
        extension = "bz2",
        mimeType = "application/x-bzip2",
        supportsCompression = true,
        supportsEncryption = false,
        singleFileOnly = true
    ),
    XZ(
        displayName = "XZ (.xz)",
        extension = "xz",
        mimeType = "application/x-xz",
        supportsCompression = true,
        supportsEncryption = false,
        singleFileOnly = true
    ),
    RAR(
        displayName = "RAR (.rar)",
        extension = "rar",
        mimeType = "application/vnd.rar",
        supportsCompression = false,
        supportsEncryption = true
    );

    companion object {
        val compressibleFormats: List<ArchiveFormat>
            get() = entries.filter { it.supportsCompression }

        fun detectFromFileName(fileName: String, mimeType: String? = null): ArchiveFormat? {
            val lower = fileName.lowercase(Locale.ROOT)
            return when {
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> TAR_GZ
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") || lower.endsWith(".tbz") -> TAR_BZ2
                lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> TAR_XZ
                lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".apk") -> ZIP
                lower.endsWith(".7z") -> SEVEN_Z
                lower.endsWith(".rar") || lower.endsWith(".cbr") -> RAR
                lower.endsWith(".tar") -> TAR
                lower.endsWith(".gz") -> GZ
                lower.endsWith(".bz2") -> BZ2
                lower.endsWith(".xz") -> XZ
                mimeType == "application/zip" || mimeType == "application/x-zip-compressed" -> ZIP
                mimeType == "application/x-7z-compressed" -> SEVEN_Z
                mimeType == "application/vnd.rar" || mimeType == "application/x-rar-compressed" -> RAR
                mimeType == "application/x-tar" -> TAR
                mimeType == "application/gzip" || mimeType == "application/x-gzip" -> GZ
                mimeType == "application/x-bzip2" -> BZ2
                mimeType == "application/x-xz" -> XZ
                else -> null
            }
        }
    }
}

enum class CompressionLevel(
    @StringRes val labelRes: Int,
    val deflateLevel: Int
) {
    STORE(R.string.compression_level_store, 0),
    FAST(R.string.compression_level_fast, 2),
    NORMAL(R.string.compression_level_normal, 6),
    MAXIMUM(R.string.compression_level_maximum, 9)
}

@Parcelize
data class SourceItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val childFileCount: Int = 1
) : Parcelable

@Parcelize
data class ArchiveEntryItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val lastModifiedMillis: Long,
    val isEncrypted: Boolean,
    val entryIndex: Int = -1
) : Parcelable {
    val savedRatioPercent: Int
        get() {
            if (isDirectory || uncompressedSize <= 0L || compressedSize <= 0L) return 0
            val saved = ((1.0 - (compressedSize.toDouble() / uncompressedSize.toDouble())) * 100.0).toInt()
            return saved.coerceIn(-100, 99)
        }
}

@Parcelize
data class CompressionConfig(
    val archiveBaseName: String = "ArcImage_Archive",
    val format: ArchiveFormat = ArchiveFormat.ZIP,
    val level: CompressionLevel = CompressionLevel.NORMAL,
    val password: String = ""
) : Parcelable

enum class ArchiveOperationType {
    INSPECTING,
    COMPRESSING,
    EXTRACTING
}

data class ArchiveOperationProgress(
    val operationType: ArchiveOperationType,
    val currentItemName: String = "",
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val outputUri: Uri? = null,
    val outputDisplayName: String? = null
) {
    val fraction: Float
        get() = when {
            totalBytes > 0L -> (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            totalItems > 0 -> (completedItems.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
}

class ArchivePasswordRequiredException(
    val archiveName: String,
    message: String = "Password required to access encrypted archive"
) : Exception(message)

class ArchiveInvalidPasswordException(
    message: String = "Incorrect password for encrypted archive"
) : Exception(message)

object FileSizeFormatter {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val unit = 1024.0
        if (bytes < unit) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceAtMost(4)
        val prefix = "KMGT"[exp - 1]
        val value = bytes / unit.pow(exp.toDouble())
        return String.format(Locale.US, "%.1f %sB", value, prefix)
    }

    fun formatDeltaPercent(originalBytes: Long, newBytes: Long): String {
        if (originalBytes <= 0L) return "0%"
        val diffPct = (((newBytes - originalBytes).toDouble() / originalBytes.toDouble()) * 100.0).toInt()
        return if (diffPct <= 0) "$diffPct%" else "+$diffPct%"
    }
}
