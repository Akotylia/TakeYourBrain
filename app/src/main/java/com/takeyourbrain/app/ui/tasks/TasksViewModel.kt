package com.takeyourbrain.app.ui.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.alarms.Alarms
import com.takeyourbrain.app.data.ShoppingRepository
import com.takeyourbrain.app.data.TaskRepository
import com.takeyourbrain.app.data.db.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class TasksUiState(
    val isLoading: Boolean = true,
    val active: List<Task> = emptyList(), // сначала сработавшие, потом по времени напоминания
    val done: List<Task> = emptyList(),   // выполненные разовые, новые сверху
    val exactAlarms: Boolean = true,      // разрешены ли точные будильники
    val toBuy: Int = 0,                   // сколько покупок во всех списках (для плитки "Покупки")
) {
    /** Ближайшее напоминание среди всех задач. */
    val nextFireAt: Long? get() = active.mapNotNull { it.nextFireAt }.minOrNull()
}

class TasksViewModel(
    private val appContext: Context,
    private val taskRepository: TaskRepository,
    shoppingRepository: ShoppingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            taskRepository.tasks.collect { all ->
                val active = all.filterNot { it.done }.sortedWith(
                    compareByDescending<Task> { it.firedAt != null }
                        .thenBy { it.nextFireAt ?: Long.MAX_VALUE }
                        .thenByDescending { it.createdAt }
                )
                val done = all.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }
                _state.update { it.copy(isLoading = false, active = active, done = done) }
            }
        }
        viewModelScope.launch {
            shoppingRepository.toBuyCount.collect { count -> _state.update { it.copy(toBuy = count) } }
        }
    }

    /** Перепроверить разрешение на точные будильники (при возврате на экран). */
    fun refresh() {
        _state.update { it.copy(exactAlarms = Alarms.canScheduleExact(appContext)) }
    }

    /** Отметить/снять отметку. Для повторяющейся задачи [onNext] получит следующее напоминание. */
    fun setDone(task: Task, done: Boolean, onNext: (LocalDateTime) -> Unit) {
        viewModelScope.launch {
            taskRepository.setDone(task.id, done)?.let(onNext)
        }
    }

    fun clearDone() {
        viewModelScope.launch { taskRepository.deleteDone() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TakeYourBrainApp
                TasksViewModel(app, app.container.taskRepository, app.container.shoppingRepository)
            }
        }
    }
}
