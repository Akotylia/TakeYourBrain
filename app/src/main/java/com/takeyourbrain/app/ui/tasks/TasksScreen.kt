package com.takeyourbrain.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.db.Task
import com.takeyourbrain.app.permissions.SystemSettings
import com.takeyourbrain.app.ui.common.ArrowLoader
import com.takeyourbrain.app.ui.common.CardGroupItem
import com.takeyourbrain.app.ui.common.EmptyState
import com.takeyourbrain.app.ui.common.WarningBanner
import com.takeyourbrain.app.ui.common.clearTopBarColors
import java.time.LocalDateTime

/** Список задач: активные (сработавшие сверху), затем выполненные. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onBack: () -> Unit,
    onOpenTask: (id: Long) -> Unit,
    viewModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Разрешение на точные будильники могли выдать в настройках
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // Отметили повторяющуюся задачу — показываем, когда следующий раз
    var markedNext by remember { mutableStateOf<LocalDateTime?>(null) }
    val nextMessage = markedNext?.let { stringResource(R.string.task_marked_next, whenText(it)) }
    LaunchedEffect(nextMessage) {
        if (nextMessage != null) {
            snackbar.showSnackbar(nextMessage)
            markedNext = null
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = { Text(stringResource(R.string.todo_tasks)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenTask(0L) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.task_new)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ArrowLoader() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp) // запас под кнопку "Новая задача"
        ) {
            if (!state.exactAlarms) {
                item {
                    WarningBanner(
                        text = R.string.tasks_exact_alarms_off,
                        button = R.string.permission_allow,
                        onClick = { SystemSettings.openExactAlarms(context) }
                    )
                }
            }
            if (state.active.isEmpty() && state.done.isEmpty()) {
                item { EmptyState(stringResource(R.string.tasks_empty)) }
            }
            // Задачи — одной карточкой-группой, выполненные — второй
            itemsIndexed(state.active, key = { _, task -> task.id }) { index, task ->
                CardGroupItem(first = index == 0, last = index == state.active.lastIndex) {
                    TaskRow(
                        task = task,
                        onCheck = { viewModel.setDone(task, true) { next -> markedNext = next } },
                        onClick = { onOpenTask(task.id) }
                    )
                }
            }
            if (state.done.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.tasks_done_section, state.done.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearDone) { Text(stringResource(R.string.tasks_clear_done)) }
                    }
                }
                itemsIndexed(state.done, key = { _, task -> task.id }) { index, task ->
                    CardGroupItem(first = index == 0, last = index == state.done.lastIndex) {
                        TaskRow(
                            task = task,
                            onCheck = { viewModel.setDone(task, false) {} },
                            onClick = { onOpenTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}

/** Строка задачи: галочка, название, что с напоминанием. */
@Composable
private fun TaskRow(task: Task, onCheck: () -> Unit, onClick: () -> Unit) {
    val fired = task.firedAt != null && !task.done
    ListItem(
        headlineContent = {
            Text(
                text = task.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.done) TextDecoration.LineThrough else null
            )
        },
        supportingContent = taskStatusText(task)?.let { text ->
            {
                Text(
                    text = text,
                    color = if (fired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = { Checkbox(checked = task.done, onCheckedChange = { onCheck() }) },
        // Фон даёт карточка вокруг строки
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
