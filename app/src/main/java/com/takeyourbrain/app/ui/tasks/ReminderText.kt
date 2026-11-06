package com.takeyourbrain.app.ui.tasks

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.db.Task
import com.takeyourbrain.app.data.rule
import com.takeyourbrain.app.domain.ReminderRule
import com.takeyourbrain.app.ui.common.daysText
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** "сегодня, 18:00", "завтра, 9:00", "пт, 9:00" (на этой неделе) или "31 окт., 9:00". */
@Composable
fun whenText(time: LocalDateTime): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val clock = formatTimeOfDay(context, time)
    val today = LocalDate.now()
    val date = time.toLocalDate()
    return when {
        date == today -> stringResource(R.string.when_today, clock)
        date == today.plusDays(1) -> stringResource(R.string.when_tomorrow, clock)
        date.isAfter(today) && date.isBefore(today.plusDays(7)) ->
            stringResource(R.string.when_day, date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), clock)
        else -> {
            val pattern = DateFormat.getBestDateTimePattern(locale, "dMMM")
            stringResource(R.string.when_day, date.format(DateTimeFormatter.ofPattern(pattern, locale)), clock)
        }
    }
}

@Composable
fun whenText(millis: Long): String = whenText(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()))

/** Правило обычными словами: "каждые 7 ч", "по сб, вс в 21:00", "каждый месяц, 1 число, в 9:00". */
@Composable
fun ruleText(rule: ReminderRule): String = when (rule) {
    is ReminderRule.Once -> whenText(rule.at)
    is ReminderRule.Interval -> stringResource(
        if (rule.fromDone) R.string.rule_text_interval_from_done else R.string.rule_text_interval,
        formatDuration(rule.everyMinutes * 60_000L)
    )
    is ReminderRule.Weekly ->
        if (rule.days.size == DayOfWeek.entries.size) {
            stringResource(R.string.rule_text_daily, formatTimeOfDay(rule.minuteOfDay))
        } else {
            stringResource(R.string.rule_text_weekly, daysText(rule.days), formatTimeOfDay(rule.minuteOfDay))
        }
    is ReminderRule.Monthly -> stringResource(R.string.rule_text_monthly, rule.dayOfMonth, formatTimeOfDay(rule.minuteOfDay))
}

/** Подпись задачи в списке: что с напоминанием сейчас. null — напоминания нет. */
@Composable
fun taskStatusText(task: Task): String? {
    val rule = task.rule()
    val snoozed = task.snoozedUntil
    val fired = task.firedAt
    val next = task.nextFireAt
    return when {
        task.done -> task.doneAt?.let { stringResource(R.string.task_done_at, whenText(it)) }
        snoozed != null -> stringResource(R.string.task_snoozed_until, whenText(snoozed))
        rule == null -> null
        rule is ReminderRule.Once -> if (fired != null) stringResource(R.string.task_fired, whenText(fired)) else whenText(rule.at)
        fired != null -> stringResource(R.string.task_fired, whenText(fired)) + " · " + ruleText(rule)
        next != null -> ruleText(rule) + " · " + stringResource(R.string.task_next, whenText(next))
        else -> ruleText(rule)
    }
}
