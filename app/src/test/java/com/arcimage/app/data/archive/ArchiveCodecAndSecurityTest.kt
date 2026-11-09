package com.arcimage.app.data.archive

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.FileOutputStream

class ArchiveCodecAndSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `ZipSlipGuard blocks Unix and Windows relative path traversal attacks`() {
        val destRoot = tempFolder.newFolder("safe_extract_root")

        assertThrows(SecurityException::class.java) {
            ZipSlipGuard.resolveSafeChildFile(destRoot, "../../etc/passwd")
        }
        assertThrows(SecurityException::class.java) {
            ZipSlipGuard.resolveSafeChildFile(destRoot, "..\\..\\Windows\\System32\\cmd.exe")
        }
        assertThrows(SecurityException::class.java) {
            ZipSlipGuard.resolveSafeChildFile(destRoot, "nested/../../outside.sh")
        }

        val safeTarget = ZipSlipGuard.resolveSafeChildFile(destRoot, "docs/2026/readme.txt")
        assertTrue(safeTarget.canonicalPath.startsWith(destRoot.canonicalPath))
    }

    @Test
    fun `AES-256 encrypted ZIP round-trips with right password and rejects wrong password`() {
        val archiveFile = tempFolder.newFile("encrypted_aes256.zip").apply { delete() }
        val secretPayload = "ArcImage Googlebook OS AES-256 Secret Payload Ελληνικά".toByteArray(Charsets.UTF_8)

        ZipFile(archiveFile, "StrongPass!2026".toCharArray()).use { zipOut ->
            val params = ZipParameters().apply {
                fileNameInZip = "vault/secret.txt"
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.MAXIMUM
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            zipOut.addStream(ByteArrayInputStream(secretPayload), params)
        }

        // Wrong password must fail
        assertThrows(ZipException::class.java) {
            ZipFile(archiveFile, "WrongPassword".toCharArray()).use { badZip ->
                val header = badZip.getFileHeader("vault/secret.txt")
                badZip.getInputStream(header).use { it.readBytes() }
            }
        }

        // Correct password succeeds and preserves UTF-8 Greek & Latin payload
        ZipFile(archiveFile, "StrongPass!2026".toCharArray()).use { goodZip ->
            assertTrue(goodZip.isEncrypted)
            val header = goodZip.getFileHeader("vault/secret.txt")
            assertTrue(header.isEncrypted)
            val extracted = goodZip.getInputStream(header).use { it.readBytes() }
            assertArrayEquals(secretPayload, extracted)
        }
    }

    @Test
    fun `7z LZMA2 archive compresses and extracts recursive entries intact`() {
        val sevenZFile = tempFolder.newFile("bundle.7z")
        val payload = "7-Zip LZMA2 Test Content repeated ".repeat(64).toByteArray()

        SevenZOutputFile(sevenZFile).use { out ->
            out.setContentCompression(SevenZMethod.LZMA2)
            val entry = SevenZArchiveEntry().apply {
                name = "project/src/Main.kt"
                isDirectory = false
            }
            out.putArchiveEntry(entry)
            out.write(payload)
            out.closeArchiveEntry()
            out.finish()
        }

        SevenZFile.builder().setFile(sevenZFile).get().use { reader ->
            val first = reader.nextEntry
            assertEquals("project/src/Main.kt", first.name)
            val outBytes = ByteArray(first.size.toInt())
            var offset = 0
            while (offset < outBytes.size) {
                offset += reader.read(outBytes, offset, outBytes.size - offset)
            }
            assertArrayEquals(payload, outBytes)
        }
    }

    @Test
    fun `TAR GZ, TAR BZ2, and TAR XZ round-trip preserve paths and contents`() {
        val sampleText = "Multi-codec TAR test payload — ArcImage".repeat(32).toByteArray()

        listOf("gz", "bz2", "xz").forEach { codec ->
            val archive = tempFolder.newFile("sample.tar.$codec")
            val rawOut = BufferedOutputStream(FileOutputStream(archive))
            val compressedOut = when (codec) {
                "gz" -> GzipCompressorOutputStream(rawOut)
                "bz2" -> BZip2CompressorOutputStream(rawOut, 1)
                else -> XZCompressorOutputStream(rawOut, 3)
            }
            TarArchiveOutputStream(compressedOut, "UTF-8").use { tarOut ->
                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                val entry = TarArchiveEntry("nested/folder/data_$codec.txt").apply {
                    size = sampleText.size.toLong()
                }
                tarOut.putArchiveEntry(entry)
                tarOut.write(sampleText)
                tarOut.closeArchiveEntry()
                tarOut.finish()
            }

            val rawIn = BufferedInputStream(FileInputStream(archive))
            val decompressedIn = when (codec) {
                "gz" -> GzipCompressorInputStream(rawIn)
                "bz2" -> BZip2CompressorInputStream(rawIn)
                else -> XZCompressorInputStream(rawIn)
            }
            TarArchiveInputStream(decompressedIn).use { tarIn ->
                val entry = tarIn.nextEntry
                assertEquals("nested/folder/data_$codec.txt", entry.name)
                assertArrayEquals(sampleText, tarIn.readBytes())
            }
        }
    }
}
