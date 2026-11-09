package com.arcimage.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arcimage.app.ArcImageApplication
import com.arcimage.app.model.AppDestination
import com.arcimage.app.model.AppLanguage
import com.arcimage.app.model.LocaleController
import com.arcimage.app.model.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ShellUiState(
    val activeDestination: AppDestination = AppDestination.ARCHIVE_MANAGER,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColor: Boolean = true,
    val showLanguageDialog: Boolean = false,
    val currentLanguage: AppLanguage = AppLanguage.SYSTEM,
    val quickLaunchRequest: QuickLaunchAction? = null
)

enum class QuickLaunchAction {
    OPEN_ARCHIVE_PICKER,
    OPEN_IMAGE_PICKER
}

class MainShellViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_DESTINATION = "shell_active_destination"
        private const val KEY_THEME = "shell_theme_preference"
        private const val KEY_DYNAMIC_COLOR = "shell_dynamic_color"
    }

    private val initialDestination = savedStateHandle.get<String>(KEY_DESTINATION)
        ?.let { runCatching { AppDestination.valueOf(it) }.getOrNull() }
        ?: AppDestination.ARCHIVE_MANAGER

    private val initialTheme = savedStateHandle.get<String>(KEY_THEME)
        ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
        ?: ThemePreference.SYSTEM

    private val initialDynamic = savedStateHandle.get<Boolean>(KEY_DYNAMIC_COLOR) ?: true

    private val _uiState = MutableStateFlow(
        ShellUiState(
            activeDestination = initialDestination,
            themePreference = initialTheme,
            useDynamicColor = initialDynamic,
            currentLanguage = LocaleController.getCurrentAppLanguage()
        )
    )
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    fun selectDestination(destination: AppDestination) {
        savedStateHandle[KEY_DESTINATION] = destination.name
        _uiState.update { it.copy(activeDestination = destination) }
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
}

object ArcImageViewModelFactory {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            MainShellViewModel(createSavedStateHandle())
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
    }
}

private fun CreationExtras.arcImageApplication(): ArcImageApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ArcImageApplication)
