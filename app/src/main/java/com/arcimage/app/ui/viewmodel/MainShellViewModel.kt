package com.arcimage.app.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arcimage.app.ArcImageApplication
import com.arcimage.app.BuildConfig
import com.arcimage.app.data.update.AppUpdateRepository
import com.arcimage.app.data.update.AppUpdateRepositoryImpl
import com.arcimage.app.data.update.UpdateCheckResult
import com.arcimage.app.model.AppDestination
import com.arcimage.app.model.AppLanguage
import com.arcimage.app.model.LocaleController
import com.arcimage.app.model.ThemePreference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShellUiState(
    val activeDestination: AppDestination = AppDestination.FILES,
    val destinationBackStack: List<AppDestination> = emptyList(),
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColor: Boolean = false,
    val showLanguageDialog: Boolean = false,
    val currentLanguage: AppLanguage = AppLanguage.SYSTEM,
    val quickLaunchRequest: QuickLaunchAction? = null,
    val showUpdateDialog: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val isCheckingUpdate: Boolean = false,
    val updateCheckResult: UpdateCheckResult? = null,
    val currentVersionName: String = BuildConfig.VERSION_NAME
)

enum class QuickLaunchAction {
    OPEN_ARCHIVE_PICKER,
    OPEN_IMAGE_PICKER
}

enum class ShortcutAction {
    OPEN,
    SELECT_ALL,
    DELETE_SELECTED,
    ESCAPE,
    NAVIGATE_UP
}

class MainShellViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val appUpdateRepository: AppUpdateRepository? = null
) : ViewModel() {

    companion object {
        private const val KEY_DESTINATION = "shell_active_destination"
        private const val KEY_BACK_STACK = "shell_destination_back_stack"
        private const val KEY_THEME = "shell_theme_preference"
        private const val KEY_DYNAMIC_COLOR = "shell_dynamic_color"
        private const val MAX_BACK_STACK = 8
    }

    private var startupUpdateChecked = false

    private val initialDestination = savedStateHandle.get<String>(KEY_DESTINATION)
        ?.let { runCatching { AppDestination.valueOf(it) }.getOrNull() }
        ?: AppDestination.FILES

    private val initialBackStack: List<AppDestination> =
        savedStateHandle.get<ArrayList<String>>(KEY_BACK_STACK)
            ?.mapNotNull { runCatching { AppDestination.valueOf(it) }.getOrNull() }
            ?: emptyList()

    private val initialTheme = savedStateHandle.get<String>(KEY_THEME)
        ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
        ?: ThemePreference.SYSTEM

    private val initialDynamic = savedStateHandle.get<Boolean>(KEY_DYNAMIC_COLOR) ?: false

    private val _uiState = MutableStateFlow(
        ShellUiState(
            activeDestination = initialDestination,
            destinationBackStack = initialBackStack,
            themePreference = initialTheme,
            useDynamicColor = initialDynamic,
            currentLanguage = LocaleController.getCurrentAppLanguage(),
            autoCheckUpdates = appUpdateRepository?.isAutoCheckEnabled() ?: true,
            currentVersionName = appUpdateRepository?.currentVersionName ?: BuildConfig.VERSION_NAME
        )
    )
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    private val _shortcutEvents = MutableSharedFlow<ShortcutAction>(extraBufferCapacity = 8)
    val shortcutEvents: SharedFlow<ShortcutAction> = _shortcutEvents.asSharedFlow()

    fun dispatchShortcut(action: ShortcutAction) {
        if (action == ShortcutAction.ESCAPE) {
            _uiState.update { it.copy(showLanguageDialog = false) }
        }
        _shortcutEvents.tryEmit(action)
    }

    init {
        (appUpdateRepository as? AppUpdateRepositoryImpl)?.onPlayUpdateDownloaded = {
            _uiState.update {
                it.copy(updateCheckResult = UpdateCheckResult.PlayStoreUpdateDownloaded)
            }
        }
    }

    fun selectDestination(destination: AppDestination) {
        val current = _uiState.value.activeDestination
        if (destination == current) return
        val nextStack = (_uiState.value.destinationBackStack + current)
            .filterIndexed { idx, item ->
                idx == 0 || item != (_uiState.value.destinationBackStack + current)[idx - 1]
            }
            .takeLast(MAX_BACK_STACK)
        savedStateHandle[KEY_DESTINATION] = destination.name
        savedStateHandle[KEY_BACK_STACK] = ArrayList(nextStack.map { it.name })
        _uiState.update {
            it.copy(
                activeDestination = destination,
                destinationBackStack = nextStack
            )
        }
    }

    /**
     * Pops to the previous destination in history, or returns to [AppDestination.FILES]
     * when currently on another screen. Returns `true` if navigation occurred.
     */
    fun navigateBackDestination(): Boolean {
        val state = _uiState.value
        if (state.destinationBackStack.isNotEmpty()) {
            val previous = state.destinationBackStack.last()
            val remaining = state.destinationBackStack.dropLast(1)
            savedStateHandle[KEY_DESTINATION] = previous.name
            savedStateHandle[KEY_BACK_STACK] = ArrayList(remaining.map { it.name })
            _uiState.update {
                it.copy(
                    activeDestination = previous,
                    destinationBackStack = remaining
                )
            }
            return true
        }
        if (state.activeDestination != AppDestination.FILES) {
            savedStateHandle[KEY_DESTINATION] = AppDestination.FILES.name
            _uiState.update { it.copy(activeDestination = AppDestination.FILES) }
            return true
        }
        return false
    }

    fun setThemePreference(preference: ThemePreference) {
        savedStateHandle[KEY_THEME] = preference.name
        _uiState.update { it.copy(themePreference = preference) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        savedStateHandle[KEY_DYNAMIC_COLOR] = enabled
        _uiState.update { it.copy(useDynamicColor = enabled) }
    }

    fun setLanguageDialogVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                showLanguageDialog = visible,
                currentLanguage = LocaleController.getCurrentAppLanguage()
            )
        }
    }

    fun applyAppLanguage(language: AppLanguage) {
        LocaleController.setAppLanguage(language)
        _uiState.update {
            it.copy(
                currentLanguage = language,
                showLanguageDialog = false
            )
        }
    }

    fun triggerQuickLaunch(action: QuickLaunchAction) {
        val targetDest = when (action) {
            QuickLaunchAction.OPEN_ARCHIVE_PICKER -> AppDestination.ARCHIVE_MANAGER
            QuickLaunchAction.OPEN_IMAGE_PICKER -> AppDestination.IMAGE_STUDIO
        }
        selectDestination(targetDest)
        _uiState.update { it.copy(quickLaunchRequest = action) }
    }

    fun consumeQuickLaunch() {
        _uiState.update { it.copy(quickLaunchRequest = null) }
    }

    // ---------------- Play-Protect-safe Update Controls ----------------

    fun setUpdateDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showUpdateDialog = visible) }
        if (visible && _uiState.value.updateCheckResult == null && !_uiState.value.isCheckingUpdate) {
            checkForUpdatesManually()
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        appUpdateRepository?.setAutoCheckEnabled(enabled)
        _uiState.update { it.copy(autoCheckUpdates = enabled) }
    }

    fun performStartupUpdateCheckIfEnabled() {
        if (startupUpdateChecked) return
        startupUpdateChecked = true
        val repo = appUpdateRepository ?: return
        if (!repo.isAutoCheckEnabled()) return
        viewModelScope.launch {
            val result = repo.checkForUpdates(isManual = false)
            if (result != null) {
                _uiState.update { state ->
                    state.copy(
                        updateCheckResult = result,
                        showUpdateDialog = state.showUpdateDialog || result.hasUpdate
                    )
                }
            }
        }
    }

    fun checkForUpdatesManually() {
        val repo = appUpdateRepository ?: return
        if (_uiState.value.isCheckingUpdate) return
        _uiState.update { it.copy(isCheckingUpdate = true) }
        viewModelScope.launch {
            val result = repo.checkForUpdates(isManual = true)
            _uiState.update {
                it.copy(
                    isCheckingUpdate = false,
                    updateCheckResult = result
                )
            }
        }
    }

    fun launchAvailableUpdate(activity: Activity, result: UpdateCheckResult) {
        appUpdateRepository?.launchUpdateFlow(activity, result)
    }
}

object ArcImageViewModelFactory {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = this.arcImageApplication()
            MainShellViewModel(
                savedStateHandle = createSavedStateHandle(),
                appUpdateRepository = app.container.appUpdateRepository
            )
        }
        initializer {
            val app = this.arcImageApplication()
            ArchiveViewModel(
                archiveRepository = app.container.archiveRepository,
                savedStateHandle = createSavedStateHandle()
            )
        }
        initializer {
            val app = this.arcImageApplication()
            ImageStudioViewModel(
                imageRepository = app.container.imageRepository,
                savedStateHandle = createSavedStateHandle()
            )
        }
        initializer {
            FileBrowserViewModel(
                appContext = this.arcImageApplication().applicationContext,
                savedStateHandle = createSavedStateHandle()
            )
        }
    }
}

private fun CreationExtras.arcImageApplication(): ArcImageApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ArcImageApplication)
