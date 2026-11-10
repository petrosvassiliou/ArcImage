package com.arcimage.app.data.files

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceFileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun classify_detectsFoldersArchivesImagesAndOthers() {
        assertEquals(FileKind.FOLDER, DeviceFileRepository.classify("DCIM", isDirectory = true))
        assertEquals(FileKind.ARCHIVE, DeviceFileRepository.classify("backup.tar.gz", isDirectory = false))
        assertEquals(FileKind.ARCHIVE, DeviceFileRepository.classify("vault.RAR", isDirectory = false))
        assertEquals(FileKind.IMAGE, DeviceFileRepository.classify("photo.HEIC", isDirectory = false))
        assertEquals(FileKind.OTHER, DeviceFileRepository.classify("notes.txt", isDirectory = false))
    }

    @Test
    fun listDirectory_putsFoldersFirstAndHidesDotFiles() {
        val root = tmp.newFolder("storage")
        File(root, "b.zip").writeBytes(ByteArray(10))
        File(root, "a.png").writeBytes(ByteArray(30))
        File(root, ".nomedia").writeText("")
        File(root, "Zeta").mkdirs()
        File(root, "Alpha").mkdirs()
        File(root, "Alpha/inside.txt").writeText("x")

        val entries = DeviceFileRepository.listDirectory(root, FileSortOrder.NAME)

        assertEquals(listOf("Alpha", "Zeta", "a.png", "b.zip"), entries.map { it.name })
        assertEquals(1, entries.first { it.name == "Alpha" }.childCount)
        assertEquals(FileKind.IMAGE, entries.first { it.name == "a.png" }.kind)

        val withHidden = DeviceFileRepository.listDirectory(root, showHidden = true)
        assertTrue(withHidden.any { it.name == ".nomedia" })
    }

    @Test
    fun listDirectory_sortsFilesBySizeDescending() {
        val root = tmp.newFolder("bySize")
        File(root, "small.jpg").writeBytes(ByteArray(5))
        File(root, "large.jpg").writeBytes(ByteArray(500))
        File(root, "medium.jpg").writeBytes(ByteArray(50))

        val names = DeviceFileRepository.listDirectory(root, FileSortOrder.SIZE).map { it.name }
        assertEquals(listOf("large.jpg", "medium.jpg", "small.jpg"), names)
    }

    @Test
    fun filterAndBreadcrumbs_workOnRealPaths() {
        val root = tmp.newFolder("vol")
        val deep = File(root, "Pictures/Trips").apply { mkdirs() }

        val crumbs = DeviceFileRepository.breadcrumbs(root.absolutePath, deep.absolutePath)
        assertEquals(listOf("Pictures", "Trips"), crumbs.map { it.first })

        File(deep, "Rome.jpg").writeBytes(ByteArray(1))
        File(deep, "Paris.jpg").writeBytes(ByteArray(1))
        val filtered = DeviceFileRepository.filter(DeviceFileRepository.listDirectory(deep), "rom")
        assertEquals(listOf("Rome.jpg"), filtered.map { it.name })
    }
}
