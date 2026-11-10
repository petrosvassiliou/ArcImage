package com.arcimage.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcimage.app.data.files.DeviceFileEntry
import com.arcimage.app.data.files.DeviceFileRepository
import com.arcimage.app.data.files.FileSortOrder
import com.arcimage.app.data.files.QuickFolder
import com.arcimage.app.data.files.StorageRoot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileBrowserUiState(
    val hasStorageAccess: Boolean = false,
    val roots: List<StorageRoot> = emptyList(),
    val activeRootPath: String? = null,
    val currentPath: String? = null,
    val entries: List<DeviceFileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val searchQuery: String = "",
    val sortOrder: FileSortOrder = FileSortOrder.NAME,
    val gridView: Boolean = false,
    val isLoading: Boolean = false
) {
    val visibleEntries: List<DeviceFileEntry>
        get() = DeviceFileRepository.filter(entries, searchQuery)

    val canNavigateUp: Boolean
        get() = currentPath != null && activeRootPath != null && currentPath != activeRootPath

    val selectedEntries: List<DeviceFileEntry>
        get() = entries.filter { it.path in selectedPaths }
}

/**
 * Drives the default "Files" home screen: a real browser over the device storage.
 * Current folder, sort, view mode and search survive window resizing / process death
 * through [SavedStateHandle] for Googlebook OS continuity.
 */
class FileBrowserViewModel(
    private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val accessChecker: (Context) -> Boolean = DeviceFileRepository::hasAllFilesAccess
) : ViewModel() {

    companion object {
        private const val KEY_PATH = "files_current_path"
        private const val KEY_ROOT = "files_active_root"
        private const val KEY_SORT = "files_sort_order"
        private const val KEY_GRID = "files_grid_view"
        private const val KEY_SEARCH = "files_search_query"
    }

    private val _uiState = MutableStateFlow(
        FileBrowserUiState(
            currentPath = savedStateHandle.get<String>(KEY_PATH),
            activeRootPath = savedStateHandle.get<String>(KEY_ROOT),
            sortOrder = savedStateHandle.get<String>(KEY_SORT)
                ?.let { runCatching { FileSortOrder.valueOf(it) }.getOrNull() }
                ?: FileSortOrder.NAME,
            gridView = savedStateHandle.get<Boolean>(KEY_GRID) ?: false,
            searchQuery = savedStateHandle.get<String>(KEY_SEARCH).orEmpty()
        )
    )
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refreshAccess()
    }

    /** Re-checks the storage permission (e.g. after returning from system settings). */
    fun refreshAccess() {
        val granted = runCatching { accessChecker(appContext) }.getOrDefault(false)
        val roots = if (granted) {
            runCatching { DeviceFileRepository.storageRoots(appContext) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        _uiState.update { it.copy(hasStorageAccess = granted, roots = roots) }
        if (!granted) return

        val state = _uiState.value
        val root = state.activeRootPath?.takeIf { path -> roots.any { it.path == path } }
            ?: roots.firstOrNull()?.path
            ?: DeviceFileRepository.primaryStorageRoot().absolutePath
        val target = state.currentPath?.takeIf { File(it).isDirectory } ?: root
        setRoot(root, persist = true)
        loadDirectory(target, keepSelection = target == state.currentPath)
    }

    fun openRoot(rootPath: String) {
        setRoot(rootPath, persist = true)
        loadDirectory(rootPath)
    }

    fun openQuickFolder(folder: QuickFolder) {
        val dir = DeviceFileRepository.quickFolder(folder)
        if (!dir.isDirectory) return
        val primary = DeviceFileRepository.primaryStorageRoot().absolutePath
        setRoot(primary, persist = true)
        loadDirectory(dir.absolutePath)
    }

    fun openDirectory(path: String) = loadDirectory(path)

    /** @return true if navigation happened, false if already at the storage root. */
    fun navigateUp(): Boolean {
        val state = _uiState.value
        if (!state.canNavigateUp) return false
        val parent = File(state.currentPath!!).parentFile ?: return false
        loadDirectory(parent.absolutePath)
        return true
    }

    fun refresh() {
        _uiState.value.currentPath?.let { loadDirectory(it, keepSelection = true) }
    }

    fun setSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH] = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortOrder(order: FileSortOrder) {
        savedStateHandle[KEY_SORT] = order.name
        _uiState.update { it.copy(sortOrder = order) }
        refresh()
    }

    fun setGridView(grid: Boolean) {
        savedStateHandle[KEY_GRID] = grid
        _uiState.update { it.copy(gridView = grid) }
    }

    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val updated = if (path in state.selectedPaths) {
                state.selectedPaths - path
            } else {
                state.selectedPaths + path
            }
            state.copy(selectedPaths = updated)
        }
    }

    fun selectAll() {
        _uiState.update { state -> state.copy(selectedPaths = state.visibleEntries.map { it.path }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    private fun setRoot(rootPath: String, persist: Boolean) {
        if (persist) savedStateHandle[KEY_ROOT] = rootPath
        _uiState.update { it.copy(activeRootPath = rootPath) }
    }

    private fun loadDirectory(path: String, keepSelection: Boolean = false) {
        loadJob?.cancel()
        savedStateHandle[KEY_PATH] = path
        _uiState.update {
            it.copy(
                currentPath = path,
                isLoading = true,
                selectedPaths = if (keepSelection) it.selectedPaths else emptySet(),
                searchQuery = if (keepSelection) it.searchQuery else ""
            )
        }
        if (!keepSelection) savedStateHandle[KEY_SEARCH] = ""
        loadJob = viewModelScope.launch {
            val sort = _uiState.value.sortOrder
            val listed = withContext(Dispatchers.IO) {
                runCatching { DeviceFileRepository.listDirectory(File(path), sort) }.getOrDefault(emptyList())
            }
            _uiState.update { state ->
                state.copy(
                    entries = listed,
                    isLoading = false,
                    selectedPaths = state.selectedPaths.filter { sel -> listed.any { it.path == sel } }.toSet()
                )
            }
        }
    }
}
