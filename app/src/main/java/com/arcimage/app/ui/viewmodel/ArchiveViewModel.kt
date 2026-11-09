package com.arcimage.app.ui.viewmodel

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcimage.app.R
import com.arcimage.app.data.archive.ArchiveRepository
import com.arcimage.app.data.archive.SingleFileCodecRequiredException
import com.arcimage.app.model.ArchiveEntryItem
import com.arcimage.app.model.ArchiveFormat
import com.arcimage.app.model.ArchiveInvalidPasswordException
import com.arcimage.app.model.ArchiveOperationProgress
import com.arcimage.app.model.ArchiveOperationType
import com.arcimage.app.model.ArchivePasswordRequiredException
import com.arcimage.app.model.ArchiveWorkspaceMode
import com.arcimage.app.model.CompressionConfig
import com.arcimage.app.model.SourceItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PasswordPurpose {
    INSPECT_ARCHIVE,
    EXTRACT_ARCHIVE
}

data class PasswordPromptState(
    val archiveUri: Uri,
    val archiveName: String,
    val purpose: PasswordPurpose,
    val hasInvalidPasswordError: Boolean = false
)

sealed interface UiBannerMessage {
    data class ResourceMessage(
        @StringRes val resId: Int,
        val formatArgs: List<Any> = emptyList(),
        val shareableUri: Uri? = null,
        val isError: Boolean = false
    ) : UiBannerMessage
}

data class ArchiveUiState(
    val mode: ArchiveWorkspaceMode = ArchiveWorkspaceMode.COMPRESS,
    val sourceItems: List<SourceItem> = emptyList(),
    val selectedSourceIds: Set<String> = emptySet(),
    val focusedSourceId: String? = null,
    val currentArchiveUri: Uri? = null,
    val currentArchiveName: String = "",
    val unlockedPassword: String? = null,
    val archiveEntries: List<ArchiveEntryItem> = emptyList(),
    val selectedEntryPaths: Set<String> = emptySet(),
    val focusedEntryPath: String? = null,
    val searchQuery: String = "",
    val destinationTreeUri: Uri? = null,
    val destinationLabel: String? = null,
    val compressionConfig: CompressionConfig = CompressionConfig(),
    val showCompressionDialog: Boolean = false,
    val passwordPrompt: PasswordPromptState? = null,
    val activeProgress: ArchiveOperationProgress? = null,
    val bannerMessage: UiBannerMessage.ResourceMessage? = null
) {
    val filteredArchiveEntries: List<ArchiveEntryItem>
        get() = if (searchQuery.isBlank()) {
            archiveEntries
        } else {
            val q = searchQuery.trim().lowercase()
            archiveEntries.filter {
                it.name.lowercase().contains(q) || it.path.lowercase().contains(q)
            }
        }

    val focusedSourceItem: SourceItem?
        get() = sourceItems.firstOrNull { it.id == focusedSourceId } ?: sourceItems.firstOrNull()

    val focusedArchiveEntry: ArchiveEntryItem?
        get() = archiveEntries.firstOrNull { it.path == focusedEntryPath } ?: filteredArchiveEntries.firstOrNull()
}

