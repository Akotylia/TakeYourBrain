package com.takeyourbrain.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.takeyourbrain.app.R
import java.time.DayOfWeek
import java.time.format.TextStyle

/** "сб, вс" — дни недели по порядку на языке приложения ("ни в какие", если дней нет). */
@Composable
fun daysText(days: Set<DayOfWeek>): String {
    if (days.isEmpty()) return stringResource(R.string.days_none)
    val locale = LocalConfiguration.current.locales[0]
    return DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}
