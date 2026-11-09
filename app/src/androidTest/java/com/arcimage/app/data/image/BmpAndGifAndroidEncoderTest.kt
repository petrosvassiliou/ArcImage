package com.arcimage.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BmpAndGifAndroidEncoderTest {

    @Test
    fun bmpEncoderRoundTripsWithAndroidBitmapFactory() {
        val srcBitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(20, 120, 220))
        }
        val out = ByteArrayOutputStream()
        BmpEncoder.encode(srcBitmap, out)
        srcBitmap.recycle()

        val encodedBytes = out.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(encodedBytes, 0, encodedBytes.size)
        assertNotNull("Android BitmapFactory must decode generated 32-bit BMP", decoded)
        assertEquals(24, decoded!!.width)
        assertEquals(16, decoded.height)
        assertEquals(Color.rgb(20, 120, 220), decoded.getPixel(12, 8))
        decoded.recycle()
    }

    @Test
    fun gifEncoderRoundTripsWithAndroidBitmapFactory() {
        val srcBitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val out = ByteArrayOutputStream()
        GifEncoder.encode(srcBitmap, out)
        srcBitmap.recycle()

        val encodedBytes = out.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(encodedBytes, 0, encodedBytes.size)
        assertNotNull("Android BitmapFactory must decode generated GIF89a", decoded)
        assertEquals(32, decoded!!.width)
        assertEquals(24, decoded.height)
        decoded.recycle()
    }
}
