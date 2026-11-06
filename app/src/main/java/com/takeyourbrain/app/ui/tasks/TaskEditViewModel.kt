package com.takeyourbrain.app.ui.tasks

import androidx.compose.runtime.getValue
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
import com.takeyourbrain.app.data.TaskRepository
import com.takeyourbrain.app.data.db.Task
import com.takeyourbrain.app.data.rule
import com.takeyourbrain.app.data.withRule
import com.takeyourbrain.app.domain.ReminderRule
import com.takeyourbrain.app.ui.navigation.Routes
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Какое напоминание выбрано на экране. "Через…" при сохранении становится разовым. */
enum class ReminderKind { NONE, ONCE, AFTER, INTERVAL, WEEKLY, MONTHLY }

/** Черновик задачи: то, что сейчас на экране. Время — минуты от полуночи. */
data class TaskDraft(
    val title: String = "",
    val note: String = "",
    val kind: ReminderKind = ReminderKind.NONE,
    val onceDate: LocalDate = nextFullHour().toLocalDate(),
    val onceMinute: Int = nextFullHour().hour * 60,
    val afterMinutes: Int = 60,
    val afterCustom: Boolean = false,   // "Через…" — своё время вместо готового
    val afterCustomText: String = "",
    val afterCustomHours: Boolean = false, // своё время в часах (иначе в минутах)
    val intervalHoursText: String = "2",
    val intervalFromDate: LocalDate = nextFullHour().toLocalDate(),
    val intervalFromMinute: Int = nextFullHour().hour * 60,
    val intervalFromDone: Boolean = false,
    val weekDays: Set<DayOfWeek> = setOf(LocalDate.now().dayOfWeek),
    val weekMinute: Int = 9 * 60,
    val monthDayText: String = LocalDate.now().dayOfMonth.toString(),
    val monthMinute: Int = 9 * 60,
)

/** Ближайший целый час вместе с датой: в 14:20 — 15:00, в 23:20 — завтра 00:00. */
private fun nextFullHour(): LocalDateTime = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS)

class TaskEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val taskId: Long = savedStateHandle[Routes.ARG_TASK_ID] ?: 0L
    val isNew: Boolean get() = taskId == 0L

    var draft by mutableStateOf(TaskDraft())
        private set
    var isLoaded by mutableStateOf(isNew)
        private set
    var errorRes by mutableStateOf<Int?>(null)
        private set
    private var original: Task? = null

    init {
        if (!isNew) {
            viewModelScope.launch {
                taskRepository.get(taskId)?.let {
                    original = it
                    draft = it.toDraft()
                }
                isLoaded = true
            }
        }
    }

    fun edit(change: (TaskDraft) -> TaskDraft) {
        draft = change(draft)
        errorRes = null
    }

    /** Проверить и сохранить. Возвращает true, если сохранено. */
    suspend fun save(): Boolean {
        val d = draft
        val now = LocalDateTime.now()
        val hours = d.intervalHoursText.toIntOrNull()
        val monthDay = d.monthDayText.toIntOrNull()
        val onceAt = d.onceDate.atStartOfDay().plusMinutes(d.onceMinute.toLong())
        // "Через…": готовый вариант или своё число минут/часов
        val afterMinutes = if (d.afterCustom) {
            d.afterCustomText.toIntOrNull()?.let { if (d.afterCustomHours) it * 60 else it }
        } else {
            d.afterMinutes
        }
        errorRes = when {
            d.title.isBlank() -> R.string.task_error_title
            d.kind == ReminderKind.ONCE && !onceAt.isAfter(now) -> R.string.task_error_past
            d.kind == ReminderKind.AFTER && (afterMinutes == null || afterMinutes !in 1..MAX_AFTER_MINUTES) ->
                R.string.task_error_after
            d.kind == ReminderKind.INTERVAL && (hours == null || hours !in 1..168) -> R.string.task_error_interval
            d.kind == ReminderKind.WEEKLY && d.weekDays.isEmpty() -> R.string.task_error_days
            d.kind == ReminderKind.MONTHLY && (monthDay == null || monthDay !in 1..31) -> R.string.task_error_month_day
            else -> null
        }
        if (errorRes != null) return false

        val rule: ReminderRule? = when (d.kind) {
            ReminderKind.NONE -> null
            ReminderKind.ONCE -> ReminderRule.Once(onceAt)
            ReminderKind.AFTER -> ReminderRule.Once(now.plusMinutes((afterMinutes ?: 0).toLong()).withSecond(0).withNano(0))
            ReminderKind.INTERVAL -> ReminderRule.Interval(
                everyMinutes = (hours ?: 1) * 60,
                from = d.intervalFromDate.atStartOfDay().plusMinutes(d.intervalFromMinute.toLong()),
                fromDone = d.intervalFromDone,
            )
            ReminderKind.WEEKLY -> ReminderRule.Weekly(d.weekDays, d.weekMinute)
            ReminderKind.MONTHLY -> ReminderRule.Monthly(monthDay ?: 1, d.monthMinute)
        }
        val base = original ?: Task(title = "", createdAt = System.currentTimeMillis())
        val task = base.copy(title = d.title.trim(), note = d.note.trim()).withRule(rule)
            // Вернули выполненную разовую задачу с новым временем — она снова в работе
            .let { if (it.done && rule != null) it.copy(done = false, doneAt = null) else it }
        taskRepository.save(task)
        return true
    }

    suspend fun delete() {
        if (!isNew) taskRepository.delete(taskId)
    }

    private fun Task.toDraft(): TaskDraft {
        val base = TaskDraft(title = title, note = note)
        return when (val r = rule()) {
            null -> base
            is ReminderRule.Once -> base.copy(
                kind = ReminderKind.ONCE, onceDate = r.at.toLocalDate(), onceMinute = r.at.hour * 60 + r.at.minute,
            )
            is ReminderRule.Interval -> base.copy(
                kind = ReminderKind.INTERVAL,
                intervalHoursText = (r.everyMinutes / 60).toString(),
                intervalFromDate = r.from.toLocalDate(),
                intervalFromMinute = r.from.hour * 60 + r.from.minute,
                intervalFromDone = r.fromDone,
            )
            is ReminderRule.Weekly -> base.copy(kind = ReminderKind.WEEKLY, weekDays = r.days, weekMinute = r.minuteOfDay)
            is ReminderRule.Monthly -> base.copy(
                kind = ReminderKind.MONTHLY, monthDayText = r.dayOfMonth.toString(), monthMinute = r.minuteOfDay,
            )
        }
    }

    companion object {
        /** Варианты "Через…" в минутах. */
        val AFTER_OPTIONS = listOf(10, 30, 60)

        /** "Через…" своё время — не больше недели. */
        const val MAX_AFTER_MINUTES = 7 * 24 * 60

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TakeYourBrainApp).container
                TaskEditViewModel(createSavedStateHandle(), container.taskRepository)
            }
        }
    }
}
