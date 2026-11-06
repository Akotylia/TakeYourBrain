package com.takeyourbrain.app.ui.stats

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.AppsRepository
import com.takeyourbrain.app.data.BankRepository
import com.takeyourbrain.app.data.BlockLogRepository
import com.takeyourbrain.app.data.RulesRepository
import com.takeyourbrain.app.data.StreakInfo
import com.takeyourbrain.app.data.StreakRepository
import com.takeyourbrain.app.data.UsageRepository
import com.takeyourbrain.app.domain.RuleEvaluator
import com.takeyourbrain.app.permissions.PermissionChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Время одного приложения за день (или за неделю). */
data class AppUsage(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
    val ms: Long,
)

/** Итоги одного дня. */
data class DayStats(
    val date: LocalDate,
    val totalMs: Long,
    val blocks: Int,
    val apps: List<AppUsage>, // от самого долгого к самому короткому
)

/** Итоги недели: сэкономлено из лимитов, банк, серия. */
data class WeekSummary(
    val savedMinutes: Int,      // сколько осталось неиспользованным от лимитов за прошедшие дни
    val bankEarned: Int,        // пришло в банк
    val bankSpent: Int,         // потрачено из банка
    val streak: StreakInfo,
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val hasUsageAccess: Boolean = true,
    val days: List<DayStats> = emptyList(), // последний — сегодня
    val week: WeekSummary? = null,
) {
    val weekTotalMs: Long get() = days.sumOf { it.totalMs }
    val weekBlocks: Int get() = days.sumOf { it.blocks }

    /** Среднее за день. Считаем только дни, за которые есть данные (например, телефон мог быть новым). */
    val dailyAverageMs: Long
        get() {
            val withData = days.count { it.totalMs > 0 }
            return if (withData == 0) 0 else weekTotalMs / withData
        }

    /** Приложения за все 7 дней вместе, от самого долгого. */
    val weekApps: List<AppUsage>
        get() = days.flatMap { it.apps }
            .groupBy { it.packageName }
            .map { (_, list) -> list.first().copy(ms = list.sumOf { it.ms }) }
            .sortedByDescending { it.ms }
}

class StatsViewModel(
    private val appsRepository: AppsRepository,
    private val usageRepository: UsageRepository,
    private val blockLogRepository: BlockLogRepository,
    private val permissionChecker: PermissionChecker,
    private val rulesRepository: RulesRepository,
    private val bankRepository: BankRepository,
    private val streakRepository: StreakRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    /** true — показываем итоги за 7 дней, false — за один день [selectedDay]. */
    var weekMode by mutableStateOf(false)
        private set
    /** Номер выбранного дня в списке (последний — сегодня). */
    var selectedDay by mutableIntStateOf(DAYS - 1)
        private set

    // Время по дням (без блокировок) и число блокировок по датам: собираем вместе в rebuild()
    private var usageDays: List<Pair<LocalDate, List<AppUsage>>> = emptyList()
    private var blocksByDate: Map<LocalDate, Int> = emptyMap()

    private var refreshJob: Job? = null
    private var blocksJob: Job? = null

    fun selectDay(index: Int) {
        selectedDay = index
        weekMode = false
    }

    fun showWeek(week: Boolean) {
        weekMode = week
    }

    /** Заново посчитать статистику (при каждом открытии экрана). */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val hasAccess = permissionChecker.hasUsageAccess()
            // Считаем только приложения с иконкой в меню — как на вкладке "Приложения"
            val apps = appsRepository.getLaunchableApps().associateBy { it.packageName }
            // Без доступа к статистике времени нет, но дни (и блокировки по ним) всё равно показываем
            val byDay = if (hasAccess) {
                usageRepository.getUsageByDay(DAYS)
            } else {
                (DAYS - 1 downTo 0).map { LocalDate.now().minusDays(it.toLong()) to emptyMap() }
            }
            usageDays = byDay.map { (date, usage) ->
                date to usage.mapNotNull { (pkg, ms) ->
                    val app = apps[pkg] ?: return@mapNotNull null
                    if (ms < 1_000) null else AppUsage(pkg, app.label, app.icon, ms)
                }.sortedByDescending { it.ms }
            }
            _state.update { it.copy(isLoading = false, hasUsageAccess = hasAccess) }
            rebuild()
            _state.update { it.copy(week = weekSummary(byDay)) }
        }
        // Подписку на блокировки тоже перезапускаем: после полуночи "последние 7 дней" сдвигаются
        blocksJob?.cancel()
        blocksJob = viewModelScope.launch {
            blockLogRepository.eventsForLastDays(DAYS).collect { events ->
                val zone = ZoneId.systemDefault()
                blocksByDate = events.groupingBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }.eachCount()
                rebuild()
            }
        }
    }

    /** Итоги недели. Сэкономленное считаем только за прошедшие дни: сегодняшний ещё не закончился. */
    private suspend fun weekSummary(byDay: List<Pair<LocalDate, Map<String, Long>>>): WeekSummary {
        val rules = rulesRepository.rules.first()
        val today = LocalDate.now()
        val saved = byDay.filter { (date, _) -> date.isBefore(today) }.sumOf { (date, usage) ->
            rules.values.sumOf { rule ->
                val earnable = RuleEvaluator.earnableMinutes(rule, date) ?: return@sumOf 0
                val used = ((usage[rule.rule.packageName] ?: 0L) / 60_000).toInt()
                (earnable - used).coerceAtLeast(0)
            }
        }
        val zone = ZoneId.systemDefault()
        val from = today.minusDays(DAYS - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val (earned, spent) = bankRepository.totalsBetween(from, System.currentTimeMillis())
        return WeekSummary(saved, earned, spent.coerceAtLeast(0), streakRepository.info())
    }

    private fun rebuild() {
        val days = usageDays.map { (date, apps) ->
            DayStats(date, apps.sumOf { it.ms }, blocksByDate[date] ?: 0, apps)
        }
        _state.update { it.copy(days = days) }
    }

    companion object {
        /** Сколько дней показываем. */
        const val DAYS = 7

        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as TakeYourBrainApp).container
                StatsViewModel(
                    c.appsRepository, c.usageRepository, c.blockLogRepository, c.permissionChecker,
                    c.rulesRepository, c.bankRepository, c.streakRepository,
                )
            }
        }
    }
}
