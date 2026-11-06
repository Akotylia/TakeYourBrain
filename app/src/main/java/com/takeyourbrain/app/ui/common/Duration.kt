package com.takeyourbrain.app.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.takeyourbrain.app.R
import java.util.Locale

/** Превращает миллисекунды в текст вида "1 ч 25 мин" / "1 h 25 min" (для экранов Compose). */
@Composable
fun formatDuration(millis: Long): String {
    val (hours, minutes) = hoursAndMinutes(millis)
    return when {
        hours == 0L && minutes == 0L -> stringResource(R.string.duration_less_than_minute)
        hours == 0L -> stringResource(R.string.duration_minutes, minutes)
        minutes == 0L -> stringResource(R.string.duration_hours, hours) // "2 ч", а не "2 ч 0 мин"
        else -> stringResource(R.string.duration_hours_minutes, hours, minutes)
    }
}

/** Целые минуты (например, баланс банка): "0 мин", "45 мин", "1 ч 30 мин". */
@Composable
fun formatMinutes(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.duration_minutes, 0L) else formatDuration(minutes * 60_000L)

/** То же самое для кода вне Compose (уведомления, службы). */
fun formatDuration(context: Context, millis: Long): String {
    val (hours, minutes) = hoursAndMinutes(millis)
    return when {
        hours == 0L && minutes == 0L -> context.getString(R.string.duration_less_than_minute)
        hours == 0L -> context.getString(R.string.duration_minutes, minutes)
        minutes == 0L -> context.getString(R.string.duration_hours, hours)
        else -> context.getString(R.string.duration_hours_minutes, hours, minutes)
    }
}

/** Обратный отсчёт "1:20:05" (часы:минуты:секунды). Числовой формат, переводить нечего. */
fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) + 999) / 1000 // округляем вверх: 0,4 с — это ещё "0:00:01"
    return String.format(Locale.ROOT, "%d:%02d:%02d", totalSeconds / 3600, totalSeconds / 60 % 60, totalSeconds % 60)
}

private fun hoursAndMinutes(millis: Long): Pair<Long, Long> {
    val totalMinutes = millis / 60_000
    return totalMinutes / 60 to totalMinutes % 60
}
