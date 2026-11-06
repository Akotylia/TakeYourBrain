package com.takeyourbrain.app.ui.tasks

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.ui.common.clearTopBarColors

/** Вкладка "Дела": крупные плитки "Задачи" и "Покупки". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    onOpenTasks: () -> Unit,
    onOpenShopping: () -> Unit,
    viewModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(colors = clearTopBarColors(), title = { Text(stringResource(R.string.nav_todo)) }) }
    ) { padding ->
        Row(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val count = state.active.size
            val next = state.nextFireAt
            Tile(
                icon = R.drawable.ic_todo,
                title = stringResource(R.string.todo_tasks),
                subtitle = if (count == 0) {
                    stringResource(R.string.todo_tasks_empty)
                } else {
                    pluralStringResource(R.plurals.todo_tasks_count, count, count)
                },
                detail = next?.let { stringResource(R.string.todo_next, whenText(it)) },
                onClick = onOpenTasks,
                modifier = Modifier.weight(1f)
            )
            Tile(
                icon = R.drawable.ic_cart,
                title = stringResource(R.string.todo_shopping),
                subtitle = if (state.toBuy == 0) {
                    stringResource(R.string.todo_shopping_empty)
                } else {
                    pluralStringResource(R.plurals.todo_shopping_count, state.toBuy, state.toBuy)
                },
                detail = null,
                onClick = onOpenShopping,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Квадратная скруглённая плитка: иконка в кружке, название и подписи. */
@Composable
private fun Tile(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.aspectRatio(1f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.weight(1f))
            Text(text = title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
