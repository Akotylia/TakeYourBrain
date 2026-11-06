package com.takeyourbrain.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.takeyourbrain.app.ui.common.formatDuration
import kotlin.math.ceil

/** Ширина колонки справа с подписью шкалы ("1 ч"). */
private val ScaleWidth = 48.dp

/**
 * Столбчатая диаграмма: по столбику на день.
 * [values] — время в миллисекундах, [labels] — подписи дней под столбиками.
 * [selected] — выделенный столбик (null — все столбики одинаковые, режим "7 дней").
 * Нажатие на столбик или подпись вызывает [onSelect].
 */
@Composable
fun DailyBarChart(
    values: List<Long>,
    labels: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    accessibilityText: String,
    modifier: Modifier = Modifier,
) {
    val scaleMax = niceMax(values.maxOrNull() ?: 0L)
    val scaleLabel = formatDuration(scaleMax)
    val selectedLabel = selected?.let { formatDuration(values[it]) }

    val colors = MaterialTheme.colorScheme
    val barColor = colors.primary
    val dimBarColor = colors.primary.copy(alpha = 0.3f) // невыбранные дни — тише
    val gridColor = colors.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface, fontWeight = FontWeight.Medium)
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.semantics { contentDescription = accessibilityText }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(values.size) {
                    detectTapGestures { offset ->
                        val chartWidth = size.width - ScaleWidth.toPx()
                        if (offset.x < chartWidth) onSelect((offset.x / (chartWidth / values.size)).toInt().coerceIn(values.indices))
                    }
                }
        ) {
            val topSpace = 28.dp.toPx() // полоса сверху для подписи выбранного значения
            val chartWidth = size.width - ScaleWidth.toPx()
            val chartHeight = size.height - topSpace
            val slot = chartWidth / values.size
            val barWidth = minOf(slot * 0.55f, 28.dp.toPx())
            val radius = 4.dp.toPx()
            val line = 1.dp.toPx()

            // Линия шкалы сверху и основание снизу — тонкие и спокойные
            drawLine(gridColor, Offset(0f, topSpace), Offset(chartWidth, topSpace), strokeWidth = line)
            drawLine(gridColor, Offset(0f, size.height - line / 2), Offset(chartWidth, size.height - line / 2), strokeWidth = line)
            val scaleText = textMeasurer.measure(scaleLabel, labelStyle)
            drawText(scaleText, topLeft = Offset(chartWidth + 6.dp.toPx(), topSpace - scaleText.size.height / 2f))

            values.forEachIndexed { index, value ->
                if (value <= 0) return@forEachIndexed
                // Совсем маленькое время всё равно видно: минимум 3 dp
                val barHeight = maxOf(chartHeight * value / scaleMax, 3.dp.toPx())
                val left = slot * index + (slot - barWidth) / 2
                val top = size.height - barHeight
                // Скруглены только верхние углы, низ стоит ровно на основании
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = top, right = left + barWidth, bottom = size.height,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                        )
                    )
                }
                drawPath(path, if (selected == null || selected == index) barColor else dimBarColor)
            }

            // Значение выбранного дня — в верхней полосе над его столбиком (туда столбики не достают)
            if (selected != null && selectedLabel != null) {
                val text = textMeasurer.measure(selectedLabel, valueStyle)
                val centerX = slot * selected + slot / 2
                val x = (centerX - text.size.width / 2f).coerceIn(0f, chartWidth - text.size.width)
                drawText(text, topLeft = Offset(x, 0f))
            }
        }

        // Подписи дней. Их тоже можно нажимать — так удобнее и для TalkBack
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == selected) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(ScaleWidth))
        }
    }
}

/**
 * Верх шкалы — "круглое" значение не меньше самого большого столбика:
 * 15 мин, 30 мин, 1 ч, дальше целые часы. Так подпись шкалы легко читать.
 */
private fun niceMax(maxMs: Long): Long {
    val minutes = ceil(maxMs / 60_000.0).toLong()
    val niceMinutes = when {
        minutes <= 15 -> 15
        minutes <= 30 -> 30
        else -> ceil(minutes / 60.0).toLong() * 60
    }
    return niceMinutes * 60_000
}
