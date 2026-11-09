package com.arcimage.app.ui.image

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import coil.compose.AsyncImage
import com.arcimage.app.R
import com.arcimage.app.data.util.DocumentUriHelper
import com.arcimage.app.model.FileSizeFormatter
import com.arcimage.app.model.ImageBatchItem
import com.arcimage.app.model.ImageFormat
import com.arcimage.app.model.ResizeMode
import com.arcimage.app.ui.components.DeterminateProgressDialog
import com.arcimage.app.ui.components.DropZoneOverlay
import com.arcimage.app.ui.components.StatusBannerCard
import com.arcimage.app.ui.components.shareOutputUri
import com.arcimage.app.ui.util.arcFileDropTarget
import com.arcimage.app.ui.util.arcHoverable
import com.arcimage.app.ui.util.arcKeyboardShortcuts
import com.arcimage.app.ui.util.arcPointerContextActions
import com.arcimage.app.ui.util.rememberArcHoverState
import com.arcimage.app.ui.viewmodel.ImageStudioUiState
import com.arcimage.app.ui.viewmodel.ImageStudioViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ImageStudioScreen(
    viewModel: ImageStudioViewModel,
    triggerOpenImagePicker: Boolean,
    onConsumedQuickLaunch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isCompactWidth = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
    var isDragHovering by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        viewModel.addImages(uris)
    }

    val openImageDocsLauncher = rememberLauncherForActivityResult(
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
        viewModel.addImages(uris)
    }

    val destinationLauncher = rememberLauncherForActivityResult(
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

    LaunchedEffect(triggerOpenImagePicker) {
        if (triggerOpenImagePicker) {
            onConsumedQuickLaunch()
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .arcKeyboardShortcuts(
                onSelectAll = { viewModel.selectAll() },
                onOpenPicker = { openImageDocsLauncher.launch(arrayOf("image/*")) },
                onDeleteSelected = { viewModel.removeSelected() },
                onEscape = { viewModel.clearSelection() }
            )
            .arcFileDropTarget(
                onHoverChanged = { isDragHovering = it },
                onUrisDropped = { uris -> viewModel.addImages(uris) }
            )
    ) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    ImageBatchMasterPane(
                        uiState = uiState,
                        isCompactWidth = isCompactWidth,
                        estimateItemSize = { viewModel.estimateItemBytes(it) },
                        onAddImages = {
                            openImageDocsLauncher.launch(arrayOf("image/*"))
                        },
                        onChooseDestination = { destinationLauncher.launch(null) },
                        onOpenSettingsPane = {
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, "settings")
                            }
                        },
                        onSelectAll = { viewModel.selectAll() },
                        onClearSelection = { viewModel.clearSelection() },
                        onRemoveSelected = { viewModel.removeSelected() },
                        onClearBatch = { viewModel.clearBatch() },
                        onFocusAndInspect = { id ->
                            viewModel.focusItem(id)
                            if (isCompactWidth) {
                                scope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
                                }
                            }
                        },
                        onToggleSelect = { viewModel.toggleSelection(it) },
                        onRemoveSingle = { viewModel.removeItem(it) },
                        onProcessBatch = { viewModel.processBatch() },
                        onDismissBanner = { viewModel.dismissBanner() }
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    ImageSettingsDetailPane(
                        uiState = uiState,
                        onSelectResizeMode = { viewModel.setResizeMode(it) },
                        onChangeCustomPercent = { viewModel.setCustomPercentage(it) },
                        onChangeExactWidth = { viewModel.setExactWidth(it) },
                        onChangeExactHeight = { viewModel.setExactHeight(it) },
                        onToggleAspectRatio = { viewModel.setMaintainAspectRatio(it) },
                        onChangeQuality = { viewModel.setQuality(it) },
                        onSelectFormat = { viewModel.setOutputFormat(it) },
                        onTogglePreserveExif = { viewModel.setPreserveExif(it) },
                        onProcessBatch = { viewModel.processBatch() }
                    )
                }
            }
        )

        DropZoneOverlay(visible = isDragHovering)
    }

    uiState.activeProgress?.let { progress ->
        DeterminateProgressDialog(
            title = stringResource(
                R.string.image_progress_processing,
                (progress.completedCount + 1).coerceAtMost(progress.totalCount),
                progress.totalCount
            ),
            subtitle = progress.currentFileName,
            fraction = progress.fraction,
            onCancel = { viewModel.cancelBatchProcessing() }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageBatchMasterPane(
    uiState: ImageStudioUiState,
    isCompactWidth: Boolean,
    estimateItemSize: (ImageBatchItem) -> Long,
    onAddImages: () -> Unit,
    onChooseDestination: () -> Unit,
    onOpenSettingsPane: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onClearBatch: () -> Unit,
    onFocusAndInspect: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onRemoveSingle: (String) -> Unit,
    onProcessBatch: () -> Unit,
    onDismissBanner: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Action Toolbar
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(onClick = onAddImages) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.image_add_photos))
            }

            if (isCompactWidth && uiState.batchItems.isNotEmpty()) {
                OutlinedButton(onClick = onOpenSettingsPane) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.image_settings_title))
                }
            }

            AssistChip(
                onClick = onChooseDestination,
                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = {
                    Text(
                        text = uiState.destinationLabel
                            ?: stringResource(R.string.archive_default_destination),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )

            if (uiState.selectedIds.isNotEmpty()) {
                AssistChip(
                    onClick = onClearSelection,
                    label = { Text(stringResource(R.string.selected_count_format, uiState.selectedIds.size)) }
                )
                IconButton(onClick = onRemoveSelected) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.action_remove_selected)
                    )
                }
            } else if (uiState.batchItems.isNotEmpty()) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Outlined.SelectAll,
                        contentDescription = stringResource(R.string.action_select_all)
                    )
                }
                IconButton(onClick = onClearBatch) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.image_clear_batch)
                    )
                }
            }
        }

        StatusBannerCard(
            banner = uiState.bannerMessage,
            onDismiss = onDismissBanner
        )

        // Responsive Grid Preview
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.batchItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.image_empty_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.image_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onAddImages) {
                        Text(stringResource(R.string.image_add_photos))
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.batchItems, key = { it.id }) { item ->
                        val estimatedOrActual = item.actualOutputSizeBytes ?: estimateItemSize(item)
                        val (targetW, targetH) = uiState.config.computeTargetDimensions(
                            item.originalWidth,
                            item.originalHeight
                        )
                        ImageGridCard(
                            item = item,
                            targetDimensions = "${item.outputWidth ?: targetW}×${item.outputHeight ?: targetH}",
                            targetFormatLabel = uiState.config.outputFormat.displayName,
                            comparedBytes = estimatedOrActual,
                            isSelected = item.id in uiState.selectedIds,
                            isFocused = item.id == uiState.focusedItemId,
                            onPrimaryClick = { onFocusAndInspect(item.id) },
                            onToggleSelect = { onToggleSelect(item.id) },
                            onSelectAll = onSelectAll,
                            onRemove = { onRemoveSingle(item.id) }
                        )
                    }
                }
            }
        }

        // Bottom Batch Action Bar
        if (uiState.batchItems.isNotEmpty()) {
            val countToProcess = if (uiState.selectedIds.isNotEmpty()) {
                uiState.selectedIds.size
            } else {
                uiState.batchItems.size
            }
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.image_savings_badge,
                                FileSizeFormatter.formatBytes(uiState.totalOriginalBytes),
                                FileSizeFormatter.formatBytes(uiState.estimatedTotalOutputBytes),
                                FileSizeFormatter.formatDeltaPercent(
                                    uiState.totalOriginalBytes,
                                    uiState.estimatedTotalOutputBytes
                                )
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${uiState.config.outputFormat.displayName} • ${uiState.config.quality}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onProcessBatch) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.image_process_batch, countToProcess))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGridCard(
    item: ImageBatchItem,
    targetDimensions: String,
    targetFormatLabel: String,
    comparedBytes: Long,
    isSelected: Boolean,
    isFocused: Boolean,
    onPrimaryClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val (hoverSource, isHovered) = rememberArcHoverState()
    var menuOffset by remember { mutableStateOf<Offset?>(null) }

    Box {
        Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (isFocused || isSelected || isHovered) 2.dp else 1.dp,
                color = when {
                    isSelected || isFocused -> MaterialTheme.colorScheme.primary
                    isHovered -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .arcHoverable(hoverSource)
                .arcPointerContextActions(
                    key = item.id,
                    onPrimaryClick = onPrimaryClick,
                    onContextMenuRequested = { offset -> menuOffset = offset }
                )
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.3f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = targetFormatLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.originalWidth}×${item.originalHeight} → $targetDimensions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.image_savings_badge,
                            FileSizeFormatter.formatBytes(item.originalSizeBytes),
                            FileSizeFormatter.formatBytes(comparedBytes),
                            FileSizeFormatter.formatDeltaPercent(item.originalSizeBytes, comparedBytes)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.actualOutputSizeBytes != null) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                    leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    onClick = {
                        menuOffset = null
                        onPrimaryClick()
                    }
                )
                if (item.outputUri != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        onClick = {
                            menuOffset = null
                            shareOutputUri(context, item.outputUri)
                        }
                    )
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageSettingsDetailPane(
    uiState: ImageStudioUiState,
    onSelectResizeMode: (ResizeMode) -> Unit,
    onChangeCustomPercent: (Int) -> Unit,
    onChangeExactWidth: (Int) -> Unit,
    onChangeExactHeight: (Int) -> Unit,
    onToggleAspectRatio: (Boolean) -> Unit,
    onChangeQuality: (Int) -> Unit,
    onSelectFormat: (ImageFormat) -> Unit,
    onTogglePreserveExif: (Boolean) -> Unit,
    onProcessBatch: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.image_settings_title),
                style = MaterialTheme.typography.titleLarge
            )
            HorizontalDivider()

            // Before / After Comparison Summary Card
            if (uiState.batchItems.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.image_comparison_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.image_original_label),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = FileSizeFormatter.formatBytes(uiState.totalOriginalBytes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.image_estimated_or_actual_label),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "${FileSizeFormatter.formatBytes(uiState.estimatedTotalOutputBytes)} (${
                                        FileSizeFormatter.formatDeltaPercent(
                                            uiState.totalOriginalBytes,
                                            uiState.estimatedTotalOutputBytes
                                        )
                                    })",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 1. Resize Mode Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.image_resize_mode_label),
                    style = MaterialTheme.typography.titleMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResizeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.config.resizeMode == mode,
                            onClick = { onSelectResizeMode(mode) },
                            label = { Text(stringResource(mode.labelRes)) }
                        )
                    }
                }

                if (uiState.config.resizeMode == ResizeMode.PERCENT_CUSTOM) {
                    OutlinedTextField(
                        value = uiState.config.customPercentage.toString(),
                        onValueChange = { raw ->
                            raw.filter { it.isDigit() }.toIntOrNull()?.let(onChangeCustomPercent)
                        },
                        label = { Text(stringResource(R.string.image_custom_percent_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (uiState.config.resizeMode == ResizeMode.EXACT_PIXELS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.config.exactWidth.toString(),
                            onValueChange = { raw ->
                                raw.filter { it.isDigit() }.toIntOrNull()?.let(onChangeExactWidth)
                            },
                            label = { Text(stringResource(R.string.image_width_px_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.config.exactHeight.toString(),
                            onValueChange = { raw ->
                                raw.filter { it.isDigit() }.toIntOrNull()?.let(onChangeExactHeight)
                            },
                            label = { Text(stringResource(R.string.image_height_px_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.image_maintain_aspect),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = uiState.config.maintainAspectRatio,
                            onCheckedChange = onToggleAspectRatio
                        )
                    }
                }
            }

            // 2. Output Format Conversion Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.image_target_format_label),
                    style = MaterialTheme.typography.titleMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ImageFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = uiState.config.outputFormat == fmt,
                            onClick = { onSelectFormat(fmt) },
                            label = { Text(fmt.displayName) }
                        )
                    }
                }
            }

            // 3. Quality / Compression Slider (1–100%) + Estimated Output Size
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.image_quality_label, uiState.config.quality),
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = uiState.config.quality.toFloat(),
                    onValueChange = { onChangeQuality(it.roundToInt()) },
                    valueRange = 1f..100f,
                    enabled = uiState.config.outputFormat.supportsLossyQuality
                )
                Text(
                    text = stringResource(
                        R.string.image_estimated_output,
                        FileSizeFormatter.formatBytes(uiState.estimatedTotalOutputBytes)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 4. Preserve vs Strip EXIF Metadata Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.image_preserve_exif_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.image_preserve_exif_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.config.preserveExif,
                    onCheckedChange = onTogglePreserveExif,
                    enabled = uiState.config.outputFormat.supportsExifMetadata
                )
            }

            if (uiState.batchItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val targetCount = if (uiState.selectedIds.isNotEmpty()) {
                    uiState.selectedIds.size
                } else {
                    uiState.batchItems.size
                }
                Button(
                    onClick = onProcessBatch,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.image_process_batch, targetCount))
                }
            }
        }
    }
}
