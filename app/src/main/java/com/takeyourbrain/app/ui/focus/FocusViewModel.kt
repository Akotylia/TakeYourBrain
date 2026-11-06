package com.takeyourbrain.app.ui.focus

import android.content.Context
import android.graphics.Bitmap
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
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.AppsRepository
import com.takeyourbrain.app.data.FocusRepository
import com.takeyourbrain.app.data.FocusSession
import com.takeyourbrain.app.data.PinCheck
import com.takeyourbrain.app.data.PinRepository
import com.takeyourbrain.app.data.RulesRepository
import com.takeyourbrain.app.data.TaskRepository
import com.takeyourbrain.app.domain.ProtectedApps
import com.takeyourbrain.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Приложение в списке выбора для фокуса. */
data class FocusAppItem(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
    val hasRule: Boolean,
)

data class FocusUiState(
    val isLoading: Boolean = true,
    val session: FocusSession? = null, // идущий (или только что закончившийся) сеанс
    val apps: List<FocusAppItem> = emptyList(),
)

class FocusViewModel(
    savedStateHandle: SavedStateHandle,
    private val appContext: Context,
    private val appsRepository: AppsRepository,
    private val rulesRepository: RulesRepository,
    private val focusRepository: FocusRepository,
    private val pinRepository: PinRepository,
    taskRepository: TaskRepository,
) : ViewModel() {

    /** Фокус на задаче: открыли из задачи (иначе null). */
    private val taskId: Long? = savedStateHandle.get<Long>(Routes.ARG_TASK_ID)
    var taskTitle by mutableStateOf<String?>(null)
        private set

    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    // Выбор пользователя храним в Compose-переменных: экран сразу видит изменения
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var query by mutableStateOf("")
    /** Длительность в минутах, или null, если выбрано "до такого-то времени". */
    var durationMinutes by mutableStateOf<Int?>(DEFAULT_DURATION_MINUTES)
        private set
    /** Время окончания (минуты от полуночи), если выбрано "До…". */
    var untilMinuteOfDay by mutableStateOf<Int?>(null)
        private set

    init {
        if (taskId != null) {
            viewModelScope.launch { taskTitle = taskRepository.get(taskId)?.title }
        }
        viewModelScope.launch {
            focusRepository.session.collect { session -> _state.update { it.copy(session = session) } }
        }
        viewModelScope.launch {
            val rules = rulesRepository.rules.first()
            // Неблокируемые (звонилка, настройки и т.п.) в фокус не попадают
            val apps = appsRepository.getLaunchableApps()
                .filterNot { ProtectedApps.isProtected(appContext, it.packageName) }
                .map { FocusAppItem(it.packageName, it.label, it.icon, hasRule = it.packageName in rules) }
            // Отмечаем то, что выбирали в прошлый раз (если эти приложения ещё установлены)
            val installed = apps.map { it.packageName }.toSet()
            selected = focusRepository.lastSelection.first().filter { it in installed }.toSet()
            _state.update { it.copy(isLoading = false, apps = apps) }
        }
    }

    fun toggle(packageName: String) {
        selected = if (packageName in selected) selected - packageName else selected + packageName
    }

    /** Отметить все приложения, для которых есть правила. */
    fun selectAppsWithRules() {
        selected = selected + _state.value.apps.filter { it.hasRule }.map { it.packageName }
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun setDuration(minutes: Int) {
        durationMinutes = minutes
        untilMinuteOfDay = null
    }

    fun setUntil(minuteOfDay: Int) {
        untilMinuteOfDay = minuteOfDay
        durationMinutes = null
    }

    /**
     * Когда закончится фокус (миллисекунды). Для "до 18:30" — ровно в ближайшие 18:30:00
     * (сегодня или завтра), чтобы на экране было именно 18:30, а не 18:29.
     */
    fun endsAt(nowMs: Long = System.currentTimeMillis()): Long? {
        durationMinutes?.let { return nowMs + it * 60_000L }
        val until = untilMinuteOfDay ?: return null
        val now = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
        var end = now.toLocalDate().atStartOfDay(ZoneId.systemDefault()).plusMinutes(until.toLong())
        if (!end.isAfter(now)) end = end.plusDays(1)
        return end.toInstant().toEpochMilli()
    }

    /** Время окончания приходится на завтра (например, сейчас 23:00, а выбрано "до 07:00"). */
    fun endsTomorrow(nowMs: Long = System.currentTimeMillis()): Boolean {
        val end = endsAt(nowMs) ?: return false
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(end).atZone(zone).toLocalDate() != Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    }

    fun start() {
        val end = endsAt() ?: return
        if (selected.isEmpty()) return
        val title = taskTitle
        viewModelScope.launch { focusRepository.start(selected, end, taskId?.takeIf { title != null }, title) }
        // Сбрасываем "до такого-то времени": к следующему разу оно устареет и означало бы "до завтра"
        setDuration(DEFAULT_DURATION_MINUTES)
    }

    suspend fun checkPin(pin: String): PinCheck = pinRepository.check(pin)

    /** Завершить досрочно (вызывать только после проверки PIN). */
    fun stop() {
        viewModelScope.launch { focusRepository.stop() }
    }

    companion object {
        /** Варианты длительности в минутах. */
        val DURATION_OPTIONS = listOf(15, 30, 45, 60, 90, 120, 180, 240)
        const val DEFAULT_DURATION_MINUTES = 60

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TakeYourBrainApp
                val c = app.container
                FocusViewModel(
                    createSavedStateHandle(), app, c.appsRepository, c.rulesRepository, c.focusRepository,
                    c.pinRepository, c.taskRepository,
                )
            }
        }
    }
}
