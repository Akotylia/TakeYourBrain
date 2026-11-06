package com.takeyourbrain.app.ui.stats

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.permissions.SystemSettings
import com.takeyourbrain.app.ui.common.ArrowLoader
import com.takeyourbrain.app.ui.common.CardShape
import com.takeyourbrain.app.ui.common.WarningBanner
import com.takeyourbrain.app.ui.common.clearTopBarColors
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatMinutes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Сколько приложений показывать в списке "Чаще всего". */
private const val TOP_COUNT = 5

/** Вкладка "Статистика": время по дням, самые используемые приложения, число блокировок. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Пересчитываем при каждом открытии экрана: время за сегодня всё время растёт
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = { Text(stringResource(R.string.nav_stats)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading || state.days.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ArrowLoader()
            }
            return@Scaffold
        }

        val week = viewModel.weekMode
        val day = state.days[viewModel.selectedDay.coerceIn(state.days.indices)]
        val locale = LocalConfiguration.current.locales[0]

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.hasUsageAccess) {
                item {
                    WarningBanner(
                        text = R.string.apps_no_usage_access,
                        button = R.string.apps_grant_access,
                        onClick = { SystemSettings.openUsageAccess(context) }
                    )
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SegmentedButton(
                        selected = !week,
                        onClick = { viewModel.showWeek(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.stats_mode_day)) }
                    SegmentedButton(
                        selected = week,
                        onClick = { viewModel.showWeek(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.stats_mode_week)) }
                }
            }

            item {
                SummaryCard(
                    title = if (week) stringResource(R.string.stats_last_7_days) else dayTitle(day.date, locale),
                    totalMs = if (week) state.weekTotalMs else day.totalMs,
                    blocks = if (week) state.weekBlocks else day.blocks,
                    averageMs = if (week) state.dailyAverageMs else null,
                )
            }

            if (week) {
                state.week?.let { summary -> item { WeekSummaryCard(summary) } }
            }

            item {
                val dayLabels = state.days.map { it.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).capitalized(locale) }
                // Для TalkBack — все дни одной строкой: "пн: 2 ч 5 мин, вт: …"
                val description = state.days.zip(dayLabels).map { (d, label) ->
                    stringResource(R.string.stats_chart_item, label, formatDuration(d.totalMs))
                }.joinToString()
                StatsCard(title = stringResource(R.string.stats_by_day)) {
                    DailyBarChart(
                        values = state.days.map { it.totalMs },
                        labels = dayLabels,
                        selected = if (week) null else viewModel.selectedDay,
                        onSelect = viewModel::selectDay,
                        accessibilityText = description,
                    )
                    Text(
                        text = stringResource(R.string.stats_chart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                val apps = (if (week) state.weekApps else day.apps).take(TOP_COUNT)
                StatsCard(title = stringResource(R.string.stats_top_apps)) {
                    if (apps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_no_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val longest = apps.firstOrNull()?.ms ?: 1L
                    apps.forEach { app -> TopAppRow(app, fraction = app.ms.toFloat() / longest) }
                }
            }
        }
    }
}

/** Итоги недели: огонёк серии, сэкономленное время, банк. */
@Composable
private fun WeekSummaryCard(summary: WeekSummary) {
    StatsCard(title = stringResource(R.string.stats_week_results)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_flame),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = pluralStringResource(R.plurals.streak_days, summary.streak.current, summary.streak.current),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.stats_streak_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        WeekLine(R.string.stats_saved, formatMinutes(summary.savedMinutes))
        WeekLine(R.string.stats_bank_earned, formatMinutes(summary.bankEarned))
        WeekLine(R.string.stats_bank_spent, formatMinutes(summary.bankSpent))
    }
}

/** Строка "Название ........ значение". */
@Composable
private fun WeekLine(@StringRes title: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Карточка с итогом: общее время и число блокировок. */
@Composable
private fun SummaryCard(title: String, totalMs: Long, blocks: Int, averageMs: Long?) {
    StatsCard(title = title) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stats_screen_time),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = formatDuration(totalMs), style = MaterialTheme.typography.headlineMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.stats_blocks),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = blocks.toString(), style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (averageMs != null) {
            Text(
                text = stringResource(R.string.stats_daily_average, formatDuration(averageMs)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Строка списка "Чаще всего": иконка, название, время и полоска относительно первого места. */
@Composable
private fun TopAppRow(app: AppUsage, fraction: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(bitmap = app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatDuration(app.ms),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {}
            )
        }
    }
}

/** Карточка-раздел с заголовком. */
@Composable
private fun StatsCard(title: String, content: @Composable () -> Unit) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** "Сегодня", "Вчера" или "Понедельник, 21 октября" (порядок слов — как принято в языке). */
@Composable
private fun dayTitle(date: LocalDate, locale: Locale): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.stats_today)
        today.minusDays(1) -> stringResource(R.string.stats_yesterday)
        else -> {
            val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
            date.format(DateTimeFormatter.ofPattern(pattern, locale)).capitalized(locale)
        }
    }
}

private fun String.capitalized(locale: Locale): String = replaceFirstChar { it.titlecase(locale) }
