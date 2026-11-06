package com.takeyourbrain.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.PinCheck
import com.takeyourbrain.app.data.PinRepository
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.data.TaskRepository
import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.permissions.PermissionChecker
import com.takeyourbrain.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/** Всё, что показывает экран настроек. */
data class SettingsUiState(
    val warningMinutes: Int = SettingsRepository.DEFAULT_WARNING_MINUTES,
    val passesPerDay: Int = SettingsRepository.DEFAULT_PASSES_PER_DAY,
    val batteryUnrestricted: Boolean = false,
    val bankCap: Int = SettingsRepository.DEFAULT_BANK_CAP,
    val bankWeeklyReset: Boolean = true,
    val bankSpendDays: Set<DayOfWeek> = AppRule.WEEKEND,
    val bankInsideBlockedBuyable: Boolean = false,
    val quietEnabled: Boolean = false,
    val quietStart: Int = SettingsRepository.DEFAULT_QUIET_START,
    val quietEnd: Int = SettingsRepository.DEFAULT_QUIET_END,
    val exactAlarms: Boolean = true,
    val debugDump: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val glass: Boolean = false,
    val userName: String = "",
    val requiredMissing: Boolean = false, // пропало обязательное разрешение — защита не работает
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val pinRepository: PinRepository,
    private val permissionChecker: PermissionChecker,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.userName.collect { name -> _state.update { it.copy(userName = name) } }
        }
        viewModelScope.launch {
            settingsRepository.hapticsEnabled.collect { on -> _state.update { it.copy(haptics = on) } }
        }
        viewModelScope.launch {
            settingsRepository.glassEnabled.collect { on -> _state.update { it.copy(glass = on) } }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode -> _state.update { it.copy(themeMode = mode) } }
        }
        viewModelScope.launch {
            settingsRepository.debugDump.collect { on -> _state.update { it.copy(debugDump = on) } }
        }
        viewModelScope.launch {
            settingsRepository.warningMinutes.collect { minutes -> _state.update { it.copy(warningMinutes = minutes) } }
        }
        viewModelScope.launch {
            settingsRepository.passesPerDay.collect { count -> _state.update { it.copy(passesPerDay = count) } }
        }
        viewModelScope.launch {
            settingsRepository.bankCap.collect { cap -> _state.update { it.copy(bankCap = cap) } }
        }
        viewModelScope.launch {
            settingsRepository.bankWeeklyReset.collect { on -> _state.update { it.copy(bankWeeklyReset = on) } }
        }
        viewModelScope.launch {
            settingsRepository.bankSpendDays.collect { days -> _state.update { it.copy(bankSpendDays = days) } }
        }
        viewModelScope.launch {
            settingsRepository.bankInsideBlockedBuyable.collect { on -> _state.update { it.copy(bankInsideBlockedBuyable = on) } }
        }
        viewModelScope.launch {
            combine(settingsRepository.quietEnabled, settingsRepository.quietStart, settingsRepository.quietEnd, ::Triple)
                .collect { (on, start, end) -> _state.update { it.copy(quietEnabled = on, quietStart = start, quietEnd = end) } }
        }
    }

    /** Тихие часы изменились — напоминания пересчитываются. */
    fun setQuietHours(enabled: Boolean, start: Int, end: Int) {
        viewModelScope.launch {
            settingsRepository.setQuietHours(enabled, start, end)
            taskRepository.rescheduleAll()
        }
    }

    // Настройки банка ослабляют или усиливают защиту, поэтому меняются только после PIN (это проверяет экран)
    fun setBankCap(minutes: Int) {
        viewModelScope.launch { settingsRepository.setBankCap(minutes) }
    }

    fun setBankWeeklyReset(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBankWeeklyReset(enabled) }
    }

    fun setBankSpendDays(days: Set<DayOfWeek>) {
        viewModelScope.launch { settingsRepository.setBankSpendDays(days) }
    }

    fun setBankInsideBlockedBuyable(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBankInsideBlockedBuyable(enabled) }
    }

    suspend fun checkPin(pin: String): PinCheck = pinRepository.check(pin)

    /** Вызывать только после проверки PIN. */
    fun setPassesPerDay(count: Int) {
        viewModelScope.launch { settingsRepository.setPassesPerDay(count) }
    }

    /** Перепроверить, сняты ли ограничения батареи (после возврата из системных настроек). */
    fun refresh() {
        val permissions = permissionChecker.check()
        _state.update {
            it.copy(
                batteryUnrestricted = permissions.batteryUnrestricted,
                exactAlarms = permissions.exactAlarms,
                requiredMissing = !permissions.allRequiredGranted,
            )
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch { settingsRepository.setUserName(name) }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) }
    }

    fun setGlass(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGlassEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDebugDump(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDebugDump(enabled) }
    }

    fun setWarningMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setWarningMinutes(minutes) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TakeYourBrainApp
                SettingsViewModel(
                    app.container.settingsRepository,
                    app.container.pinRepository,
                    app.container.permissionChecker,
                    app.container.taskRepository,
                )
            }
        }
    }
}
