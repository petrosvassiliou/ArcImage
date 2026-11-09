package com.arcimage.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.arcimage.app.R
import com.arcimage.app.model.AppDestination
import com.arcimage.app.ui.archive.ArchiveManagerScreen
import com.arcimage.app.ui.image.ImageStudioScreen
import com.arcimage.app.ui.viewmodel.ArchiveViewModel
import com.arcimage.app.ui.viewmodel.ImageStudioViewModel
import com.arcimage.app.ui.viewmodel.MainShellViewModel
import com.arcimage.app.ui.viewmodel.QuickLaunchAction

@Composable
fun ArcImageShell(
    shellViewModel: MainShellViewModel,
    archiveViewModel: ArchiveViewModel,
    imageStudioViewModel: ImageStudioViewModel
) {
    val shellState by shellViewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isCompactScreen =
        adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

    var showThemeMenu by rememberSaveable { mutableStateOf(false) }

    if (isCompactScreen) {
        // =====================================================================
        // COMPACT SCREENS (Android Phones): TopAppBar + Bottom NavigationBar
        // =====================================================================
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arcimage_logo),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.app_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = { shellViewModel.setLanguageDialogVisible(true) }) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = stringResource(R.string.action_language),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(shellState.currentLanguage.badgeCode)
                        }
                        Box {
                            IconButton(onClick = { showThemeMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = stringResource(R.string.action_theme)
                                )
                            }
                            ThemeSettingsMenu(
                                expanded = showThemeMenu,
                                currentTheme = shellState.themePreference,
                                useDynamicColor = shellState.useDynamicColor,
                                onSelectTheme = { shellViewModel.setThemePreference(it) },
                                onToggleDynamicColor = { shellViewModel.setUseDynamicColor(it) },
                                onDismiss = { showThemeMenu = false }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    AppDestination.entries.forEach { destination ->
                        val selected = shellState.activeDestination == destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { shellViewModel.selectDestination(destination) },
                            icon = {
                                Icon(
                                    imageVector = destinationIcon(destination, selected),
                                    contentDescription = stringResource(destination.labelRes)
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            ActiveFeatureWorkspace(
                destination = shellState.activeDestination,
                quickLaunchAction = shellState.quickLaunchRequest,
                onConsumedQuickLaunch = { shellViewModel.consumeQuickLaunch() },
                archiveViewModel = archiveViewModel,
                imageStudioViewModel = imageStudioViewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    } else {
        // =====================================================================
        // EXPANDED SCREENS (Googlebook OS Laptops / Desktops):
        // NavigationRail with "ArcImage" Header + Multi-Pane Master-Detail Layout
        // =====================================================================
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    header = {
                        Column(
                            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arcimage_logo),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(26.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    AppDestination.entries.forEach { destination ->
                        val selected = shellState.activeDestination == destination
                        NavigationRailItem(
                            selected = selected,
                            onClick = { shellViewModel.selectDestination(destination) },
                            icon = {
                                Icon(
                                    imageVector = destinationIcon(destination, selected),
                                    contentDescription = stringResource(destination.labelRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(destination.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // In-App Language Picker Trigger in Rail
                    FilledTonalIconButton(
                        onClick = { shellViewModel.setLanguageDialogVisible(true) }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = stringResource(R.string.action_language)
                        )
                    }
                    Text(
                        text = shellState.currentLanguage.badgeCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = stringResource(R.string.action_theme)
                            )
                        }
                        ThemeSettingsMenu(
                            expanded = showThemeMenu,
                            currentTheme = shellState.themePreference,
                            useDynamicColor = shellState.useDynamicColor,
                            onSelectTheme = { shellViewModel.setThemePreference(it) },
                            onToggleDynamicColor = { shellViewModel.setUseDynamicColor(it) },
                            onDismiss = { showThemeMenu = false }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                VerticalDivider()

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Desktop Workspace Header with Localized Subtitle, Shortcut Hints & Language Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(shellState.activeDestination.labelRes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Keyboard,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.shortcuts_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            TextButton(onClick = { shellViewModel.setLanguageDialogVisible(true) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (shellState.currentLanguage.nativeDisplayName.isNotBlank()) {
                                        shellState.currentLanguage.nativeDisplayName
                                    } else {
                                        stringResource(R.string.action_language)
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    ActiveFeatureWorkspace(
                        destination = shellState.activeDestination,
                        quickLaunchAction = shellState.quickLaunchRequest,
                        onConsumedQuickLaunch = { shellViewModel.consumeQuickLaunch() },
                        archiveViewModel = archiveViewModel,
                        imageStudioViewModel = imageStudioViewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (shellState.showLanguageDialog) {
        LanguagePickerDialog(
            currentLanguage = shellState.currentLanguage,
            onSelectLanguage = { shellViewModel.applyAppLanguage(it) },
            onDismiss = { shellViewModel.setLanguageDialogVisible(false) }
        )
    }
}

@Composable
private fun ActiveFeatureWorkspace(
    destination: AppDestination,
    quickLaunchAction: QuickLaunchAction?,
    onConsumedQuickLaunch: () -> Unit,
    archiveViewModel: ArchiveViewModel,
    imageStudioViewModel: ImageStudioViewModel,
    modifier: Modifier = Modifier
) {
    when (destination) {
        AppDestination.ARCHIVE_MANAGER -> {
            ArchiveManagerScreen(
                viewModel = archiveViewModel,
                triggerOpenArchivePicker = quickLaunchAction == QuickLaunchAction.OPEN_ARCHIVE_PICKER,
                onConsumedQuickLaunch = onConsumedQuickLaunch,
                modifier = modifier
            )
        }
        AppDestination.IMAGE_STUDIO -> {
            ImageStudioScreen(
                viewModel = imageStudioViewModel,
                triggerOpenImagePicker = quickLaunchAction == QuickLaunchAction.OPEN_IMAGE_PICKER,
                onConsumedQuickLaunch = onConsumedQuickLaunch,
                modifier = modifier
            )
        }
    }
}

private fun destinationIcon(destination: AppDestination, selected: Boolean) = when (destination) {
    AppDestination.ARCHIVE_MANAGER -> if (selected) Icons.Filled.FolderZip else Icons.Outlined.FolderZip
    AppDestination.IMAGE_STUDIO -> if (selected) {
        Icons.Filled.PhotoSizeSelectLarge
    } else {
        Icons.Outlined.PhotoSizeSelectLarge
    }
}
