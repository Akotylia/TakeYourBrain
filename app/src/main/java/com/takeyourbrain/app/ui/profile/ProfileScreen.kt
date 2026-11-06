package com.takeyourbrain.app.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.StreakInfo
import com.takeyourbrain.app.domain.DayStatus
import com.takeyourbrain.app.ui.common.clearTopBarColors
import com.takeyourbrain.app.ui.common.formatMinutes
import com.takeyourbrain.app.ui.navigation.LocalBottomBarSpace

/** Вкладка "Профиль": огонёк серии сверху, ниже — банк, статистика, настройки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenBank: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(colors = clearTopBarColors(), title = { Text(stringResource(R.string.nav_profile)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = LocalBottomBarSpace.current),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StreakCard(state.streak)
            // Пункты одной группой: общая карточка, внутри строки
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ProfileRow(
                        icon = R.drawable.ic_bank,
                        title = stringResource(R.string.bank_title),
                        subtitle = stringResource(R.string.profile_bank_desc, formatMinutes(state.bankTotal)),
                        onClick = onOpenBank
                    )
                    ProfileRow(
                        icon = R.drawable.ic_stats,
                        title = stringResource(R.string.nav_stats),
                        subtitle = stringResource(R.string.profile_stats_desc),
                        onClick = onOpenStats
                    )
                    ProfileRow(
                        icon = R.drawable.ic_settings,
                        title = stringResource(R.string.nav_settings),
                        subtitle = stringResource(R.string.profile_settings_desc),
                        onClick = onOpenSettings
                    )
                }
            }
        }
    }
}

/** Огонёк серии: сколько дней подряд без превышения лимитов, рекорд и как идёт сегодня. */
@Composable
private fun StreakCard(streak: StreakInfo?) {
    val burning = (streak?.current ?: 0) > 0
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (burning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        if (burning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flame),
                    contentDescription = null,
                    tint = if (burning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.streak_title), style = MaterialTheme.typography.labelLarge)
                val current = streak?.current ?: 0
                Text(
                    text = pluralStringResource(R.plurals.streak_days, current, current),
                    style = MaterialTheme.typography.headlineMedium
                )
                if (streak != null) {
                    if (streak.best > 0) {
                        Text(
                            text = pluralStringResource(R.plurals.streak_best, streak.best, streak.best),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(
                            when (streak.today) {
                                DayStatus.CLEAN -> R.string.streak_today_clean
                                DayStatus.BROKEN -> R.string.streak_today_broken
                                DayStatus.NEUTRAL -> R.string.streak_today_neutral
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/** Строка профиля: иконка в скруглённом квадрате, название, пояснение, стрелка. */
@Composable
private fun ProfileRow(@DrawableRes icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    // Фон даёт карточка вокруг строки
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}
