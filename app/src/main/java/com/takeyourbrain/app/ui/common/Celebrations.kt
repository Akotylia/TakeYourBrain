package com.takeyourbrain.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.takeyourbrain.app.R
import com.takeyourbrain.app.notifications.AppSound
import com.takeyourbrain.app.ui.theme.LogoAccent
import com.takeyourbrain.app.ui.theme.LogoBgBottom

// "Маленькие праздники" на весь экран: фокус завершён и серия 7 / 30 дней.

/** Общий вид праздника: рисунок, подпись и кнопка на тёмном фоне логотипа. */
@Composable
private fun CelebrationDialog(
    title: String,
    text: String,
    button: String,
    onDismiss: () -> Unit,
    picture: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        LogoBackdrop {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                picture()
                Spacer(Modifier.height(8.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = LogoAccent, contentColor = LogoBgBottom),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(button) }
            }
        }
    }
}

/**
 * Время фокуса вышло, пока экран открыт: логотип с полной анимацией. Звук здесь не играем —
 * его уже играет FocusEndReceiver вместе с уведомлением.
 */
@Composable
fun FocusDoneDialog(taskTitle: String?, onDismiss: () -> Unit) {
    val time = rememberLogoTime()
    CelebrationDialog(
        title = stringResource(R.string.focus_finished_title),
        text = taskTitle ?: stringResource(R.string.focus_finished_text),
        button = stringResource(R.string.dialog_ok),
        onDismiss = onDismiss
    ) {
        AnimatedLogo(timeMs = { time.value }, modifier = Modifier.size(260.dp))
    }
}

/** Серия дошла до [days] дней: стрелка замыкает круг вокруг огонька, огонёк покачивается. Со звуком. */
@Composable
fun StreakDialog(days: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        AppSound.play(context, R.raw.tyb_logo_sound)
        ring.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
    }
    val sway by rememberInfiniteTransition(label = "flame").animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway"
    )
    CelebrationDialog(
        title = pluralStringResource(R.plurals.streak_days, days, days),
        text = stringResource(R.string.streak_celebrate_text),
        button = stringResource(R.string.streak_celebrate_ok),
        onDismiss = onDismiss
    ) {
        ArrowRing(
            progress = ring.value,
            modifier = Modifier.size(220.dp),
            strokeWidth = 8.dp,
            color = LogoAccent,
            trackColor = Color.White.copy(alpha = 0.12f)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_flame),
                contentDescription = null,
                tint = LogoAccent,
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        rotationZ = sway
                        // Огонёк "вспыхивает": растёт вместе со стрелкой
                        val grow = 0.7f + 0.3f * ring.value
                        scaleX = grow
                        scaleY = grow
                    }
            )
        }
    }
}
