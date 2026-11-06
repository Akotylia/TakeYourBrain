package com.takeyourbrain.app.block

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeyourbrain.app.AppContainer
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.InstalledApp
import com.takeyourbrain.app.domain.BlockInfo
import com.takeyourbrain.app.domain.BlockReason
import com.takeyourbrain.app.ui.common.ArrowRing
import com.takeyourbrain.app.ui.common.formatCountdown
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle

/** Содержимое экрана блокировки. */
@Composable
fun BlockScreen(
    info: BlockInfo,
    container: AppContainer,
    onGoHome: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    // Название и иконку заблокированного приложения загружаем один раз
    val app by produceState<InstalledApp?>(null, info.packageName) {
        value = container.appsRepository.getApp(info.packageName)
    }
    // Фокус на задаче — покажем, на какой
    val focusSession by container.focusRepository.session.collectAsStateWithLifecycle(initialValue = null)
    // Текущее время, обновляется каждую секунду — для обратного отсчёта
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = LocalDateTime.now()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            app?.let {
                Image(
                    bitmap = remember(it.icon) { it.icon.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(it.label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = reasonText(info.decision.reason, now),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            val focusTask = focusSession?.taskTitle
            if (info.decision.reason is BlockReason.FocusMode && focusTask != null) {
                Text(
                    text = stringResource(R.string.focus_on_task, focusTask),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            CalmTimer(unlockAt = info.decision.unlockAt, now = now)
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (info.decision.reason == BlockReason.FullBlock && info.decision.unlockAt == null) {
                    stringResource(R.string.block_full_how_to_remove)
                } else {
                    remainingText(info.decision.unlockAt, now)
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.block_calm_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            // "Вместо этого" — задачи из ежедневника
            TasksSection(taskRepository = container.taskRepository, onOpenTasks = onOpenTasks)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.block_go_home))
            }
            // Время из банка (если банк здесь работает: не фокус, не полная блокировка)
            info.bankMultiplier?.let { multiplier ->
                Spacer(Modifier.height(24.dp))
                BankSection(
                    packageName = info.packageName,
                    multiplier = multiplier,
                    bankRepository = container.bankRepository,
                    settingsRepository = container.settingsRepository,
                    onOpenApp = onOpenApp
                )
            }
            // В фокусе и при полной блокировке пропуски не действуют
            val reason = info.decision.reason
            if (reason !is BlockReason.FocusMode && reason != BlockReason.FullBlock) {
                Spacer(Modifier.height(16.dp))
                UnlockSection(
                    packageName = info.packageName,
                    passRepository = container.passRepository,
                    pinRepository = container.pinRepository,
                    onOpenApp = onOpenApp
                )
            }
        }
    }
}

/**
 * Спокойный таймер: мозг из логотипа медленно "дышит" (вдох-выдох 4 с), вокруг стрелка идёт по кругу
 * до разблокировки. Без времени разблокировки (полная блокировка) — только дышащий мозг.
 */
@Composable
private fun CalmTimer(unlockAt: LocalDateTime?, now: LocalDateTime) {
    // Сколько было до разблокировки, когда открылся экран: от этого считаем, какая часть круга осталась
    val shownAt = remember { now }
    val total = unlockAt?.let { Duration.between(shownAt, it).toMillis() } ?: 0L
    val left = unlockAt?.let { Duration.between(now, it).toMillis() } ?: 0L
    val progress = if (total > 0) (left.toFloat() / total).coerceIn(0f, 1f) else 0f
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )
    ArrowRing(progress = progress, modifier = Modifier.size(200.dp), strokeWidth = 6.dp) {
        Box(
            Modifier
                .size(120.dp)
                .scale(breath)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brain),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

/** Причина блокировки обычными словами. */
@Composable
private fun reasonText(reason: BlockReason, now: LocalDateTime): String = when (reason) {
    BlockReason.FullBlock -> stringResource(R.string.block_reason_full)

    is BlockReason.LimitReached ->
        stringResource(R.string.block_reason_limit, formatDuration(reason.limitMinutes * 60_000L))

    is BlockReason.OutsideAllowed -> {
        val next = reason.next
        when {
            next == null -> stringResource(R.string.block_reason_allow_none)
            next.start.toLocalDate() == now.toLocalDate() ->
                stringResource(R.string.block_reason_allow_today, time(next.start), time(next.end))
            else ->
                stringResource(R.string.block_reason_allow_day, dayName(next.start), time(next.start), time(next.end))
        }
    }

    is BlockReason.InsideBlocked ->
        if (reason.until.toLocalDate() == now.toLocalDate()) {
            stringResource(R.string.block_reason_blocked_until, time(reason.until))
        } else {
            stringResource(R.string.block_reason_blocked_until_day, dayName(reason.until), time(reason.until))
        }

    is BlockReason.FocusMode -> stringResource(R.string.block_reason_focus)
}

/** "До разблокировки: 1:20:05" или пояснение, если блокировка бессрочная или уже закончилась. */
@Composable
private fun remainingText(unlockAt: LocalDateTime?, now: LocalDateTime): String {
    if (unlockAt == null) return stringResource(R.string.block_no_unlock_time)
    val left = Duration.between(now, unlockAt)
    if (left.isNegative || left.isZero) return stringResource(R.string.block_time_over)
    return stringResource(R.string.block_remaining, formatCountdown(left.toMillis()))
}

@Composable
private fun time(dateTime: LocalDateTime): String = formatTimeOfDay(dateTime.hour * 60 + dateTime.minute)

@Composable
private fun dayName(dateTime: LocalDateTime): String {
    val locale = LocalConfiguration.current.locales[0]
    return dateTime.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
}
