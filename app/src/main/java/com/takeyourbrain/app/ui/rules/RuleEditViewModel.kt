package com.takeyourbrain.app.ui.rules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.R
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.AppsRepository
import com.takeyourbrain.app.data.BankRepository
import com.takeyourbrain.app.data.ElementUsageRepository
import com.takeyourbrain.app.data.InstalledApp
import com.takeyourbrain.app.data.PinCheck
import com.takeyourbrain.app.data.PinRepository
import com.takeyourbrain.app.data.RulesRepository
import com.takeyourbrain.app.data.UsageRepository
import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.data.db.RuleWithIntervals
import com.takeyourbrain.app.data.db.ScheduleInterval
import com.takeyourbrain.app.data.db.ScheduleMode
import com.takeyourbrain.app.data.db.daysToMask
import com.takeyourbrain.app.domain.BlockableElement
import com.takeyourbrain.app.ui.navigation.Routes
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/** Период расписания в форме редактирования. По умолчанию 18:00–20:00 каждый день. */
data class IntervalDraft(
    val startMinute: Int = 18 * 60,
    val endMinute: Int = 20 * 60,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
) {
    /** Период переходит через полночь (например, 23:00–07:00). */
    val crossesMidnight: Boolean get() = endMinute < startMinute
}

/** Заблокированная часть приложения в черновике: всегда или после лимита в день. */
data class ElementDraft(
    val limited: Boolean = false,
    val minutesText: String = DEFAULT_ELEMENT_LIMIT.toString(),
)

/** Лимит на часть приложения (например, Shorts) по умолчанию, минут в день. */
const val DEFAULT_ELEMENT_LIMIT = 15

/** Черновик правила: то, что сейчас на экране, ещё не сохранено. */
data class RuleDraft(
    val fullBlock: Boolean = false,
    val limitEnabled: Boolean = false,
    val limitMinutesText: String = "30",   // лимит на каждый день или (если splitWeekend) на будни
    val splitWeekend: Boolean = false,      // разный лимит в будни и в выходные
    val weekendLimitText: String = "60",    // лимит на выходные (если splitWeekend)
    val scheduleMode: ScheduleMode = ScheduleMode.NONE,
    val intervals: List<IntervalDraft> = emptyList(),
    val elements: Map<String, ElementDraft> = emptyMap(), // заблокированные части приложения (например, YouTube Shorts)
)

class RuleEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val rulesRepository: RulesRepository,
    private val appsRepository: AppsRepository,
    private val usageRepository: UsageRepository,
    private val bankRepository: BankRepository,
    private val pinRepository: PinRepository,
    private val elementUsageRepository: ElementUsageRepository,
) : ViewModel() {

    /** Для какого приложения правило (берётся из адреса экрана). */
    val packageName: String = checkNotNull(savedStateHandle[Routes.ARG_PACKAGE_NAME])

    // На экране есть поле ввода, поэтому храним состояние в Compose-переменных (ввод без задержек)
    var draft by mutableStateOf(RuleDraft())
        private set
    var app by mutableStateOf<InstalledApp?>(null)
        private set
    var usageTodayMs by mutableLongStateOf(0L)
        private set
    var bankBalance by mutableIntStateOf(0)
        private set
    var ruleExists by mutableStateOf(false)
        private set
    /** В сохранённом правиле сейчас действует полная блокировка (её снятие требует ожидания). */
    var fullBlockActive by mutableStateOf(false)
        private set
    /** Когда снимется полная блокировка (если снятие уже назначено), миллисекунды. */
    var unblockAt by mutableStateOf<Long?>(null)
        private set
    var isLoaded by mutableStateOf(false)
        private set
    /** Текст ошибки (id строки) или null, если ошибок нет. */
    var errorRes by mutableStateOf<Int?>(null)
        private set
    /** Введён ли PIN. Нужен только для существующего правила: новое создаётся без PIN. */
    var pinVerified by mutableStateOf(false)
        private set
    /** Показать предупреждение перед включением полной блокировки. */
    var confirmFullBlock by mutableStateOf(false)
        private set

    suspend fun checkPin(pin: String): PinCheck = pinRepository.check(pin)

    fun onPinVerified() {
        pinVerified = true
    }

    init {
        viewModelScope.launch {
            app = appsRepository.getApp(packageName)
            usageTodayMs = usageRepository.getTodayUsage()[packageName] ?: 0L
            // Ожидание снятия могло уже закончиться — сначала доводим его до конца
            rulesRepository.finishPendingUnblocks()
            rulesRepository.getRule(packageName)?.let { loadRule(it) }
            bankBalance = bankRepository.balance(packageName)
            elementUsedMs = blockableElements.associate { it.id to elementUsageRepository.usedToday(it.id) }
            isLoaded = true
        }
    }

    private fun loadRule(rule: RuleWithIntervals) {
        draft = rule.toDraft()
        ruleExists = true
        fullBlockActive = rule.rule.isFullBlockActive(System.currentTimeMillis())
        unblockAt = rule.rule.fullBlockOffAt.takeIf { fullBlockActive }
    }

    /** Переключатель полной блокировки. Перед включением — предупреждение (если она ещё не действует). */
    fun onFullBlockToggle(value: Boolean) {
        if (value && !fullBlockActive) confirmFullBlock = true else edit { it.copy(fullBlock = value) }
    }

    fun confirmFullBlock() {
        confirmFullBlock = false
        edit { it.copy(fullBlock = true) }
    }

    fun dismissFullBlock() {
        confirmFullBlock = false
    }

    /** Передумали снимать полную блокировку: ожидание отменяется сразу. */
    fun cancelUnblock() {
        viewModelScope.launch {
            rulesRepository.cancelUnblock(packageName)
            rulesRepository.getRule(packageName)?.let { loadRule(it) }
        }
    }

    /** Время ожидания вышло: снимаем блокировку и показываем правило таким, каким оно стало. */
    fun onUnblockTimeReached() {
        viewModelScope.launch {
            rulesRepository.finishPendingUnblocks()
            val rule = rulesRepository.getRule(packageName)
            if (rule != null) {
                loadRule(rule)
            } else {
                // В правиле ничего не осталось — оно удалено
                draft = RuleDraft()
                ruleExists = false
                fullBlockActive = false
                unblockAt = null
            }
        }
    }

    fun setLimitEnabled(value: Boolean) = edit { it.copy(limitEnabled = value) }

    /** Включить/выключить блокировку части приложения (например, YouTube Shorts). */
    fun setElement(id: String, blocked: Boolean) =
        edit { it.copy(elements = if (blocked) it.elements + (id to ElementDraft()) else it.elements - id) }

    /** Часть приложения: блокировать всегда ([limited] = false) или после лимита в день. */
    fun setElementLimited(id: String, limited: Boolean) =
        edit { d -> d.copy(elements = d.elements + (id to (d.elements[id] ?: ElementDraft()).copy(limited = limited))) }

    fun setElementLimitText(id: String, text: String) =
        edit { d -> d.copy(elements = d.elements + (id to (d.elements[id] ?: ElementDraft()).copy(minutesText = text.onlyDigits()))) }

    /** Сколько сегодня уже смотрели части приложения (id → миллисекунды), для подписи под лимитом. */
    var elementUsedMs by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    /** Части этого приложения, которые можно заблокировать. */
    val blockableElements: List<BlockableElement> = BlockableElement.forPackage(packageName)

    fun setSplitWeekend(value: Boolean) = edit { it.copy(splitWeekend = value) }

    /** В поле лимита оставляем только цифры (не больше 4). */
    fun setLimitText(text: String) = edit { it.copy(limitMinutesText = text.onlyDigits()) }

    fun setWeekendLimitText(text: String) = edit { it.copy(weekendLimitText = text.onlyDigits()) }

    private fun String.onlyDigits() = filter(Char::isDigit).take(4)

    fun setScheduleMode(mode: ScheduleMode) = edit {
        // При включении расписания сразу добавляем один период, чтобы было что настраивать
        val intervals = if (mode != ScheduleMode.NONE && it.intervals.isEmpty()) listOf(IntervalDraft()) else it.intervals
        it.copy(scheduleMode = mode, intervals = intervals)
    }

    fun addInterval() = edit { it.copy(intervals = it.intervals + IntervalDraft()) }

    fun updateInterval(index: Int, interval: IntervalDraft) = edit {
        it.copy(intervals = it.intervals.mapIndexed { i, old -> if (i == index) interval else old })
    }

    fun removeInterval(index: Int) = edit {
        it.copy(intervals = it.intervals.filterIndexed { i, _ -> i != index })
    }

    private fun edit(change: (RuleDraft) -> RuleDraft) {
        draft = change(draft)
        errorRes = null
    }

    /** Проверить и сохранить правило. Возвращает true, если сохранено. */
    suspend fun save(): Boolean {
        val d = draft
        // Пустое поле — в эти дни лимита нет (только при раздельном лимите)
        val weekday = d.limitMinutesText.toIntOrNull()
        val weekend = if (d.splitWeekend) d.weekendLimitText.toIntOrNull() else weekday
        val limits = listOfNotNull(weekday, weekend)
        val hasSchedule = d.scheduleMode != ScheduleMode.NONE
        val hasCondition = d.fullBlock || d.limitEnabled || hasSchedule || d.elements.isNotEmpty()
        errorRes = when {
            // Всё выключили при действующей полной блокировке — это снятие: правило удалится после ожидания
            !hasCondition && !fullBlockActive -> R.string.error_no_condition
            d.limitEnabled && limits.isEmpty() ->
                if (d.splitWeekend) R.string.error_limit_split_empty else R.string.error_limit_range
            d.limitEnabled && limits.any { it !in 1..1440 } -> R.string.error_limit_range
            d.elements.values.any { it.limited && it.minutesText.toIntOrNull() !in 1..1440 } -> R.string.error_limit_range
            hasSchedule && d.intervals.isEmpty() -> R.string.error_no_intervals
            hasSchedule && d.intervals.any { it.days.isEmpty() } -> R.string.error_no_days
            hasSchedule && d.intervals.any { it.startMinute == it.endMinute } -> R.string.error_same_time
            else -> null
        }
        if (errorRes != null) return false

        val rule = AppRule(
            packageName = packageName,
            fullBlock = d.fullBlock,
            weekdayLimitMinutes = if (d.limitEnabled) weekday else null,
            weekendLimitMinutes = if (d.limitEnabled) weekend else null,
            scheduleMode = d.scheduleMode,
            // "youtube_shorts" — всегда, "youtube_shorts:15" — после 15 минут в день
            blockedElements = d.elements.toSortedMap().map { (id, e) -> if (e.limited) "$id:${e.minutesText.toInt()}" else id }
                .joinToString(","),
        )
        val intervals = if (!hasSchedule) emptyList() else d.intervals.map {
            ScheduleInterval(
                packageName = packageName,
                startMinute = it.startMinute,
                endMinute = it.endMinute,
                daysMask = daysToMask(it.days),
            )
        }
        rulesRepository.saveRule(rule, intervals)
        return true
    }

    suspend fun delete() = rulesRepository.deleteRule(packageName)

    private fun RuleWithIntervals.toDraft(): RuleDraft {
        val weekday = rule.weekdayLimitMinutes
        val weekend = rule.weekendLimitMinutes
        val split = weekday != weekend
        return RuleDraft(
            // Если снятие уже назначено, переключатель показываем выключенным (пользователь его выключил)
            fullBlock = rule.fullBlock && rule.fullBlockOffAt == null,
            limitEnabled = weekday != null || weekend != null,
            limitMinutesText = weekday?.toString() ?: if (split) "" else "30",
            splitWeekend = split,
            weekendLimitText = weekend?.toString() ?: "",
            scheduleMode = rule.scheduleMode,
            intervals = intervals.map { IntervalDraft(it.startMinute, it.endMinute, it.days) },
            elements = rule.elementLimits.mapValues { (_, limit) ->
                if (limit == null) ElementDraft() else ElementDraft(limited = true, minutesText = limit.toString())
            },
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TakeYourBrainApp).container
                RuleEditViewModel(
                    createSavedStateHandle(),
                    container.rulesRepository,
                    container.appsRepository,
                    container.usageRepository,
                    container.bankRepository,
                    container.pinRepository,
                    container.elementUsageRepository,
                )
            }
        }
    }
}
