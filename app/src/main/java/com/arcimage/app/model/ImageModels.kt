package com.arcimage.app.model

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.StringRes
import com.arcimage.app.R
import kotlinx.parcelize.Parcelize
import java.util.Locale
import kotlin.math.roundToInt

enum class ImageFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val supportsLossyQuality: Boolean,
    val supportsExifMetadata: Boolean
) {
    JPEG(
        displayName = "JPG",
        extension = "jpg",
        mimeType = "image/jpeg",
        supportsLossyQuality = true,
        supportsExifMetadata = true
    ),
    PNG(
        displayName = "PNG",
        extension = "png",
        mimeType = "image/png",
        supportsLossyQuality = false,
        supportsExifMetadata = true
    ),
    WEBP(
        displayName = "WEBP",
        extension = "webp",
        mimeType = "image/webp",
        supportsLossyQuality = true,
        supportsExifMetadata = true
    ),
    GIF(
        displayName = "GIF",
        extension = "gif",
        mimeType = "image/gif",
        supportsLossyQuality = false,
        supportsExifMetadata = false
    ),
    BMP(
        displayName = "BMP",
        extension = "bmp",
        mimeType = "image/bmp",
        supportsLossyQuality = false,
        supportsExifMetadata = false
    ),
    HEIC(
        displayName = "HEIC",
        extension = "heic",
        mimeType = "image/heic",
        supportsLossyQuality = true,
        supportsExifMetadata = false
    );

    companion object {
        fun isSupportedImage(fileName: String, mimeType: String?): Boolean {
            if (mimeType?.startsWith("image/") == true) return true
            val lower = fileName.lowercase(Locale.ROOT)
            return lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".png") ||
                lower.endsWith(".webp") ||
                lower.endsWith(".gif") ||
                lower.endsWith(".bmp") ||
                lower.endsWith(".heic") ||
                lower.endsWith(".heif")
        }
    }
}

enum class ResizeMode(
    @StringRes val labelRes: Int
) {
    PERCENT_25(R.string.resize_mode_25),
    PERCENT_50(R.string.resize_mode_50),
    PERCENT_75(R.string.resize_mode_75),
    PERCENT_CUSTOM(R.string.resize_mode_custom_pct),
    EXACT_PIXELS(R.string.resize_mode_exact_px)
}

@Parcelize
data class ImageBatchItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val originalSizeBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val hasExif: Boolean,
    val exifSummary: String = "",
    val outputUri: Uri? = null,
    val actualOutputSizeBytes: Long? = null,
    val outputWidth: Int? = null,
    val outputHeight: Int? = null
) : Parcelable

@Parcelize
data class ImageProcessConfig(
    val resizeMode: ResizeMode = ResizeMode.PERCENT_75,
    val customPercentage: Int = 80,
    val exactWidth: Int = 1920,
    val exactHeight: Int = 1080,
    val maintainAspectRatio: Boolean = true,
    val quality: Int = 85,
    val outputFormat: ImageFormat = ImageFormat.WEBP,
    val preserveExif: Boolean = false
) : Parcelable {

    fun computeTargetDimensions(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        val safeSrcW = sourceWidth.coerceAtLeast(1)
        val safeSrcH = sourceHeight.coerceAtLeast(1)

        return when (resizeMode) {
            ResizeMode.PERCENT_25 -> Pair(
                (safeSrcW * 0.25f).roundToInt().coerceAtLeast(1),
                (safeSrcH * 0.25f).roundToInt().coerceAtLeast(1)
            )
            ResizeMode.PERCENT_50 -> Pair(
                (safeSrcW * 0.50f).roundToInt().coerceAtLeast(1),
                (safeSrcH * 0.50f).roundToInt().coerceAtLeast(1)
            )
            ResizeMode.PERCENT_75 -> Pair(
                (safeSrcW * 0.75f).roundToInt().coerceAtLeast(1),
                (safeSrcH * 0.75f).roundToInt().coerceAtLeast(1)
            )
            ResizeMode.PERCENT_CUSTOM -> {
                val scale = (customPercentage.coerceIn(1, 400) / 100f)
                Pair(
                    (safeSrcW * scale).roundToInt().coerceAtLeast(1),
                    (safeSrcH * scale).roundToInt().coerceAtLeast(1)
                )
            }
            ResizeMode.EXACT_PIXELS -> {
                val requestedW = exactWidth.coerceAtLeast(1)
                val requestedH = exactHeight.coerceAtLeast(1)
                if (!maintainAspectRatio) {
                    Pair(requestedW, requestedH)
                } else {
                    val aspect = safeSrcW.toDouble() / safeSrcH.toDouble()
                    val fitByWidthH = (requestedW / aspect).roundToInt().coerceAtLeast(1)
                    if (fitByWidthH <= requestedH) {
                        Pair(requestedW, fitByWidthH)
                    } else {
                        val fitByHeightW = (requestedH * aspect).roundToInt().coerceAtLeast(1)
                        Pair(fitByHeightW, requestedH)
                    }
                }
            }
        }
    }
}

data class ImageBatchProgress(
    val completedCount: Int,
    val totalCount: Int,
    val currentFileName: String,
    val updatedItem: ImageBatchItem? = null
) {
    val fraction: Float
        get() = if (totalCount > 0) {
            (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        } else 0f
}
