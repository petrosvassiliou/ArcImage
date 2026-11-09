package com.arcimage.app.data.image

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ExifHelper {

    private val PRESERVABLE_TAGS = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_IMAGE_DESCRIPTION
    )

    fun readExifSummary(context: Context, uri: Uri): Pair<Boolean, String> {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val presentParts = mutableListOf<String>()
                exif.getAttribute(ExifInterface.TAG_MODEL)?.takeIf { it.isNotBlank() }?.let {
                    presentParts += it.trim()
                }
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.takeIf { it.isNotBlank() }?.let {
                    presentParts += it.trim()
                }
                val hasGps = exif.latLong != null
                if (hasGps) {
                    presentParts += "GPS"
                }
                val anyMetadata = presentParts.isNotEmpty() || PRESERVABLE_TAGS.any { tag ->
                    !exif.getAttribute(tag).isNullOrBlank()
                }
                anyMetadata to presentParts.joinToString(" • ")
            } ?: (false to "")
        }.getOrDefault(false to "")
    }

    fun copyExifMetadata(
        context: Context,
        sourceUri: Uri,
        targetFile: File,
        targetWidth: Int,
        targetHeight: Int
    ) {
        runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { srcStream ->
                val srcExif = ExifInterface(srcStream)
                val dstExif = ExifInterface(targetFile)

                for (tag in PRESERVABLE_TAGS) {
                    val value = srcExif.getAttribute(tag)
                    if (!value.isNullOrEmpty()) {
                        dstExif.setAttribute(tag, value)
                    }
                }
                // ImageDecoder already applies rotation during decode; normalize orientation
                dstExif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
                dstExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, targetWidth.toString())
                dstExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, targetHeight.toString())
                dstExif.saveAttributes()
            }
        }
    }
}
