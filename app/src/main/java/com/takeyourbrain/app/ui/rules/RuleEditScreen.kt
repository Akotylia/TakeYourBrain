package com.takeyourbrain.app.ui.rules

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.RulesRepository
import com.takeyourbrain.app.data.db.ScheduleMode
import com.takeyourbrain.app.domain.BlockableElement
import com.takeyourbrain.app.ui.common.AppAlertDialog
import com.takeyourbrain.app.ui.common.ArrowLoader
import com.takeyourbrain.app.ui.common.ArrowRing
import com.takeyourbrain.app.ui.common.CardShape
import com.takeyourbrain.app.ui.common.ChoicePill
import com.takeyourbrain.app.ui.common.TimeSelectDialog
import com.takeyourbrain.app.ui.common.clearTopBarColors
import com.takeyourbrain.app.ui.common.formatCountdown
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatMinutes
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import com.takeyourbrain.app.ui.pin.PinDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle

/** Экран создания и редактирования правила для одного приложения. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    onClose: () -> Unit,
    viewModel: RuleEditViewModel = viewModel(factory = RuleEditViewModel.Factory),
) {
    val draft = viewModel.draft
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val appLabel = viewModel.app?.label ?: viewModel.packageName

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        viewModel.app?.icon?.let { icon ->
                            Image(
                                bitmap = remember(icon) { icon.asImageBitmap() },
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (!viewModel.isLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ArrowLoader() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding() // когда открыта клавиатура, экран становится короче и прокручивается
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.rule_any_condition),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Полная блокировка
            SectionCard {
                SwitchRow(
                    title = R.string.rule_full_block,
                    description = R.string.rule_full_block_desc,
                    checked = draft.fullBlock,
                    onCheckedChange = viewModel::onFullBlockToggle
                )
                val unblockAt = viewModel.unblockAt
                when {
                    unblockAt != null -> PendingUnblock(
                        unblockAt = unblockAt,
                        onCancel = viewModel::cancelUnblock,
                        onTimeReached = viewModel::onUnblockTimeReached
                    )
                    viewModel.fullBlockActive && !draft.fullBlock -> NoteText(R.string.rule_full_block_will_wait)
                    draft.fullBlock -> NoteText(R.string.rule_full_block_note)
                }
            }

            // 2. Дневной лимит
            SectionCard {
                SwitchRow(
                    title = R.string.rule_limit,
                    description = R.string.rule_limit_desc,
                    checked = draft.limitEnabled,
                    onCheckedChange = viewModel::setLimitEnabled
                )
                if (draft.limitEnabled) {
                    val onDone = { focusManager.clearFocus() }
                    if (draft.splitWeekend) {
                        // Один набор таблеток, переключатель сверху выбирает, что меняем: будни или выходные
                        var editingWeekend by rememberSaveable { mutableStateOf(false) }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            DaysSegment(R.string.rule_limit_weekdays, draft.limitMinutesText, !editingWeekend, 0) {
                                editingWeekend = false
                            }
                            DaysSegment(R.string.rule_limit_weekend, draft.weekendLimitText, editingWeekend, 1) {
                                editingWeekend = true
                            }
                        }
                        key(editingWeekend) {
                            if (editingWeekend) {
                                LimitPicker(draft.weekendLimitText, viewModel::setWeekendLimitText, allowNone = true, onDone)
                            } else {
                                LimitPicker(draft.limitMinutesText, viewModel::setLimitText, allowNone = true, onDone)
                            }
                        }
                        NoteText(R.string.rule_limit_empty_hint)
                    } else {
                        LimitPicker(draft.limitMinutesText, viewModel::setLimitText, allowNone = false, onDone)
                    }
                    CheckboxRow(R.string.rule_limit_split, draft.splitWeekend, viewModel::setSplitWeekend)
                    Text(
                        text = stringResource(R.string.rule_used_today, formatDuration(viewModel.usageTodayMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.rule_bank_balance, formatMinutes(viewModel.bankBalance)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    NoteText(R.string.rule_bank_hint)
                }
            }

            // 3. Расписание
            SectionCard {
                Text(stringResource(R.string.rule_schedule), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    RadioRow(R.string.schedule_none, draft.scheduleMode == ScheduleMode.NONE) {
                        viewModel.setScheduleMode(ScheduleMode.NONE)
                    }
                    RadioRow(R.string.schedule_allow_only, draft.scheduleMode == ScheduleMode.ALLOW_ONLY) {
                        viewModel.setScheduleMode(ScheduleMode.ALLOW_ONLY)
                    }
                    RadioRow(R.string.schedule_block_during, draft.scheduleMode == ScheduleMode.BLOCK_DURING) {
                        viewModel.setScheduleMode(ScheduleMode.BLOCK_DURING)
                    }
                }
                if (draft.scheduleMode != ScheduleMode.NONE) {
                    Text(
                        text = stringResource(R.string.schedule_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    draft.intervals.forEachIndexed { index, interval ->
                        IntervalEditor(
                            interval = interval,
                            onChange = { viewModel.updateInterval(index, it) },
                            onDelete = { viewModel.removeInterval(index) }
                        )
                    }
                    OutlinedButton(onClick = viewModel::addInterval) {
                        Text(stringResource(R.string.interval_add))
                    }
                }
            }

            // 4. Части приложения (например, YouTube Shorts) — только там, где это умеем
            if (viewModel.blockableElements.isNotEmpty()) {
                SectionCard {
                    Text(stringResource(R.string.rule_elements), style = MaterialTheme.typography.titleMedium)
                    viewModel.blockableElements.forEach { element ->
                        SwitchRow(
                            title = element.title(),
                            description = element.description(),
                            checked = element.id in draft.elements,
                            onCheckedChange = { viewModel.setElement(element.id, it) }
                        )
                        draft.elements[element.id]?.let { setting ->
                            ElementLimit(
                                setting = setting,
                                usedMs = viewModel.elementUsedMs[element.id] ?: 0L,
                                onLimitedChange = { viewModel.setElementLimited(element.id, it) },
                                onMinutesChange = { viewModel.setElementLimitText(element.id, it) },
                                onDone = { focusManager.clearFocus() }
                            )
                        }
                    }
                }
            }

            viewModel.errorRes?.let { error ->
                Text(
                    text = stringResource(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = { scope.launch { if (viewModel.save()) onClose() } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.rule_save))
            }
            if (viewModel.ruleExists) {
                TextButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.rule_delete))
                }
            }
        }
    }

    // Существующее правило можно менять только после ввода PIN; "Отмена" — выходим с экрана
    if (viewModel.isLoaded && viewModel.ruleExists && !viewModel.pinVerified) {
        PinDialog(
            title = R.string.pin_enter_to_edit,
            onCheck = viewModel::checkPin,
            onSuccess = viewModel::onPinVerified,
            onDismiss = onClose
        )
    }

    if (viewModel.confirmFullBlock) {
        AppAlertDialog(
            onDismissRequest = viewModel::dismissFullBlock,
            icon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
            title = { Text(stringResource(R.string.rule_full_block_confirm_title)) },
            text = { Text(stringResource(R.string.rule_full_block_confirm_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmFullBlock) { Text(stringResource(R.string.rule_full_block_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissFullBlock) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    if (showDeleteDialog) {
        // При действующей полной блокировке правило удалится только после 10 минут ожидания
        val text = if (viewModel.fullBlockActive) R.string.rule_delete_confirm_full else R.string.rule_delete_confirm
        AppAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            text = { Text(stringResource(text, appLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        viewModel.delete()
                        onClose()
                    }
                }) { Text(stringResource(R.string.rule_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

/** "Всегда" или "После лимита": таблетки, а для лимита — тот же выбор минут, что и у дневного лимита. */
@Composable
private fun ElementLimit(
    setting: ElementDraft,
    usedMs: Long,
    onLimitedChange: (Boolean) -> Unit,
    onMinutesChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoicePill(
            text = stringResource(R.string.element_always),
            selected = !setting.limited,
            onClick = { onLimitedChange(false) },
            modifier = Modifier.weight(1f)
        )
        ChoicePill(
            text = stringResource(R.string.element_after_limit),
            selected = setting.limited,
            onClick = { onLimitedChange(true) },
            modifier = Modifier.weight(1f)
        )
    }
    if (setting.limited) {
        LimitPicker(setting.minutesText, onMinutesChange, allowNone = false, onDone = onDone)
        Text(
            text = stringResource(R.string.element_used_today, formatDuration(usedMs)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@StringRes
private fun BlockableElement.title(): Int = when (this) {
    BlockableElement.YOUTUBE_SHORTS -> R.string.element_youtube_shorts
}

@StringRes
private fun BlockableElement.description(): Int = when (this) {
    BlockableElement.YOUTUBE_SHORTS -> R.string.element_youtube_shorts_desc
}

/** Карточка-раздел с отступами внутри. */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** Строка с заголовком, описанием и переключателем. Нажать можно на всю строку. */
@Composable
private fun SwitchRow(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Половинка переключателя "Будни / Выходные": название и выбранный лимит под ним. */
@Composable
private fun SingleChoiceSegmentedButtonRowScope.DaysSegment(
    @StringRes title: Int,
    value: String,
    selected: Boolean,
    index: Int,
    onClick: () -> Unit,
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
        icon = {} // без галочки: так подписи помещаются
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(title), maxLines = 1)
            Text(limitValueText(value), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

/** Строка с галочкой. Нажать можно на всю строку. */
@Composable
private fun CheckboxRow(@StringRes text: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Небольшое пояснение под настройкой. */
@Composable
private fun NoteText(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** "Полная блокировка снимется в 15:42 · 9:12" и кнопка "Не снимать". */
@Composable
private fun PendingUnblock(unblockAt: Long, onCancel: () -> Unit, onTimeReached: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(unblockAt) {
        while (now < unblockAt) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
        onTimeReached()
    }
    val context = LocalContext.current
    val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(unblockAt), ZoneId.systemDefault())
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Стрелка по кругу: сколько осталось из 10 минут ожидания (круг убывает)
            val progress = ((unblockAt - now).toFloat() / RulesRepository.UNBLOCK_DELAY_MS).coerceIn(0f, 1f)
            ArrowRing(
                progress = progress,
                modifier = Modifier.size(64.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rule_unblock_pending, formatTimeOfDay(context, at)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(text = formatCountdown(unblockAt - now), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.rule_unblock_cancel))
                }
            }
        }
    }
}

/** Вариант выбора с кружком (радиокнопкой). */
@Composable
private fun RadioRow(@StringRes text: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Один период расписания: время начала и конца, дни недели, кнопка удаления. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalEditor(
    interval: IntervalDraft,
    onChange: (IntervalDraft) -> Unit,
    onDelete: () -> Unit,
) {
    // Какое время сейчас выбираем в диалоге: true — начало, false — конец, null — диалог закрыт
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    // Язык телефона (экран перерисуется, если его поменять)
    val locale = LocalConfiguration.current.locales[0]

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                TimeButton(R.string.interval_start, formatTimeOfDay(interval.startMinute)) { editingStart = true }
                Spacer(Modifier.width(12.dp))
                TimeButton(R.string.interval_end, formatTimeOfDay(interval.endMinute)) { editingStart = false }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.interval_delete))
                }
            }
            if (interval.crossesMidnight) {
                Text(
                    text = stringResource(R.string.interval_next_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day in interval.days
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val days = if (selected) interval.days - day else interval.days + day
                            onChange(interval.copy(days = days))
                        },
                        // Названия дней берём из системы: "пн", "Mon" — язык подставится сам
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }) }
                    )
                }
            }
        }
    }

    editingStart?.let { isStart ->
        TimeSelectDialog(
            initialMinuteOfDay = if (isStart) interval.startMinute else interval.endMinute,
            onConfirm = { minute ->
                onChange(if (isStart) interval.copy(startMinute = minute) else interval.copy(endMinute = minute))
                editingStart = null
            },
            onDismiss = { editingStart = null }
        )
    }
}

/** Подпись ("Начало") и кнопка со временем под ней. */
@Composable
private fun TimeButton(@StringRes label: Int, time: String, onClick: () -> Unit) {
    Column {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onClick) { Text(time) }
    }
}
