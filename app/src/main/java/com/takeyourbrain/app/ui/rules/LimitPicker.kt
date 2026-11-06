package com.takeyourbrain.app.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.takeyourbrain.app.R
import com.takeyourbrain.app.ui.common.ChoicePill
import com.takeyourbrain.app.ui.common.formatDuration

/** Готовые варианты лимита в минутах. Своё число — запасной вариант под ними. */
val LIMIT_PRESETS = listOf(5, 10, 15, 30, 60, 120, 180, 240)

/**
 * Выбор лимита: таблетки с готовыми значениями (по 4 в ряд) и "Своё" для ввода числа.
 * [value] — минуты текстом; пустой текст — "без лимита" (можно выбрать, только если [allowNone]).
 */
@Composable
fun LimitPicker(value: String, onValueChange: (String) -> Unit, allowNone: Boolean, onDone: () -> Unit) {
    val minutes = value.toIntOrNull()
    // Поле "Своё" открыто, если сохранено не готовое значение или его выбрали сами
    var customOpen by rememberSaveable { mutableStateOf(minutes != null && minutes !in LIMIT_PRESETS) }
    var focusField by remember { mutableStateOf(false) }
    // Своё поле с выделением: при открытии старое число выделено, и ввод сразу его заменяет
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LIMIT_PRESETS.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { preset ->
                    ChoicePill(
                        text = formatDuration(preset * 60_000L),
                        selected = !customOpen && minutes == preset,
                        onClick = {
                            customOpen = false
                            onValueChange(preset.toString())
                            onDone()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowNone) {
                ChoicePill(
                    text = stringResource(R.string.limit_none),
                    selected = !customOpen && value.isEmpty(),
                    onClick = {
                        customOpen = false
                        onValueChange("")
                        onDone()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            ChoicePill(
                text = stringResource(R.string.limit_custom),
                icon = R.drawable.ic_edit,
                selected = customOpen,
                onClick = {
                    customOpen = true
                    focusField = true
                    field = TextFieldValue(value, TextRange(0, value.length))
                },
                modifier = Modifier.weight(1f)
            )
        }
        if (customOpen) {
            val focusRequester = remember { FocusRequester() }
            OutlinedTextField(
                value = field,
                onValueChange = {
                    val digits = it.text.filter(Char::isDigit).take(4)
                    field = it.copy(text = digits)
                    onValueChange(digits)
                },
                label = { Text(stringResource(R.string.limit_custom_label)) },
                suffix = { Text(stringResource(R.string.limit_minutes_suffix)) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            // Нажали "Своё" — сразу открываем клавиатуру
            LaunchedEffect(focusField) {
                if (focusField) {
                    focusRequester.requestFocus()
                    focusField = false
                }
            }
        }
    }
}

/** "30 мин" или "без лимита" — для подписи на переключателе "Будни / Выходные". */
@Composable
fun limitValueText(value: String): String {
    val minutes = value.toIntOrNull()
    return if (minutes == null || minutes <= 0) stringResource(R.string.limit_none_short) else formatDuration(minutes * 60_000L)
}
