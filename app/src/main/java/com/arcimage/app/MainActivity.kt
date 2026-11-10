package com.arcimage.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcimage.app.data.util.DocumentUriHelper
import com.arcimage.app.model.AppDestination
import com.arcimage.app.model.ArchiveFormat
import com.arcimage.app.model.ImageFormat
import com.arcimage.app.ui.components.ArcImageShell
import com.arcimage.app.ui.theme.ArcImageTheme
import com.arcimage.app.ui.viewmodel.ArcImageViewModelFactory
import com.arcimage.app.ui.viewmodel.ArchiveViewModel
import com.arcimage.app.ui.viewmodel.FileBrowserViewModel
import com.arcimage.app.ui.viewmodel.ImageStudioViewModel
import com.arcimage.app.ui.viewmodel.MainShellViewModel
import com.arcimage.app.ui.viewmodel.QuickLaunchAction
import com.arcimage.app.ui.viewmodel.ShortcutAction

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_QUICK_EXTRACT = "com.arcimage.app.action.QUICK_EXTRACT"
        const val ACTION_QUICK_IMAGE = "com.arcimage.app.action.QUICK_IMAGE"
    }

    private val shellViewModel: MainShellViewModel by viewModels { ArcImageViewModelFactory.Factory }
    private val archiveViewModel: ArchiveViewModel by viewModels { ArcImageViewModelFactory.Factory }
    private val imageStudioViewModel: ImageStudioViewModel by viewModels { ArcImageViewModelFactory.Factory }
    private val fileBrowserViewModel: FileBrowserViewModel by viewModels { ArcImageViewModelFactory.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Only process initial launch intent once so SavedStateHandle continuity isn't overwritten on resize/recreate
        if (savedInstanceState == null) {
            handleIncomingSystemIntent(intent)
        }

        // Play-Protect-safe startup update check (respects user toggle & 12h cooldown)
        shellViewModel.performStartupUpdateCheckIfEnabled()

        setContent {
            val shellState by shellViewModel.uiState.collectAsStateWithLifecycle()

            ArcImageTheme(
                themePreference = shellState.themePreference,
                dynamicColor = shellState.useDynamicColor
            ) {
                ArcImageShell(
                    shellViewModel = shellViewModel,
                    archiveViewModel = archiveViewModel,
                    imageStudioViewModel = imageStudioViewModel,
                    fileBrowserViewModel = fileBrowserViewModel
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isActionDown = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed

        // Ctrl+O / Cmd+O always opens the active screen's file picker regardless of focus
        if (isActionDown && ctrlOrMeta && event.keyCode == KeyEvent.KEYCODE_O) {
            shellViewModel.dispatchShortcut(ShortcutAction.OPEN)
            return true
        }

        if (super.dispatchKeyEvent(event)) {
            return true
        }

        if (isActionDown) {
            val focusedView = window?.decorView?.findFocus()
            val isEditingText = focusedView?.onCheckIsTextEditor() == true
            when {
                !isEditingText && ctrlOrMeta && event.keyCode == KeyEvent.KEYCODE_A -> {
                    shellViewModel.dispatchShortcut(ShortcutAction.SELECT_ALL)
                    return true
                }
                !isEditingText && event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL -> {
                    shellViewModel.dispatchShortcut(ShortcutAction.DELETE_SELECTED)
                    return true
                }
                !isEditingText && event.keyCode == KeyEvent.KEYCODE_DEL -> {
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
                    return true
                }
                event.keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                    focusedView?.clearFocus()
                    shellViewModel.dispatchShortcut(ShortcutAction.ESCAPE)
                    return true
                }
            }
        }
        return false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingSystemIntent(intent)
    }

    private fun handleIncomingSystemIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            ACTION_QUICK_EXTRACT -> {
                shellViewModel.triggerQuickLaunch(QuickLaunchAction.OPEN_ARCHIVE_PICKER)
            }

            ACTION_QUICK_IMAGE -> {
                shellViewModel.triggerQuickLaunch(QuickLaunchAction.OPEN_IMAGE_PICKER)
            }

            Intent.ACTION_VIEW -> {
                val dataUri = intent.data ?: return
                routeIncomingUris(listOf(dataUri), explicitMimeType = intent.type)
            }

            Intent.ACTION_SEND -> {
                val sharedUri = extractParcelableUri(intent, Intent.EXTRA_STREAM)
                    ?: extractClipDataUris(intent).firstOrNull()
                    ?: return
                routeIncomingUris(listOf(sharedUri), explicitMimeType = intent.type)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val sharedUris = extractParcelableUriList(intent, Intent.EXTRA_STREAM)
                    .ifEmpty { extractClipDataUris(intent) }
                if (sharedUris.isNotEmpty()) {
                    routeIncomingUris(sharedUris, explicitMimeType = intent.type)
                }
            }
        }
    }

    private fun routeIncomingUris(uris: List<Uri>, explicitMimeType: String?) {
        if (uris.isEmpty()) return

        val allAreImages = uris.all { uri ->
            val name = DocumentUriHelper.resolveDisplayName(this, uri)
            val mime = explicitMimeType ?: DocumentUriHelper.resolveMimeType(this, uri)
            ImageFormat.isSupportedImage(name, mime) && ArchiveFormat.detectFromFileName(name, mime) == null
        }

        if (allAreImages) {
            shellViewModel.selectDestination(AppDestination.IMAGE_STUDIO)
            imageStudioViewModel.addImages(uris)
        } else {
            shellViewModel.selectDestination(AppDestination.ARCHIVE_MANAGER)
            archiveViewModel.handleDroppedOrSharedArchiveUris(uris)
        }
    }

    private fun extractParcelableUri(intent: Intent, key: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }

    private fun extractParcelableUriList(intent: Intent, key: String): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(key) ?: emptyList()
        }
    }

    private fun extractClipDataUris(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        val result = mutableListOf<Uri>()
        for (i in 0 until clip.itemCount) {
            clip.getItemAt(i)?.uri?.let { result += it }
        }
        return result
    }
}
