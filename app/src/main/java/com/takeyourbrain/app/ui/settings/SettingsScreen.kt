package com.takeyourbrain.app.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.PassRepository
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.domain.BankPricing
import com.takeyourbrain.app.permissions.SystemSettings
import com.takeyourbrain.app.ui.common.AppAlertDialog
import com.takeyourbrain.app.ui.common.CardShape
import com.takeyourbrain.app.ui.common.SettingsRow
import com.takeyourbrain.app.ui.common.TimeSelectDialog
import com.takeyourbrain.app.ui.common.WarningBanner
import com.takeyourbrain.app.ui.common.clearTopBarColors
import com.takeyourbrain.app.ui.common.daysText
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import com.takeyourbrain.app.ui.pin.PinDialog
import com.takeyourbrain.app.ui.theme.ThemeMode
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * Настройки: группы в отдельных карточках (Основное, Внешний вид, Защита, Банк, Напоминания, Батарея),
 * у каждой строки иконка в квадратике. Редкое — на экране "Дополнительно".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenMore: () -> Unit,
    onChangePin: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Изменение настройки, которое выполнится после ввода PIN (null — окно PIN закрыто)
    var pendingChange by remember { mutableStateOf<(() -> Unit)?>(null) }
    var askPinForChange by remember { mutableStateOf(false) }
    var showSpendDays by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }

    // После возврата из системных настроек обновляем статус батареи и разрешений
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Пропало обязательное разрешение — защита не работает, это видно сразу
            if (state.requiredMissing) {
                WarningBanner(text = R.string.settings_permissions_missing, button = R.string.apps_turn_on, onClick = onOpenPermissions)
            }

            SettingsGroup(R.string.settings_section_main) {
                SettingsRow(
                    icon = R.drawable.ic_profile,
                    title = stringResource(R.string.settings_name),
                    subtitle = stringResource(R.string.settings_name_desc),
                    onClick = { showName = true },
                    trailing = { ValueText(state.userName.ifBlank { stringResource(R.string.settings_name_empty) }) }
                )
                RowDivider()
                LanguageItem()
            }

            SettingsGroup(R.string.settings_section_appearance) {
                ChoiceRow(
                    icon = R.drawable.ic_theme,
                    title = stringResource(R.string.settings_theme),
                    value = stringResource(state.themeMode.label),
                    options = ThemeMode.entries,
                    selected = state.themeMode,
                    optionText = { stringResource(it.label) },
                    onChange = viewModel::setThemeMode
                )
                RowDivider()
                SwitchRow(R.drawable.ic_glass, R.string.settings_glass, R.string.settings_glass_desc, state.glass) {
                    viewModel.setGlass(it)
                }
                RowDivider()
                SwitchRow(R.drawable.ic_vibration, R.string.settings_haptics, R.string.settings_haptics_desc, state.haptics) {
                    viewModel.setHaptics(it)
                }
            }

            SettingsGroup(R.string.settings_section_protection) {
                ChoiceRow(
                    icon = R.drawable.ic_bell,
                    title = stringResource(R.string.settings_warning_time),
                    value = stringResource(R.string.settings_warning_minutes, state.warningMinutes),
                    options = SettingsRepository.WARNING_OPTIONS,
                    selected = state.warningMinutes,
                    optionText = { stringResource(R.string.settings_warning_minutes, it) },
                    onChange = viewModel::setWarningMinutes
                )
                RowDivider()
                val passMinutes = (PassRepository.PASS_DURATION_MS / 60_000).toInt()
                ChoiceRow(
                    icon = R.drawable.ic_pass,
                    title = stringResource(R.string.settings_passes),
                    subtitle = stringResource(R.string.settings_passes_desc, passMinutes),
                    value = state.passesPerDay.toString(),
                    options = SettingsRepository.PASSES_OPTIONS,
                    selected = state.passesPerDay,
                    optionText = { it.toString() },
                    // Пропуски и банк влияют на силу защиты, поэтому меняем их только после PIN
                    onChange = { count -> if (count != state.passesPerDay) pendingChange = { viewModel.setPassesPerDay(count) } }
                )
                RowDivider()
                SettingsRow(
                    icon = R.drawable.ic_key,
                    title = stringResource(R.string.settings_change_pin),
                    subtitle = stringResource(R.string.settings_change_pin_desc),
                    onClick = { askPinForChange = true },
                    trailing = { Chevron() }
                )
            }

            SettingsGroup(R.string.settings_section_bank) {
                ChoiceRow(
                    icon = R.drawable.ic_bank,
                    title = stringResource(R.string.settings_bank_cap),
                    value = formatDuration(state.bankCap * 60_000L),
                    options = SettingsRepository.BANK_CAP_OPTIONS,
                    selected = state.bankCap,
                    optionText = { formatDuration(it * 60_000L) },
                    onChange = { cap -> if (cap != state.bankCap) pendingChange = { viewModel.setBankCap(cap) } }
                )
                RowDivider()
                SettingsRow(
                    icon = R.drawable.ic_week,
                    title = stringResource(R.string.settings_bank_spend_days),
                    onClick = { showSpendDays = true },
                    trailing = { ValueText(daysText(state.bankSpendDays)) }
                )
                RowDivider()
                SwitchRow(
                    R.drawable.ic_repeat, R.string.settings_bank_weekly_reset,
                    R.string.settings_bank_weekly_reset_desc, state.bankWeeklyReset
                ) { value -> pendingChange = { viewModel.setBankWeeklyReset(value) } }
                RowDivider()
                SettingsRow(
                    icon = R.drawable.ic_lock,
                    title = stringResource(R.string.settings_bank_buy_blocked),
                    subtitle = pluralStringResource(
                        R.plurals.settings_bank_buy_blocked_desc, BankPricing.INSIDE_BLOCKED, BankPricing.INSIDE_BLOCKED
                    ),
                    onClick = {
                        val value = !state.bankInsideBlockedBuyable
                        pendingChange = { viewModel.setBankInsideBlockedBuyable(value) }
                    },
                    trailing = { Switch(checked = state.bankInsideBlockedBuyable, onCheckedChange = null) }
                )
            }

            SettingsGroup(R.string.settings_section_reminders) {
                SwitchRow(R.drawable.ic_moon, R.string.settings_quiet_hours, R.string.settings_quiet_hours_desc, state.quietEnabled) {
                    viewModel.setQuietHours(it, state.quietStart, state.quietEnd)
                }
                if (state.quietEnabled) {
                    QuietHoursRow(
                        start = state.quietStart,
                        end = state.quietEnd,
                        onChange = { start, end -> viewModel.setQuietHours(true, start, end) }
                    )
                }
                RowDivider()
                SettingsRow(
                    icon = R.drawable.ic_timer,
                    title = stringResource(R.string.settings_exact_alarms),
                    subtitle = stringResource(
                        if (state.exactAlarms) R.string.settings_exact_alarms_on else R.string.settings_exact_alarms_off
                    ),
                    onClick = { SystemSettings.openExactAlarms(context) },
                    trailing = { Chevron() }
                )
            }

            SettingsGroup(R.string.settings_section_battery) {
                BatteryGroup(
                    unrestricted = state.batteryUnrestricted,
                    onAllow = { SystemSettings.openBatteryOptimization(context) },
                    onOpenBattery = { SystemSettings.openSamsungBattery(context) }
                )
            }

            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                SettingsRow(
                    icon = R.drawable.ic_info,
                    title = stringResource(R.string.settings_more),
                    subtitle = stringResource(R.string.settings_more_desc),
                    onClick = onOpenMore,
                    trailing = { Chevron() }
                )
            }
        }
    }

    pendingChange?.let { change ->
        PinDialog(
            title = R.string.pin_enter_to_change_setting,
            onCheck = viewModel::checkPin,
            onSuccess = {
                change()
                pendingChange = null
            },
            onDismiss = { pendingChange = null }
        )
    }
    if (showSpendDays) {
        SpendDaysDialog(
            initial = state.bankSpendDays,
            onConfirm = { days ->
                showSpendDays = false
                if (days != state.bankSpendDays) pendingChange = { viewModel.setBankSpendDays(days) }
            },
            onDismiss = { showSpendDays = false }
        )
    }
    if (askPinForChange) {
        PinDialog(
            title = R.string.pin_enter_current,
            onCheck = viewModel::checkPin,
            onSuccess = {
                askPinForChange = false
                onChangePin()
            },
            onDismiss = { askPinForChange = false }
        )
    }
    if (showName) {
        NameDialog(
            initial = state.userName,
            onSave = { name ->
                viewModel.setUserName(name)
                showName = false
            },
            onDismiss = { showName = false }
        )
    }
}

