package com.takeyourbrain.app.ui.apps

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.ui.common.ArrowLoader
import com.takeyourbrain.app.ui.common.CardGroupItem
import com.takeyourbrain.app.ui.common.CardShape
import com.takeyourbrain.app.ui.common.SearchPill
import com.takeyourbrain.app.ui.common.StreakDialog
import com.takeyourbrain.app.ui.common.WarningBanner
import com.takeyourbrain.app.data.AppSort
import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.permissions.SystemSettings
import com.takeyourbrain.app.ui.common.clearTopBarColors
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.navigation.LocalBottomBarSpace
import com.takeyourbrain.app.ui.permissions.AccessibilityDisclosureDialog
import com.takeyourbrain.app.ui.rules.ruleSummary

/** Экран со списком приложений, поиском и временем использования за сегодня. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onAppClick: (packageName: String) -> Unit,
    onOpenStats: () -> Unit,
    viewModel: AppsViewModel = viewModel(factory = AppsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query = viewModel.query
    val context = LocalContext.current

    // Делим на два раздела (с правилом / без), фильтруем поиском и сортируем.
    // Пересчитываем только когда что-то из этого меняется.
    val (blockedApps, otherApps) = remember(state.apps, state.rules, query, state.blockedSort, state.allSort) {
        val found = state.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        // Системные приложения всегда в "Все приложения", даже если правило для них осталось с прошлых версий
        val (blocked, other) = found.partition { it.packageName in state.rules && !it.isProtected }
        blocked.sortedFor(state.blockedSort) to other.sortedFor(state.allSort)
    }

    // Нажатие на приложение: системное — объясняем, почему нельзя; остальные — открываем правило
    val openApp: (AppItem) -> Unit = { app ->
        if (app.isProtected) {
            Toast.makeText(context, R.string.apps_protected_toast, Toast.LENGTH_LONG).show()
        } else {
            onAppClick(app.packageName)
        }
    }

    // Перед включением службы — объяснение и согласие (требование Google Play)
    var showDisclosure by remember { mutableStateOf(false) }
    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showDisclosure = false
                SystemSettings.openAccessibility(context)
            },
            onDecline = { showDisclosure = false }
        )
    }

    state.celebrateStreak?.let { days -> StreakDialog(days, onDismiss = viewModel::dismissCelebration) }

    // Каждый раз, когда экран снова виден, обновляем время использования
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(colors = clearTopBarColors(), title = { Text(stringResource(R.string.nav_apps)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding() // список не прячется под клавиатурой во время поиска
        ) {
            if (!state.isLoading && !state.accessibilityEnabled) {
                WarningBanner(
                    text = R.string.apps_accessibility_off,
                    button = R.string.apps_turn_on,
                    onClick = { showDisclosure = true }
                )
            }
            if (!state.isLoading && !state.hasUsageAccess) {
                WarningBanner(
                    text = R.string.apps_no_usage_access,
                    button = R.string.apps_grant_access,
                    onClick = { SystemSettings.openUsageAccess(context) }
                )
            }

            // Карточка "Сегодня" — нажатие открывает полную статистику
            if (!state.isLoading && state.hasUsageAccess) {
                TodayCard(totalMs = state.totalTodayMs, blocks = state.blocksToday, streak = state.streak, onClick = onOpenStats)
            }

            SearchField(query = query, onQueryChange = viewModel::onQueryChange)

            when {
                state.isLoading -> CenteredBox { ArrowLoader() }
                blockedApps.isEmpty() && otherApps.isEmpty() ->
                    CenteredBox { Text(stringResource(R.string.apps_nothing_found)) }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Запас внизу: список прокручивается под плавающей панелью, последний пункт не прячется
                    contentPadding = PaddingValues(bottom = LocalBottomBarSpace.current)
                ) {
                    // Две карточки: поменьше "Заблокированные" (всегда первая), побольше "Все приложения".
                    // Заголовок с сортировкой — внутри карточки сверху.
                    if (blockedApps.isNotEmpty()) {
                        item(key = "header_blocked") {
                            CardGroupItem(first = true, last = false) {
                                SectionHeader(
                                    title = R.string.apps_section_blocked,
                                    count = blockedApps.size,
                                    sort = state.blockedSort,
                                    onSortChange = viewModel::setBlockedSort
                                )
                            }
                        }
                        itemsIndexed(blockedApps, key = { _, app -> app.packageName }) { index, app ->
                            CardGroupItem(first = false, last = index == blockedApps.lastIndex) {
                                AppRow(
                                    app = app,
                                    rule = state.rules[app.packageName],
                                    showUsage = state.hasUsageAccess,
                                    onClick = { openApp(app) }
                                )
                            }
                        }
                        item(key = "gap") { Spacer(Modifier.height(12.dp)) }
                    }
                    if (otherApps.isNotEmpty()) {
                        item(key = "header_all") {
                            CardGroupItem(first = true, last = false) {
                                SectionHeader(
                                    title = R.string.apps_section_all,
                                    count = otherApps.size,
                                    sort = state.allSort,
                                    onSortChange = viewModel::setAllSort
                                )
                            }
                        }
                        itemsIndexed(otherApps, key = { _, app -> app.packageName }) { index, app ->
                            CardGroupItem(first = false, last = index == otherApps.lastIndex) {
                                AppRow(
                                    app = app,
                                    rule = null,
                                    showUsage = state.hasUsageAccess,
                                    onClick = { openApp(app) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Заголовок раздела: название и число приложений слева, кнопка сортировки справа. */
