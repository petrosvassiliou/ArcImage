package com.arcimage.app.ui.files

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arcimage.app.R
import com.arcimage.app.data.files.DeviceFileEntry
import com.arcimage.app.data.files.DeviceFileRepository
import com.arcimage.app.data.files.FileKind
import com.arcimage.app.data.files.FileSortOrder
import com.arcimage.app.data.files.QuickFolder
import com.arcimage.app.model.FileSizeFormatter
import com.arcimage.app.ui.components.DropZoneOverlay
import com.arcimage.app.ui.theme.ArcBrandGradient
import com.arcimage.app.ui.util.arcFileDropTarget
import com.arcimage.app.ui.util.arcHoverable
import com.arcimage.app.ui.util.arcKeyboardShortcuts
import com.arcimage.app.ui.util.arcPointerContextActions
import com.arcimage.app.ui.util.rememberArcHoverState
import com.arcimage.app.ui.viewmodel.FileBrowserUiState
import com.arcimage.app.ui.viewmodel.FileBrowserViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Default ArcImage home: a modern browser over the REAL device file system
 * (internal shared storage, SD cards, USB drives). No sample or placeholder content.
 *
 * - Tap a folder to open it, an archive to inspect it, an image to edit it.
 * - Long-press / right-click for the context menu; multi-select to compress or batch-edit.
 * - Keyboard: Ctrl+A select all, Ctrl+O open files, Backspace up, Esc clear selection.
 */
