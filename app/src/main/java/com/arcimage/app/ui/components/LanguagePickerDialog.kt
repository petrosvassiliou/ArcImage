package com.arcimage.app.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arcimage.app.R
import com.arcimage.app.model.AppLanguage
import com.arcimage.app.model.ThemePreference

/**
 * Flag-based language selector: displays crisp country flags instead of text labels
 * (UK, Germany, Italy, France, Spain, Greece), while keeping native language names
 * as accessibility `contentDescription`s for TalkBack and UI testing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePickerDialog(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val activeFlagLanguage = resolveFlagLanguage(currentLanguage)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = stringResource(R.string.language_dialog_title))
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(
                    maxItemsInEachRow = 3,
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SelectableFlagLanguages.forEach { lang ->
                        val isSelected = lang == activeFlagLanguage
                        Surface(
                            onClick = { onSelectLanguage(lang) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = if (isSelected) {
                                BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            },
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription = lang.nativeDisplayName
                            }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CountryFlagIcon(
                                    language = lang,
                                    width = 56.dp,
                                    height = 38.dp,
                                    cornerRadius = 8.dp
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
fun ThemeSettingsMenu(
    expanded: Boolean,
    currentTheme: ThemePreference,
    useDynamicColor: Boolean,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onOpenUpdates: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        ThemePreference.entries.forEach { pref ->
            DropdownMenuItem(
                text = { Text(stringResource(pref.labelRes)) },
                leadingIcon = {
                    RadioButton(
                        selected = pref == currentTheme,
                        onClick = null
                    )
                },
                onClick = {
                    onSelectTheme(pref)
                    onDismiss()
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dynamic_color_label)) },
                trailingIcon = {
                    Switch(
                        checked = useDynamicColor,
                        onCheckedChange = null
                    )
                },
                onClick = {
                    onToggleDynamicColor(!useDynamicColor)
                }
            )
        }

        if (onOpenUpdates != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.update_check_now)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdateAlt,
                        contentDescription = null
                    )
                },
                onClick = {
                    onDismiss()
                    onOpenUpdates()
                }
            )
        }
    }
}
