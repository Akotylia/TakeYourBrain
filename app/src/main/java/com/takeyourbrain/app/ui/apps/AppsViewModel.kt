package com.takeyourbrain.app.ui.apps

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.AppSort
import com.takeyourbrain.app.data.AppsRepository
import com.takeyourbrain.app.data.BlockLogRepository
import com.takeyourbrain.app.data.RulesRepository
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.data.StreakRepository
import com.takeyourbrain.app.data.UsageRepository
import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.domain.ProtectedApps
import com.takeyourbrain.app.permissions.PermissionChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Одна строка списка приложений. */
data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
    val installTime: Long,
    val usageTodayMs: Long,
    val isProtected: Boolean, // системное приложение, которое нельзя заблокировать
)

/** Всё, что нужно экрану списка приложений. */
data class AppsUiState(
    val isLoading: Boolean = true,
    val hasUsageAccess: Boolean = true,
    val accessibilityEnabled: Boolean = true, // без службы спец. возможностей блокировка не работает
    val apps: List<AppItem> = emptyList(),
    val rules: Map<String, AppRule> = emptyMap(), // правила по имени пакета
    val blockedSort: AppSort = AppSort.USAGE,     // сортировка раздела "Заблокированные"
    val allSort: AppSort = AppSort.USAGE,         // сортировка раздела "Все приложения"
    val blocksToday: Int = 0,                      // сколько раз сегодня сработала блокировка
    val streak: Int = 0,                           // огонёк: дней подряд без превышений
    val celebrateStreak: Int? = null,              // серия дошла до рубежа (7, 30) — показать праздник
) {
    /** Всё время в приложениях за сегодня (как в статистике). */
    val totalTodayMs: Long get() = apps.sumOf { it.usageTodayMs }
}

class AppsViewModel(
    private val appContext: Context,
    private val appsRepository: AppsRepository,
    private val usageRepository: UsageRepository,
    private val rulesRepository: RulesRepository,
    private val settingsRepository: SettingsRepository,
    private val blockLogRepository: BlockLogRepository,
    private val permissionChecker: PermissionChecker,
    private val streakRepository: StreakRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    // Текст поиска храним в Compose-состоянии: так поле ввода работает без задержек
    var query by mutableStateOf("")
        private set

    private var refreshJob: Job? = null
    private var blocksJob: Job? = null

    init {
        // Правила и выбранные сортировки приходят сами при каждом изменении
        viewModelScope.launch {
            rulesRepository.rules.collect { rules ->
                _state.update { it.copy(rules = rules.mapValues { (_, value) -> value.rule }) }
            }
        }
        viewModelScope.launch {
            settingsRepository.blockedSort.collect { sort -> _state.update { it.copy(blockedSort = sort) } }
        }
        viewModelScope.launch {
            settingsRepository.allSort.collect { sort -> _state.update { it.copy(allSort = sort) } }
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun setBlockedSort(sort: AppSort) {
        viewModelScope.launch { settingsRepository.setBlockedSort(sort) }
    }

    fun setAllSort(sort: AppSort) {
        viewModelScope.launch { settingsRepository.setAllSort(sort) }
    }

    /** Заново загрузить приложения и время использования (при каждом открытии экрана). */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Ожидание снятия полной блокировки могло закончиться, пока приложение было закрыто
            rulesRepository.finishPendingUnblocks()
            // Блокировки за сегодня (подписку обновляем: после полуночи "сегодня" сдвигается)
            blocksJob?.cancel()
            blocksJob = launch {
                blockLogRepository.eventsForLastDays(1).collect { events ->
                    _state.update { it.copy(blocksToday = events.size) }
                }
            }
            val hasAccess = permissionChecker.hasUsageAccess()
            val apps = appsRepository.getLaunchableApps()
            val usage = if (hasAccess) usageRepository.getTodayUsage() else emptyMap()
            val items = apps.map {
                AppItem(
                    packageName = it.packageName,
                    label = it.label,
                    icon = it.icon,
                    installTime = it.installTime,
                    usageTodayMs = usage[it.packageName] ?: 0L,
                    isProtected = ProtectedApps.isProtected(appContext, it.packageName),
                )
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    hasUsageAccess = hasAccess,
                    accessibilityEnabled = permissionChecker.isAccessibilityServiceEnabled(),
                    apps = items,
                )
            }
            val streak = streakRepository.info().current
            val milestone = streakRepository.milestoneToCelebrate(streak)
            _state.update { it.copy(streak = streak, celebrateStreak = milestone) }
        }
    }

    /** Праздник серии показан: этот рубеж больше не празднуем. */
    fun dismissCelebration() {
        val milestone = _state.value.celebrateStreak ?: return
        _state.update { it.copy(celebrateStreak = null) }
        viewModelScope.launch { streakRepository.markCelebrated(milestone) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TakeYourBrainApp).container
                AppsViewModel(
                    this[APPLICATION_KEY] as TakeYourBrainApp,
                    container.appsRepository,
                    container.usageRepository,
                    container.rulesRepository,
                    container.settingsRepository,
                    container.blockLogRepository,
                    container.permissionChecker,
                    container.streakRepository,
                )
            }
        }
    }
}

/** Отсортировать приложения выбранным способом. При равенстве — по названию. */
fun List<AppItem>.sortedFor(sort: AppSort): List<AppItem> {
    val byName = compareBy<AppItem> { it.label.lowercase() }
    return when (sort) {
        AppSort.USAGE -> sortedWith(compareByDescending<AppItem> { it.usageTodayMs }.then(byName))
        AppSort.NAME -> sortedWith(byName)
        AppSort.INSTALL_DATE -> sortedWith(compareByDescending<AppItem> { it.installTime }.then(byName))
    }
}
