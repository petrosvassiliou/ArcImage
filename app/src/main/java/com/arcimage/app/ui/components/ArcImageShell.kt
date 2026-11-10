package com.arcimage.app.ui.components

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.arcimage.app.R
import com.arcimage.app.data.util.DocumentUriHelper
import com.arcimage.app.model.AppDestination
import com.arcimage.app.model.ArchiveFormat
import com.arcimage.app.model.ImageFormat
import com.arcimage.app.ui.archive.ArchiveManagerScreen
import com.arcimage.app.ui.files.FileBrowserScreen
import com.arcimage.app.ui.image.ImageStudioScreen
import com.arcimage.app.ui.util.arcHoverable
import com.arcimage.app.ui.util.rememberArcHoverState
import com.arcimage.app.ui.viewmodel.ArchiveViewModel
import com.arcimage.app.ui.viewmodel.FileBrowserViewModel
import com.arcimage.app.ui.viewmodel.ImageStudioViewModel
import com.arcimage.app.ui.viewmodel.MainShellViewModel
import com.arcimage.app.ui.viewmodel.QuickLaunchAction
import com.arcimage.app.ui.viewmodel.ShortcutAction
import kotlinx.coroutines.flow.Flow

@Composable
fun ArcImageShell(
    shellViewModel: MainShellViewModel,
    archiveViewModel: ArchiveViewModel,
    imageStudioViewModel: ImageStudioViewModel,
    fileBrowserViewModel: FileBrowserViewModel
) {
    val shellState by shellViewModel.uiState.collectAsStateWithLifecycle()
    val fileBrowserState by fileBrowserViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val localView = LocalView.current
    val focusManager = LocalFocusManager.current
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isCompactScreen =
        adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

    var showThemeMenu by rememberSaveable { mutableStateOf(false) }
    var subScreenBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }

    val onBackClick: () -> Unit = {
        if (subScreenBackHandler?.invoke() == true) {
            // Handled by inner detail pane (e.g., Image Studio single-pane inspector)
        } else if (shellState.activeDestination == AppDestination.FILES) {
            when {
                fileBrowserState.selectedPaths.isNotEmpty() -> fileBrowserViewModel.clearSelection()
                fileBrowserState.canNavigateUp -> fileBrowserViewModel.navigateUp()
                !shellViewModel.navigateBackDestination() -> {
                    (context as? Activity)?.moveTaskToBack(true)
                }
            }
        } else {
            if (!shellViewModel.navigateBackDestination()) {
                (context as? Activity)?.moveTaskToBack(true)
            }
        }
    }

    BackHandler(
        enabled = shellState.activeDestination != AppDestination.FILES ||
            shellState.destinationBackStack.isNotEmpty()
    ) {
        onBackClick()
    }

    val themeButton: @Composable () -> Unit = {
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
                onOpenUpdates = { shellViewModel.setUpdateDialogVisible(true) },
                onDismiss = { showThemeMenu = false }
            )
        }
    }

    // Bubble-phase key handler complements MainActivity.dispatchKeyEvent when a Compose widget holds focus
    val shellShortcutModifier = Modifier.onKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown || keyEvent.nativeKeyEvent.repeatCount > 0) {
            return@onKeyEvent false
        }
        val ctrlOrMeta = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
        val isEditingText =
            localView.findFocus()?.onCheckIsTextEditor() == true || localView.onCheckIsTextEditor()

        when {
            ctrlOrMeta && keyEvent.key == Key.O -> {
                shellViewModel.dispatchShortcut(ShortcutAction.OPEN)
                true
            }
            !isEditingText && ctrlOrMeta && keyEvent.key == Key.A -> {
                shellViewModel.dispatchShortcut(ShortcutAction.SELECT_ALL)
                true
            }
            !isEditingText && keyEvent.key == Key.Delete -> {
                shellViewModel.dispatchShortcut(ShortcutAction.DELETE_SELECTED)
                true
            }
            !isEditingText && keyEvent.key == Key.Backspace -> {
                val hasSelection = when (shellViewModel.uiState.value.activeDestination) {
                    AppDestination.FILES -> fileBrowserViewModel.uiState.value.selectedPaths.isNotEmpty()
                    AppDestination.ARCHIVE_MANAGER -> {
                        val s = archiveViewModel.uiState.value
                        s.selectedSourceIds.isNotEmpty() || s.selectedEntryPaths.isNotEmpty()
                    }
                    AppDestination.IMAGE_STUDIO -> imageStudioViewModel.uiState.value.selectedIds.isNotEmpty()
                }
                shellViewModel.dispatchShortcut(
                    if (hasSelection) ShortcutAction.DELETE_SELECTED else ShortcutAction.NAVIGATE_UP
                )
                true
            }
            keyEvent.key == Key.Escape -> {
                focusManager.clearFocus()
                shellViewModel.dispatchShortcut(ShortcutAction.ESCAPE)
                true
            }
            else -> false
        }
    }

    Box(modifier = Modifier.fillMaxSize().then(shellShortcutModifier)) {
        if (isCompactScreen) {
            // =====================================================================
            // COMPACT SCREENS (Android Phones): Back button + brand top bar + Bottom NavigationBar
            // =====================================================================
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShellBackButton(onClick = onBackClick)
                        ArcImageLogo(size = 34.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(shellState.activeDestination.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LanguageFlagDropdown(
                            currentLanguage = shellState.currentLanguage,
                            expanded = shellState.showLanguageDialog,
                            onExpandedChange = { shellViewModel.setLanguageDialogVisible(it) },
                            onSelectLanguage = { shellViewModel.applyAppLanguage(it) }
                        )
                        UpdateStatusButton(
                            hasUpdate = shellState.updateCheckResult?.hasUpdate == true,
                            onClick = { shellViewModel.setUpdateDialogVisible(true) }
                        )
                        themeButton()
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                                label = {
                                    Text(
                                        text = stringResource(destination.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                ActiveFeatureWorkspace(
                    destination = shellState.activeDestination,
                    quickLaunchAction = shellState.quickLaunchRequest,
                    onConsumedQuickLaunch = { shellViewModel.consumeQuickLaunch() },
                    onNavigate = { shellViewModel.selectDestination(it) },
                    onRegisterSubBackHandler = { subScreenBackHandler = it },
                    shortcutEvents = shellViewModel.shortcutEvents,
                    archiveViewModel = archiveViewModel,
                    imageStudioViewModel = imageStudioViewModel,
                    fileBrowserViewModel = fileBrowserViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        } else {
            // =====================================================================
            // EXPANDED SCREENS (Googlebook OS Laptops / Desktops):
            // Navigation Rail with "ArcImage" header + floating rounded workspace panel
            // =====================================================================
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    NavigationRail(
                        containerColor = Color.Transparent,
                        header = {
                            Column(
                                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ArcImageLogo(size = 48.dp)
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(96.dp)
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
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        UpdateStatusButton(
                            hasUpdate = shellState.updateCheckResult?.hasUpdate == true,
                            onClick = { shellViewModel.setUpdateDialogVisible(true) }
                        )
                        themeButton()
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Floating, rounded workspace panel (modern desktop look)
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = 10.dp, end = 10.dp, bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Desktop workspace header: Back button, title, interactive shortcut bar & Country Flag dropdown
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ShellBackButton(onClick = onBackClick)

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(shellState.activeDestination.labelRes),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.app_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                DesktopShortcutBar(
                                    onShortcut = { shellViewModel.dispatchShortcut(it) }
                                )

                                LanguageFlagDropdown(
                                    currentLanguage = shellState.currentLanguage,
                                    expanded = shellState.showLanguageDialog,
                                    onExpandedChange = { shellViewModel.setLanguageDialogVisible(it) },
                                    onSelectLanguage = { shellViewModel.applyAppLanguage(it) }
                                )
                            }

                            ActiveFeatureWorkspace(
                                destination = shellState.activeDestination,
                                quickLaunchAction = shellState.quickLaunchRequest,
                                onConsumedQuickLaunch = { shellViewModel.consumeQuickLaunch() },
                                onNavigate = { shellViewModel.selectDestination(it) },
                                onRegisterSubBackHandler = { subScreenBackHandler = it },
                                shortcutEvents = shellViewModel.shortcutEvents,
                                archiveViewModel = archiveViewModel,
                                imageStudioViewModel = imageStudioViewModel,
                                fileBrowserViewModel = fileBrowserViewModel,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (shellState.showUpdateDialog) {
        AppUpdateDialog(
            currentVersion = shellState.currentVersionName,
            autoCheckEnabled = shellState.autoCheckUpdates,
            isChecking = shellState.isCheckingUpdate,
            lastResult = shellState.updateCheckResult,
            onToggleAutoCheck = { shellViewModel.setAutoCheckUpdates(it) },
            onCheckNow = { shellViewModel.checkForUpdatesManually() },
            onInstallUpdate = { result ->
                (context as? Activity)?.let { activity ->
                    shellViewModel.launchAvailableUpdate(activity, result)
                }
            },
            onDismiss = { shellViewModel.setUpdateDialogVisible(false) }
        )
    }
}

/**
 * Interactive desktop shortcut bar (`[⌨️] Ctrl+O Open • Ctrl+A Select All • Del Remove • Esc Clear`):
 * works both via hardware keyboard shortcuts AND by clicking any of the 4 action pills directly.
 */
@Composable
private fun DesktopShortcutBar(
    onShortcut: (ShortcutAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val rawHint = stringResource(R.string.shortcuts_hint)
    val actions = listOf(
        ShortcutAction.OPEN,
        ShortcutAction.SELECT_ALL,
        ShortcutAction.DELETE_SELECTED,
        ShortcutAction.ESCAPE
    )
    val labels = remember(rawHint) { rawHint.split(" • ") }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Keyboard,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp, end = 4.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (labels.size == actions.size) {
                labels.forEachIndexed { index, segmentLabel ->
                    ShortcutSegmentButton(
                        label = segmentLabel,
                        onClick = { onShortcut(actions[index]) }
                    )
                    if (index < labels.lastIndex) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                ShortcutSegmentButton(
                    label = rawHint,
                    onClick = { onShortcut(ShortcutAction.OPEN) }
                )
            }
        }
    }
}

@Composable
private fun ShortcutSegmentButton(
    label: String,
    onClick: () -> Unit
) {
    val (hoverSource, isHovered) = rememberArcHoverState()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isHovered) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .arcHoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isHovered) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

/** Persistent Back button shown across every screen (Compact & Expanded). */
@Composable
private fun ShellBackButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.size(38.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Full-colour ArcImage brand mark (arched frame + zipper + landscape; intentionally no letters). */
@Composable
fun ArcImageLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_arcimage_logo),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

@Composable
private fun UpdateStatusButton(
    hasUpdate: Boolean,
    onClick: () -> Unit
) {
    Box {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdateAlt,
                contentDescription = stringResource(R.string.action_updates),
                tint = if (hasUpdate) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (hasUpdate) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun ActiveFeatureWorkspace(
    destination: AppDestination,
    quickLaunchAction: QuickLaunchAction?,
    onConsumedQuickLaunch: () -> Unit,
    onNavigate: (AppDestination) -> Unit,
    onRegisterSubBackHandler: (((() -> Boolean)?) -> Unit),
    shortcutEvents: Flow<ShortcutAction>,
    archiveViewModel: ArchiveViewModel,
    imageStudioViewModel: ImageStudioViewModel,
    fileBrowserViewModel: FileBrowserViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    when (destination) {
        AppDestination.FILES -> {
            FileBrowserScreen(
                viewModel = fileBrowserViewModel,
                onOpenArchive = { uri ->
                    archiveViewModel.openArchiveForInspection(uri)
                    onNavigate(AppDestination.ARCHIVE_MANAGER)
                },
                onOpenImages = { uris ->
                    if (uris.isNotEmpty()) {
                        imageStudioViewModel.addImages(uris)
                        onNavigate(AppDestination.IMAGE_STUDIO)
                    }
                },
                onCompress = { uris ->
                    if (uris.isNotEmpty()) {
                        archiveViewModel.prepareCompressionFromFiles(uris)
                        onNavigate(AppDestination.ARCHIVE_MANAGER)
                    }
                },
                onOpenDocuments = { uris ->
                    if (uris.isNotEmpty()) {
                        val allImages = uris.all { uri: Uri ->
                            val name = DocumentUriHelper.resolveDisplayName(context, uri)
                            val mime = DocumentUriHelper.resolveMimeType(context, uri)
                            ImageFormat.isSupportedImage(name, mime) &&
                                ArchiveFormat.detectFromFileName(name, mime) == null
                        }
                        if (allImages) {
                            imageStudioViewModel.addImages(uris)
                            onNavigate(AppDestination.IMAGE_STUDIO)
                        } else {
                            archiveViewModel.handleDroppedOrSharedArchiveUris(uris)
                            onNavigate(AppDestination.ARCHIVE_MANAGER)
                        }
                    }
                },
                shortcutEvents = shortcutEvents,
                modifier = modifier
            )
        }

        AppDestination.ARCHIVE_MANAGER -> {
            ArchiveManagerScreen(
                viewModel = archiveViewModel,
                triggerOpenArchivePicker = quickLaunchAction == QuickLaunchAction.OPEN_ARCHIVE_PICKER,
                onConsumedQuickLaunch = onConsumedQuickLaunch,
                shortcutEvents = shortcutEvents,
                modifier = modifier
            )
        }

        AppDestination.IMAGE_STUDIO -> {
            ImageStudioScreen(
                viewModel = imageStudioViewModel,
                triggerOpenImagePicker = quickLaunchAction == QuickLaunchAction.OPEN_IMAGE_PICKER,
                onConsumedQuickLaunch = onConsumedQuickLaunch,
                onRegisterSubBackHandler = onRegisterSubBackHandler,
                shortcutEvents = shortcutEvents,
                modifier = modifier
            )
        }
    }
}

private fun destinationIcon(destination: AppDestination, selected: Boolean) = when (destination) {
    AppDestination.FILES -> if (selected) Icons.Rounded.Folder else Icons.Outlined.Folder
    AppDestination.ARCHIVE_MANAGER -> if (selected) Icons.Rounded.FolderZip else Icons.Outlined.FolderZip
    AppDestination.IMAGE_STUDIO -> if (selected) {
        Icons.Rounded.PhotoSizeSelectLarge
    } else {
        Icons.Outlined.PhotoSizeSelectLarge
    }
}
