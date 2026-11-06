package com.takeyourbrain.app.block

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.TaskRepository
import com.takeyourbrain.app.data.tasksForToday

/**
 * "Вместо этого — 3 задачи": на экране блокировки показываем дела на сегодня из ежедневника
 * и кнопку, которая открывает список задач. Если дел нет — ничего не показываем.
 */
@Composable
fun TasksSection(taskRepository: TaskRepository, onOpenTasks: () -> Unit) {
    val tasks by taskRepository.tasks.collectAsStateWithLifecycle(initialValue = emptyList())
    // Только дела на сегодня: задачи на другие дни здесь не нужны
    val active = remember(tasks) { tasksForToday(tasks, System.currentTimeMillis()) }
    if (active.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_todo), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = pluralStringResource(R.plurals.block_tasks_title, active.size, active.size),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            active.take(MAX_SHOWN).forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(start = 8.dp, end = 12.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FilledTonalButton(onClick = onOpenTasks, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.block_open_tasks))
            }
        }
    }
}

private const val MAX_SHOWN = 3
