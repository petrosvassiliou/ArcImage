package com.arcimage.app.ui.viewmodel

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.arcimage.app.data.archive.ArchiveRepository
import com.arcimage.app.data.files.FileSortOrder
import com.arcimage.app.data.image.ImageRepository
import com.arcimage.app.data.update.AppUpdateRepository
import com.arcimage.app.data.update.UpdateCheckResult
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `MainShellViewModel persists destination, back stack, and navigates back cleanly`() {
        val sharedHandle = SavedStateHandle()
        val vm1 = MainShellViewModel(sharedHandle)

        vm1.selectDestination(AppDestination.IMAGE_STUDIO)
        vm1.selectDestination(AppDestination.ARCHIVE_MANAGER)
        vm1.setThemePreference(ThemePreference.DARK)
        vm1.setUseDynamicColor(false)

        // Recreate ViewModel from same SavedStateHandle (simulating cross-device continuity handoff)
        val vm2 = MainShellViewModel(sharedHandle)
        assertEquals(AppDestination.ARCHIVE_MANAGER, vm2.uiState.value.activeDestination)
        assertEquals(ThemePreference.DARK, vm2.uiState.value.themePreference)
        assertEquals(false, vm2.uiState.value.useDynamicColor)

        // Back button pops ARCHIVE_MANAGER -> IMAGE_STUDIO -> FILES
        assertTrue(vm2.navigateBackDestination())
        assertEquals(AppDestination.IMAGE_STUDIO, vm2.uiState.value.activeDestination)
        assertTrue(vm2.navigateBackDestination())
        assertEquals(AppDestination.FILES, vm2.uiState.value.activeDestination)
        assertFalse(vm2.navigateBackDestination())
    }

    @Test
    fun `MainShellViewModel supports automatic startup check and manual update check`() = runTest {
        var autoEnabled = true
        var nextResult: UpdateCheckResult = UpdateCheckResult.ReleasePageUpdateAvailable(
            currentVersion = "1.1.0",
            latestVersion = "1.2.0",
            releaseUrl = "https://github.com/petrosvassiliou/ArcImage/releases/tag/v1.2.0"
        )
        val fakeUpdateRepo = object : AppUpdateRepository {
            override val currentVersionName: String = "1.1.0"
            override fun isAutoCheckEnabled(): Boolean = autoEnabled
            override fun setAutoCheckEnabled(enabled: Boolean) {
                autoEnabled = enabled
            }
            override suspend fun checkForUpdates(isManual: Boolean): UpdateCheckResult = nextResult
            override fun launchUpdateFlow(activity: Activity, result: UpdateCheckResult) = Unit
            override fun completeFlexiblePlayUpdate() = Unit
        }

        val vm = MainShellViewModel(SavedStateHandle(), fakeUpdateRepo)
        vm.performStartupUpdateCheckIfEnabled()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showUpdateDialog)
        assertEquals(true, vm.uiState.value.updateCheckResult?.hasUpdate)

        // Manual check when up to date
        vm.setUpdateDialogVisible(false)
        nextResult = UpdateCheckResult.UpToDate("1.1.0")
        vm.checkForUpdatesManually()
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.updateCheckResult?.hasUpdate)
    }

    @Test
    fun `ArchiveViewModel prepareCompressionFromFiles prefills destination folder, archive name, and opens dialog`() = runTest {
        val tempDir = File(RuntimeEnvironment.getApplication().cacheDir, "DocumentsTest").apply { mkdirs() }
        val tempFile = File(tempDir, "QuarterlyReport.pdf").apply { writeText("sample") }
        val fileUri = Uri.fromFile(tempFile)

        val fakeArchiveRepo = object : ArchiveRepository {
            override suspend fun resolveSourceItems(uris: List<Uri>): List<SourceItem> = listOf(
                SourceItem(
                    id = fileUri.toString(),
                    uri = fileUri,
                    name = "QuarterlyReport.pdf",
                    sizeBytes = 6L,
                    isDirectory = false
                )
            )
            override suspend fun inspectArchive(archiveUri: Uri, password: String?) = emptyList<ArchiveEntryItem>()
            override fun compressItems(items: List<SourceItem>, config: CompressionConfig, destinationTreeUri: Uri?) = flow<ArchiveOperationProgress> {}
            override fun extractArchive(archiveUri: Uri, destinationTreeUri: Uri?, selectedEntryPaths: Set<String>?, password: String?) = flow<ArchiveOperationProgress> {}
        }

        val vm = ArchiveViewModel(fakeArchiveRepo, SavedStateHandle())
        vm.prepareCompressionFromFiles(listOf(fileUri))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.showCompressionDialog)
        assertEquals("QuarterlyReport", state.compressionConfig.archiveBaseName)
        assertEquals(Uri.fromFile(tempDir), state.destinationTreeUri)
        assertEquals("DocumentsTest", state.destinationLabel)
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

    @Test
    fun `MainShellViewModel dispatches global shortcut events and closes language dropdown on Escape`() =
        runTest(testDispatcher) {
            val vm = MainShellViewModel(SavedStateHandle())
            val received = mutableListOf<ShortcutAction>()
            val collectorJob = launch {
                vm.shortcutEvents.collect { received += it }
            }
            advanceUntilIdle()

            vm.setLanguageDialogVisible(true)
            assertTrue(vm.uiState.value.showLanguageDialog)

            vm.dispatchShortcut(ShortcutAction.OPEN)
            vm.dispatchShortcut(ShortcutAction.SELECT_ALL)
            vm.dispatchShortcut(ShortcutAction.DELETE_SELECTED)
            vm.dispatchShortcut(ShortcutAction.ESCAPE)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.showLanguageDialog)
            assertEquals(
                listOf(
                    ShortcutAction.OPEN,
                    ShortcutAction.SELECT_ALL,
                    ShortcutAction.DELETE_SELECTED,
                    ShortcutAction.ESCAPE
                ),
                received
            )
            collectorJob.cancel()
        }

    @Test
    fun `FileBrowserViewModel deletes selected files from disk and refreshes folder`() =
        runTest(testDispatcher) {
            val context = RuntimeEnvironment.getApplication()
            val tempDir = File(context.cacheDir, "delete_test_${System.nanoTime()}").apply { mkdirs() }
            val keepFile = File(tempDir, "keep.txt").apply { writeText("keep") }
            val removeFile = File(tempDir, "remove.txt").apply { writeText("remove") }

            try {
                val vm = FileBrowserViewModel(context, SavedStateHandle(), accessChecker = { true })
                vm.openRoot(tempDir.absolutePath)
                advanceUntilIdle()
                val loadDeadline = System.currentTimeMillis() + 3_000L
                while ((vm.uiState.value.isLoading || vm.uiState.value.entries.size != 2) &&
                    System.currentTimeMillis() < loadDeadline
                ) {
                    Thread.sleep(15)
                    advanceUntilIdle()
                }

                assertEquals(2, vm.uiState.value.entries.size)
                vm.toggleSelection(removeFile.absolutePath)
                assertEquals(setOf(removeFile.absolutePath), vm.uiState.value.selectedPaths)

                vm.deleteSelected()
                advanceUntilIdle()
                val deleteDeadline = System.currentTimeMillis() + 3_000L
                while ((removeFile.exists() || vm.uiState.value.isLoading || vm.uiState.value.entries.size != 1) &&
                    System.currentTimeMillis() < deleteDeadline
                ) {
                    Thread.sleep(15)
                    advanceUntilIdle()
                }

                assertFalse(removeFile.exists())
                assertTrue(keepFile.exists())
                assertTrue(vm.uiState.value.selectedPaths.isEmpty())
                assertEquals(listOf(keepFile.absolutePath), vm.uiState.value.entries.map { it.path })
            } finally {
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun `ArchiveViewModel extractSpecificEntries extracts targeted folder across password prompt without mutating selection`() =
        runTest {
            val fakeUri = Uri.parse("content://com.arcimage.test/mixed_vault.zip")
            var extractedSelection: Set<String>? = null
            var extractedPassword: String? = null

            val fakeArchiveRepo = object : ArchiveRepository {
                override suspend fun resolveSourceItems(uris: List<Uri>): List<SourceItem> = emptyList()

                override suspend fun inspectArchive(archiveUri: Uri, password: String?): List<ArchiveEntryItem> =
                    listOf(
                        ArchiveEntryItem("docs/", "docs", true, 0L, 0L, 0L, false, 0),
                        ArchiveEntryItem("docs/secret.pdf", "secret.pdf", false, 4096L, 2048L, 0L, true, 1),
                        ArchiveEntryItem("public/readme.txt", "readme.txt", false, 512L, 256L, 0L, false, 1)
                    )

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
                ): Flow<ArchiveOperationProgress> = flow {
                    extractedSelection = selectedEntryPaths
                    extractedPassword = password
                    emit(
                        ArchiveOperationProgress(
                            operationType = com.arcimage.app.model.ArchiveOperationType.EXTRACTING,
                            currentItemName = "docs/secret.pdf",
                            processedBytes = 4096L,
                            totalBytes = 4096L,
                            completedItems = 1,
                            totalItems = 1
                        )
                    )
                }
            }

            val vm = ArchiveViewModel(fakeArchiveRepo, SavedStateHandle())
            vm.openArchiveForInspection(fakeUri, password = null)
            advanceUntilIdle()

            // User has checked "public/readme.txt" in the multi-select checkboxes
            vm.toggleEntrySelection("public/readme.txt")
            assertEquals(setOf("public/readme.txt"), vm.uiState.value.selectedEntryPaths)

            // User clicks "Extract" on focused/context-menu folder "docs/" (which has an encrypted child)
            vm.extractSpecificEntries(setOf("docs/"))
            advanceUntilIdle()

            // Multi-select checkboxes remain untouched while password prompt preserves explicit target
            assertEquals(setOf("public/readme.txt"), vm.uiState.value.selectedEntryPaths)
            assertNotNull(vm.uiState.value.passwordPrompt)
            assertEquals(setOf("docs/"), vm.uiState.value.passwordPrompt?.explicitTargetPaths)

            // Submitting password extracts ONLY "docs/" with password and preserves user selection
            vm.submitPasswordPrompt("vaultPass99")
            advanceUntilIdle()

            assertNull(vm.uiState.value.passwordPrompt)
            assertEquals(setOf("docs/"), extractedSelection)
            assertEquals("vaultPass99", extractedPassword)
            assertEquals(setOf("public/readme.txt"), vm.uiState.value.selectedEntryPaths)
        }

    @Test
    fun `ImageStudioViewModel persists destinationTreeUri and destinationLabel across SavedStateHandle recreation`() {
        val fakeImageRepo = object : ImageRepository {
            override suspend fun loadBatchItems(uris: List<Uri>): List<ImageBatchItem> = emptyList()
            override fun estimateItemOutputSize(item: ImageBatchItem, config: ImageProcessConfig): Long = 0L
            override fun estimateBatchOutputSize(items: List<ImageBatchItem>, config: ImageProcessConfig): Long = 0L
            override fun processBatch(
                items: List<ImageBatchItem>,
                config: ImageProcessConfig,
                destinationTreeUri: Uri?
            ): Flow<ImageBatchProgress> = flow {}
        }

        val handle = SavedStateHandle()
        val destUri = Uri.parse("file:///storage/emulated/0/Pictures/ConvertedBatch")
        val vm1 = ImageStudioViewModel(fakeImageRepo, handle)
        vm1.setDestinationTree(destUri, "ConvertedBatch")

        val vm2 = ImageStudioViewModel(fakeImageRepo, handle)
        assertEquals(destUri, vm2.uiState.value.destinationTreeUri)
        assertEquals("ConvertedBatch", vm2.uiState.value.destinationLabel)
    }

    @Test
    fun `DocumentUriHelper resolves accurate MIME types and safe URIs for local files`() {
        val context = RuntimeEnvironment.getApplication()
        val sample7z = File(context.cacheDir, "backup_2026.7z").apply { writeText("7z") }
        val sampleTarGz = File(context.cacheDir, "logs.tar.gz").apply { writeText("gz") }
        val sampleWebp = File(context.cacheDir, "banner.webp").apply { writeText("webp") }
        val samplePdf = File(context.cacheDir, "invoice.pdf").apply { writeText("pdf") }

        try {
            assertEquals(
                "application/x-7z-compressed",
                com.arcimage.app.data.util.DocumentUriHelper.resolveMimeType(context, Uri.fromFile(sample7z))
            )
            assertEquals(
                "application/gzip",
                com.arcimage.app.data.util.DocumentUriHelper.resolveMimeType(context, Uri.fromFile(sampleTarGz))
            )
            assertEquals(
                "image/webp",
                com.arcimage.app.data.util.DocumentUriHelper.resolveMimeType(context, Uri.fromFile(sampleWebp))
            )
            assertEquals(
                "application/pdf",
                com.arcimage.app.data.util.DocumentUriHelper.resolveMimeType(context, Uri.fromFile(samplePdf))
            )
            val safeUri = com.arcimage.app.data.util.DocumentUriHelper.safeUriForFile(context, samplePdf)
            assertNotNull(safeUri)
        } finally {
            sample7z.delete()
            sampleTarGz.delete()
            sampleWebp.delete()
            samplePdf.delete()
        }
    }
}


