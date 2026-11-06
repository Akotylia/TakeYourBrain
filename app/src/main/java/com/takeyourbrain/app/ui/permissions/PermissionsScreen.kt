package com.takeyourbrain.app.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.permissions.SystemSettings
import com.takeyourbrain.app.ui.common.CardShape
import com.takeyourbrain.app.ui.common.clearTopBarColors
import kotlinx.coroutines.launch

/** Насколько важно разрешение. */
private enum class Importance(@param:StringRes val label: Int) {
    REQUIRED(R.string.permission_required),
    RECOMMENDED(R.string.permission_recommended),
}

/**
 * Помощник по разрешениям: показывает по шагам, что нужно выдать и что уже выдано.
 * Показывается при первом запуске, потом его можно открыть из Настроек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    showBack: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: PermissionsViewModel = viewModel(factory = PermissionsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
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

    // Каждый раз, когда возвращаемся на экран (например, из настроек), обновляем статусы
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // Системный диалог "Разрешить уведомления?"
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Если диалог больше не показывается (пользователь уже отказывал), открываем настройки уведомлений
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if (!granted && canAskAgain == false) SystemSettings.openNotificationSettings(context)
        viewModel.refresh()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.permissions_intro), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.permissions_progress, state.grantedCount, state.totalCount),
                style = MaterialTheme.typography.titleMedium
            )

            PermissionStep(
                number = 1,
                title = R.string.perm_usage_title,
                description = R.string.perm_usage_desc,
                importance = Importance.REQUIRED,
                granted = state.usageAccess,
                buttonText = R.string.permission_open_settings,
                onClick = { SystemSettings.openUsageAccess(context) },
            )
            PermissionStep(
                number = 2,
                title = R.string.perm_accessibility_title,
                description = R.string.perm_accessibility_desc,
                importance = Importance.REQUIRED,
                granted = state.accessibility,
                buttonText = R.string.permission_open_settings,
                // Сначала объяснение и согласие (требование Google Play), и только потом настройки
                onClick = { showDisclosure = true },
                secondButtonText = R.string.permission_app_info,
                onSecondClick = { SystemSettings.openAppDetails(context) },
            )
            PermissionStep(
                number = 3,
                title = R.string.perm_notifications_title,
                description = R.string.perm_notifications_desc,
                importance = Importance.RECOMMENDED,
                granted = state.notifications,
                buttonText = R.string.permission_allow,
                onClick = {
                    val permissionGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    // Разрешение есть, но уведомления выключены вручную: диалог не поможет, нужны настройки
                    if (permissionGranted) SystemSettings.openNotificationSettings(context)
                    else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
            PermissionStep(
                number = 4,
                title = R.string.perm_battery_title,
                description = R.string.perm_battery_desc,
                importance = Importance.RECOMMENDED,
                granted = state.batteryUnrestricted,
                buttonText = R.string.permission_open_settings,
                onClick = { SystemSettings.openBatteryOptimization(context) },
            )
            PermissionStep(
                number = 5,
                title = R.string.perm_exact_alarms_title,
                description = R.string.perm_exact_alarms_desc,
                importance = Importance.RECOMMENDED,
                granted = state.exactAlarms,
                buttonText = R.string.permission_allow,
                onClick = { SystemSettings.openExactAlarms(context) },
            )

            if (!state.allRequiredGranted) {
                Text(
                    text = stringResource(R.string.permissions_not_all_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.markOnboardingDone()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.permissions_continue))
            }
        }
    }
}

/** Карточка одного шага: статус, описание и кнопка, открывающая нужные настройки. */
@Composable
private fun PermissionStep(
    number: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    importance: Importance,
    granted: Boolean,
    @StringRes buttonText: Int,
    onClick: () -> Unit,
    @StringRes secondButtonText: Int? = null,
    onSecondClick: () -> Unit = {},
) {
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_circle_outline),
                    contentDescription = stringResource(
                        if (granted) R.string.permission_status_granted else R.string.permission_status_not_granted
                    ),
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.permission_step_title, number, stringResource(title)),
                        style = MaterialTheme.typography.titleMedium
                    )
                    // Если выдано, пишем "Выдано", иначе насколько оно важно
                    Text(
                        text = stringResource(if (granted) R.string.permission_status_granted else importance.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            granted -> MaterialTheme.colorScheme.primary
                            importance == Importance.REQUIRED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Button(onClick = onClick) { Text(stringResource(buttonText)) }
                if (secondButtonText != null) {
                    TextButton(onClick = onSecondClick) { Text(stringResource(secondButtonText)) }
                }
            }
        }
    }
}
