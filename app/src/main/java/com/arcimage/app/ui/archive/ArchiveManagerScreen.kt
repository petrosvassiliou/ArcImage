package com.arcimage.app.ui.archive

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.arcimage.app.R
import com.arcimage.app.data.files.DeviceFileRepository
import com.arcimage.app.data.util.DocumentUriHelper
import com.arcimage.app.model.ArchiveEntryItem
import com.arcimage.app.model.ArchiveFormat
import com.arcimage.app.model.ArchiveOperationType
import com.arcimage.app.model.ArchiveWorkspaceMode
import com.arcimage.app.model.FileSizeFormatter
import com.arcimage.app.model.SourceItem
import com.arcimage.app.ui.components.DeterminateProgressDialog
import com.arcimage.app.ui.components.DropZoneOverlay
import com.arcimage.app.ui.components.FileKindTile
import com.arcimage.app.ui.components.FloatingActionPill
import com.arcimage.app.ui.components.ModernEmptyState
import com.arcimage.app.ui.components.PillSearchField
import com.arcimage.app.ui.components.PillTabs
import com.arcimage.app.ui.components.RoundedListContainer
import com.arcimage.app.ui.components.StatusBannerCard
import com.arcimage.app.ui.theme.ArcBrandGradient
import com.arcimage.app.ui.util.arcFileDropTarget
import com.arcimage.app.ui.util.arcHoverable
import com.arcimage.app.ui.util.arcPointerContextActions
import com.arcimage.app.ui.util.rememberArcHoverState
import com.arcimage.app.ui.viewmodel.ArchiveUiState
import com.arcimage.app.ui.viewmodel.ArchiveViewModel
import com.arcimage.app.ui.viewmodel.ShortcutAction
import kotlinx.coroutines.flow.Flow

