package com.arcimage.app.data.image

import android.graphics.Bitmap
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin 32-bit BGRA Windows DIB (BITMAPINFOHEADER) encoder.
 */
object BmpEncoder {

    fun encode(bitmap: Bitmap, out: OutputStream) {
        val width = bitmap.width
        val height = bitmap.height
        val rowPixels = IntArray(width)
        encodeRows(width = width, height = height, out = out) { y ->
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
            rowPixels
        }
    }

    /**
     * Encodes raw ARGB_8888 pixels (row-major `width * height`) into a 32-bit BGRA BMP stream.
     * Directly testable on pure JVM unit tests as well as Android instrumentation.
     */
    fun encodeArgbPixels(width: Int, height: Int, argbPixels: IntArray, out: OutputStream) {
        require(width > 0 && height > 0) { "BMP dimensions must be positive" }
        require(argbPixels.size >= width * height) { "Insufficient pixel buffer for ${width}x${height}" }
        val rowSlice = IntArray(width)
        encodeRows(width = width, height = height, out = out) { y ->
            System.arraycopy(argbPixels, y * width, rowSlice, 0, width)
            rowSlice
        }
    }

    private inline fun encodeRows(
        width: Int,
        height: Int,
        out: OutputStream,
        rowProvider: (y: Int) -> IntArray
    ) {
        val rowBytes = width * 4
        val imageSize = rowBytes * height
        val fileHeaderSize = 14
        val infoHeaderSize = 40
        val fileSize = fileHeaderSize + infoHeaderSize + imageSize

        val header = ByteBuffer.allocate(fileHeaderSize + infoHeaderSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        // BITMAPFILEHEADER (14 bytes)
        header.put('B'.code.toByte())
        header.put('M'.code.toByte())
        header.putInt(fileSize)
        header.putShort(0)
        header.putShort(0)
        header.putInt(fileHeaderSize + infoHeaderSize)

        // BITMAPINFOHEADER (40 bytes) - top-down negative height for direct scanline streaming
        header.putInt(infoHeaderSize)
        header.putInt(width)
        header.putInt(-height)
        header.putShort(1) // planes
        header.putShort(32) // bits per pixel (BGRA)
        header.putInt(0) // BI_RGB uncompressed
        header.putInt(imageSize)
        header.putInt(2835) // ~72 DPI X
        header.putInt(2835) // ~72 DPI Y
        header.putInt(0)
        header.putInt(0)

        out.write(header.array())

        val rowBuffer = ByteArray(rowBytes)
        for (y in 0 until height) {
            val rowPixels = rowProvider(y)
            var offset = 0
            for (x in 0 until width) {
                val argb = rowPixels[x]
                rowBuffer[offset++] = (argb and 0xFF).toByte()          // Blue
                rowBuffer[offset++] = ((argb shr 8) and 0xFF).toByte()  // Green
                rowBuffer[offset++] = ((argb shr 16) and 0xFF).toByte() // Red
                rowBuffer[offset++] = ((argb shr 24) and 0xFF).toByte() // Alpha
            }
            out.write(rowBuffer)
        }
        out.flush()
    }
}