@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel,
    onOpenArchive: (Uri) -> Unit,
    onOpenImages: (List<Uri>) -> Unit,
    onCompress: (List<Uri>) -> Unit,
    onOpenDocuments: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dropHover by remember { mutableStateOf(false) }

    // Re-check access whenever the user returns (e.g. from the "All files access" settings page)
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshAccess() }

    val documentsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onOpenDocuments(uris) }

    val requestAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appSpecific = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { context.startActivity(appSpecific) }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    BackHandler(enabled = state.selectedPaths.isNotEmpty() || state.canNavigateUp) {
        if (state.selectedPaths.isNotEmpty()) viewModel.clearSelection() else viewModel.navigateUp()
    }

    val openEntry: (DeviceFileEntry) -> Unit = { entry ->
        when (entry.kind) {
            FileKind.FOLDER -> viewModel.openDirectory(entry.path)
            FileKind.ARCHIVE -> onOpenArchive(Uri.fromFile(File(entry.path)))
            FileKind.IMAGE -> onOpenImages(listOf(Uri.fromFile(File(entry.path))))
            FileKind.OTHER -> viewModel.toggleSelection(entry.path)
        }
    }
    val onEntryClick: (DeviceFileEntry) -> Unit = { entry ->
        if (state.selectedPaths.isNotEmpty()) viewModel.toggleSelection(entry.path) else openEntry(entry)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .arcFileDropTarget(
                onHoverChanged = { dropHover = it },
                onUrisDropped = onOpenDocuments
            )
    ) {
        if (!state.hasStorageAccess) {
            PermissionOnboarding(
                onGrantAccess = requestAccess,
                onOpenDocuments = { documentsLauncher.launch(arrayOf("*/*")) }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LocationChips(
                    state = state,
                    onOpenRoot = viewModel::openRoot,
                    onOpenQuickFolder = viewModel::openQuickFolder
                )

                PathToolbar(
                    state = state,
                    onNavigateUp = { viewModel.navigateUp() },
                    onOpenDirectory = viewModel::openDirectory,
                    onSortSelected = viewModel::setSortOrder,
                    onToggleGrid = { viewModel.setGridView(!state.gridView) },
                    onRefresh = viewModel::refresh
                )

                SearchPill(
                    query = state.searchQuery,
                    onQueryChange = viewModel::setSearchQuery
                )

                FileListContainer(
                    state = state,
                    onEntryClick = onEntryClick,
                    onOpenEntry = openEntry,
                    onToggleSelection = viewModel::toggleSelection,
                    onCompressEntry = { onCompress(listOf(Uri.fromFile(File(it.path)))) },
                    onSelectAll = viewModel::selectAll,
                    onOpenPicker = { documentsLauncher.launch(arrayOf("*/*")) },
                    onBackspace = {
                        if (state.selectedPaths.isNotEmpty()) viewModel.clearSelection() else viewModel.navigateUp()
                    },
                    onEscape = viewModel::clearSelection,
                    modifier = Modifier.weight(1f)
                )

                SelectionBar(
                    state = state,
                    onClear = viewModel::clearSelection,
                    onCompress = {
                        onCompress(state.selectedEntries.map { Uri.fromFile(File(it.path)) })
                        viewModel.clearSelection()
                    },
                    onEditImages = {
                        onOpenImages(
                            state.selectedEntries
                                .filter { it.kind == FileKind.IMAGE }
                                .map { Uri.fromFile(File(it.path)) }
                        )
                        viewModel.clearSelection()
                    }
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        DropZoneOverlay(visible = dropHover)
    }
}

// -----------------------------------------------------------------------------
// Permission onboarding (shown until the user grants device file access)
// -----------------------------------------------------------------------------
@Composable
private fun PermissionOnboarding(
    onGrantAccess: () -> Unit,
    onOpenDocuments: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(ArcBrandGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.files_permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.files_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onGrantAccess,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.files_grant_access))
                }
                OutlinedButton(
                    onClick = onOpenDocuments,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.files_open_documents))
                }
                Text(
                    text = stringResource(R.string.files_drop_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Storage volumes + quick-access folders
// -----------------------------------------------------------------------------
@Composable
private fun LocationChips(
    state: FileBrowserUiState,
    onOpenRoot: (String) -> Unit,
    onOpenQuickFolder: (QuickFolder) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.roots, key = { "root:" + it.path }) { root ->
            FilterChip(
                selected = state.activeRootPath == root.path,
                onClick = { onOpenRoot(root.path) },
                label = {
                    Text(
                        root.description ?: stringResource(
                            if (root.isPrimary) R.string.files_internal_storage else R.string.files_external_storage
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (root.isPrimary) Icons.Rounded.PhoneAndroid else Icons.Rounded.SdCard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = CircleShape
            )
        }
        items(QuickFolder.entries, key = { "quick:" + it.name }) { folder ->
            AssistChip(
                onClick = { onOpenQuickFolder(folder) },
                label = { Text(stringResource(quickFolderLabel(folder))) },
                leadingIcon = {
                    Icon(
                        imageVector = quickFolderIcon(folder),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = CircleShape,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Breadcrumbs + view controls
// -----------------------------------------------------------------------------
@Composable
private fun PathToolbar(
    state: FileBrowserUiState,
    onNavigateUp: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onSortSelected: (FileSortOrder) -> Unit,
    onToggleGrid: () -> Unit,
    onRefresh: () -> Unit
) {
    val root = state.activeRootPath
    val current = state.currentPath
    val rootLabel = state.roots.firstOrNull { it.path == root }?.let { r ->
        r.description ?: stringResource(
            if (r.isPrimary) R.string.files_internal_storage else R.string.files_external_storage
        )
    } ?: stringResource(R.string.files_internal_storage)
    val crumbs = if (root != null && current != null && current.startsWith(root)) {
        DeviceFileRepository.breadcrumbs(root, current)
    } else {
        emptyList()
    }
    val crumbState = rememberLazyListState()
    LaunchedEffect(crumbs.size) {
        if (crumbs.isNotEmpty()) crumbState.animateScrollToItem(crumbs.size)
    }
    var sortMenu by remember { mutableStateOf(false) }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateUp, enabled = state.canNavigateUp) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.files_navigate_up))
            }
            LazyRow(
                state = crumbState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "crumb-root") {
                    Crumb(
                        label = rootLabel,
                        isLast = crumbs.isEmpty(),
                        onClick = { root?.let(onOpenDirectory) }
                    )
                }
                itemsIndexed(crumbs, key = { _, c -> c.second }) { index, (name, path) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Crumb(label = name, isLast = index == crumbs.lastIndex, onClick = { onOpenDirectory(path) })
                    }
                }
            }
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.files_sort))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    FileSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sortLabel(order))) },
                            leadingIcon = {
                                RadioButton(selected = state.sortOrder == order, onClick = null)
                            },
                            onClick = {
                                sortMenu = false
                                onSortSelected(order)
                            }
                        )
                    }
                }
            }
            IconButton(onClick = onToggleGrid) {
                Icon(
                    imageVector = if (state.gridView) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                    contentDescription = stringResource(
                        if (state.gridView) R.string.files_view_list else R.string.files_view_grid
                    )
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.files_refresh))
            }
        }
    }
}

