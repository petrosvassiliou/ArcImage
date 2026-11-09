package com.arcimage.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcimage.app.R
import com.arcimage.app.data.image.ImageRepository
import com.arcimage.app.model.FileSizeFormatter
import com.arcimage.app.model.ImageBatchItem
import com.arcimage.app.model.ImageBatchProgress
import com.arcimage.app.model.ImageFormat
import com.arcimage.app.model.ImageProcessConfig
import com.arcimage.app.model.ResizeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ImageStudioUiState(
    val batchItems: List<ImageBatchItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val focusedItemId: String? = null,
    val config: ImageProcessConfig = ImageProcessConfig(),
    val estimatedTotalOutputBytes: Long = 0L,
    val destinationTreeUri: Uri? = null,
    val destinationLabel: String? = null,
    val activeProgress: ImageBatchProgress? = null,
    val bannerMessage: UiBannerMessage.ResourceMessage? = null
) {
    val totalOriginalBytes: Long
        get() = batchItems.sumOf { it.originalSizeBytes }

    val focusedItem: ImageBatchItem?
        get() = batchItems.firstOrNull { it.id == focusedItemId } ?: batchItems.firstOrNull()
}

class ImageStudioViewModel(
    private val imageRepository: ImageRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_BATCH_ITEMS = "image_studio_batch_items"
        private const val KEY_CONFIG = "image_studio_config"
        private const val KEY_DEST_URI = "image_studio_dest_uri"
    }

    private var batchJob: Job? = null

    private val _uiState = MutableStateFlow(restoreFromSavedState())
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    private fun restoreFromSavedState(): ImageStudioUiState {
        val items = savedStateHandle.get<ArrayList<ImageBatchItem>>(KEY_BATCH_ITEMS) ?: emptyList()
        val config = savedStateHandle.get<ImageProcessConfig>(KEY_CONFIG) ?: ImageProcessConfig()
        val destUri = savedStateHandle.get<Uri>(KEY_DEST_URI)
        return ImageStudioUiState(
            batchItems = items,
            focusedItemId = items.firstOrNull()?.id,
            config = config,
            estimatedTotalOutputBytes = imageRepository.estimateBatchOutputSize(items, config),
            destinationTreeUri = destUri
        )
    }

    private fun persistSnapshot(state: ImageStudioUiState) {
        savedStateHandle[KEY_BATCH_ITEMS] = ArrayList(state.batchItems)
        savedStateHandle[KEY_CONFIG] = state.config
        savedStateHandle[KEY_DEST_URI] = state.destinationTreeUri
    }

    fun estimateItemBytes(item: ImageBatchItem): Long {
        return imageRepository.estimateItemOutputSize(item, _uiState.value.config)
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val loaded = imageRepository.loadBatchItems(uris)
            if (loaded.isEmpty()) return@launch
            _uiState.update { state ->
                val merged = (state.batchItems + loaded).distinctBy { it.id }
                val firstImg = merged.first()
                // Initialize exact pixel defaults to first image's dimensions if still default
                val adjustedConfig = if (state.batchItems.isEmpty()) {
                    state.config.copy(
                        exactWidth = firstImg.originalWidth,
                        exactHeight = firstImg.originalHeight
                    )
                } else {
                    state.config
                }
                val updated = state.copy(
                    batchItems = merged,
                    focusedItemId = state.focusedItemId ?: firstImg.id,
                    config = adjustedConfig,
                    estimatedTotalOutputBytes = imageRepository.estimateBatchOutputSize(merged, adjustedConfig),
                    bannerMessage = null
                )
                persistSnapshot(updated)
                updated
            }
        }
    }

    fun setResizeMode(mode: ResizeMode) {
        updateConfig { it.copy(resizeMode = mode) }
    }

    fun setCustomPercentage(percent: Int) {
        updateConfig { it.copy(customPercentage = percent.coerceIn(1, 400)) }
    }

    fun setExactWidth(newWidth: Int) {
        val safeW = newWidth.coerceIn(1, 16384)
        updateConfig { cfg ->
            if (cfg.maintainAspectRatio) {
                val ref = _uiState.value.focusedItem
                val aspect = if (ref != null && ref.originalHeight > 0) {
                    ref.originalWidth.toDouble() / ref.originalHeight.toDouble()
                } else {
                    16.0 / 9.0
                }
                val proportionalH = (safeW / aspect).roundToInt().coerceAtLeast(1)
                cfg.copy(exactWidth = safeW, exactHeight = proportionalH)
            } else {
                cfg.copy(exactWidth = safeW)
            }
        }
    }

    fun setExactHeight(newHeight: Int) {
        val safeH = newHeight.coerceIn(1, 16384)
        updateConfig { cfg ->
            if (cfg.maintainAspectRatio) {
                val ref = _uiState.value.focusedItem
                val aspect = if (ref != null && ref.originalHeight > 0) {
                    ref.originalWidth.toDouble() / ref.originalHeight.toDouble()
                } else {
                    16.0 / 9.0
                }
                val proportionalW = (safeH * aspect).roundToInt().coerceAtLeast(1)
                cfg.copy(exactWidth = proportionalW, exactHeight = safeH)
            } else {
                cfg.copy(exactHeight = safeH)
            }
        }
    }

    fun setMaintainAspectRatio(maintain: Boolean) {
        updateConfig { it.copy(maintainAspectRatio = maintain) }
    }

    fun setQuality(quality: Int) {
        updateConfig { it.copy(quality = quality.coerceIn(1, 100)) }
    }

    fun setOutputFormat(format: ImageFormat) {
        updateConfig { it.copy(outputFormat = format) }
    }

    fun setPreserveExif(preserve: Boolean) {
        updateConfig { it.copy(preserveExif = preserve) }
    }

    fun setDestinationTree(uri: Uri?, label: String?) {
        _uiState.update { state ->
            state.copy(destinationTreeUri = uri, destinationLabel = label).also { persistSnapshot(it) }
        }
    }

    private fun updateConfig(transform: (ImageProcessConfig) -> ImageProcessConfig) {
        _uiState.update { state ->
            val newConfig = transform(state.config)
            val newEstimate = imageRepository.estimateBatchOutputSize(state.batchItems, newConfig)
            state.copy(
                config = newConfig,
                estimatedTotalOutputBytes = newEstimate
            ).also { persistSnapshot(it) }
        }
    }

    fun processBatch() {
        val stateSnapshot = _uiState.value
        val targets = if (stateSnapshot.selectedIds.isNotEmpty()) {
            stateSnapshot.batchItems.filter { it.id in stateSnapshot.selectedIds }
        } else {
            stateSnapshot.batchItems
        }
        if (targets.isEmpty()) return

        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            try {
                val updatedMap = mutableMapOf<String, ImageBatchItem>()
                imageRepository.processBatch(
                    items = targets,
                    config = stateSnapshot.config,
                    destinationTreeUri = stateSnapshot.destinationTreeUri
                ).collect { progress ->
                    progress.updatedItem?.let { updated ->
                        updatedMap[updated.id] = updated
                    }
                    _uiState.update { cur ->
                        val mergedItems = cur.batchItems.map { updatedMap[it.id] ?: it }
                        cur.copy(
                            batchItems = mergedItems,
                            activeProgress = progress
                        )
                    }
                }

                val totalActualBytes = updatedMap.values.sumOf { it.actualOutputSizeBytes ?: 0L }
                val firstShareUri = updatedMap.values.firstOrNull()?.outputUri

                _uiState.update { cur ->
                    val updated = cur.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.image_status_batch_complete,
                            formatArgs = listOf(
                                updatedMap.size,
                                FileSizeFormatter.formatBytes(totalActualBytes)
                            ),
                            shareableUri = firstShareUri,
                            isError = false
                        )
                    )
                    persistSnapshot(updated)
                    updated
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        activeProgress = null,
                        bannerMessage = UiBannerMessage.ResourceMessage(
                            resId = R.string.error_image_decode_failed,
                            formatArgs = listOf(e.localizedMessage ?: "Batch conversion error"),
                            isError = true
                        )
                    )
                }
            }
        }
    }

    fun cancelBatchProcessing() {
        batchJob?.cancel()
        batchJob = null
        _uiState.update { it.copy(activeProgress = null) }
    }

    // Selection, Focus & Googlebook OS Keyboard Shortcuts
    fun focusItem(id: String) {
        _uiState.update { it.copy(focusedItemId = id) }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val next = if (id in state.selectedIds) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(selectedIds = next, focusedItemId = id)
        }
    }

    fun removeItem(id: String) {
        _uiState.update { state ->
            val remaining = state.batchItems.filterNot { it.id == id }
            val updated = state.copy(
                batchItems = remaining,
                selectedIds = state.selectedIds - id,
                focusedItemId = if (state.focusedItemId == id) remaining.firstOrNull()?.id else state.focusedItemId,
                estimatedTotalOutputBytes = imageRepository.estimateBatchOutputSize(remaining, state.config)
            )
            persistSnapshot(updated)
            updated
        }
    }

    fun clearBatch() {
        _uiState.update { state ->
            val updated = state.copy(
                batchItems = emptyList(),
                selectedIds = emptySet(),
                focusedItemId = null,
                estimatedTotalOutputBytes = 0L
            )
            persistSnapshot(updated)
            updated
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.batchItems.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun removeSelected() {
        _uiState.update { state ->
            if (state.selectedIds.isEmpty()) return@update state
            val remaining = state.batchItems.filterNot { it.id in state.selectedIds }
            val updated = state.copy(
                batchItems = remaining,
                selectedIds = emptySet(),
                focusedItemId = remaining.firstOrNull()?.id,
                estimatedTotalOutputBytes = imageRepository.estimateBatchOutputSize(remaining, state.config)
            )
            persistSnapshot(updated)
            updated
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }
}
