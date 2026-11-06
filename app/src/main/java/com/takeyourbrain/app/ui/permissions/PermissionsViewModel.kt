package com.takeyourbrain.app.ui.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.permissions.PermissionChecker
import com.takeyourbrain.app.permissions.PermissionsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionsViewModel(
    private val permissionChecker: PermissionChecker,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(permissionChecker.check())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    /** Перепроверить разрешения (например, после возврата из системных настроек). */
    fun refresh() {
        _state.value = permissionChecker.check()
    }

    /** Запомнить, что помощник пройден, чтобы при следующем запуске сразу открывать список приложений. */
    suspend fun markOnboardingDone() {
        settingsRepository.setOnboardingDone()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TakeYourBrainApp).container
                PermissionsViewModel(container.permissionChecker, container.settingsRepository)
            }
        }
    }
}
