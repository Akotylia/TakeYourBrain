package com.takeyourbrain.app.block

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.PassRepository
import com.takeyourbrain.app.data.PinRepository
import com.takeyourbrain.app.notifications.AppSound
import com.takeyourbrain.app.ui.common.ArrowRing
import com.takeyourbrain.app.ui.navigation.LocalHapticsEnabled
import com.takeyourbrain.app.ui.pin.PinDialog
import kotlinx.coroutines.launch

/** Шаги разблокировки: кнопка → PIN → пауза 30 с → "Открыть на 10 мин". */
private enum class UnlockStep { IDLE, ENTER_PIN, WAITING, READY }

/**
 * Разблокировка с экрана блокировки.
 * Пауза нужна, чтобы успеть передумать: если уйти с экрана, она начинается заново.
 */
@Composable
fun UnlockSection(
    packageName: String,
    passRepository: PassRepository,
    pinRepository: PinRepository,
    onOpenApp: () -> Unit,
) {
    var step by remember(packageName) { mutableStateOf(UnlockStep.IDLE) }
    val scope = rememberCoroutineScope()
    // Сколько пропусков осталось сегодня (null — ещё читаем)
    val passesLeft by produceState<Int?>(null, packageName) { value = passRepository.passesLeftToday() }
    val passMinutes = (PassRepository.PASS_DURATION_MS / 60_000).toInt()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val haptics = LocalHapticsEnabled.current

    // Ушли с экрана во время паузы — начинаем сначала
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (step == UnlockStep.WAITING) step = UnlockStep.IDLE
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (step) {
            UnlockStep.IDLE, UnlockStep.ENTER_PIN -> {
                val left = passesLeft ?: return@Column
                if (left > 0) {
                    OutlinedButton(onClick = { step = UnlockStep.ENTER_PIN }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.block_unlock))
                    }
                    SmallText(stringResource(R.string.block_passes_left, left))
                } else {
                    SmallText(stringResource(R.string.block_no_passes))
                }
            }

            UnlockStep.WAITING -> {
                val waitMs = PassRepository.UNLOCK_WAIT_SECONDS * 1_000
                // Стрелка из логотипа заполняет круг ровно за 30 секунд, в середине — сколько осталось
                val progress = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    progress.animateTo(1f, tween(waitMs, easing = LinearEasing))
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    AppSound.play(context, R.raw.tyb_ding)
                    step = UnlockStep.READY
                }
                val seconds = ((1f - progress.value) * PassRepository.UNLOCK_WAIT_SECONDS).let { kotlin.math.ceil(it).toInt() }
                ArrowRing(progress = progress.value, modifier = Modifier.size(140.dp)) {
                    Text(seconds.toString(), style = MaterialTheme.typography.displaySmall)
                }
                Text(
                    text = stringResource(R.string.block_wait_title_short),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                SmallText(stringResource(R.string.block_wait_text))
                TextButton(onClick = { step = UnlockStep.IDLE }) { Text(stringResource(R.string.block_wait_cancel)) }
            }

            UnlockStep.READY -> {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            passRepository.grant(packageName)
                            onOpenApp()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.block_open_for, passMinutes))
                }
                TextButton(onClick = { step = UnlockStep.IDLE }) { Text(stringResource(R.string.block_wait_cancel)) }
            }
        }
    }

    if (step == UnlockStep.ENTER_PIN) {
        PinDialog(
            title = R.string.pin_enter_to_unlock,
            onCheck = pinRepository::check,
            onSuccess = { step = UnlockStep.WAITING },
            onDismiss = { step = UnlockStep.IDLE }
        )
    }
}

@Composable
private fun SmallText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}