@Composable
fun ArchiveManagerScreen(
    viewModel: ArchiveViewModel,
    triggerOpenArchivePicker: Boolean,
    onConsumedQuickLaunch: () -> Unit,
    shortcutEvents: Flow<ShortcutAction>? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isCompactWidth =
        adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
    var isDragHovering by rememberSaveable { mutableStateOf(false) }

    // File & Folder Pickers
    val addFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        viewModel.addSourceUris(uris)
    }

    val addFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addSourceUris(listOf(treeUri))
        }
    }

    val openArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.openArchiveForInspection(uri)
        }
    }

    val destinationTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val label = DocumentUriHelper.resolveDisplayName(context, treeUri)
            viewModel.setDestinationTree(treeUri, label)
        }
    }

    // Handle Glance Desktop Widget quick-launch trigger
    LaunchedEffect(triggerOpenArchivePicker) {
        if (triggerOpenArchivePicker) {
            onConsumedQuickLaunch()
            viewModel.setMode(ArchiveWorkspaceMode.EXPLORE)
            openArchiveLauncher.launch(arrayOf("*/*"))
        }
    }

    // Handle global hardware keyboard shortcuts & interactive header shortcut bar
    LaunchedEffect(shortcutEvents) {
        shortcutEvents?.collect { action ->
            val current = viewModel.uiState.value
            when (action) {
                ShortcutAction.OPEN -> {
                    if (current.mode == ArchiveWorkspaceMode.COMPRESS) {
                        addFilesLauncher.launch(arrayOf("*/*"))
                    } else {
                        openArchiveLauncher.launch(arrayOf("*/*"))
                    }
                }
                ShortcutAction.SELECT_ALL -> viewModel.selectAllInCurrentMode()
                ShortcutAction.DELETE_SELECTED -> viewModel.deleteSelectedItems()
                ShortcutAction.ESCAPE -> {
                    if (current.selectedSourceIds.isEmpty() &&
                        current.selectedEntryPaths.isEmpty() &&
                        !current.showCompressionDialog &&
                        current.passwordPrompt == null &&
                        current.searchQuery.isNotEmpty()
                    ) {
                        viewModel.setSearchQuery("")
                    } else {
                        viewModel.clearSelectionAndModals()
                    }
                }
                ShortcutAction.NAVIGATE_UP -> Unit
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .arcFileDropTarget(
                onHoverChanged = { isDragHovering = it },
                onUrisDropped = { uris -> viewModel.handleDroppedOrSharedArchiveUris(uris) }
            )
    ) {
        if (isCompactWidth) {
            // Compact single-column layout: contents fill the main screen area
            ArchiveCompactLayout(
                uiState = uiState,
                onSelectMode = { viewModel.setMode(it) },
                onAddFiles = { addFilesLauncher.launch(arrayOf("*/*")) },
                onAddFolder = { addFolderLauncher.launch(null) },
                onOpenArchive = { openArchiveLauncher.launch(arrayOf("*/*")) },
                onChooseDestination = { destinationTreeLauncher.launch(null) },
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onSelectAll = { viewModel.selectAllInCurrentMode() },
                onClearSelection = { viewModel.clearSelectionAndModals() },
                onDeleteSelected = { viewModel.deleteSelectedItems() },
                onToggleSource = { viewModel.toggleSourceSelection(it) },
                onInspectSource = { viewModel.focusSourceItem(it) },
                onRemoveSource = { viewModel.removeSourceItem(it) },
                onToggleEntry = { viewModel.toggleEntrySelection(it) },
                onInspectEntry = { viewModel.focusArchiveEntry(it) },
                onExtractSingleEntry = { path ->
                    if (path !in uiState.selectedEntryPaths) {
                        viewModel.toggleEntrySelection(path)
                    }
                    viewModel.startExtraction(extractOnlySelected = true)
                },
                onOpenCompressDialog = { viewModel.setCompressionDialogVisible(true) },
                onExtractAll = { viewModel.startExtraction(extractOnlySelected = false) },
                onExtractSelected = { viewModel.startExtraction(extractOnlySelected = true) },
                onDismissBanner = { viewModel.dismissBanner() }
            )
        } else {
            // Medium / Expanded layout (Googlebook OS, Desktops, Tablets):
            // - Left column: mode & actions at top-left, Compressed File Info on the BOTTOM-LEFT
            // - Main screen (right, full height): Contents of the compressed file (or queued items)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ArchiveLeftSidebar(
                    uiState = uiState,
                    onSelectMode = { viewModel.setMode(it) },
                    onAddFiles = { addFilesLauncher.launch(arrayOf("*/*")) },
                    onAddFolder = { addFolderLauncher.launch(null) },
                    onOpenArchive = { openArchiveLauncher.launch(arrayOf("*/*")) },
                    onChooseDestination = { destinationTreeLauncher.launch(null) },
                    onExtractFocused = { path ->
                        if (path !in uiState.selectedEntryPaths) {
                            viewModel.toggleEntrySelection(path)
                        }
                        viewModel.startExtraction(extractOnlySelected = true)
                    },
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                )

                ArchiveMainContentsArea(
                    uiState = uiState,
                    onAddFiles = { addFilesLauncher.launch(arrayOf("*/*")) },
                    onAddFolder = { addFolderLauncher.launch(null) },
                    onOpenArchive = { openArchiveLauncher.launch(arrayOf("*/*")) },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSelectAll = { viewModel.selectAllInCurrentMode() },
                    onClearSelection = { viewModel.clearSelectionAndModals() },
                    onDeleteSelected = { viewModel.deleteSelectedItems() },
                    onToggleSource = { viewModel.toggleSourceSelection(it) },
                    onInspectSource = { viewModel.focusSourceItem(it) },
                    onRemoveSource = { viewModel.removeSourceItem(it) },
                    onToggleEntry = { viewModel.toggleEntrySelection(it) },
                    onInspectEntry = { viewModel.focusArchiveEntry(it) },
                    onExtractSingleEntry = { path ->
                        if (path !in uiState.selectedEntryPaths) {
                            viewModel.toggleEntrySelection(path)
                        }
                        viewModel.startExtraction(extractOnlySelected = true)
                    },
                    onOpenCompressDialog = { viewModel.setCompressionDialogVisible(true) },
                    onExtractAll = { viewModel.startExtraction(extractOnlySelected = false) },
                    onExtractSelected = { viewModel.startExtraction(extractOnlySelected = true) },
                    onDismissBanner = { viewModel.dismissBanner() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        DropZoneOverlay(visible = isDragHovering)
    }

    // Compression Dialog: asks for Destination Folder, Archive Name, and Archive Type
    if (uiState.showCompressionDialog) {
        CompressionSettingsDialog(
            initialConfig = uiState.compressionConfig,
            currentDestinationUri = uiState.destinationTreeUri,
            currentDestinationLabel = uiState.destinationLabel,
            onRequestSystemFolderPicker = { destinationTreeLauncher.launch(null) },
            onConfirm = { config, destUri, destLabel ->
                viewModel.startCompression(config, destUri, destLabel)
            },
            onDismiss = { viewModel.setCompressionDialogVisible(false) }
        )
    }

    uiState.passwordPrompt?.let { prompt ->
        PasswordPromptDialog(
            promptState = prompt,
            onUnlock = { pwd -> viewModel.submitPasswordPrompt(pwd) },
            onDismiss = { viewModel.dismissPasswordPrompt() }
        )
    }

    uiState.activeProgress?.let { progress ->
        val title = when (progress.operationType) {
            ArchiveOperationType.COMPRESSING -> stringResource(R.string.archive_progress_compressing)
            ArchiveOperationType.EXTRACTING -> stringResource(R.string.archive_progress_extracting)
            ArchiveOperationType.INSPECTING -> stringResource(R.string.archive_progress_inspecting)
        }
        DeterminateProgressDialog(
            title = title,
            subtitle = progress.currentItemName,
            fraction = progress.fraction,
            onCancel = { viewModel.cancelActiveOperation() }
        )
    }
}

// =============================================================================
// Left Sidebar (Expanded / Googlebook OS): Controls at top-left, Archive Info on BOTTOM-LEFT
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveLeftSidebar(
    uiState: ArchiveUiState,
    onSelectMode: (ArchiveWorkspaceMode) -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenArchive: () -> Unit,
    onChooseDestination: () -> Unit,
    onExtractFocused: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompress = uiState.mode == ArchiveWorkspaceMode.COMPRESS

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top-left: Mode switcher & quick actions
        PillTabs(
            options = listOf(
                stringResource(R.string.archive_mode_compress) to Icons.Rounded.Archive,
                stringResource(R.string.archive_mode_explore) to Icons.Rounded.FolderZip
            ),
            selectedIndex = if (isCompress) 0 else 1,
            onSelect = { index ->
                onSelectMode(if (index == 0) ArchiveWorkspaceMode.COMPRESS else ArchiveWorkspaceMode.EXPLORE)
            },
            modifier = Modifier.fillMaxWidth()
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isCompress) {
                FilledTonalButton(onClick = onAddFiles, shape = CircleShape) {
                    Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_add_files))
                }
                FilledTonalButton(onClick = onAddFolder, shape = CircleShape) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_add_folder))
                }
            } else {
                FilledTonalButton(
                    onClick = onOpenArchive,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_open_archive))
                }
            }

            AssistChip(
                onClick = onChooseDestination,
                shape = CircleShape,
                leadingIcon = {
                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = {
                    Text(
                        text = uiState.destinationLabel
                            ?: stringResource(R.string.archive_default_destination),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        // Push the Compressed File Info panel cleanly to the BOTTOM-LEFT
        Spacer(modifier = Modifier.weight(1f, fill = true))

        CompressedFileInfoCard(
            uiState = uiState,
            onExtractFocused = onExtractFocused,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Bottom-left Archive Info panel: shows the metadata of the opened compressed file
 * (filename, format, packed size, unpacked total, space saved %, item count, encryption,
 * and currently focused entry), or queued compression summary in Compress mode.
 */
@Composable
private fun CompressedFileInfoCard(
    uiState: ArchiveUiState,
    onExtractFocused: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.archive_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (uiState.mode == ArchiveWorkspaceMode.EXPLORE && uiState.archiveEntries.isNotEmpty()) {
                val unpackedTotal = uiState.archiveEntries.sumOf { it.uncompressedSize.coerceAtLeast(0L) }
                val packedTotal = uiState.currentArchiveSizeBytes.takeIf { it > 0L }
                    ?: uiState.archiveEntries.sumOf { it.compressedSize.coerceAtLeast(0L) }
                val detectedFormat = ArchiveFormat.detectFromFileName(uiState.currentArchiveName)
                val anyEncrypted = uiState.archiveEntries.any { it.isEncrypted }

                // Brand gradient summary header for the compressed file
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(ArcBrandGradient)
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderZip,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.currentArchiveName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = (detectedFormat?.displayName ?: "Archive") + " • " +
                                    stringResource(R.string.files_items_count, uiState.archiveEntries.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactStatTile(
                        label = stringResource(R.string.archive_file_size_label),
                        value = FileSizeFormatter.formatBytes(packedTotal),
                        modifier = Modifier.weight(1f)
                    )
                    CompactStatTile(
                        label = stringResource(R.string.archive_total_unpacked_label),
                        value = FileSizeFormatter.formatBytes(unpackedTotal),
                        modifier = Modifier.weight(1f)
                    )
                }

                InspectorRow(
                    label = stringResource(R.string.archive_encrypted_status),
                    value = if (anyEncrypted) {
                        stringResource(R.string.archive_encrypted_yes)
                    } else {
                        stringResource(R.string.archive_encrypted_no)
                    }
                )

                // Selected / Focused entry quick details
                uiState.focusedArchiveEntry?.let { entry ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    Text(
                        text = stringResource(R.string.archive_selected_entry_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = entry.path,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!entry.isDirectory) {
                        FilledTonalButton(
                            onClick = { onExtractFocused(entry.path) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.widget_action_extract))
                        }
                    }
                }
            } else if (uiState.mode == ArchiveWorkspaceMode.COMPRESS && uiState.sourceItems.isNotEmpty()) {
                val totalSourceBytes = uiState.sourceItems.sumOf { it.sizeBytes }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactStatTile(
                        label = stringResource(R.string.archive_uncompressed_size),
                        value = FileSizeFormatter.formatBytes(totalSourceBytes),
                        modifier = Modifier.weight(1f)
                    )
                    CompactStatTile(
                        label = stringResource(R.string.archive_format_label),
                        value = ".${uiState.compressionConfig.format.extension}",
                        modifier = Modifier.weight(1f)
                    )
                }
                InspectorRow(
                    label = stringResource(R.string.archive_destination_label),
                    value = uiState.destinationLabel
                        ?: stringResource(R.string.archive_default_destination)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.archive_inspector_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =============================================================================
// Main Screen Area: Archive Contents (or Queued Source Files) fill the main screen
// =============================================================================
@Composable
private fun ArchiveMainContentsArea(
    uiState: ArchiveUiState,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenArchive: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onToggleSource: (String) -> Unit,
    onInspectSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onToggleEntry: (String) -> Unit,
    onInspectEntry: (String) -> Unit,
    onExtractSingleEntry: (String) -> Unit,
    onOpenCompressDialog: () -> Unit,
    onExtractAll: () -> Unit,
    onExtractSelected: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompress = uiState.mode == ArchiveWorkspaceMode.COMPRESS
    val selectedCount = if (isCompress) uiState.selectedSourceIds.size else uiState.selectedEntryPaths.size
    val hasItems = if (isCompress) uiState.sourceItems.isNotEmpty() else uiState.archiveEntries.isNotEmpty()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusBannerCard(
            banner = uiState.bannerMessage,
            onDismiss = onDismissBanner
        )

        if (hasItems) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isCompress) {
                    PillSearchField(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = stringResource(R.string.archive_search_placeholder),
                        clearDescription = stringResource(R.string.action_clear_selection),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.files_items_count, uiState.sourceItems.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selectedCount > 0) {
                    AssistChip(
                        onClick = onClearSelection,
                        shape = CircleShape,
                        leadingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(stringResource(R.string.selected_count_format, selectedCount)) }
                    )
                    if (isCompress) {
                        IconButton(onClick = onDeleteSelected) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.action_remove_selected)
                            )
                        }
                    }
                } else {
                    IconButton(onClick = onSelectAll) {
                        Icon(
                            imageVector = Icons.Rounded.SelectAll,
                            contentDescription = stringResource(R.string.action_select_all)
                        )
                    }
                }
            }
        }

        // Full-screen main contents container
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isCompress) {
                if (uiState.sourceItems.isEmpty()) {
                    ModernEmptyState(
                        icon = Icons.Rounded.Archive,
                        title = stringResource(R.string.archive_empty_compress_title),
                        description = stringResource(R.string.archive_empty_compress_desc),
                        primaryText = stringResource(R.string.archive_add_files),
                        onPrimary = onAddFiles,
                        secondaryText = stringResource(R.string.archive_add_folder),
                        onSecondary = onAddFolder
                    )
                } else {
                    RoundedListContainer(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.sourceItems, key = { it.id }) { item ->
                                SourceItemRow(
                                    item = item,
                                    isSelected = item.id in uiState.selectedSourceIds,
                                    isFocused = item.id == uiState.focusedSourceId,
                                    onToggleSelect = { onToggleSource(item.id) },
                                    onInspect = { onInspectSource(item.id) },
                                    onRemove = { onRemoveSource(item.id) },
                                    onSelectAll = onSelectAll
                                )
                            }
                        }
                    }
                }
            } else {
                if (uiState.archiveEntries.isEmpty()) {
                    ModernEmptyState(
                        icon = Icons.Rounded.FolderZip,
                        title = stringResource(R.string.archive_empty_explore_title),
                        description = stringResource(R.string.archive_empty_explore_desc),
                        primaryText = stringResource(R.string.archive_open_archive),
                        onPrimary = onOpenArchive
                    )
                } else {
                    RoundedListContainer(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.filteredArchiveEntries, key = { it.path }) { entry ->
                                ArchiveEntryRow(
                                    entry = entry,
                                    isSelected = entry.path in uiState.selectedEntryPaths,
                                    isFocused = entry.path == uiState.focusedEntryPath,
                                    onToggleSelect = { onToggleEntry(entry.path) },
                                    onInspect = { onInspectEntry(entry.path) },
                                    onExtractEntry = { onExtractSingleEntry(entry.path) },
                                    onSelectAll = onSelectAll
                                )
                            }
                        }
                    }
                }
            }

            // Floating action pill anchored inside the main workspace
            if (isCompress && uiState.sourceItems.isNotEmpty()) {
                FloatingActionPill(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                ) {
                    val totalSize = uiState.sourceItems.sumOf { it.sizeBytes }
                    Text(
                        text = "${uiState.sourceItems.size} • ${FileSizeFormatter.formatBytes(totalSize)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onOpenCompressDialog, shape = CircleShape) {
                        Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.archive_compress_now))
                    }
                }
            } else if (!isCompress && uiState.archiveEntries.isNotEmpty()) {
                FloatingActionPill(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.files_items_count, uiState.filteredArchiveEntries.size),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.selectedEntryPaths.isNotEmpty()) {
                        FilledTonalButton(onClick = onExtractSelected, shape = CircleShape) {
                            Text(
                                stringResource(
                                    R.string.archive_extract_selected,
                                    uiState.selectedEntryPaths.size
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    Button(onClick = onExtractAll, shape = CircleShape) {
                        Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.archive_extract_all), maxLines = 1)
                    }
                }
            }
        }
    }
}

