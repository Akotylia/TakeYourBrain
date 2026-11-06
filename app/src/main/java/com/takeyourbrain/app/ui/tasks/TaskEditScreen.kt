package com.takeyourbrain.app.ui.tasks

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.ui.common.AppAlertDialog
import com.takeyourbrain.app.ui.common.ArrowLoader
import com.takeyourbrain.app.ui.common.CardShape
import com.takeyourbrain.app.ui.common.ChoicePill
import com.takeyourbrain.app.ui.common.DialogBlurBehind
import com.takeyourbrain.app.ui.common.IconBadge
import com.takeyourbrain.app.ui.common.TimeSelectDialog
import com.takeyourbrain.app.ui.common.clearTopBarColors
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Экран задачи: название, заметка и напоминание. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(
    onClose: () -> Unit,
    onFocus: (taskId: Long) -> Unit,
    viewModel: TaskEditViewModel = viewModel(factory = TaskEditViewModel.Factory),
) {
    val draft = viewModel.draft
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showDelete by remember { mutableStateOf(false) }
    var showKinds by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = { Text(stringResource(if (viewModel.isNew) R.string.task_new else R.string.task_edit_title)) },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { text -> viewModel.edit { it.copy(title = text) } },
                label = { Text(stringResource(R.string.task_title)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = { text -> viewModel.edit { it.copy(note = text) } },
                label = { Text(stringResource(R.string.task_note)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.task_reminder), style = MaterialTheme.typography.titleMedium)
                    ReminderKindButton(kind = draft.kind, onClick = { showKinds = true })
                    ReminderSettings(draft = draft, onChange = viewModel::edit, onDone = { focusManager.clearFocus() })
                }
            }

            viewModel.errorRes?.let { error ->
                Text(stringResource(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { scope.launch { if (viewModel.save()) onClose() } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.rule_save))
            }
            if (!viewModel.isNew) {
                // Фокус на задаче: отвлекающие приложения блокируются, пока делаешь
                OutlinedButton(onClick = { onFocus(viewModel.taskId) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_focus), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.task_focus))
                }
                TextButton(
                    onClick = { showDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.task_delete))
                }
            }
        }
    }

    if (showKinds) {
        ReminderKindSheet(
            selected = draft.kind,
            onSelect = { kind ->
                viewModel.edit { it.copy(kind = kind) }
                showKinds = false
            },
            onDismiss = { showKinds = false }
        )
    }

    if (showDelete) {
        AppAlertDialog(
            onDismissRequest = { showDelete = false },
            text = { Text(stringResource(R.string.task_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    scope.launch {
                        viewModel.delete()
                        onClose()
                    }
                }) { Text(stringResource(R.string.task_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

@StringRes
private fun ReminderKind.label(): Int = when (this) {
    ReminderKind.NONE -> R.string.reminder_none
    ReminderKind.ONCE -> R.string.reminder_once
    ReminderKind.AFTER -> R.string.reminder_after
    ReminderKind.INTERVAL -> R.string.reminder_interval
    ReminderKind.WEEKLY -> R.string.reminder_weekly
    ReminderKind.MONTHLY -> R.string.reminder_monthly
}

@StringRes
private fun ReminderKind.description(): Int = when (this) {
    ReminderKind.NONE -> R.string.reminder_none_desc
    ReminderKind.ONCE -> R.string.reminder_once_desc
    ReminderKind.AFTER -> R.string.reminder_after_desc
    ReminderKind.INTERVAL -> R.string.reminder_interval_desc
    ReminderKind.WEEKLY -> R.string.reminder_weekly_desc
    ReminderKind.MONTHLY -> R.string.reminder_monthly_desc
}

@DrawableRes
private fun ReminderKind.icon(): Int = when (this) {
    ReminderKind.NONE -> R.drawable.ic_bell_off
    ReminderKind.ONCE -> R.drawable.ic_bell
    ReminderKind.AFTER -> R.drawable.ic_timer
    ReminderKind.INTERVAL -> R.drawable.ic_repeat
    ReminderKind.WEEKLY -> R.drawable.ic_week
    ReminderKind.MONTHLY -> R.drawable.ic_month
}

/** Большая скруглённая кнопка с выбранным видом напоминания; открывает список вариантов. */
@Composable
private fun ReminderKindButton(kind: ReminderKind, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface, // на карточке выделяется
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(kind.icon(), highlighted = kind != ReminderKind.NONE)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(kind.label()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .rotate(90f) // стрелка вниз: откроется список
            )
        }
    }
}

/** Шторка снизу со всеми видами напоминаний: иконка, название, пример. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderKindSheet(selected: ReminderKind, onSelect: (ReminderKind) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        DialogBlurBehind()
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.reminder_choose_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            ReminderKind.entries.forEach { kind ->
                val isSelected = kind == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = isSelected, onClick = { onSelect(kind) }, role = Role.RadioButton)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(kind.icon(), highlighted = isSelected)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(kind.label()), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(kind.description()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}


/** Настройки выбранного вида напоминания. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderSettings(draft: TaskDraft, onChange: ((TaskDraft) -> TaskDraft) -> Unit, onDone: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    when (draft.kind) {
        ReminderKind.NONE -> Hint(R.string.reminder_none_hint)

        ReminderKind.ONCE -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateButton(draft.onceDate) { date -> onChange { it.copy(onceDate = date) } }
            TimeButton(draft.onceMinute) { minute -> onChange { it.copy(onceMinute = minute) } }
        }

        ReminderKind.AFTER -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 10 мин, 30 мин, 1 ч и "Своё"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskEditViewModel.AFTER_OPTIONS.forEach { minutes ->
                    ChoicePill(
                        text = formatDuration(minutes * 60_000L),
                        selected = !draft.afterCustom && draft.afterMinutes == minutes,
                        onClick = { onChange { it.copy(afterMinutes = minutes, afterCustom = false) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                ChoicePill(
                    text = stringResource(R.string.limit_custom),
                    icon = R.drawable.ic_edit,
                    selected = draft.afterCustom,
                    onClick = { onChange { it.copy(afterCustom = true) } },
                    modifier = Modifier.weight(1f)
                )
            }
            if (draft.afterCustom) AfterCustom(draft, onChange, onDone)
        }

        ReminderKind.INTERVAL -> {
            OutlinedTextField(
                value = draft.intervalHoursText,
                onValueChange = { text -> onChange { it.copy(intervalHoursText = text.filter(Char::isDigit).take(3)) } },
                label = { Text(stringResource(R.string.reminder_interval_hours)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.reminder_first_time), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateButton(draft.intervalFromDate) { date -> onChange { it.copy(intervalFromDate = date) } }
                TimeButton(draft.intervalFromMinute) { minute -> onChange { it.copy(intervalFromMinute = minute) } }
            }
            Column(Modifier.selectableGroup()) {
                RadioRow(R.string.reminder_from_start, !draft.intervalFromDone) { onChange { it.copy(intervalFromDone = false) } }
                RadioRow(R.string.reminder_from_done, draft.intervalFromDone) { onChange { it.copy(intervalFromDone = true) } }
            }
        }

        ReminderKind.WEEKLY -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day in draft.weekDays
                    FilterChip(
                        selected = selected,
                        onClick = { onChange { it.copy(weekDays = if (selected) it.weekDays - day else it.weekDays + day) } },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { c -> c.titlecase(locale) }) }
                    )
                }
            }
            TimeButton(draft.weekMinute) { minute -> onChange { it.copy(weekMinute = minute) } }
        }

        ReminderKind.MONTHLY -> {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.monthDayText,
                    onValueChange = { text -> onChange { it.copy(monthDayText = text.filter(Char::isDigit).take(2)) } },
                    label = { Text(stringResource(R.string.reminder_month_day)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    singleLine = true,
                    modifier = Modifier.width(140.dp)
                )
                TimeButton(draft.monthMinute) { minute -> onChange { it.copy(monthMinute = minute) } }
            }
            Hint(R.string.reminder_month_hint)
        }
    }
}

/** "Через…" своё время: число и единица (минуты или часы). Клавиатура открывается сразу. */
@Composable
private fun AfterCustom(draft: TaskDraft, onChange: ((TaskDraft) -> TaskDraft) -> Unit, onDone: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.afterCustomText,
            onValueChange = { text -> onChange { it.copy(afterCustomText = text.filter(Char::isDigit).take(4)) } },
            label = { Text(stringResource(R.string.reminder_after_custom)) },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        ChoicePill(
            text = stringResource(R.string.reminder_unit_minutes),
            selected = !draft.afterCustomHours,
            onClick = { onChange { it.copy(afterCustomHours = false) } },
            horizontalPadding = 16.dp
        )
        ChoicePill(
            text = stringResource(R.string.reminder_unit_hours),
            selected = draft.afterCustomHours,
            onClick = { onChange { it.copy(afterCustomHours = true) } },
            horizontalPadding = 16.dp
        )
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Кнопка с датой; нажатие открывает календарь (прошедшие дни выбрать нельзя). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEdMMM") }
    OutlinedButton(onClick = { open = true }) { Text(date.format(DateTimeFormatter.ofPattern(pattern, locale))) }
    if (open) {
        // Календарь работает с полночью по UTC
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(LocalDate.now())
            }
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        ) {
            DialogBlurBehind()
            DatePicker(state = state)
        }
    }
}

/** Кнопка со временем; нажатие открывает циферблат. */
@Composable
private fun TimeButton(minuteOfDay: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(formatTimeOfDay(minuteOfDay)) }
    if (open) {
        TimeSelectDialog(
            initialMinuteOfDay = minuteOfDay,
            onConfirm = {
                onPick(it)
                open = false
            },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun RadioRow(@StringRes text: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Hint(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
