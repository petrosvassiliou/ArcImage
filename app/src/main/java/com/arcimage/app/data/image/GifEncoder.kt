package com.arcimage.app.data.image

import android.graphics.Bitmap
import java.io.OutputStream

/**
 * Pure Kotlin GIF89a encoder using 3-3-2 uniform 256-color palette quantization
 * and standard variable-width GIF LZW compression.
 */
object GifEncoder {

    fun encode(bitmap: Bitmap, out: OutputStream) {
        val width = bitmap.width.coerceIn(1, 65535)
        val height = bitmap.height.coerceIn(1, 65535)
        val rowBuffer = IntArray(width)
        encodeRows(width = width, height = height, out = out) { y ->
            bitmap.getPixels(rowBuffer, 0, width, 0, y, width, 1)
            rowBuffer
        }
    }

    /**
     * Encodes raw ARGB_8888 pixels (row-major `width * height`) into a valid GIF89a stream.
     * Directly testable on pure JVM unit tests as well as Android instrumentation.
     */
    fun encodeArgbPixels(width: Int, height: Int, argbPixels: IntArray, out: OutputStream) {
        require(width in 1..65535 && height in 1..65535) { "GIF dimensions must be in 1..65535" }
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
        // 1. Header: GIF89a
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))

        // 2. Logical Screen Descriptor (Global Color Table Flag = 1, 256 colors -> size bits = 7)
        writeShortLE(out, width)
        writeShortLE(out, height)
        out.write(0xF7) // Global Color Table present (1), 8-bit color res (111), sort=0, size=7 (2^(7+1)=256)
        out.write(0)    // Background color index
        out.write(0)    // Pixel aspect ratio

        // 3. Global Color Table (256 RGB entries mapped via 3-bit R, 3-bit G, 2-bit B)
        for (i in 0 until 256) {
            val r3 = (i shr 5) and 0x07
            val g3 = (i shr 2) and 0x07
            val b2 = i and 0x03
            val r = (r3 * 255) / 7
            val g = (g3 * 255) / 7
            val b = (b2 * 340) / 4 // 0, 85, 170, 255
            out.write(r.coerceIn(0, 255))
            out.write(g.coerceIn(0, 255))
            out.write(b.coerceIn(0, 255))
        }

        // 4. Image Descriptor
        out.write(0x2C) // Image Separator
        writeShortLE(out, 0) // Left
        writeShortLE(out, 0) // Top
        writeShortLE(out, width)
        writeShortLE(out, height)
        out.write(0x00) // No local color table, non-interlaced

        // 5. Quantize pixels to 8-bit indices (0..255)
        val totalPixels = width * height
        val indexedPixels = ByteArray(totalPixels)
        var idx = 0
        for (y in 0 until height) {
            val rowBuffer = rowProvider(y)
            for (x in 0 until width) {
                val argb = rowBuffer[x]
                val alpha = (argb ushr 24) and 0xFF
                if (alpha < 32) {
                    indexedPixels[idx++] = 0
                } else {
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    val r3 = (r * 7 + 127) / 255
                    val g3 = (g * 7 + 127) / 255
                    val b2 = (b * 3 + 127) / 255
                    indexedPixels[idx++] = ((r3 shl 5) or (g3 shl 2) or b2).toByte()
                }
            }
        }

        // 6. LZW Image Data (minCodeSize = 8)
        LzwGifCompressor(minCodeSize = 8, pixels = indexedPixels).compress(out)

        // 7. GIF Trailer
        out.write(0x3B)
        out.flush()
    }

    private fun writeShortLE(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private class LzwGifCompressor(
        private val minCodeSize: Int,
        private val pixels: ByteArray
    ) {
        private val clearCode = 1 shl minCodeSize
        private val endOfInfoCode = clearCode + 1
        private val maxCode = 4096

        private val block = ByteArray(255)
        private var blockLen = 0
        private var bitAccumulator = 0
        private var bitCount = 0

        fun compress(out: OutputStream) {
            out.write(minCodeSize)

            val tableSize = 5003
            val hashKeys = IntArray(tableSize) { -1 }
            val hashValues = IntArray(tableSize)

            var codeSize = minCodeSize + 1
            var nextAvailableCode = endOfInfoCode + 1

            fun resetDictionary() {
                hashKeys.fill(-1)
                codeSize = minCodeSize + 1
                nextAvailableCode = endOfInfoCode + 1
            }

            outputCode(clearCode, codeSize, out)
            if (pixels.isEmpty()) {
                outputCode(endOfInfoCode, codeSize, out)
                flushBits(out)
                out.write(0)
                return
            }

            var prefix = pixels[0].toInt() and 0xFF

            for (i in 1 until pixels.size) {
                val k = pixels[i].toInt() and 0xFF
                val compositeKey = (prefix shl 8) or k
                var h = ((k shl 4) xor prefix) % tableSize
                if (h < 0) h += tableSize

                var foundCode = -1
                val step = if (h == 0) 1 else tableSize - h
                while (true) {
                    val existingKey = hashKeys[h]
                    if (existingKey == -1) break
                    if (existingKey == compositeKey) {
                        foundCode = hashValues[h]
                        break
                    }
                    h -= step
                    if (h < 0) h += tableSize
                }

                if (foundCode != -1) {
                    prefix = foundCode
                } else {
                    outputCode(prefix, codeSize, out)
                    if (nextAvailableCode < maxCode) {
                        hashKeys[h] = compositeKey
                        hashValues[h] = nextAvailableCode
                        if (nextAvailableCode == (1 shl codeSize) && codeSize < 12) {
                            codeSize++
                        }
                        nextAvailableCode++
                    } else {
                        outputCode(clearCode, codeSize, out)
                        resetDictionary()
                    }
                    prefix = k
                }
            }

            outputCode(prefix, codeSize, out)
            outputCode(endOfInfoCode, codeSize, out)
            flushBits(out)
            out.write(0)
        }

        private fun outputCode(code: Int, codeSize: Int, out: OutputStream) {
            bitAccumulator = bitAccumulator or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                appendByteToSubBlock((bitAccumulator and 0xFF).toByte(), out)
                bitAccumulator = bitAccumulator ushr 8
                bitCount -= 8
            }
        }

        private fun flushBits(out: OutputStream) {
            if (bitCount > 0) {
                appendByteToSubBlock((bitAccumulator and 0xFF).toByte(), out)
                bitAccumulator = 0
                bitCount = 0
            }
            if (blockLen > 0) {
                out.write(blockLen)
                out.write(block, 0, blockLen)
                blockLen = 0
            }
        }

        private fun appendByteToSubBlock(b: Byte, out: OutputStream) {
            block[blockLen++] = b
            if (blockLen == 255) {
                out.write(255)
                out.write(block, 0, 255)
                blockLen = 0
            }
        }
    }
}