// =============================================================================
// Compact Portrait Layout (Phones): Contents occupy the main screen immediately
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveCompactLayout(
    uiState: ArchiveUiState,
    onSelectMode: (ArchiveWorkspaceMode) -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenArchive: () -> Unit,
    onChooseDestination: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onToggleSource: (String) -> Unit,
    onInspectSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onToggleEntry: (String) -> Unit,
    onInspectEntry: (String) -> Unit,
    onExtractSingleEntry: (String) -> Unit,
    onOpenCompressDialog: () -> Unit,
    onExtractAll: () -> Unit,
    onExtractSelected: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val isCompress = uiState.mode == ArchiveWorkspaceMode.COMPRESS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PillTabs(
            options = listOf(
                stringResource(R.string.archive_mode_compress) to Icons.Rounded.Archive,
                stringResource(R.string.archive_mode_explore) to Icons.Rounded.FolderZip
            ),
            selectedIndex = if (isCompress) 0 else 1,
            onSelect = { index ->
                onSelectMode(if (index == 0) ArchiveWorkspaceMode.COMPRESS else ArchiveWorkspaceMode.EXPLORE)
            }
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isCompress) {
                FilledTonalButton(onClick = onAddFiles, shape = CircleShape) {
                    Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_add_files))
                }
                FilledTonalButton(onClick = onAddFolder, shape = CircleShape) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_add_folder))
                }
            } else {
                FilledTonalButton(onClick = onOpenArchive, shape = CircleShape) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_open_archive))
                }
            }

            AssistChip(
                onClick = onChooseDestination,
                shape = CircleShape,
                leadingIcon = {
                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = {
                    Text(
                        text = uiState.destinationLabel
                            ?: stringResource(R.string.archive_default_destination),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        // Main contents area dominates the screen
        ArchiveMainContentsArea(
            uiState = uiState,
            onAddFiles = onAddFiles,
            onAddFolder = onAddFolder,
            onOpenArchive = onOpenArchive,
            onSearchQueryChange = onSearchQueryChange,
            onSelectAll = onSelectAll,
            onClearSelection = onClearSelection,
            onDeleteSelected = onDeleteSelected,
            onToggleSource = onToggleSource,
            onInspectSource = onInspectSource,
            onRemoveSource = onRemoveSource,
            onToggleEntry = onToggleEntry,
            onInspectEntry = onInspectEntry,
            onExtractSingleEntry = onExtractSingleEntry,
            onOpenCompressDialog = onOpenCompressDialog,
            onExtractAll = onExtractAll,
            onExtractSelected = onExtractSelected,
            onDismissBanner = onDismissBanner,
            modifier = Modifier.weight(1f)
        )

        // Compact bottom info strip when exploring an archive
        if (!isCompress && uiState.archiveEntries.isNotEmpty()) {
            val unpacked = uiState.archiveEntries.sumOf { it.uncompressedSize.coerceAtLeast(0L) }
            val packed = uiState.currentArchiveSizeBytes.takeIf { it > 0L }
                ?: uiState.archiveEntries.sumOf { it.compressedSize.coerceAtLeast(0L) }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderZip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.currentArchiveName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${FileSizeFormatter.formatBytes(packed)} → ${FileSizeFormatter.formatBytes(unpacked)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Shared row chrome: rounded, tonal highlight for selection/hover/focus instead of outlined cards. */
@Composable
private fun rowBackground(isSelected: Boolean, isHovered: Boolean, isFocused: Boolean): Color = when {
    isSelected -> MaterialTheme.colorScheme.secondaryContainer
    isHovered || isFocused -> MaterialTheme.colorScheme.surfaceContainerHigh
    else -> Color.Transparent
}

@Composable
private fun SourceItemRow(
    item: SourceItem,
    isSelected: Boolean,
    isFocused: Boolean,
    onToggleSelect: () -> Unit,
    onInspect: () -> Unit,
    onRemove: () -> Unit,
    onSelectAll: () -> Unit
) {
    val (hoverSource, isHovered) = rememberArcHoverState()
    var menuOffset by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(rowBackground(isSelected, isHovered, isFocused))
                .arcHoverable(hoverSource)
                .arcPointerContextActions(
                    key = item.id,
                    onPrimaryClick = onInspect,
                    onContextMenuRequested = { offset -> menuOffset = offset }
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
            FileKindTile(kind = DeviceFileRepository.classify(item.name, item.isDirectory))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = FileSizeFormatter.formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        menuOffset?.let { pos ->
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuOffset = null },
                offset = with(density) { DpOffset(pos.x.toDp(), 0.dp) }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_inspect)) },
                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    onClick = {
                        menuOffset = null
                        onInspect()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_select_all)) },
                    leadingIcon = { Icon(Icons.Outlined.SelectAll, contentDescription = null) },
                    onClick = {
                        menuOffset = null
                        onSelectAll()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove)) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    onClick = {
                        menuOffset = null
                        onRemove()
                    }
                )
            }
        }
    }
}