@Composable
private fun Crumb(label: String, isLast: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
        color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .arcPointerContextActions(key = label, onPrimaryClick = onClick, onContextMenuRequested = { })
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun SearchPill(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.files_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear_selection))
                }
            }
        },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// -----------------------------------------------------------------------------
// File list / grid
// -----------------------------------------------------------------------------
@Composable
private fun FileListContainer(
    state: FileBrowserUiState,
    onEntryClick: (DeviceFileEntry) -> Unit,
    onOpenEntry: (DeviceFileEntry) -> Unit,
    onToggleSelection: (String) -> Unit,
    onCompressEntry: (DeviceFileEntry) -> Unit,
    onSelectAll: () -> Unit,
    onOpenPicker: () -> Unit,
    onBackspace: () -> Unit,
    onEscape: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.currentPath) { runCatching { focusRequester.requestFocus() } }
    val visible = state.visibleEntries

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.large)
            .focusRequester(focusRequester)
            .arcKeyboardShortcuts(
                onSelectAll = onSelectAll,
                onOpenPicker = onOpenPicker,
                onDeleteSelected = onBackspace,
                onEscape = onEscape
            )
            .focusable()
    ) {
        Column {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                visible.isEmpty() && !state.isLoading -> EmptyFolderState(
                    isSearch = state.searchQuery.isNotBlank()
                )

                state.gridView -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visible, key = { it.path }) { entry ->
                        FileGridCard(
                            entry = entry,
                            selected = entry.path in state.selectedPaths,
                            onClick = { onEntryClick(entry) },
                            onOpen = { onOpenEntry(entry) },
                            onToggleSelection = { onToggleSelection(entry.path) },
                            onCompress = { onCompressEntry(entry) }
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visible, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            selected = entry.path in state.selectedPaths,
                            selectionMode = state.selectedPaths.isNotEmpty(),
                            onClick = { onEntryClick(entry) },
                            onOpen = { onOpenEntry(entry) },
                            onToggleSelection = { onToggleSelection(entry.path) },
                            onCompress = { onCompressEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    entry: DeviceFileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onCompress: () -> Unit
) {
    val (hoverSource, hovered) = rememberArcHoverState()
    var menuOpen by remember { mutableStateOf(false) }
    val background = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(background)
                .arcHoverable(hoverSource)
                .arcPointerContextActions(
                    key = entry.path,
                    onPrimaryClick = onClick,
                    onContextMenuRequested = { menuOpen = true }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FileThumbnail(entry = entry, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entrySubtitle(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
            } else {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        EntryContextMenu(
            expanded = menuOpen,
            entry = entry,
            selected = selected,
            onDismiss = { menuOpen = false },
            onOpen = onOpen,
            onToggleSelection = onToggleSelection,
            onCompress = onCompress
        )
    }
}

@Composable
private fun FileGridCard(
    entry: DeviceFileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onCompress: () -> Unit
) {
    val (hoverSource, hovered) = rememberArcHoverState()
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier
                .fillMaxWidth()
                .arcHoverable(hoverSource)
                .clip(MaterialTheme.shapes.large)
                .arcPointerContextActions(
                    key = entry.path,
                    onPrimaryClick = onClick,
                    onContextMenuRequested = { menuOpen = true }
                )
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.25f),
                    contentAlignment = Alignment.Center
                ) {
                    FileThumbnail(entry = entry, size = null)
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entrySubtitle(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        EntryContextMenu(
            expanded = menuOpen,
            entry = entry,
            selected = selected,
            onDismiss = { menuOpen = false },
            onOpen = onOpen,
            onToggleSelection = onToggleSelection,
            onCompress = onCompress
        )
    }
}

@Composable
private fun EntryContextMenu(
    expanded: Boolean,
    entry: DeviceFileEntry,
    selected: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onCompress: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (entry.kind != FileKind.OTHER) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (entry.kind) {
                                FileKind.ARCHIVE -> R.string.action_open_archive
                                FileKind.IMAGE -> R.string.action_open_in_studio
                                else -> R.string.action_open_folder
                            }
                        )
                    )
                },
                leadingIcon = { Icon(kindIcon(entry.kind), contentDescription = null) },
                onClick = {
                    onDismiss()
                    onOpen()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (selected) R.string.action_clear_selection else R.string.action_select)) },
            leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
            onClick = {
                onDismiss()
                onToggleSelection()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_compress_selection)) },
            leadingIcon = { Icon(Icons.Rounded.Compress, contentDescription = null) },
            onClick = {
                onDismiss()
                onCompress()
            }
        )
    }
}

