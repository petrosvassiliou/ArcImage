package com.arcimage.app.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.arcimage.app.R
import com.arcimage.app.model.ArchiveFormat
import com.arcimage.app.model.CompressionConfig
import com.arcimage.app.model.CompressionLevel
import com.arcimage.app.ui.viewmodel.PasswordPromptState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressionSettingsDialog(
    initialConfig: CompressionConfig,
    onConfirm: (CompressionConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var archiveName by rememberSaveable { mutableStateOf(initialConfig.archiveBaseName) }
    var selectedFormatName by rememberSaveable { mutableStateOf(initialConfig.format.name) }
    var selectedLevelName by rememberSaveable { mutableStateOf(initialConfig.level.name) }
    var password by rememberSaveable { mutableStateOf(initialConfig.password) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val selectedFormat = ArchiveFormat.valueOf(selectedFormatName)
    val selectedLevel = CompressionLevel.valueOf(selectedLevelName)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Archive,
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
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text(stringResource(R.string.archive_name_label)) },
                    suffix = { Text(".${selectedFormat.extension}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                                label = { Text(".${format.extension}") }
                            )
                        }
                    }
                }

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

                if (selectedFormat.supportsEncryption) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.archive_password_label)) },
                        supportingText = { Text(stringResource(R.string.archive_password_supporting)) },
                        singleLine = true,
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
                onClick = {
                    onConfirm(
                        CompressionConfig(
                            archiveBaseName = archiveName.ifBlank { "ArcImage_Archive" },
                            format = selectedFormat,
                            level = selectedLevel,
                            password = if (selectedFormat.supportsEncryption) password else ""
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.archive_start_compression))
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
                onClick = { onUnlock(password) }
            ) {
                Text(stringResource(R.string.archive_unlock_action))
            }
        }
    )
}