@Composable
private fun ArchiveEntryRow(
    entry: ArchiveEntryItem,
    isSelected: Boolean,
    isFocused: Boolean,
    onToggleSelect: () -> Unit,
    onInspect: () -> Unit,
    onExtractEntry: () -> Unit,
    onSelectAll: () -> Unit
) {
    val (hoverSource, isHovered) = rememberArcHoverState()
    var menuOffset by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(rowBackground(isSelected, isHovered, isFocused))
                .arcHoverable(hoverSource)
                .arcPointerContextActions(
                    key = entry.path,
                    onPrimaryClick = onInspect,
                    onContextMenuRequested = { offset -> menuOffset = offset }
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
            FileKindTile(
                kind = DeviceFileRepository.classify(entry.name.ifBlank { entry.path }, entry.isDirectory),
                size = 40.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name.ifBlank { entry.path },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.isEncrypted) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.archive_encrypted_yes),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (!entry.isDirectory) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = FileSizeFormatter.formatBytes(
                            entry.uncompressedSize.takeIf { it > 0 } ?: entry.compressedSize
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        menuOffset?.let { pos ->
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuOffset = null },
                offset = with(density) { DpOffset(pos.x.toDp(), 0.dp) }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_inspect)) },
                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    onClick = {
                        menuOffset = null
                        onInspect()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_action_extract)) },
                    leadingIcon = { Icon(Icons.Outlined.Unarchive, contentDescription = null) },
                    onClick = {
                        menuOffset = null
                        onExtractEntry()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_select_all)) },
                    leadingIcon = { Icon(Icons.Outlined.SelectAll, contentDescription = null) },
                    onClick = {
                        menuOffset = null
                        onSelectAll()
                    }
                )
            }
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
