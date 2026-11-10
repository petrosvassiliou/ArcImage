package com.arcimage.app.ui.archive

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcimage.app.R
import com.arcimage.app.data.files.DeviceFileRepository
import com.arcimage.app.data.files.QuickFolder
import com.arcimage.app.model.ArchiveFormat
import com.arcimage.app.model.CompressionConfig
import com.arcimage.app.model.CompressionLevel
import com.arcimage.app.ui.viewmodel.PasswordPromptState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressionSettingsDialog(
    initialConfig: CompressionConfig,
    currentDestinationUri: Uri?,
    currentDestinationLabel: String?,
    onRequestSystemFolderPicker: () -> Unit,
    onConfirm: (CompressionConfig, Uri?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasDeviceStorageAccess = remember {
        runCatching { DeviceFileRepository.hasAllFilesAccess(context) }.getOrDefault(false)
    }

    var archiveName by rememberSaveable(initialConfig.archiveBaseName) {
        mutableStateOf(initialConfig.archiveBaseName)
    }
    var selectedFormatName by rememberSaveable { mutableStateOf(initialConfig.format.name) }
    var selectedLevelName by rememberSaveable { mutableStateOf(initialConfig.level.name) }
    var password by rememberSaveable { mutableStateOf(initialConfig.password) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    var chosenDestUriString by rememberSaveable(currentDestinationUri?.toString()) {
        mutableStateOf(currentDestinationUri?.toString())
    }
    var chosenDestLabel by rememberSaveable(currentDestinationLabel) {
        mutableStateOf(currentDestinationLabel)
    }
    var showLocalFolderBrowser by rememberSaveable { mutableStateOf(false) }

    // Sync if the external SAF folder picker updated the ViewModel's destination while dialog is open
    LaunchedEffect(currentDestinationUri, currentDestinationLabel) {
        if (currentDestinationUri != null) {
            chosenDestUriString = currentDestinationUri.toString()
            chosenDestLabel = currentDestinationLabel
        }
    }

    val selectedFormat = ArchiveFormat.valueOf(selectedFormatName)
    val selectedLevel = CompressionLevel.valueOf(selectedLevelName)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = stringResource(R.string.archive_dialog_compress_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Destination Folder Picker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.archive_destination_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chosenDestLabel
                                        ?: stringResource(R.string.archive_default_destination),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subPath = chosenDestUriString
                                    ?.takeIf { it.startsWith("file://") }
                                    ?.let { Uri.parse(it).path }
                                if (!subPath.isNullOrBlank()) {
                                    Text(
                                        text = subPath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (hasDeviceStorageAccess) {
                                        showLocalFolderBrowser = true
                                    } else {
                                        onRequestSystemFolderPicker()
                                    }
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.archive_browse_folder),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // 2. Archive Filename
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text(stringResource(R.string.archive_name_label)) },
                    suffix = { Text(".${selectedFormat.extension}") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. Archive Format / Type
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.archive_format_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ArchiveFormat.compressibleFormats.forEach { format ->
                            FilterChip(
                                selected = format == selectedFormat,
                                onClick = { selectedFormatName = format.name },
                                shape = CircleShape,
                                label = { Text(".${format.extension}") }
                            )
                        }
                    }
                }

                // 4. Compression Level
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.archive_level_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        CompressionLevel.entries.forEachIndexed { index, level ->
                            SegmentedButton(
                                selected = level == selectedLevel,
                                onClick = { selectedLevelName = level.name },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = CompressionLevel.entries.size
                                )
                            ) {
                                Text(
                                    text = stringResource(level.labelRes),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // 5. Optional Password Protection
                if (selectedFormat.supportsEncryption) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.archive_password_label)) },
                        supportingText = { Text(stringResource(R.string.archive_password_supporting)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (showPassword) {
                                        stringResource(R.string.archive_password_hide)
                                    } else {
                                        stringResource(R.string.archive_password_show)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(
                shape = CircleShape,
                onClick = {
                    onConfirm(
                        CompressionConfig(
                            archiveBaseName = archiveName.ifBlank { "ArcImage_Archive" },
                            format = selectedFormat,
                            level = selectedLevel,
                            password = if (selectedFormat.supportsEncryption) password else ""
                        ),
                        chosenDestUriString?.let { Uri.parse(it) },
                        chosenDestLabel
                    )
                }
            ) {
                Text(stringResource(R.string.archive_start_compression))
            }
        }
    )

    if (showLocalFolderBrowser) {
        val initialFolder = chosenDestUriString
            ?.takeIf { it.startsWith("file://") }
            ?.let { Uri.parse(it).path }
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }
            ?: runCatching { DeviceFileRepository.quickFolder(QuickFolder.DOWNLOADS) }
                .getOrNull()
                ?.takeIf { it.isDirectory }
            ?: DeviceFileRepository.primaryStorageRoot()

        DeviceFolderPickerDialog(
            initialDir = initialFolder,
            onSelectDirectory = { dir ->
                chosenDestUriString = Uri.fromFile(dir).toString()
                chosenDestLabel = dir.name.ifBlank { dir.absolutePath }
                showLocalFolderBrowser = false
            },
            onOpenSystemPicker = {
                showLocalFolderBrowser = false
                onRequestSystemFolderPicker()
            },
            onDismiss = { showLocalFolderBrowser = false }
        )
    }
}

/**
 * In-app destination folder browser over real device storage so the user can freely pick
 * ANY folder (including `Download` or root folders that Android's SAF picker restricts).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceFolderPickerDialog(
    initialDir: File,
    onSelectDirectory: (File) -> Unit,
    onOpenSystemPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val rootPath = remember { DeviceFileRepository.primaryStorageRoot().absolutePath }
    var currentPath by rememberSaveable { mutableStateOf(initialDir.absolutePath) }
    val currentDir = File(currentPath)
    val subFolders = remember(currentPath) {
        runCatching {
            DeviceFileRepository.listDirectory(currentDir)
                .filter { it.isDirectory }
        }.getOrDefault(emptyList())
    }
    val canGoUp = currentDir.parentFile != null && currentDir.absolutePath != rootPath

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = stringResource(R.string.archive_destination_label))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick folder shortcuts
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { currentPath = rootPath },
                        shape = CircleShape,
                        label = { Text(stringResource(R.string.files_internal_storage)) }
                    )
                    AssistChip(
                        onClick = {
                            val dl = DeviceFileRepository.quickFolder(QuickFolder.DOWNLOADS)
                            if (dl.isDirectory) currentPath = dl.absolutePath
                        },
                        shape = CircleShape,
                        label = { Text(stringResource(R.string.files_quick_downloads)) }
                    )
                    AssistChip(
                        onClick = {
                            val docs = DeviceFileRepository.quickFolder(QuickFolder.DOCUMENTS)
                            if (docs.isDirectory) currentPath = docs.absolutePath
                        },
                        shape = CircleShape,
                        label = { Text(stringResource(R.string.files_quick_documents)) }
                    )
                }

                // Current folder path bar + Up button
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                currentDir.parentFile?.let { currentPath = it.absolutePath }
                            },
                            enabled = canGoUp,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(R.string.files_navigate_up),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = currentPath,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Subfolder list
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 260.dp)
                ) {
                    if (subFolders.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.files_empty_folder),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(6.dp)) {
                            items(subFolders, key = { it.path }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable { currentPath = entry.path }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenSystemPicker,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.archive_system_folder_picker))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectDirectory(currentDir) },
                shape = CircleShape
            ) {
                Text(stringResource(R.string.archive_select_this_folder))
            }
        }
    )
}

@Composable
fun PasswordPromptDialog(
    promptState: PasswordPromptState,
    onUnlock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by rememberSaveable(promptState.archiveUri.toString()) { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = stringResource(R.string.archive_password_prompt_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.archive_password_prompt_desc, promptState.archiveName),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.archive_password_input_label)) },
                    isError = promptState.hasInvalidPasswordError,
                    supportingText = if (promptState.hasInvalidPasswordError) {
                        {
                            Text(
                                text = stringResource(R.string.error_invalid_password),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (showPassword) {
                                    stringResource(R.string.archive_password_hide)
                                } else {
                                    stringResource(R.string.archive_password_show)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(
                enabled = password.isNotEmpty(),
                shape = CircleShape,
                onClick = { onUnlock(password) }
            ) {
                Text(stringResource(R.string.archive_unlock_action))
            }
        }
    )
}