/** Kind-tinted icon tile, or a real thumbnail for images. [size] null = fill parent. */
@Composable
private fun FileThumbnail(entry: DeviceFileEntry, size: Dp?) {
    val (container, content) = kindColors(entry.kind)
    val shape = if (size == null) MaterialTheme.shapes.medium else MaterialTheme.shapes.small
    val boxModifier = (if (size == null) Modifier.fillMaxSize() else Modifier.size(size))
        .clip(shape)
        .background(container)
    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = kindIcon(entry.kind),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(if (size == null) 40.dp else 24.dp)
        )
        if (entry.kind == FileKind.IMAGE) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(entry.path))
                    .size(320)
                    .crossfade(true)
                    .build(),
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun EmptyFolderState(isSearch: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearch) Icons.Rounded.Search else Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = stringResource(if (isSearch) R.string.files_no_results else R.string.files_empty_folder),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Floating multi-selection action bar
// -----------------------------------------------------------------------------
@Composable
private fun SelectionBar(
    state: FileBrowserUiState,
    onClear: () -> Unit,
    onCompress: () -> Unit,
    onEditImages: () -> Unit
) {
    val count = state.selectedPaths.size
    val hasImages = state.selectedEntries.any { it.kind == FileKind.IMAGE }
    AnimatedVisibility(
        visible = count > 0,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_clear_selection),
                        tint = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
                Text(
                    text = stringResource(R.string.files_selected_count, count),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (hasImages) {
                    FilledTonalButton(onClick = onEditImages) {
                        Icon(Icons.Rounded.PhotoSizeSelectLarge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_open_in_studio), maxLines = 1)
                    }
                }
                Button(onClick = onCompress) {
                    Icon(Icons.Rounded.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_compress_selection), maxLines = 1)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------
@Composable
private fun entrySubtitle(entry: DeviceFileEntry): String {
    val date = remember(entry.lastModified) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.lastModified))
    }
    return if (entry.isDirectory) {
        stringResource(R.string.files_items_count, entry.childCount) + " • " + date
    } else {
        FileSizeFormatter.formatBytes(entry.sizeBytes) + " • " + date
    }
}

@Composable
private fun kindColors(kind: FileKind): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        FileKind.FOLDER -> scheme.primaryContainer to scheme.onPrimaryContainer
        FileKind.ARCHIVE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        FileKind.IMAGE -> scheme.secondaryContainer to scheme.onSecondaryContainer
        FileKind.OTHER -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
}

private fun kindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Rounded.Folder
    FileKind.ARCHIVE -> Icons.Rounded.FolderZip
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.OTHER -> Icons.Rounded.Description
}

private fun quickFolderLabel(folder: QuickFolder): Int = when (folder) {
    QuickFolder.DOWNLOADS -> R.string.files_quick_downloads
    QuickFolder.CAMERA -> R.string.files_quick_camera
    QuickFolder.PICTURES -> R.string.files_quick_pictures
    QuickFolder.DOCUMENTS -> R.string.files_quick_documents
}

private fun quickFolderIcon(folder: QuickFolder): ImageVector = when (folder) {
    QuickFolder.DOWNLOADS -> Icons.Rounded.Download
    QuickFolder.CAMERA -> Icons.Rounded.PhotoCamera
    QuickFolder.PICTURES -> Icons.Rounded.Photo
    QuickFolder.DOCUMENTS -> Icons.Rounded.Description
}

private fun sortLabel(order: FileSortOrder): Int = when (order) {
    FileSortOrder.NAME -> R.string.files_sort_name
    FileSortOrder.DATE -> R.string.files_sort_date
    FileSortOrder.SIZE -> R.string.files_sort_size
}