class ArchiveViewModel(
    private val archiveRepository: ArchiveRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_MODE = "archive_workspace_mode"
        private const val KEY_SOURCES = "archive_source_items"
        private const val KEY_ARCHIVE_URI = "archive_current_uri"
        private const val KEY_ARCHIVE_NAME = "archive_current_name"
        private const val KEY_ENTRIES = "archive_entries_list"
        private const val KEY_CONFIG = "archive_compression_config"
        private const val KEY_SEARCH = "archive_search_query"
        private const val KEY_DEST_URI = "archive_dest_tree_uri"
    }

    private var activeJob: Job? = null

    private val _uiState = MutableStateFlow(restoreStateFromHandle())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private fun restoreStateFromHandle(): ArchiveUiState {
        val mode = savedStateHandle.get<String>(KEY_MODE)
            ?.let { runCatching { ArchiveWorkspaceMode.valueOf(it) }.getOrNull() }
            ?: ArchiveWorkspaceMode.COMPRESS
        val sources = savedStateHandle.get<ArrayList<SourceItem>>(KEY_SOURCES) ?: emptyList()
        val archiveUri = savedStateHandle.get<Uri>(KEY_ARCHIVE_URI)
        val archiveName = savedStateHandle.get<String>(KEY_ARCHIVE_NAME) ?: ""
        val entries = savedStateHandle.get<ArrayList<ArchiveEntryItem>>(KEY_ENTRIES) ?: emptyList()
        val config = savedStateHandle.get<CompressionConfig>(KEY_CONFIG) ?: CompressionConfig()
        val search = savedStateHandle.get<String>(KEY_SEARCH) ?: ""
        val destUri = savedStateHandle.get<Uri>(KEY_DEST_URI)

        return ArchiveUiState(
            mode = mode,
            sourceItems = sources,
            focusedSourceId = sources.firstOrNull()?.id,
            currentArchiveUri = archiveUri,
            currentArchiveName = archiveName,
            archiveEntries = entries,
            focusedEntryPath = entries.firstOrNull()?.path,
            compressionConfig = config,
            searchQuery = search,
            destinationTreeUri = destUri
        )
    }

    private fun persistSnapshot(state: ArchiveUiState) {
        savedStateHandle[KEY_MODE] = state.mode.name
        savedStateHandle[KEY_SOURCES] = ArrayList(state.sourceItems)
        savedStateHandle[KEY_ARCHIVE_URI] = state.currentArchiveUri
        savedStateHandle[KEY_ARCHIVE_NAME] = state.currentArchiveName
        savedStateHandle[KEY_ENTRIES] = ArrayList(state.archiveEntries)
        savedStateHandle[KEY_CONFIG] = state.compressionConfig
        savedStateHandle[KEY_SEARCH] = state.searchQuery
        savedStateHandle[KEY_DEST_URI] = state.destinationTreeUri
    }

    fun setMode(mode: ArchiveWorkspaceMode) {
        _uiState.update { state ->
            state.copy(mode = mode, bannerMessage = null).also { persistSnapshot(it) }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(searchQuery = query).also { persistSnapshot(it) }
        }
    }

    fun setDestinationTree(uri: Uri?, label: String?) {
        _uiState.update { state ->
            state.copy(destinationTreeUri = uri, destinationLabel = label).also { persistSnapshot(it) }
        }
    }

    fun addSourceUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = archiveRepository.resolveSourceItems(uris)
            _uiState.update { state ->
                val merged = (state.sourceItems + resolved).distinctBy { it.id }
                val updated = state.copy(
                    mode = ArchiveWorkspaceMode.COMPRESS,
                    sourceItems = merged,
                    focusedSourceId = state.focusedSourceId ?: merged.firstOrNull()?.id,
                    bannerMessage = null
                )
                persistSnapshot(updated)
                updated
            }
        }
    }

    fun handleDroppedOrSharedArchiveUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = archiveRepository.resolveSourceItems(uris)
            val singleItem = resolved.singleOrNull()
            if (singleItem != null &&
                !singleItem.isDirectory &&
                ArchiveFormat.detectFromFileName(singleItem.name) != null
            ) {
                openArchiveForInspection(singleItem.uri, password = null)
            } else {
                _uiState.update { state ->
                    val merged = (state.sourceItems + resolved).distinctBy { it.id }
                    val updated = state.copy(
                        mode = ArchiveWorkspaceMode.COMPRESS,
                        sourceItems = merged,
                        focusedSourceId = merged.firstOrNull()?.id
                    )
                    persistSnapshot(updated)
                    updated
                }
            }
        }
    }

    fun openArchiveForInspection(uri: Uri, password: String? = null) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val resolvedItem = archiveRepository.resolveSourceItems(listOf(uri)).firstOrNull()
            val displayName = resolvedItem?.name ?: "Archive"

            _uiState.update {
                it.copy(
                    mode = ArchiveWorkspaceMode.EXPLORE,
                    activeProgress = ArchiveOperationProgress(
                        operationType = ArchiveOperationType.INSPECTING,
                        currentItemName = displayName
                    ),
                    bannerMessage = null
                )
            }

            try {
                val entries = archiveRepository.inspectArchive(uri, password)
                _uiState.update { state ->
                    val updated = state.copy(
                        mode = ArchiveWorkspaceMode.EXPLORE,
                        currentArchiveUri = uri,
                        currentArchiveName = displayName,
                        unlockedPassword = password,
                        archiveEntries = entries,
                        selectedEntryPaths = emptySet(),
                        focusedEntryPath = entries.firstOrNull()?.path,
                        passwordPrompt = null,
                        activeProgress = null
                    )
                    persistSnapshot(updated)
                    updated
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArchivePasswordRequiredException) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        passwordPrompt = PasswordPromptState(
                            archiveUri = uri,
                            archiveName = displayName,
                            purpose = PasswordPurpose.INSPECT_ARCHIVE,
                            hasInvalidPasswordError = false
                        )
                    )
                }
            } catch (e: ArchiveInvalidPasswordException) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        passwordPrompt = PasswordPromptState(
                            archiveUri = uri,
                            archiveName = displayName,
                            purpose = PasswordPurpose.INSPECT_ARCHIVE,
                            hasInvalidPasswordError = true
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.error_archive_corrupted,
                            formatArgs = listOf(e.localizedMessage ?: displayName),
                            isError = true
                        )
                    )
                }
            }
        }
    }

    fun submitPasswordPrompt(password: String) {
        val prompt = _uiState.value.passwordPrompt ?: return
        when (prompt.purpose) {
            PasswordPurpose.INSPECT_ARCHIVE -> {
                openArchiveForInspection(prompt.archiveUri, password)
            }
            PasswordPurpose.EXTRACT_ARCHIVE -> {
                _uiState.update { it.copy(unlockedPassword = password, passwordPrompt = null) }
                startExtraction(extractOnlySelected = _uiState.value.selectedEntryPaths.isNotEmpty(), explicitPassword = password)
            }
        }
    }

    fun dismissPasswordPrompt() {
        _uiState.update { it.copy(passwordPrompt = null) }
    }

    fun setCompressionDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showCompressionDialog = visible) }
    }

    fun updateCompressionConfig(config: CompressionConfig) {
        _uiState.update { state ->
            state.copy(compressionConfig = config).also { persistSnapshot(it) }
        }
    }

    fun startCompression(config: CompressionConfig) {
        val stateSnapshot = _uiState.value
        val itemsToCompress = if (stateSnapshot.selectedSourceIds.isNotEmpty()) {
            stateSnapshot.sourceItems.filter { it.id in stateSnapshot.selectedSourceIds }
        } else {
            stateSnapshot.sourceItems
        }
        if (itemsToCompress.isEmpty()) return

        updateCompressionConfig(config)
        _uiState.update { it.copy(showCompressionDialog = false, bannerMessage = null) }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                var finalProgress: ArchiveOperationProgress? = null
                archiveRepository.compressItems(
                    items = itemsToCompress,
                    config = config,
                    destinationTreeUri = _uiState.value.destinationTreeUri
                ).collect { progress ->
                    finalProgress = progress
                    _uiState.update { it.copy(activeProgress = progress) }
                }
                val outName = finalProgress?.outputDisplayName
                    ?: "${config.archiveBaseName}.${config.format.extension}"
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.archive_status_compress_success,
                            formatArgs = listOf(outName),
                            shareableUri = finalProgress?.outputUri,
                            isError = false
                        )
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.error_operation_cancelled,
                            isError = false
                        )
                    )
                }
                throw e
            } catch (_: SingleFileCodecRequiredException) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.error_single_file_codec,
                            isError = true
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.error_generic,
                            formatArgs = listOf(e.localizedMessage ?: "Compression failed"),
                            isError = true
                        )
                    )
                }
            }
        }
    }

    fun startExtraction(extractOnlySelected: Boolean, explicitPassword: String? = null) {
        val state = _uiState.value
        val archiveUri = state.currentArchiveUri ?: return
        val selectedPaths = if (extractOnlySelected && state.selectedEntryPaths.isNotEmpty()) {
            state.selectedEntryPaths
        } else {
            null
        }
        val passwordToUse = explicitPassword ?: state.unlockedPassword

        // If any targeted entry is marked encrypted and we don't have a password yet, prompt immediately
        val targetEntries = if (selectedPaths != null) {
            state.archiveEntries.filter { it.path in selectedPaths }
        } else {
            state.archiveEntries
        }
        if (targetEntries.any { it.isEncrypted } && passwordToUse.isNullOrEmpty()) {
            _uiState.update {
                it.copy(
                    passwordPrompt = PasswordPromptState(
                        archiveUri = archiveUri,
                        archiveName = state.currentArchiveName,
                        purpose = PasswordPurpose.EXTRACT_ARCHIVE,
                        hasInvalidPasswordError = false
                    )
                )
            }
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                var finalProgress: ArchiveOperationProgress? = null
                archiveRepository.extractArchive(
                    archiveUri = archiveUri,
                    destinationTreeUri = state.destinationTreeUri,
                    selectedEntryPaths = selectedPaths,
                    password = passwordToUse
                ).collect { progress ->
                    finalProgress = progress
                    _uiState.update { it.copy(activeProgress = progress) }
                }
                val count = finalProgress?.completedItems ?: targetEntries.size
                val destName = finalProgress?.outputDisplayName ?: state.currentArchiveName
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.archive_status_extract_success,
                            formatArgs = listOf(count, destName),
                            isError = false
                        )
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.error_operation_cancelled,
                            isError = false
                        )
                    )
                }
                throw e
            } catch (_: ArchivePasswordRequiredException) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        passwordPrompt = PasswordPromptState(
                            archiveUri = archiveUri,
                            archiveName = state.currentArchiveName,
                            purpose = PasswordPurpose.EXTRACT_ARCHIVE,
                            hasInvalidPasswordError = false
                        )
                    )
                }
            } catch (_: ArchiveInvalidPasswordException) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        unlockedPassword = null,
                        passwordPrompt = PasswordPromptState(
                            archiveUri = archiveUri,
                            archiveName = state.currentArchiveName,
                            purpose = PasswordPurpose.EXTRACT_ARCHIVE,
                            hasInvalidPasswordError = true
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.error_generic,
                            formatArgs = listOf(e.localizedMessage ?: "Extraction failed"),
                            isError = true
                        )
                    )
                }
            }
        }
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        _uiState.update { it.copy(activeProgress = null) }
    }

    // Selection, Focus & Keyboard Shortcut Actions (Googlebook OS)
    fun toggleSourceSelection(id: String) {
        _uiState.update { state ->
            val next = if (id in state.selectedSourceIds) {
                state.selectedSourceIds - id
            } else {
                state.selectedSourceIds + id
            }
            state.copy(selectedSourceIds = next, focusedSourceId = id)
        }
    }

    fun focusSourceItem(id: String) {
        _uiState.update { it.copy(focusedSourceId = id) }
    }

    fun removeSourceItem(id: String) {
        _uiState.update { state ->
            val remaining = state.sourceItems.filterNot { it.id == id }
            val updated = state.copy(
                sourceItems = remaining,
                selectedSourceIds = state.selectedSourceIds - id,
                focusedSourceId = if (state.focusedSourceId == id) remaining.firstOrNull()?.id else state.focusedSourceId
            )
            persistSnapshot(updated)
            updated
        }
    }

    fun toggleEntrySelection(path: String) {
        _uiState.update { state ->
            val next = if (path in state.selectedEntryPaths) {
                state.selectedEntryPaths - path
            } else {
                state.selectedEntryPaths + path
            }
            state.copy(selectedEntryPaths = next, focusedEntryPath = path)
        }
    }

    fun focusArchiveEntry(path: String) {
        _uiState.update { it.copy(focusedEntryPath = path) }
    }

    fun selectAllInCurrentMode() {
        _uiState.update { state ->
            when (state.mode) {
                ArchiveWorkspaceMode.COMPRESS -> state.copy(
                    selectedSourceIds = state.sourceItems.map { it.id }.toSet()
                )
                ArchiveWorkspaceMode.EXPLORE -> state.copy(
                    selectedEntryPaths = state.filteredArchiveEntries.map { it.path }.toSet()
                )
            }
        }
    }

    fun clearSelectionAndModals() {
        _uiState.update { state ->
            state.copy(
                selectedSourceIds = emptySet(),
                selectedEntryPaths = emptySet(),
                showCompressionDialog = false,
                passwordPrompt = null
            )
        }
    }

    fun deleteSelectedItems() {
        _uiState.update { state ->
            if (state.mode == ArchiveWorkspaceMode.COMPRESS && state.selectedSourceIds.isNotEmpty()) {
                val remaining = state.sourceItems.filterNot { it.id in state.selectedSourceIds }
                val updated = state.copy(
                    sourceItems = remaining,
                    selectedSourceIds = emptySet(),
                    focusedSourceId = remaining.firstOrNull()?.id
                )
                persistSnapshot(updated)
                updated
            } else {
                state
            }
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }
}
