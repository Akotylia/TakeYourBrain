package com.takeyourbrain.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.takeyourbrain.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Рисунки в стиле логотипа: стрелка по кругу ("время идёт / цикл завершён") и мозг.

/**
 * Дуга-стрелка из логотипа, заполненная на [progress] (0..1) — как таймер. На конце остриё.
 * Под ней бледная дорожка [trackColor].
 */
fun DrawScope.drawArrowArc(progress: Float, color: Color, trackColor: Color, stroke: Float) {
    val radius = size.minDimension / 2 - stroke * 1.6f
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
    val start = -90f
    val sweep = 360f * progress.coerceIn(0f, 1f)
    if (sweep <= 0f) return
    drawArc(color, start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    // Остриё на конце дуги, по направлению движения (по часовой)
    val end = Math.toRadians((start + sweep).toDouble())
    val tip = Offset(center.x + radius * cos(end).toFloat(), center.y + radius * sin(end).toFloat())
    val tangent = Offset(-sin(end).toFloat(), cos(end).toFloat())
    val normal = Offset(cos(end).toFloat(), sin(end).toFloat())
    val len = stroke * 1.8f
    val head = Path().apply {
        moveTo(tip.x - tangent.x * len + normal.x * len, tip.y - tangent.y * len + normal.y * len)
        lineTo(tip.x, tip.y)
        lineTo(tip.x - tangent.x * len - normal.x * len, tip.y - tangent.y * len - normal.y * len)
    }
    drawPath(head, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Круг-таймер со стрелкой и содержимым в середине (время, мозг). */
@Composable
fun ArrowRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawArrowArc(progress, color, trackColor, strokeWidth.toPx()) }
        content()
    }
}

/** Загрузка: стрелка из логотипа крутится против часовой стрелки, вместо стандартного кружка. */
@Composable
fun ArrowLoader(modifier: Modifier = Modifier) {
    val angle by rememberInfiniteTransition(label = "loader").animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Icon(
        painter = painterResource(R.drawable.ic_loader),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { rotationZ = angle }
    )
}

// Половинки мозга из ic_brain (сетка 24×24)
private const val BRAIN_LEFT =
    "M11,5.5a2,2 0 0,0 -3.6,-0.6 2.2,2.2 0 0,0 -2.5,3 2.4,2.4 0 0,0 -0.9,4.1 2.4,2.4 0 0,0 0.9,4.1 2.2,2.2 0 0,0 2.5,3 2,2 0 0,0 3.6,-0.6z"
private const val BRAIN_RIGHT =
    "M13,5.5a2,2 0 0,1 3.6,-0.6 2.2,2.2 0 0,1 2.5,3 2.4,2.4 0 0,1 0.9,4.1 2.4,2.4 0 0,1 -0.9,4.1 2.2,2.2 0 0,1 -2.5,3 2,2 0 0,1 -3.6,-0.6z"

// Где у мозга верх и низ (в единицах сетки 24) — между ними "наливается" жидкость
private const val BRAIN_TOP = 3.4f
private const val BRAIN_BOTTOM = 20.7f

/**
 * Мозг из логотипа, который "наливается" жидкостью: [fill] от 0 (пусто) до 1 (полный).
 * Поверхность жидкости — мягкая волна, уровень плавно поднимается при открытии.
 */
@Composable
fun BrainTank(fill: Float, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(fill.coerceIn(0f, 1f), tween(1500), label = "level")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val halves = remember { listOf(BRAIN_LEFT, BRAIN_RIGHT).map { PathParser().parsePathString(it).toPath() } }
    val liquid = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        translate((size.width - 24 * scale) / 2, (size.height - 24 * scale) / 2) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                val surface = BRAIN_BOTTOM - level * (BRAIN_BOTTOM - BRAIN_TOP)
                halves.forEach { half ->
                    clipPath(half) {
                        // Две волны со сдвигом — жидкость "живая"
                        drawPath(wave(surface, phase, 0.45f), liquid.copy(alpha = 0.45f))
                        drawPath(wave(surface + 0.25f, phase + 1.6f, 0.35f), liquid.copy(alpha = 0.85f))
                    }
                    drawPath(half, outline, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** Жидкость: волнистая поверхность на высоте [surface] и всё, что ниже. */
private fun wave(surface: Float, phase: Float, amplitude: Float): Path = Path().apply {
    moveTo(0f, 24f)
    var x = 0f
    while (x <= 24f) {
        lineTo(x, surface + amplitude * sin(x * 0.7f + phase))
        x += 0.5f
    }
    lineTo(24f, 24f)
    close()
}
