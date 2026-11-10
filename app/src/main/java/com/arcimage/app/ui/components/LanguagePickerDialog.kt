package com.arcimage.app.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arcimage.app.R
import com.arcimage.app.model.AppLanguage
import com.arcimage.app.model.ThemePreference

/**
 * Country-flag dropdown language selector: displays the active country flag with a dropdown
 * arrow and opens an anchored Material 3 [DropdownMenu] listing all 6 supported country flags
 * (UK, Germany, Italy, France, Spain, Greece), while keeping native language names as
 * accessibility `contentDescription`s for TalkBack and UI testing.
 */
@Composable
fun LanguageFlagDropdown(
    currentLanguage: AppLanguage,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFlagLanguage = resolveFlagLanguage(currentLanguage)
    val actionLangDesc = stringResource(R.string.action_language)

    Box(modifier = modifier) {
        Surface(
            onClick = { onExpandedChange(!expanded) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = actionLangDesc
            }
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CountryFlagIcon(
                    language = activeFlagLanguage,
                    width = 28.dp,
                    height = 19.dp,
                    cornerRadius = 4.dp
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            SelectableFlagLanguages.forEach { lang ->
                val isSelected = lang == activeFlagLanguage
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CountryFlagIcon(
                                language = lang,
                                width = 38.dp,
                                height = 25.dp,
                                cornerRadius = 5.dp
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    onClick = {
                        onSelectLanguage(lang)
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = lang.nativeDisplayName
                        }
                )
            }
        }
    }
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
