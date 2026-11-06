package com.takeyourbrain.app.ui.pin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.takeyourbrain.app.R
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.PinCheck
import com.takeyourbrain.app.data.PinRepository
import com.takeyourbrain.app.ui.common.AppAlertDialog
import com.takeyourbrain.app.ui.navigation.LocalHapticsEnabled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Длина PIN ещё не прочитана. */
private const val LOADING = -1

/**
 * Ввод PIN на весь экран, как в банковском приложении: "Введите PIN-код", зачем он нужен ([title]),
 * кружки и цифровая клавиатура. Проверяет сам, как только введены все цифры.
 * @param onCheck проверка PIN (обычно pinRepository::check)
 * @param onSuccess вызывается, если PIN верный
 */
@Composable
fun PinDialog(
    @StringRes title: Int,
    onCheck: suspend (String) -> PinCheck,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pinRepository = (LocalContext.current.applicationContext as TakeYourBrainApp).container.pinRepository
    // Сколько цифр в PIN (null — старый PIN неизвестной длины: тогда кнопка "Готово")
    val length by produceState<Int?>(LOADING) { value = pinRepository.pinLength.first() }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<PinCheck?>(null) }
    var errorKey by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val haptics = LocalHapticsEnabled.current

    fun submit(value: String) {
        if (value.length < PinRepository.MIN_LENGTH || checking) return
        checking = true
        scope.launch {
            val result = onCheck(value)
            checking = false
            if (result is PinCheck.Ok) {
                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onSuccess()
            } else {
                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Reject)
                error = result
                errorKey++
                pin = ""
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.pin_enter_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(32.dp))
                val known = length?.takeIf { it != LOADING }
                PinDots(
                    count = known ?: maxOf(PinRepository.MIN_LENGTH, pin.length),
                    filled = pin.length,
                    error = error != null && pin.isEmpty(),
                    errorKey = errorKey
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .height(56.dp)
                ) {
                    when (val e = error) {
                        is PinCheck.Wrong -> ErrorText(stringResource(R.string.pin_wrong, e.attemptsLeft))
                        is PinCheck.LockedOut -> ErrorText(stringResource(R.string.pin_locked, e.secondsLeft))
                        else -> if (length == null && pin.length >= PinRepository.MIN_LENGTH) {
                            // Старый PIN неизвестной длины — подтверждаем кнопкой (один раз, дальше длина известна)
                            TextButton(onClick = { submit(pin) }, enabled = !checking) {
                                Text(stringResource(R.string.pin_done))
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                PinKeypad(
                    enabled = !checking && length != LOADING,
                    onDigit = { digit ->
                        val max = known ?: PinRepository.MAX_LENGTH
                        if (pin.length < max) {
                            pin += digit
                            error = null
                            if (known != null && pin.length == known) submit(pin)
                        }
                    },
                    onBackspace = { pin = pin.dropLast(1) },
                    leftLabel = stringResource(R.string.dialog_cancel),
                    onLeft = onDismiss
                )
                TextButton(onClick = { showForgot = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.pin_forgot))
                }
            }
        }
    }

    if (showForgot) {
        AppAlertDialog(
            onDismissRequest = { showForgot = false },
            title = { Text(stringResource(R.string.pin_forgot)) },
            text = { Text(stringResource(R.string.pin_forgot_text)) },
            confirmButton = {
                TextButton(onClick = { showForgot = false }) { Text(stringResource(R.string.dialog_ok)) }
            }
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}
