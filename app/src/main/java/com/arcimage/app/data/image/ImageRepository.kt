package com.arcimage.app.data.image

import android.net.Uri
import com.arcimage.app.model.ImageBatchItem
import com.arcimage.app.model.ImageBatchProgress
import com.arcimage.app.model.ImageProcessConfig
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    suspend fun loadBatchItems(uris: List<Uri>): List<ImageBatchItem>

    fun estimateItemOutputSize(item: ImageBatchItem, config: ImageProcessConfig): Long

    fun estimateBatchOutputSize(items: List<ImageBatchItem>, config: ImageProcessConfig): Long

    fun processBatch(
        items: List<ImageBatchItem>,
        config: ImageProcessConfig,
        destinationTreeUri: Uri? = null
    ): Flow<ImageBatchProgress>
}
