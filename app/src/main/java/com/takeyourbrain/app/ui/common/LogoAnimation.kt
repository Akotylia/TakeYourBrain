package com.takeyourbrain.app.ui.common

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.takeyourbrain.app.ui.theme.LogoAccent
import com.takeyourbrain.app.ui.theme.LogoBgBottom
import com.takeyourbrain.app.ui.theme.LogoBgTop

// Анимация логотипа, вариант А (docs/design/logo-animation/tyb_logo_animation.html): рисуются половинки мозга
// и извилины, зелёная стрелка обегает круг, выскакивает остриё, короткое свечение. Сетка рисунка — 1024×1024.

/** Сколько длится анимация логотипа, мс. */
const val LOGO_ANIMATION_MS = 2700

private val Hemispheres = listOf(
    "M482.0,315.0 A50.0,50.0 0 0 0 397.0,342.5 A54.6,54.6 0 0 0 334.8,417.5 A58.8,58.8 0 0 0 312.0,520.0 A58.8,58.8 0 0 0 334.8,622.5 A54.6,54.6 0 0 0 397.0,697.5 A50.0,50.0 0 0 0 482.0,725.0 Z",
    "M542.0,315.0 A50.0,50.0 0 0 1 627.0,342.5 A54.6,54.6 0 0 1 689.2,417.5 A58.8,58.8 0 0 1 712.0,520.0 A58.8,58.8 0 0 1 689.2,622.5 A54.6,54.6 0 0 1 627.0,697.5 A50.0,50.0 0 0 1 542.0,725.0 Z",
)

// Извилины: начало рисования (мс) и линия
private val Grooves = listOf(
    450 to "M427.0,385.0 C382.0,405.0 372.0,455.0 400.0,492.0",
    490 to "M327.0,480.0 C367.0,505.0 377.0,555.0 352.0,592.0",
    530 to "M434.0,622.0 C402.0,632.0 390.0,660.0 396.0,688.0",
    450 to "M597.0,385.0 C642.0,405.0 652.0,455.0 624.0,492.0",
    490 to "M697.0,480.0 C657.0,505.0 647.0,555.0 672.0,592.0",
    530 to "M590.0,622.0 C622.0,632.0 634.0,660.0 628.0,688.0",
)
private const val RING = "M322.7,241.7 A330,330 0 1 0 677.0,226.2"
private const val ARROW = "M716.7,177.5 L619.6,193.1 L654.7,284.9"
private val ArrowPivot = Offset(677f, 226.2f)
private val LogoCenter = Offset(512f, 512f)

private val DrawEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val SoftEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val PopEasing = CubicBezierEasing(0.34f, 1.9f, 0.64f, 1f) // пружинка: чуть больше 1 и обратно

private fun parse(data: String): Path = PathParser().parsePathString(data).toPath()

/** Часть [start]..[start]+[duration] (мс) на шкале времени [t]: от 0 до 1. */
private fun part(t: Float, start: Int, duration: Int, easing: Easing = LinearEasing): Float =
    easing.transform(((t - start) / duration).coerceIn(0f, 1f))

/** Время анимации в мс: запускается при появлении на экране. */
@Composable
fun rememberLogoTime(): Animatable<Float, AnimationVector1D> {
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(LOGO_ANIMATION_MS.toFloat(), tween(LOGO_ANIMATION_MS, easing = LinearEasing)) }
    return time
}

/** Логотип в момент времени [timeMs]. Размер задаёт [modifier] (рисунок вписывается в квадрат). */
@Composable
fun AnimatedLogo(timeMs: () -> Float, modifier: Modifier = Modifier) {
    val hemispheres = remember { Hemispheres.map(::parse) }
    val grooves = remember { Grooves.map { (start, data) -> start to parse(data) } }
    val ring = remember { parse(RING) }
    val arrow = remember { parse(ARROW) }
    val measure = remember { PathMeasure() }
    Canvas(modifier) {
        val t = timeMs()
        val unit = size.minDimension / 1024f
        translate((size.width - 1024 * unit) / 2, (size.height - 1024 * unit) / 2) {
            scale(unit, unit, pivot = Offset.Zero) {
                // Свечение вокруг в самом конце
                val glow = part(t, 1450, 240) * (1 - part(t, 1690, 960))
                if (glow > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(LogoAccent.copy(alpha = 0.25f * glow), Color.Transparent), LogoCenter, 470f),
                        470f,
                        LogoCenter
                    )
                }
                // Мозг: появляется с лёгким увеличением, в конце слегка "вздыхает"
                val appear = part(t, 50, 800, SoftEasing)
                val breath = part(t, 1500, 300, SoftEasing) - part(t, 1800, 300, SoftEasing)
                val brainScale = (0.86f + 0.14f * appear) * (1f + 0.045f * breath)
                val brainColor = Color.White.copy(alpha = part(t, 50, 360))
                scale(brainScale, brainScale, pivot = LogoCenter) {
                    val drawn = part(t, 100, 750, DrawEasing)
                    hemispheres.forEach { drawPart(measure, it, drawn, brainColor, 40f) }
                    grooves.forEach { (start, path) -> drawPart(measure, path, part(t, start, 450, SoftEasing), brainColor, 32f) }
                }
                // Зелёная стрелка обегает круг, на конце выскакивает остриё
                drawPart(measure, ring, part(t, 600, 900, DrawEasing), LogoAccent, 44f)
                val pop = part(t, 1420, 330, PopEasing)
                if (pop > 0f) {
                    scale(pop, pop, pivot = ArrowPivot) {
                        drawPath(arrow, LogoAccent, style = Stroke(44f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

/** Рисует начало линии [path]: долю [progress] от всей длины. */
private fun DrawScope.drawPart(measure: PathMeasure, path: Path, progress: Float, color: Color, width: Float) {
    if (progress <= 0f) return
    measure.setPath(path, false)
    val part = Path()
    measure.getSegment(0f, measure.length * progress, part, true)
    drawPath(part, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Тёмный фон логотипа на весь экран (в любой теме), значки строки состояния на нём светлые. */
@Composable
fun LogoBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val view = LocalView.current
    val activity = LocalActivity.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = bars?.isAppearanceLightStatusBars
        val lightNav = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            lightStatus?.let { bars.isAppearanceLightStatusBars = it }
            lightNav?.let { bars.isAppearanceLightNavigationBars = it }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(LogoBgTop, LogoBgBottom)))
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}