@Composable
private fun SectionHeader(
    @StringRes title: Int,
    count: Int,
    sort: AppSort,
    onSortChange: (AppSort) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.apps_section_count, stringResource(title), count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        // Box нужен, чтобы меню открылось рядом с кнопкой, а не посреди экрана
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.sort_button))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AppSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.label)) },
                        onClick = {
                            onSortChange(option)
                            menuOpen = false
                        },
                        // Галочка у выбранного варианта
                        leadingIcon = {
                            if (option == sort) Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

/** Подпись варианта сортировки для меню. */
private val AppSort.label: Int
    @StringRes get() = when (this) {
        AppSort.USAGE -> R.string.sort_by_usage
        AppSort.NAME -> R.string.sort_by_name
        AppSort.INSTALL_DATE -> R.string.sort_by_install_date
    }

/** "Сегодня 2 ч 14 мин · Блокировок 3 · 🔥 5 ›" — главное за день, вход в статистику. */
@Composable
private fun TodayCard(totalMs: Long, blocks: Int, streak: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stats_today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = formatDuration(totalMs), style = MaterialTheme.typography.headlineSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.stats_blocks),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = blocks.toString(), style = MaterialTheme.typography.headlineSmall)
            }
            // Огонёк серии: горит, если есть хоть один день без превышений
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 16.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_flame),
                    contentDescription = stringResource(R.string.streak_title),
                    tint = if (streak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Text(text = streak.toString(), style = MaterialTheme.typography.headlineSmall)
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.apps_open_stats),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    SearchPill(query, onQueryChange, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** Строка списка: иконка, название, время за сегодня и краткое описание правила (если есть). */
@Composable
private fun AppRow(app: AppItem, rule: AppRule?, showUsage: Boolean, onClick: () -> Unit) {
    // Bitmap → ImageBitmap один раз, а не при каждой перерисовке
    val icon = remember(app.icon) { app.icon.asImageBitmap() }
    ListItem(
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (showUsage) {
                    Text(
                        if (app.usageTodayMs > 0) stringResource(R.string.apps_today_usage, formatDuration(app.usageTodayMs))
                        else stringResource(R.string.apps_not_used_today)
                    )
                }
                if (rule != null) {
                    Text(
                        text = ruleSummary(rule),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (app.isProtected) {
                    Text(
                        text = stringResource(R.string.apps_protected),
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        leadingContent = { Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp)) },
        // Фон даёт карточка вокруг строки
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
