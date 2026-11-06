package com.takeyourbrain.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

// Стеклянный стиль (Настройки → Внешний вид): панель размывает то, что под ней, карточки полупрозрачные,
// за окнами размыт фон, а на фоне экрана — мягкие цветные пятна, чтобы стеклу было что размывать.

/** Включён ли стеклянный стиль. */
val LocalGlass = compositionLocalOf { false }

/**
 * Фон всех экранов: цвет темы, а в стеклянном стиле ещё 3 размытых пятна (мятное, бирюзовое, персиковое),
 * как в макете `TYB-logo-animation.html`. Сами экраны прозрачные и лежат поверх.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val glass = LocalGlass.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        // Экраны прозрачные и не задают цвет текста и значков сами — задаём его здесь
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        if (glass) {
            // В тёмной теме пятна приглушённее, иначе "ночной лес" становится пёстрым
            val k = if (dark) 0.5f else 1f
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                blob(Color(0xFF5CF2B4).copy(alpha = 0.35f * k), Offset(w * 0.1f, h * 0.72f), w * 0.75f)
                blob(Color(0xFF6FC7D6).copy(alpha = 0.35f * k), Offset(w * 0.95f, h * 0.92f), w * 0.7f)
                blob(Color(0xFFF5C6A5).copy(alpha = 0.22f * k), Offset(w * 0.6f, h * 0.35f), w * 0.55f)
            }
        }
        content()
        }
    }
}

/** Размытое пятно: цвет в середине плавно уходит в прозрачность к краю. */
private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
}

/** Стекло нижней панели: размытие ~22 dp и полупрозрачный слой цвета панели поверх. */
@Composable
fun glassBarStyle(): HazeStyle {
    val bar = MaterialTheme.colorScheme.surfaceBright
    return HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.background,
        tint = HazeTint(bar.copy(alpha = 0.55f)),
        blurRadius = 22.dp,
        noiseFactor = 0.08f
    )
}

/** Тонкая светлая обводка стекла: на тёмном фоне белый виден сильнее, поэтому слабее. */
@Composable
fun glassEdge(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Color.White.copy(alpha = if (dark) 0.18f else 0.6f)
}

/** Верхняя панель без своего фона: под ней виден общий фон экрана (и пятна в стеклянном стиле). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun clearTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)

/** Сколько окон сейчас открыто: пока есть хоть одно, корень навигации размывает экран за ними. */
val LocalOpenDialogs = compositionLocalOf { mutableIntStateOf(0) }

/**
 * Вызывать внутри содержимого окна или шторки: в стеклянном стиле экран за ними размывается.
 * Размываем сами, а не средствами Android: системное размытие между окнами есть не на всех телефонах
 * (на Samsung A56 его нет).
 */
@Composable
fun DialogBlurBehind() {
    val openDialogs = LocalOpenDialogs.current
    DisposableEffect(openDialogs) {
        openDialogs.intValue++
        onDispose { openDialogs.intValue-- }
    }
}

/** Обычное окно-вопрос Material 3, но в стеклянном стиле фон за ним размыт. */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            DialogBlurBehind()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text
    )
}
