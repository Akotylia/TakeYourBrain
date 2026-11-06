package com.takeyourbrain.app.ui.pin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeyourbrain.app.R
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.PinRepository
import com.takeyourbrain.app.ui.navigation.LocalHapticsEnabled
import kotlinx.coroutines.launch

/**
 * Создание PIN как в банковском приложении: "Здравствуйте", "Придумайте PIN-код" → 4 цифры →
 * "Повторите PIN-код". Совпал — стрелка замыкает круг вокруг галочки и дальше. Не совпал — кружки
 * трясутся, "Коды не совпадают", всё сначала. При первом запуске "Назад" нет; при смене из
 * Настроек ([isChange]) старый PIN уже проверен.
 */
@Composable
fun PinSetupScreen(isChange: Boolean, onDone: () -> Unit, onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as TakeYourBrainApp).container
    val userName by container.settingsRepository.userName.collectAsStateWithLifecycle(initialValue = "")
    var repeatStep by remember { mutableStateOf(false) } // false — придумываем, true — повторяем
    var first by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    var errorKey by remember { mutableIntStateOf(0) }
    var created by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val haptics = LocalHapticsEnabled.current

    fun onFull(pin: String) {
        if (!repeatStep) {
            first = pin
            current = ""
            repeatStep = true
            return
        }
        if (pin == first) {
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            scope.launch {
                container.pinRepository.setPin(pin)
                created = true
            }
        } else {
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Reject)
            mismatch = true
            errorKey++
            first = ""
            current = ""
            repeatStep = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.systemBarsPadding()) {
            if (isChange && !created) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                val title = when {
                    isChange -> stringResource(R.string.pin_change_title)
                    userName.isNotBlank() -> stringResource(R.string.pin_hello_name, userName)
                    else -> stringResource(R.string.pin_hello)
                }
                Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Text(
                    text = stringResource(
                        when {
                            created -> R.string.pin_created
                            repeatStep -> R.string.pin_repeat_title
                            else -> R.string.pin_create_title
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(32.dp))
                if (created) {
                    PinCreatedMark(onFinished = onDone)
                    Spacer(Modifier.weight(2f))
                } else {
                    PinDots(count = PinRepository.LENGTH, filled = current.length, error = mismatch && current.isEmpty(), errorKey = errorKey)
                    Box(Modifier.padding(top = 16.dp).height(56.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (mismatch) {
                            Text(stringResource(R.string.pin_mismatch), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        } else if (!repeatStep && !isChange) {
                            Text(
                                text = stringResource(R.string.pin_setup_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    PinKeypad(
                        onDigit = { digit ->
                            if (current.length < PinRepository.LENGTH) {
                                current += digit
                                mismatch = false
                                if (current.length == PinRepository.LENGTH) onFull(current)
                            }
                        },
                        onBackspace = { current = current.dropLast(1) },
                        // На шаге "Повторите" можно вернуться к первому; при смене PIN — отменить
                        leftLabel = when {
                            repeatStep -> stringResource(R.string.pin_back)
                            isChange -> stringResource(R.string.dialog_cancel)
                            else -> null
                        },
                        onLeft = {
                            if (repeatStep) {
                                repeatStep = false
                                first = ""
                                current = ""
                            } else {
                                onBack()
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
