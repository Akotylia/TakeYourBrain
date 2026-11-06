package com.takeyourbrain.app.ui.rules

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.data.db.ScheduleMode
import com.takeyourbrain.app.domain.BlockableElement
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Короткое описание правила для списка: "Лимит 30 мин · Блокировка по расписанию". */
@Composable
fun ruleSummary(rule: AppRule): String {
    val context = LocalContext.current
    val parts = buildList {
        if (rule.isFullBlockActive(System.currentTimeMillis())) {
            val offAt = rule.fullBlockOffAt
            if (offAt == null) {
                add(stringResource(R.string.summary_full_block))
            } else {
                // Идёт ожидание снятия — показываем, когда снимется
                val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(offAt), ZoneId.systemDefault())
                add(stringResource(R.string.summary_full_block_until, formatTimeOfDay(context, at)))
            }
        }
        val weekday = rule.weekdayLimitMinutes
        val weekend = rule.weekendLimitMinutes
        when {
            weekday == weekend && weekday != null -> add(stringResource(R.string.summary_limit, minutes(weekday)))
            weekday != null && weekend != null ->
                add(stringResource(R.string.summary_limit_split, minutes(weekday), minutes(weekend)))
            weekday != null -> add(stringResource(R.string.summary_limit_weekdays, minutes(weekday)))
            weekend != null -> add(stringResource(R.string.summary_limit_weekend, minutes(weekend)))
        }
        when (rule.scheduleMode) {
            ScheduleMode.ALLOW_ONLY -> add(stringResource(R.string.summary_allow_only))
            ScheduleMode.BLOCK_DURING -> add(stringResource(R.string.summary_block_during))
            ScheduleMode.NONE -> Unit
        }
        val shorts = BlockableElement.YOUTUBE_SHORTS.id
        if (shorts in rule.elementLimits) {
            val limit = rule.elementLimits[shorts]
            add(if (limit == null) stringResource(R.string.summary_shorts) else stringResource(R.string.summary_shorts_limit, minutes(limit)))
        }
    }
    return parts.joinToString(stringResource(R.string.list_separator))
}

@Composable
private fun minutes(value: Int): String = formatDuration(value * 60_000L)
