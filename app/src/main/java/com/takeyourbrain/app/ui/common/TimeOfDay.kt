package com.takeyourbrain.app.ui.common

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.takeyourbrain.app.R
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Минуты от начала суток → "07:30" или "7:30 AM" (как настроено в телефоне). */
@Composable
fun formatTimeOfDay(minuteOfDay: Int): String =
    formatTimeOfDay(LocalContext.current, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))

/** То же самое для кода вне Compose (уведомления, службы). */
fun formatTimeOfDay(context: Context, dateTime: LocalDateTime): String =
    formatTimeOfDay(context, dateTime.toLocalTime())

private fun formatTimeOfDay(context: Context, time: LocalTime): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}

/** Диалог с циферблатом для выбора времени. Возвращает минуты от начала суток. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSelectDialog(
    initialMinuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AppAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
        text = { TimePicker(state = state) }
    )
}