/** Группа настроек: подпись цветом акцента над карточкой, внутри — строки. */
@Composable
private fun SettingsGroup(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        content = content
    )
}

/** Тонкая линия между строками одной карточки (начинается после иконки). */
@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ValueText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Chevron() {
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
}

/** Строка с переключателем: нажать можно на всю строку. */
@Composable
private fun SwitchRow(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsRow(
        icon = icon,
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) }
    )
}

/** Строка с текущим значением справа; меню с вариантами открывается рядом со строкой. */
@Composable
private fun <T> ChoiceRow(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    options: List<T>,
    selected: T,
    optionText: @Composable (T) -> String,
    onChange: (T) -> Unit,
    subtitle: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        SettingsRow(icon = icon, title = title, subtitle = subtitle, onClick = { menuOpen = true }, trailing = { ValueText(value) })
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    onClick = {
                        menuOpen = false
                        onChange(option)
                    },
                    // Галочка у выбранного варианта
                    leadingIcon = {
                        if (option == selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                    }
                )
            }
        }
    }
}

/** "Язык: Русский". После смены система сама перезапускает экраны на новом языке. */
@Composable
private fun LanguageItem() {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    ChoiceRow(
        icon = R.drawable.ic_language,
        title = stringResource(R.string.settings_language),
        value = stringResource(current.label),
        options = AppLanguage.entries,
        selected = current,
        optionText = { stringResource(it.label) },
        onChange = { language -> if (language != current) AppLanguage.set(context, language) }
    )
}

