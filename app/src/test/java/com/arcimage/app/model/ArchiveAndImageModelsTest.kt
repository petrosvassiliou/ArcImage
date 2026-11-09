package com.arcimage.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveAndImageModelsTest {

    @Test
    fun `detectFromFileName identifies single and compound archive extensions`() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.detectFromFileName("backup.zip"))
        assertEquals(ArchiveFormat.SEVEN_Z, ArchiveFormat.detectFromFileName("project.7z"))
        assertEquals(ArchiveFormat.RAR, ArchiveFormat.detectFromFileName("photos_rar5.rar"))
        assertEquals(ArchiveFormat.TAR, ArchiveFormat.detectFromFileName("bundle.tar"))
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.detectFromFileName("sources.tar.gz"))
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.detectFromFileName("sources.tgz"))
        assertEquals(ArchiveFormat.TAR_BZ2, ArchiveFormat.detectFromFileName("logs.tar.bz2"))
        assertEquals(ArchiveFormat.TAR_BZ2, ArchiveFormat.detectFromFileName("logs.tbz2"))
        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormat.detectFromFileName("kernel.tar.xz"))
        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormat.detectFromFileName("kernel.txz"))
        assertEquals(ArchiveFormat.GZ, ArchiveFormat.detectFromFileName("dump.sql.gz"))
        assertEquals(ArchiveFormat.BZ2, ArchiveFormat.detectFromFileName("trace.bz2"))
        assertEquals(ArchiveFormat.XZ, ArchiveFormat.detectFromFileName("firmware.bin.xz"))
        assertNull(ArchiveFormat.detectFromFileName("photo.heic", "image/heic"))
    }

    @Test
    fun `RAR is marked extract-only while ZIP supports encryption and compression`() {
        assertTrue(ArchiveFormat.ZIP.supportsCompression)
        assertTrue(ArchiveFormat.ZIP.supportsEncryption)
        assertEquals(false, ArchiveFormat.RAR.supportsCompression)
        assertTrue(ArchiveFormat.RAR.supportsEncryption)
    }

    @Test
    fun `AppLanguage maps all 6 supported locales and regional tags`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromLanguageTag("de-DE"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromLanguageTag("it-IT"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromLanguageTag("fr-FR"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromLanguageTag("es-ES"))
        assertEquals(AppLanguage.GREEK, AppLanguage.fromLanguageTag("el-GR"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("ja-JP"))
    }

    @Test
    fun `ImageProcessConfig computes percentage and aspect-ratio-locked dimensions accurately`() {
        val pct25 = ImageProcessConfig(resizeMode = ResizeMode.PERCENT_25)
        assertEquals(1000 to 500, pct25.computeTargetDimensions(4000, 2000))

        val pct50 = ImageProcessConfig(resizeMode = ResizeMode.PERCENT_50)
        assertEquals(960 to 540, pct50.computeTargetDimensions(1920, 1080))

        val pctCustom = ImageProcessConfig(resizeMode = ResizeMode.PERCENT_CUSTOM, customPercentage = 60)
        assertEquals(600 to 300, pctCustom.computeTargetDimensions(1000, 500))

        // Aspect ratio preserved inside 1000x1000 bounding box for 4000x2000 (2:1) image -> 1000x500
        val exactLocked = ImageProcessConfig(
            resizeMode = ResizeMode.EXACT_PIXELS,
            exactWidth = 1000,
            exactHeight = 1000,
            maintainAspectRatio = true
        )
        assertEquals(1000 to 500, exactLocked.computeTargetDimensions(4000, 2000))

        // Aspect ratio unlocked -> exact 1000x1000 stretch
        val exactUnlocked = exactLocked.copy(maintainAspectRatio = false)
        assertEquals(1000 to 1000, exactUnlocked.computeTargetDimensions(4000, 2000))
    }

    @Test
    fun `FileSizeFormatter and savedRatioPercent format sizes and deltas cleanly`() {
        assertEquals("0 B", FileSizeFormatter.formatBytes(0L))
        assertEquals("512 B", FileSizeFormatter.formatBytes(512L))
        assertEquals("1.0 KB", FileSizeFormatter.formatBytes(1024L))
        assertEquals("2.5 MB", FileSizeFormatter.formatBytes((2.5 * 1024 * 1024).toLong()))
        assertEquals("-75%", FileSizeFormatter.formatDeltaPercent(1000L, 250L))
        assertEquals("+20%", FileSizeFormatter.formatDeltaPercent(1000L, 1200L))

        val entry = ArchiveEntryItem(
            path = "docs/manual.pdf",
            name = "manual.pdf",
            isDirectory = false,
            uncompressedSize = 10_000L,
            compressedSize = 3_500L,
            lastModifiedMillis = 0L,
            isEncrypted = true
        )
        assertEquals(65, entry.savedRatioPercent)
    }
}
