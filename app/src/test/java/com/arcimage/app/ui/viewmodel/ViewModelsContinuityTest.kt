package com.arcimage.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.arcimage.app.data.archive.ArchiveRepository
import com.arcimage.app.data.files.FileSortOrder
import com.arcimage.app.data.image.ImageRepository
import com.arcimage.app.model.AppDestination
import com.arcimage.app.model.ArchiveEntryItem
import com.arcimage.app.model.ArchiveOperationProgress
import com.arcimage.app.model.ArchivePasswordRequiredException
import com.arcimage.app.model.ArchiveWorkspaceMode
import com.arcimage.app.model.CompressionConfig
import com.arcimage.app.model.ImageBatchItem
import com.arcimage.app.model.ImageBatchProgress
import com.arcimage.app.model.ImageFormat
import com.arcimage.app.model.ImageProcessConfig
import com.arcimage.app.model.ResizeMode
import com.arcimage.app.model.SourceItem
import com.arcimage.app.model.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelsContinuityTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `MainShellViewModel persists destination and theme across Googlebook window recreation`() {
        val sharedHandle = SavedStateHandle()
        val vm1 = MainShellViewModel(sharedHandle)

        vm1.selectDestination(AppDestination.IMAGE_STUDIO)
        vm1.setThemePreference(ThemePreference.DARK)
        vm1.setUseDynamicColor(false)

        // Recreate ViewModel from same SavedStateHandle (simulating cross-device continuity handoff)
        val vm2 = MainShellViewModel(sharedHandle)
        assertEquals(AppDestination.IMAGE_STUDIO, vm2.uiState.value.activeDestination)
        assertEquals(ThemePreference.DARK, vm2.uiState.value.themePreference)
        assertEquals(false, vm2.uiState.value.useDynamicColor)
    }

    @Test
    fun `ArchiveViewModel handles encrypted RAR password prompt and keyboard selection shortcuts`() = runTest {
        val fakeUri = Uri.parse("content://com.arcimage.test/encrypted.rar")
        val fakeArchiveRepo = object : ArchiveRepository {
            override suspend fun resolveSourceItems(uris: List<Uri>): List<SourceItem> =
                uris.mapIndexed { i, u ->
                    SourceItem(
                        id = "item_$i",
                        uri = u,
                        name = "encrypted.rar",
                        sizeBytes = 4096L,
                        isDirectory = false
                    )
                }

            override suspend fun inspectArchive(archiveUri: Uri, password: String?): List<ArchiveEntryItem> {
                if (password != "rar5pass") {
                    throw ArchivePasswordRequiredException("encrypted.rar")
                }
                return listOf(
                    ArchiveEntryItem("doc1.pdf", "doc1.pdf", false, 2000L, 1000L, 0L, true, 0),
                    ArchiveEntryItem("doc2.pdf", "doc2.pdf", false, 3000L, 1500L, 0L, true, 1)
                )
            }

            override fun compressItems(
                items: List<SourceItem>,
                config: CompressionConfig,
                destinationTreeUri: Uri?
            ): Flow<ArchiveOperationProgress> = flow {}

            override fun extractArchive(
                archiveUri: Uri,
                destinationTreeUri: Uri?,
                selectedEntryPaths: Set<String>?,
                password: String?
            ): Flow<ArchiveOperationProgress> = flow {}
        }

        val handle = SavedStateHandle()
        val vm = ArchiveViewModel(fakeArchiveRepo, handle)

        // 1. Opening encrypted archive without password triggers PasswordPromptState
        vm.openArchiveForInspection(fakeUri, password = null)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.passwordPrompt)
        assertEquals(ArchiveWorkspaceMode.EXPLORE, vm.uiState.value.mode)

        // 2. Submitting valid password unlocks entries and clears modal
        vm.submitPasswordPrompt("rar5pass")
        advanceUntilIdle()
        assertNull(vm.uiState.value.passwordPrompt)
        assertEquals(2, vm.uiState.value.archiveEntries.size)

        // 3. Ctrl+A selects all entries; Esc clears selection
        vm.selectAllInCurrentMode()
        assertEquals(2, vm.uiState.value.selectedEntryPaths.size)
        vm.clearSelectionAndModals()
        assertTrue(vm.uiState.value.selectedEntryPaths.isEmpty())
    }

    @Test
    fun `ImageStudioViewModel locks aspect ratio, updates size estimates, and processes batch`() = runTest {
        val fakeUri = Uri.parse("content://com.arcimage.test/photo.jpg")
        val fakeImageRepo = object : ImageRepository {
            override suspend fun loadBatchItems(uris: List<Uri>): List<ImageBatchItem> = listOf(
                ImageBatchItem(
                    id = "img_1",
                    uri = fakeUri,
                    name = "photo.jpg",
                    mimeType = "image/jpeg",
                    originalSizeBytes = 4_000_000L,
                    originalWidth = 4000,
                    originalHeight = 2000,
                    hasExif = true
                )
            )

            override fun estimateItemOutputSize(item: ImageBatchItem, config: ImageProcessConfig): Long {
                val (w, h) = config.computeTargetDimensions(item.originalWidth, item.originalHeight)
                return ((w.toLong() * h.toLong() * config.quality) / 200L).coerceAtLeast(512L)
            }

            override fun estimateBatchOutputSize(
                items: List<ImageBatchItem>,
                config: ImageProcessConfig
            ): Long = items.sumOf { estimateItemOutputSize(it, config) }

            override fun processBatch(
                items: List<ImageBatchItem>,
                config: ImageProcessConfig,
                destinationTreeUri: Uri?
            ): Flow<ImageBatchProgress> = flow {
                items.forEachIndexed { index, item ->
                    emit(
                        ImageBatchProgress(
                            completedCount = index + 1,
                            totalCount = items.size,
                            currentFileName = item.name,
                            updatedItem = item.copy(actualOutputSizeBytes = 320_000L)
                        )
                    )
                }
            }
        }

        val handle = SavedStateHandle()
        val vm = ImageStudioViewModel(fakeImageRepo, handle)

        vm.addImages(listOf(fakeUri))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.batchItems.size)

        // Maintain aspect ratio: changing width on 4000x2000 (2:1) image to 1200 -> height becomes 600
        vm.setResizeMode(ResizeMode.EXACT_PIXELS)
        vm.setMaintainAspectRatio(true)
        vm.setExactWidth(1200)
        assertEquals(1200, vm.uiState.value.config.exactWidth)
        assertEquals(600, vm.uiState.value.config.exactHeight)

        // Lowering quality reduces estimated output size
        vm.setOutputFormat(ImageFormat.HEIC)
        vm.setQuality(90)
        val highQSize = vm.uiState.value.estimatedTotalOutputBytes
        vm.setQuality(40)
        val lowQSize = vm.uiState.value.estimatedTotalOutputBytes
        assertTrue(lowQSize < highQSize)

        // Batch processing populates actualOutputSizeBytes and banner message
        vm.processBatch()
        advanceUntilIdle()
        assertEquals(320_000L, vm.uiState.value.batchItems.first().actualOutputSizeBytes)
        assertNotNull(vm.uiState.value.bannerMessage)
    }

    @Test
    fun `App opens on the real device Files browser with brand colours by default`() {
        val vm = MainShellViewModel(SavedStateHandle())
        assertEquals(AppDestination.FILES, vm.uiState.value.activeDestination)
        assertEquals(false, vm.uiState.value.useDynamicColor)
        assertEquals(AppDestination.FILES, AppDestination.entries.first())
    }

    @Test
    fun `FileBrowserViewModel persists view mode, sort and search across window recreation`() {
        val handle = SavedStateHandle()
        val context = RuntimeEnvironment.getApplication()
        val vm1 = FileBrowserViewModel(context, handle, accessChecker = { false })
        vm1.setGridView(true)
        vm1.setSortOrder(FileSortOrder.DATE)
        vm1.setSearchQuery("trip")

        val vm2 = FileBrowserViewModel(context, handle, accessChecker = { false })
        assertEquals(false, vm2.uiState.value.hasStorageAccess)
        assertEquals(true, vm2.uiState.value.gridView)
        assertEquals(FileSortOrder.DATE, vm2.uiState.value.sortOrder)
        assertEquals("trip", vm2.uiState.value.searchQuery)
    }
}
