package com.arcimage.app.ui.archive

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcimage.app.R
import com.arcimage.app.data.util.DocumentUriHelper
import com.arcimage.app.model.ArchiveEntryItem
import com.arcimage.app.model.ArchiveOperationType
import com.arcimage.app.model.ArchiveWorkspaceMode
import com.arcimage.app.model.FileSizeFormatter
import com.arcimage.app.model.SourceItem
import com.arcimage.app.ui.components.DeterminateProgressDialog
import com.arcimage.app.ui.components.DropZoneOverlay
import com.arcimage.app.ui.components.StatusBannerCard
import com.arcimage.app.ui.util.arcFileDropTarget
import com.arcimage.app.ui.util.arcHoverable
import com.arcimage.app.ui.util.arcKeyboardShortcuts
import com.arcimage.app.ui.util.arcPointerContextActions
import com.arcimage.app.ui.util.rememberArcHoverState
import com.arcimage.app.ui.viewmodel.ArchiveUiState
import com.arcimage.app.ui.viewmodel.ArchiveViewModel
import kotlinx.coroutines.launch

@Composable
fun ArchiveManagerScreen(
    viewModel: ArchiveViewModel,
    triggerOpenArchivePicker: Boolean,
    onConsumedQuickLaunch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val focusRequester = remember { FocusRequester() }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .arcKeyboardShortcuts(
                onSelectAll = { viewModel.selectAllInCurrentMode() },
                onOpenPicker = {
                    if (uiState.mode == ArchiveWorkspaceMode.COMPRESS) {
                        addFilesLauncher.launch(arrayOf("*/*"))
                    } else {
                        openArchiveLauncher.launch(arrayOf("*/*"))
                    }
                },
                onDeleteSelected = { viewModel.deleteSelectedItems() },
                onEscape = { viewModel.clearSelectionAndModals() }
            )
            .arcFileDropTarget(
                onHoverChanged = { isDragHovering = it },
                onUrisDropped = { uris -> viewModel.handleDroppedOrSharedArchiveUris(uris) }
            )
    ) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    ArchiveMasterPane(
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
                        onInspectSource = { id ->
                            viewModel.focusSourceItem(id)
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
                            }
                        },
                        onRemoveSource = { viewModel.removeSourceItem(it) },
                        onToggleEntry = { viewModel.toggleEntrySelection(it) },
                        onInspectEntry = { path ->
                            viewModel.focusArchiveEntry(path)
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, path)
                            }
                        },
                        onExtractSingleEntry = { path ->
                            viewModel.toggleEntrySelection(path)
                            viewModel.startExtraction(extractOnlySelected = true)
                        },
                        onOpenCompressDialog = { viewModel.setCompressionDialogVisible(true) },
                        onExtractAll = { viewModel.startExtraction(extractOnlySelected = false) },
                        onExtractSelected = { viewModel.startExtraction(extractOnlySelected = true) },
                        onDismissBanner = { viewModel.dismissBanner() }
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    ArchiveInspectorDetailPane(
                        uiState = uiState,
                        onExtractFocused = { path ->
                            if (path !in uiState.selectedEntryPaths) {
                                viewModel.toggleEntrySelection(path)
                            }
                            viewModel.startExtraction(extractOnlySelected = true)
                        }
                    )
                }
            }
        )

        DropZoneOverlay(visible = isDragHovering)
    }

    // Modals & Non-blocking Determinate Progress
    if (uiState.showCompressionDialog) {
        CompressionSettingsDialog(
            initialConfig = uiState.compressionConfig,
            onConfirm = { config -> viewModel.startCompression(config) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveMasterPane(
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
    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Selector Header
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SegmentedButton(
                selected = uiState.mode == ArchiveWorkspaceMode.COMPRESS,
                onClick = { onSelectMode(ArchiveWorkspaceMode.COMPRESS) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) {
                Text(stringResource(R.string.archive_mode_compress))
            }
            SegmentedButton(
                selected = uiState.mode == ArchiveWorkspaceMode.EXPLORE,
                onClick = { onSelectMode(ArchiveWorkspaceMode.EXPLORE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Outlined.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp)) }
            ) {
                Text(stringResource(R.string.archive_mode_explore))
            }
        }

        // Action Bar
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (uiState.mode == ArchiveWorkspaceMode.COMPRESS) {
                FilledTonalButton(onClick = onAddFiles) {
                    Icon(Icons.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_add_files))
                }
                FilledTonalButton(onClick = onAddFolder) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_add_folder))
                }
            } else {
                FilledTonalButton(onClick = onOpenArchive) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.archive_open_archive))
                }
            }

            AssistChip(
                onClick = onChooseDestination,
                leadingIcon = {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
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

            val selectedCount = if (uiState.mode == ArchiveWorkspaceMode.COMPRESS) {
                uiState.selectedSourceIds.size
            } else {
                uiState.selectedEntryPaths.size
            }

            if (selectedCount > 0) {
                AssistChip(
                    onClick = onClearSelection,
                    label = { Text(stringResource(R.string.selected_count_format, selectedCount)) }
                )
                if (uiState.mode == ArchiveWorkspaceMode.COMPRESS) {
                    IconButton(onClick = onDeleteSelected) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.action_remove_selected)
                        )
                    }
                }
            } else if ((uiState.mode == ArchiveWorkspaceMode.COMPRESS && uiState.sourceItems.isNotEmpty()) ||
                (uiState.mode == ArchiveWorkspaceMode.EXPLORE && uiState.archiveEntries.isNotEmpty())
            ) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Outlined.SelectAll,
                        contentDescription = stringResource(R.string.action_select_all)
                    )
                }
            }
        }

        StatusBannerCard(
            banner = uiState.bannerMessage,
            onDismiss = onDismissBanner
        )

        if (uiState.mode == ArchiveWorkspaceMode.EXPLORE && uiState.archiveEntries.isNotEmpty()) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.archive_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Main List Content
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.mode == ArchiveWorkspaceMode.COMPRESS) {
                if (uiState.sourceItems.isEmpty()) {
                    EmptyArchiveState(
                        title = stringResource(R.string.archive_empty_compress_title),
                        description = stringResource(R.string.archive_empty_compress_desc),
                        primaryButtonText = stringResource(R.string.archive_add_files),
                        onPrimaryClick = onAddFiles
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            } else {
                if (uiState.archiveEntries.isEmpty()) {
                    EmptyArchiveState(
                        title = stringResource(R.string.archive_empty_explore_title),
                        description = stringResource(R.string.archive_empty_explore_desc),
                        primaryButtonText = stringResource(R.string.archive_open_archive),
                        onPrimaryClick = onOpenArchive
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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

        // Bottom Workspace Action Footer
        if (uiState.mode == ArchiveWorkspaceMode.COMPRESS && uiState.sourceItems.isNotEmpty()) {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalSize = uiState.sourceItems.sumOf { it.sizeBytes }
                    Text(
                        text = "${uiState.sourceItems.size} • ${FileSizeFormatter.formatBytes(totalSize)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onOpenCompressDialog) {
                        Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.archive_compress_now))
                    }
                }
            }
        } else if (uiState.mode == ArchiveWorkspaceMode.EXPLORE && uiState.archiveEntries.isNotEmpty()) {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.currentArchiveName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.selectedEntryPaths.isNotEmpty()) {
                        OutlinedButton(onClick = onExtractSelected) {
                            Text(
                                stringResource(
                                    R.string.archive_extract_selected,
                                    uiState.selectedEntryPaths.size
                                )
                            )
                        }
                    }
                    Button(onClick = onExtractAll) {
                        Icon(Icons.Outlined.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.archive_extract_all))
                    }
                }
            }
        }
    }
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

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        isHovered || isFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surface
    }

    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(
                width = if (isFocused || isHovered) 1.5.dp else 1.dp,
                color = if (isFocused || isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .arcHoverable(hoverSource)
                .arcPointerContextActions(
                    key = item.id,
                    onPrimaryClick = onInspect,
                    onContextMenuRequested = { offset -> menuOffset = offset }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
                Icon(
                    imageVector = if (item.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = if (item.isDirectory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
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
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.action_remove)
                    )
                }
            }
        }

        // Googlebook OS Right-Click / Long-Press Context Menu
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

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        isHovered || isFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surface
    }

    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(
                width = if (isFocused || isHovered) 1.5.dp else 1.dp,
                color = if (isFocused || isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .arcHoverable(hoverSource)
                .arcPointerContextActions(
                    key = entry.path,
                    onPrimaryClick = onInspect,
                    onContextMenuRequested = { offset -> menuOffset = offset }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
                Icon(
                    imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = if (entry.isDirectory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name.ifBlank { entry.path },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (entry.isEncrypted) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = stringResource(R.string.archive_encrypted_yes),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (!entry.isDirectory) {
                    Text(
                        text = FileSizeFormatter.formatBytes(
                            entry.uncompressedSize.takeIf { it > 0 } ?: entry.compressedSize
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun ArchiveInspectorDetailPane(
    uiState: ArchiveUiState,
    onExtractFocused: (String) -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.archive_inspector_title),
                style = MaterialTheme.typography.titleLarge
            )
            HorizontalDivider()

            if (uiState.mode == ArchiveWorkspaceMode.EXPLORE && uiState.focusedArchiveEntry != null) {
                val entry = uiState.focusedArchiveEntry!!
                InspectorRow(stringResource(R.string.archive_entry_path), entry.path)
                InspectorRow(
                    stringResource(R.string.archive_entry_type),
                    if (entry.isDirectory) {
                        stringResource(R.string.archive_type_folder)
                    } else {
                        stringResource(R.string.archive_type_file)
                    }
                )
                if (!entry.isDirectory) {
                    InspectorRow(
                        stringResource(R.string.archive_uncompressed_size),
                        FileSizeFormatter.formatBytes(entry.uncompressedSize)
                    )
                    if (entry.compressedSize > 0L) {
                        InspectorRow(
                            stringResource(R.string.archive_compressed_size),
                            FileSizeFormatter.formatBytes(entry.compressedSize)
                        )
                        InspectorRow(
                            stringResource(R.string.archive_compression_ratio),
                            "${entry.savedRatioPercent}%"
                        )
                    }
                }
                InspectorRow(
                    stringResource(R.string.archive_encrypted_status),
                    if (entry.isEncrypted) {
                        stringResource(R.string.archive_encrypted_yes)
                    } else {
                        stringResource(R.string.archive_encrypted_no)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onExtractFocused(entry.path) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.widget_action_extract))
                }
            } else if (uiState.mode == ArchiveWorkspaceMode.COMPRESS && uiState.focusedSourceItem != null) {
                val src = uiState.focusedSourceItem!!
                InspectorRow(stringResource(R.string.archive_entry_path), src.name)
                InspectorRow(
                    stringResource(R.string.archive_entry_type),
                    if (src.isDirectory) {
                        "${stringResource(R.string.archive_type_folder)} (${src.childFileCount})"
                    } else {
                        stringResource(R.string.archive_type_file)
                    }
                )
                InspectorRow(
                    stringResource(R.string.archive_uncompressed_size),
                    FileSizeFormatter.formatBytes(src.sizeBytes)
                )
                InspectorRow(
                    stringResource(R.string.archive_format_label),
                    uiState.compressionConfig.format.displayName
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.archive_inspector_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun EmptyArchiveState(
    title: String,
    description: String,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderZip,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onPrimaryClick) {
            Text(primaryButtonText)
        }
    }
}
