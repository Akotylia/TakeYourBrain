package com.takeyourbrain.app.ui.focus

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.FocusSession
import com.takeyourbrain.app.ui.common.AppCard
import com.takeyourbrain.app.ui.common.ArrowLoader
import com.takeyourbrain.app.ui.common.ArrowRing
import com.takeyourbrain.app.ui.common.CardGroupItem
import com.takeyourbrain.app.ui.common.ChoicePill
import com.takeyourbrain.app.ui.common.FocusDoneDialog
import com.takeyourbrain.app.ui.common.LocalGlass
import com.takeyourbrain.app.ui.common.SearchPill
import com.takeyourbrain.app.ui.common.TimeSelectDialog
import com.takeyourbrain.app.ui.common.clearTopBarColors
import com.takeyourbrain.app.ui.common.formatCountdown
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import com.takeyourbrain.app.ui.navigation.LocalBottomBarSpace
import com.takeyourbrain.app.ui.pin.PinDialog
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/**
 * Вкладка "Фокус": настройка и запуск, а во время фокуса — обратный отсчёт.
 * Открыт из задачи — есть стрелка "назад" ([onBack]) и фокус запускается на эту задачу.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(onBack: (() -> Unit)? = null, viewModel: FocusViewModel = viewModel(factory = FocusViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Текущее время, обновляется каждую секунду — для отсчёта и чтобы заметить конец фокуса
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val active = state.session?.takeIf { it.isActive(nowMs) }

    // Время вышло прямо на этом экране — "Фокус завершён" (досрочная отмена через PIN сюда не попадает)
    var running by remember { mutableStateOf<FocusSession?>(null) }
    var finished by remember { mutableStateOf<FocusSession?>(null) }
    LaunchedEffect(active) {
        val last = running
        // Настоящее время, а не nowMs: будильник снимает сеанс раньше, чем nowMs успеет обновиться
        if (active == null && last != null && System.currentTimeMillis() >= last.endsAt) finished = last
        running = active
    }
    finished?.let { session -> FocusDoneDialog(session.taskTitle, onDismiss = { finished = null }) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = { Text(stringResource(R.string.nav_focus)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Кнопка "Начать" — над плавающей панелью вкладок (на экране "фокус из задачи" панели нет)
            if (!state.isLoading && active == null) {
                StartBar(viewModel, Modifier.navigationBarsPadding().padding(bottom = LocalBottomBarSpace.current))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding() // когда открыта клавиатура, список становится короче и не прячется под ней
        ) {
            when {
                state.isLoading -> ArrowLoader(Modifier.align(Alignment.Center))
                active != null -> ActiveFocus(active, state.apps, nowMs, viewModel)
                else -> FocusSetup(state.apps, viewModel)
            }
        }
    }
}

/** Идёт фокус: большой отсчёт, иконки заблокированных приложений, досрочное завершение через PIN. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFocus(session: FocusSession, apps: List<FocusAppItem>, nowMs: Long, viewModel: FocusViewModel) {
    var askPin by remember { mutableStateOf(false) }
    val total = (session.endsAt - session.startedAt).coerceAtLeast(1)
    val left = session.endsAt - nowMs
    val endTime = Instant.ofEpochMilli(session.endsAt).atZone(ZoneId.systemDefault()).toLocalTime()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.focus_active_title), style = MaterialTheme.typography.headlineSmall)
        session.taskTitle?.let { title ->
            Text(
                text = stringResource(R.string.focus_on_task, title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
        // Круг показывает, сколько ещё осталось
        ArrowRing(progress = left.toFloat() / total, modifier = Modifier.size(240.dp), strokeWidth = 10.dp) {
            Text(formatCountdown(left), style = MaterialTheme.typography.displaySmall)
        }
        Text(
            text = stringResource(R.string.focus_until, formatTimeOfDay(endTime.hour * 60 + endTime.minute)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.focus_apps_blocked, session.apps.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.filter { it.packageName in session.apps }.forEach { app ->
                Image(
                    bitmap = remember(app.icon) { app.icon.asImageBitmap() },
                    contentDescription = app.label,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        OutlinedButton(onClick = { askPin = true }) { Text(stringResource(R.string.focus_stop)) }
        Spacer(Modifier.height(LocalBottomBarSpace.current))
    }

    if (askPin) {
        PinDialog(
            title = R.string.pin_enter_to_stop_focus,
            onCheck = viewModel::checkPin,
            onSuccess = {
                askPin = false
                viewModel.stop()
            },
            onDismiss = { askPin = false }
        )
    }
}

/** Фокус не идёт: выбор длительности и приложений. */
@Composable
private fun FocusSetup(apps: List<FocusAppItem>, viewModel: FocusViewModel) {
    var pickUntil by remember { mutableStateOf(false) }
    val query = viewModel.query
    val visibleApps = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        viewModel.taskTitle?.let { title ->
            item { TaskBanner(title) }
        }
        item {
            Text(
                text = stringResource(R.string.focus_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            // Длительность — таблетки в карточке (по 4 в ряд) и "До…" на всю ширину
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.focus_duration), style = MaterialTheme.typography.titleMedium)
                FocusViewModel.DURATION_OPTIONS.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            ChoicePill(
                                text = formatDuration(minutes * 60_000L),
                                selected = viewModel.durationMinutes == minutes,
                                onClick = { viewModel.setDuration(minutes) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                val until = viewModel.untilMinuteOfDay
                ChoicePill(
                    text = when {
                        until == null -> stringResource(R.string.focus_until_pick)
                        viewModel.endsTomorrow() -> stringResource(R.string.focus_until_tomorrow, formatTimeOfDay(until))
                        else -> stringResource(R.string.focus_until, formatTimeOfDay(until))
                    },
                    icon = R.drawable.ic_timer,
                    selected = until != null,
                    onClick = { pickUntil = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            SearchPill(
                query = query,
                onQueryChange = { viewModel.query = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            )
        }
        // Приложения — карточка-группа: сверху заголовок с кнопками, ниже строки
        item {
            CardGroupItem(first = true, last = visibleApps.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.focus_apps_selected, viewModel.selected.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::selectAppsWithRules) { Text(stringResource(R.string.focus_select_with_rules)) }
                    TextButton(onClick = viewModel::clearSelection) { Text(stringResource(R.string.focus_clear)) }
                }
            }
        }
        itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
            val checked = app.packageName in viewModel.selected
            CardGroupItem(first = false, last = index == visibleApps.lastIndex) {
                ListItem(
                    headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (app.hasRule) {
                        { Text(stringResource(R.string.focus_has_rule)) }
                    } else {
                        null
                    },
                    leadingContent = {
                        Image(
                            bitmap = remember(app.icon) { app.icon.asImageBitmap() },
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    // Фон даёт карточка вокруг строки
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { viewModel.toggle(app.packageName) }
                )
            }
        }
    }

    if (pickUntil) {
        TimeSelectDialog(
            initialMinuteOfDay = viewModel.untilMinuteOfDay ?: (18 * 60),
            onConfirm = {
                viewModel.setUntil(it)
                pickUntil = false
            },
            onDismiss = { pickUntil = false }
        )
    }
}

/** Нижняя панель с кнопкой "Начать фокус". */
@Composable
private fun StartBar(viewModel: FocusViewModel, modifier: Modifier = Modifier) {
    val durationMinutes = viewModel.durationMinutes
    val until = viewModel.untilMinuteOfDay
    val canStart = viewModel.selected.isNotEmpty() && (durationMinutes != null || until != null)
    // В стеклянном стиле у полосы нет своего фона, иначе она закрывает пятна
    val glass = LocalGlass.current
    Surface(
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
        tonalElevation = if (glass) 0.dp else 3.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (viewModel.selected.isEmpty()) {
                Text(
                    text = stringResource(R.string.focus_choose_app_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Button(onClick = viewModel::start, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        until != null && viewModel.endsTomorrow() ->
                            stringResource(R.string.focus_start_until_tomorrow, formatTimeOfDay(until))
                        until != null -> stringResource(R.string.focus_start_until, formatTimeOfDay(until))
                        durationMinutes != null -> stringResource(R.string.focus_start_for, formatDuration(durationMinutes * 60_000L))
                        else -> stringResource(R.string.focus_start)
                    }
                )
            }
        }
    }
}

/** Карточка сверху: фокус запускается на задачу. */
@Composable
private fun TaskBanner(title: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_todo), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.focus_task_banner), style = MaterialTheme.typography.labelLarge)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