/** "С 23:00 до 07:00" — две кнопки, каждая открывает циферблат. */
@Composable
private fun QuietHoursRow(start: Int, end: Int, onChange: (start: Int, end: Int) -> Unit) {
    // Какое время сейчас выбираем: true — начало, false — конец, null — циферблат закрыт
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    Row(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.settings_quiet_from))
        OutlinedButton(onClick = { editingStart = true }) { Text(formatTimeOfDay(start)) }
        Text(stringResource(R.string.settings_quiet_to))
        OutlinedButton(onClick = { editingStart = false }) { Text(formatTimeOfDay(end)) }
    }
    editingStart?.let { isStart ->
        TimeSelectDialog(
            initialMinuteOfDay = if (isStart) start else end,
            onConfirm = { minute ->
                if (isStart) onChange(minute, end) else onChange(start, minute)
                editingStart = null
            },
            onDismiss = { editingStart = null }
        )
    }
}

/**
 * Батарея Samsung. Если работа в фоне уже разрешена — карточка сжимается до строки "✅ Работа в фоне разрешена"
 * и подсказки про "Без ограничений". Иначе — два шага с кнопками.
 */
@Composable
private fun BatteryGroup(unrestricted: Boolean, onAllow: () -> Unit, onOpenBattery: () -> Unit) {
    if (unrestricted) {
        SettingsRow(
            icon = R.drawable.ic_battery,
            title = stringResource(R.string.settings_battery_ok),
            subtitle = stringResource(R.string.settings_battery_ok_desc),
            onClick = onOpenBattery,
            trailing = { Chevron() }
        )
        return
    }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_battery_intro), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.settings_battery_step1), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onAllow) { Text(stringResource(R.string.permission_open_settings)) }
        Text(stringResource(R.string.settings_battery_step2), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onOpenBattery) { Text(stringResource(R.string.settings_open_battery)) }
    }
}

/** "Как к вам обращаться": имя для приветствия "Здравствуйте, Егор". Пусто — просто "Здравствуйте". */
@Composable
private fun NameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text(stringResource(R.string.settings_name_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(name) })
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.dialog_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } }
    )
}

/** Выбор дней, в которые можно тратить банк. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpendDaysDialog(initial: Set<DayOfWeek>, onConfirm: (Set<DayOfWeek>) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(initial) }
    val locale = LocalConfiguration.current.locales[0]
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_bank_spend_days)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day in days
                    FilterChip(
                        selected = selected,
                        onClick = { days = if (selected) days - day else days + day },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }) }
                    )
                }
            }
        },
        confirmButton = {
            // Пустой набор означает "никогда" — это допустимо (банк только копится)
            TextButton(onClick = { onConfirm(days) }) { Text(stringResource(R.string.dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
