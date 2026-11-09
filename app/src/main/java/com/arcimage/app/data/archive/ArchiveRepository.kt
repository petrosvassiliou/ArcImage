package com.arcimage.app.data.archive

import android.net.Uri
import com.arcimage.app.model.ArchiveEntryItem
import com.arcimage.app.model.ArchiveOperationProgress
import com.arcimage.app.model.CompressionConfig
import com.arcimage.app.model.SourceItem
import kotlinx.coroutines.flow.Flow

interface ArchiveRepository {
    suspend fun resolveSourceItems(uris: List<Uri>): List<SourceItem>

    suspend fun inspectArchive(
        archiveUri: Uri,
        password: String? = null
    ): List<ArchiveEntryItem>

    fun compressItems(
        items: List<SourceItem>,
        config: CompressionConfig,
        destinationTreeUri: Uri? = null
    ): Flow<ArchiveOperationProgress>

    fun extractArchive(
        archiveUri: Uri,
        destinationTreeUri: Uri? = null,
        selectedEntryPaths: Set<String>? = null,
        password: String? = null
    ): Flow<ArchiveOperationProgress>
}
