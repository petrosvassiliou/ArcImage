package com.arcimage.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.heifwriter.HeifWriter
import com.arcimage.app.data.util.DocumentUriHelper
import com.arcimage.app.model.ImageBatchItem
import com.arcimage.app.model.ImageBatchProgress
import com.arcimage.app.model.ImageFormat
import com.arcimage.app.model.ImageProcessConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.pow

class ImageRepositoryImpl(
    private val context: Context
) : ImageRepository {

    override suspend fun loadBatchItems(uris: List<Uri>): List<ImageBatchItem> =
        withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                runCatching { inspectSingleImage(uri) }.getOrNull()
            }
        }

    override fun estimateItemOutputSize(
        item: ImageBatchItem,
        config: ImageProcessConfig
    ): Long {
        val (targetW, targetH) = config.computeTargetDimensions(item.originalWidth, item.originalHeight)
        val pixelCount = targetW.toLong() * targetH.toLong()
        val qNorm = (config.quality.coerceIn(1, 100) / 100.0)

        val estimatedBytes: Double = when (config.outputFormat) {
            ImageFormat.BMP -> 54.0 + (pixelCount * 4.0)
            ImageFormat.PNG -> pixelCount * 1.45
            ImageFormat.GIF -> 800.0 + (pixelCount * 0.55)
            ImageFormat.JPEG -> {
                // Realistic perceptual bpp curve for JPEG (0.18 bpp at Q=10 up to ~3.4 bpp at Q=100)
                val bytesPerPixel = 0.04 + 0.42 * qNorm.pow(2.2)
                pixelCount * bytesPerPixel
            }
            ImageFormat.WEBP -> {
                val bytesPerPixel = if (config.quality == 100) {
                    1.05
                } else {
                    0.028 + 0.30 * qNorm.pow(2.1)
                }
                pixelCount * bytesPerPixel
            }
            ImageFormat.HEIC -> {
                val bytesPerPixel = 0.022 + 0.24 * qNorm.pow(2.1)
                pixelCount * bytesPerPixel
            }
        }
        val exifOverhead = if (config.preserveExif && item.hasExif && config.outputFormat.supportsExifMetadata) {
            4096L
        } else {
            0L
        }
        return (estimatedBytes.toLong() + exifOverhead).coerceAtLeast(256L)
    }

    override fun estimateBatchOutputSize(
        items: List<ImageBatchItem>,
        config: ImageProcessConfig
    ): Long {
        return items.sumOf { estimateItemOutputSize(it, config) }
    }

    override fun processBatch(
        items: List<ImageBatchItem>,
        config: ImageProcessConfig,
        destinationTreeUri: Uri?
    ): Flow<ImageBatchProgress> = flow {
        val workDir = File(context.cacheDir, "image_batch").apply { mkdirs() }

        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()

            emit(
                ImageBatchProgress(
                    completedCount = index,
                    totalCount = items.size,
                    currentFileName = item.name
                )
            )

            val (targetW, targetH) = config.computeTargetDimensions(
                item.originalWidth,
                item.originalHeight
            )

            val decodedBitmap = decodeScaledBitmap(item.uri, targetW, targetH)
            val tempOutFile = File(
                workDir,
                "img_${UUID.randomUUID()}.${config.outputFormat.extension}"
            )

            try {
                encodeBitmapToFormat(
                    bitmap = decodedBitmap,
                    outputFile = tempOutFile,
                    format = config.outputFormat,
                    quality = config.quality.coerceIn(1, 100)
                )

                // Strip EXIF by default (fresh bitmap encode has zero camera/GPS EXIF);
                // copy EXIF explicitly only when preserveExif == true and supported.
                if (config.preserveExif && config.outputFormat.supportsExifMetadata) {
                    ExifHelper.copyExifMetadata(
                        context = context,
                        sourceUri = item.uri,
                        targetFile = tempOutFile,
                        targetWidth = decodedBitmap.width,
                        targetHeight = decodedBitmap.height
                    )
                }

                val actualBytes = tempOutFile.length()
                val stem = item.name.substringBeforeLast('.', item.name).ifBlank { "image_${index + 1}" }
                val desiredName = "${stem}_${decodedBitmap.width}x${decodedBitmap.height}.${config.outputFormat.extension}"

                val (exportedUri, _) = DocumentUriHelper.exportFileToDestination(
                    context = context,
                    localSourceFile = tempOutFile,
                    desiredFileName = desiredName,
                    mimeType = config.outputFormat.mimeType,
                    destinationTreeUri = destinationTreeUri
                )

                val updatedItem = item.copy(
                    outputUri = exportedUri,
                    actualOutputSizeBytes = actualBytes,
                    outputWidth = decodedBitmap.width,
                    outputHeight = decodedBitmap.height
                )

                emit(
                    ImageBatchProgress(
                        completedCount = index + 1,
                        totalCount = items.size,
                        currentFileName = item.name,
                        updatedItem = updatedItem
                    )
                )
            } finally {
                decodedBitmap.recycle()
                if (tempOutFile.exists()) {
                    tempOutFile.delete()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun inspectSingleImage(uri: Uri): ImageBatchItem {
        val displayName = DocumentUriHelper.resolveDisplayName(context, uri)
        val sizeBytes = DocumentUriHelper.resolveFileSize(context, uri)
        val mimeType = DocumentUriHelper.resolveMimeType(context, uri) ?: "image/*"

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        var width = boundsOptions.outWidth.coerceAtLeast(1)
        var height = boundsOptions.outHeight.coerceAtLeast(1)

        // Fallback to ImageDecoder header inspection for HEIC/HEIF or exotic containers
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            runCatching {
                val source = if (uri.scheme == "file") {
                    ImageDecoder.createSource(File(uri.path!!))
                } else {
                    ImageDecoder.createSource(context.contentResolver, uri)
                }
                val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    width = info.size.width.coerceAtLeast(1)
                    height = info.size.height.coerceAtLeast(1)
                    decoder.setTargetSize(16, 16)
                }
                bmp.recycle()
            }
        }

        val (hasExif, exifSummary) = ExifHelper.readExifSummary(context, uri)

        return ImageBatchItem(
            id = uri.toString(),
            uri = uri,
            name = displayName,
            mimeType = mimeType,
            originalSizeBytes = sizeBytes,
            originalWidth = width,
            originalHeight = height,
            hasExif = hasExif,
            exifSummary = exifSummary
        )
    }

    private fun decodeScaledBitmap(
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val source = if (uri.scheme == "file") {
            ImageDecoder.createSource(File(uri.path!!))
        } else {
            ImageDecoder.createSource(context.contentResolver, uri)
        }

        val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            decoder.setTargetSize(targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1))
        }

        return if (decoded.width != targetWidth || decoded.height != targetHeight) {
            val exact = Bitmap.createScaledBitmap(
                decoded,
                targetWidth.coerceAtLeast(1),
                targetHeight.coerceAtLeast(1),
                true
            )
            if (exact !== decoded) {
                decoded.recycle()
            }
            exact
        } else {
            decoded
        }
    }

    private fun encodeBitmapToFormat(
        bitmap: Bitmap,
        outputFile: File,
        format: ImageFormat,
        quality: Int
    ) {
        when (format) {
            ImageFormat.JPEG -> {
                // Composite onto opaque white if source has alpha channel
                val rgbBitmap = ensureOpaqueIfNeeded(bitmap)
                BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                    rgbBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                if (rgbBitmap !== bitmap) rgbBitmap.recycle()
            }
            ImageFormat.PNG -> {
                BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            ImageFormat.WEBP -> {
                BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val compressFormat = if (quality == 100) {
                            Bitmap.CompressFormat.WEBP_LOSSLESS
                        } else {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        }
                        bitmap.compress(compressFormat, quality, out)
                    } else {
                        @Suppress("DEPRECATION")
                        bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
                    }
                }
            }
            ImageFormat.BMP -> {
                BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                    BmpEncoder.encode(bitmap, out)
                }
            }
            ImageFormat.GIF -> {
                BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                    GifEncoder.encode(bitmap, out)
                }
            }
            ImageFormat.HEIC -> {
                // Hardware/Software HEIF/HEIC writer (even dimensions required by HEVC encoders)
                val evenW = (bitmap.width + (bitmap.width % 2)).coerceAtLeast(2)
                val evenH = (bitmap.height + (bitmap.height % 2)).coerceAtLeast(2)
                val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888 ||
                    bitmap.width != evenW ||
                    bitmap.height != evenH
                ) {
                    val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    val scaled = Bitmap.createScaledBitmap(copy, evenW, evenH, true)
                    if (scaled !== copy) copy.recycle()
                    scaled
                } else {
                    bitmap
                }

                try {
                    HeifWriter.Builder(
                        outputFile.absolutePath,
                        argbBitmap.width,
                        argbBitmap.height,
                        HeifWriter.INPUT_MODE_BITMAP
                    )
                        .setQuality(quality)
                        .setMaxImages(1)
                        .build()
                        .use { writer ->
                            writer.start()
                            writer.addBitmap(argbBitmap)
                            writer.stop(5_000L)
                        }
                } finally {
                    if (argbBitmap !== bitmap) {
                        argbBitmap.recycle()
                    }
                }
            }
        }
    }

    private fun ensureOpaqueIfNeeded(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(flattened)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        return flattened
    }
}
