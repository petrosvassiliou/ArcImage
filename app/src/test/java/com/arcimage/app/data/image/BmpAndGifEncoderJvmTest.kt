package com.arcimage.app.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

class BmpAndGifEncoderJvmTest {

    @Test
    fun `BmpEncoder writes valid 32-bit BGRA DIB header and scanlines`() {
        val width = 4
        val height = 3
        // Pure opaque Red (0xFFFF0000), Green (0xFF00FF00), Blue (0xFF0000FF) rows
        val pixels = IntArray(width * height) { idx ->
            when (idx / width) {
                0 -> 0xFFFF0000.toInt()
                1 -> 0xFF00FF00.toInt()
                else -> 0xFF0000FF.toInt()
            }
        }

        val out = ByteArrayOutputStream()
        BmpEncoder.encodeArgbPixels(width, height, pixels, out)
        val bytes = out.toByteArray()

        // 14-byte file header + 40-byte DIB header + 4*3*4 pixel bytes = 102 bytes
        assertEquals(54 + (width * height * 4), bytes.size)
        assertEquals('B'.code.toByte(), bytes[0])
        assertEquals('M'.code.toByte(), bytes[1])

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(bytes.size, buf.getInt(2))
        assertEquals(54, buf.getInt(10)) // Pixel data offset
        assertEquals(40, buf.getInt(14)) // BITMAPINFOHEADER size
        assertEquals(width, buf.getInt(18))
        assertEquals(-height, buf.getInt(22)) // Top-down scanline order
        assertEquals(32, buf.getShort(28).toInt()) // 32-bit BGRA

        // First pixel is Opaque Red (0xFFFF0000) -> BGRA = [0x00, 0x00, 0xFF, 0xFF]
        assertEquals(0x00, bytes[54].toInt() and 0xFF)
        assertEquals(0x00, bytes[55].toInt() and 0xFF)
        assertEquals(0xFF, bytes[56].toInt() and 0xFF)
        assertEquals(0xFF, bytes[57].toInt() and 0xFF)
    }

    @Test
    fun `GifEncoder produces valid GIF89a image readable by standard ImageIO decoder`() {
        val width = 16
        val height = 12
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val r = (x * 255) / (width - 1)
            val g = (y * 255) / (height - 1)
            val b = 128
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val out = ByteArrayOutputStream()
        GifEncoder.encodeArgbPixels(width, height, pixels, out)
        val gifBytes = out.toByteArray()

        // Verify GIF89a signature & trailer byte 0x3B
        val headerAscii = String(gifBytes, 0, 6, Charsets.US_ASCII)
        assertEquals("GIF89a", headerAscii)
        assertEquals(0x3B, gifBytes.last().toInt() and 0xFF)

        // Decode with standard JDK ImageIO GIF reader to verify LZW decompression integrity
        val decodedImage = ImageIO.read(ByteArrayInputStream(gifBytes))
        assertNotNull("Generated GIF89a stream must decode cleanly via ImageIO", decodedImage)
        assertEquals(width, decodedImage.width)
        assertEquals(height, decodedImage.height)
        assertTrue(gifBytes.size > 800)
    }
}
