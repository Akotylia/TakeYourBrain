package com.takeyourbrain.app.ui.pin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takeyourbrain.app.R
import com.takeyourbrain.app.ui.navigation.LocalHapticsEnabled

/**
 * Кружки PIN: пустые — контур, заполненные — цвет акцента (с маленькой анимацией).
 * Когда [errorKey] меняется — кружки трясутся и краснеют.
 */
@Composable
fun PinDots(count: Int, filled: Int, error: Boolean, errorKey: Int) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorKey) {
        if (errorKey > 0) {
            shake.animateTo(0f, keyframes {
                durationMillis = 400
                -24f at 50
                24f at 120
                -16f at 190
                16f at 260
                -6f at 330
            })
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }
    ) {
        repeat(count) { index ->
            val isFilled = index < filled
            val color by animateColorAsState(
                when {
                    error -> MaterialTheme.colorScheme.error
                    isFilled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                label = "dotColor"
            )
            // Заполнилась — кружок чуть "подпрыгивает"
            val scale by animateFloatAsState(if (isFilled) 1.15f else 1f, label = "dotScale")
            Box(
                Modifier
                    .size(16.dp)
                    .scale(scale)
                    .then(if (isFilled || error) Modifier.background(color, CircleShape) else Modifier.border(2.dp, color, CircleShape))
            )
        }
    }
}

/**
 * Цифровая клавиатура как в банковских приложениях: крупные тонкие цифры без рамок,
 * внизу слева — текст-действие ([leftLabel], например "Назад"), справа — "стереть".
 */
@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean = true,
    leftLabel: String? = null,
    onLeft: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val haptics = LocalHapticsEnabled.current
    fun tap(action: () -> Unit) {
        if (!enabled) return
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        action()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("123", "456", "789").forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { digit -> Key(onClick = { tap { onDigit(digit) } }) { DigitText(digit) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (leftLabel != null) {
                Key(onClick = onLeft) {
                    Text(leftLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Box(Modifier.size(KeySize))
            }
            Key(onClick = { tap { onDigit('0') } }) { DigitText('0') }
            Key(onClick = { tap(onBackspace) }) {
                Icon(painterResource(R.drawable.ic_backspace), stringResource(R.string.pin_backspace), modifier = Modifier.size(28.dp))
            }
        }
    }
}

private val KeySize = 80.dp

/** Кнопка клавиатуры: при нажатии под ней на миг появляется светлый круг. */
@Composable
private fun Key(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(KeySize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun DigitText(digit: Char) {
    Text(digit.toString(), fontSize = 36.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
}
