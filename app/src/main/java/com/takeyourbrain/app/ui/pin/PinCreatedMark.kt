package com.takeyourbrain.app.ui.pin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * "PIN создан": стрелка из логотипа обегает круг и замыкается вокруг галочки, потом [onFinished].
 * Стрелка по кругу у нас значит "цикл завершён".
 */
@Composable
fun PinCreatedMark(onFinished: () -> Unit) {
    val arc = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        arc.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        check.animateTo(1f, tween(300))
        delay(500)
        onFinished()
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(120.dp)) {
        val stroke = 7.dp.toPx()
        val radius = size.minDimension / 2 - stroke * 2
        val topLeft = Offset(center.x - radius, center.y - radius)
        // Дуга почти по кругу, как в логотипе: от "двух часов" по часовой стрелке
        val start = -60f
        val sweep = 320f * arc.value
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        // Остриё стрелки на конце дуги, когда она почти замкнулась
        if (arc.value > 0.9f) {
            val end = Math.toRadians((start + sweep).toDouble())
            val tip = Offset(center.x + radius * cos(end).toFloat(), center.y + radius * sin(end).toFloat())
            val tangent = Offset(-sin(end).toFloat(), cos(end).toFloat()) // направление движения по часовой
            val normal = Offset(cos(end).toFloat(), sin(end).toFloat())
            val len = stroke * 2.2f
            val head = Path().apply {
                moveTo(tip.x - tangent.x * len + normal.x * len, tip.y - tangent.y * len + normal.y * len)
                lineTo(tip.x, tip.y)
                lineTo(tip.x - tangent.x * len - normal.x * len, tip.y - tangent.y * len - normal.y * len)
            }
            drawPath(head, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Галочка рисуется после дуги
        if (check.value > 0f) {
            val full = Path().apply {
                moveTo(center.x - radius * 0.42f, center.y + radius * 0.02f)
                lineTo(center.x - radius * 0.1f, center.y + radius * 0.34f)
                lineTo(center.x + radius * 0.45f, center.y - radius * 0.3f)
            }
            val measure = PathMeasure().apply { setPath(full, false) }
            val part = Path()
            measure.getSegment(0f, measure.length * check.value, part, true)
            drawPath(part, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
