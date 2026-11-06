package com.takeyourbrain.app.ui.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.notifications.AppSound
import com.takeyourbrain.app.ui.common.CardShape
import com.takeyourbrain.app.ui.common.FocusDoneDialog
import com.takeyourbrain.app.ui.common.SettingsRow
import com.takeyourbrain.app.ui.common.StreakDialog
import com.takeyourbrain.app.ui.common.clearTopBarColors
import com.takeyourbrain.app.ui.intro.AppTour
import com.takeyourbrain.app.widget.StreakWidget

/**
 * "Дополнительно": редкое, что не должно мозолить глаза в Настройках — разрешения и помощник,
 * версия приложения, раздел для разработчика (только в отладочной сборке).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onReplayIntro: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTour by remember { mutableStateOf(false) }
    var previewStreak by remember { mutableStateOf(false) }
    var previewFocus by remember { mutableStateOf(false) }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName.orEmpty()
    }
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = { Text(stringResource(R.string.settings_more)) },
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
                .padding(16.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                SettingsRow(
                    icon = R.drawable.ic_todo,
                    title = stringResource(R.string.settings_permissions),
                    subtitle = stringResource(R.string.settings_permissions_summary),
                    onClick = onOpenPermissions,
                    trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = R.drawable.ic_info,
                    title = stringResource(R.string.settings_replay_tour),
                    subtitle = stringResource(R.string.settings_replay_tour_desc),
                    onClick = { showTour = true },
                    trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = R.drawable.ic_brain,
                    title = stringResource(R.string.settings_replay_intro),
                    onClick = onReplayIntro,
                    trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = R.drawable.ic_flame,
                    title = stringResource(R.string.settings_add_widget),
                    subtitle = stringResource(R.string.settings_add_widget_desc),
                    onClick = { StreakWidget.requestPin(context) },
                    trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = R.drawable.ic_shield,
                    title = stringResource(R.string.privacy_title),
                    onClick = onOpenPrivacy,
                    trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = R.drawable.ic_info,
                    title = stringResource(R.string.settings_version),
                    trailing = { Text(version, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
            // Для разработчика: только в отладочной сборке (в Google Play этого не будет)
            if (debuggable) {
                Text(
                    text = stringResource(R.string.settings_section_dev),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    SettingsRow(
                        icon = R.drawable.ic_stats,
                        title = stringResource(R.string.settings_debug_dump),
                        subtitle = stringResource(R.string.settings_debug_dump_desc),
                        onClick = { viewModel.setDebugDump(!state.debugDump) },
                        trailing = { Switch(checked = state.debugDump, onCheckedChange = null) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = R.drawable.ic_flame,
                        title = stringResource(R.string.settings_preview_streak),
                        onClick = { previewStreak = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = R.drawable.ic_focus,
                        title = stringResource(R.string.settings_preview_focus),
                        onClick = {
                            AppSound.play(context, R.raw.tyb_logo_sound)
                            previewFocus = true
                        }
                    )
                }
            }
        }
    }
    if (showTour) AppTour(onDone = { showTour = false })
    if (previewStreak) StreakDialog(days = 7, onDismiss = { previewStreak = false })
    if (previewFocus) FocusDoneDialog(taskTitle = null, onDismiss = { previewFocus = false })
}
