package com.takeyourbrain.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takeyourbrain.app.ui.common.LocalGlass
import com.takeyourbrain.app.ui.common.glassBarStyle
import com.takeyourbrain.app.ui.common.glassEdge
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Сколько места снизу занимает плавающая панель (на вкладках). Списки вкладок добавляют этот запас
 * в конец, чтобы последний пункт не прятался под панелью. На остальных экранах — 0.
 */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/** Включена ли лёгкая вибрация (Настройки → Внешний вид → Вибрация). */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Высота капсулы и отступ под ней — из них складывается [LocalBottomBarSpace]. */
private val BarHeight = 64.dp
private val BarBottomMargin = 12.dp
val FloatingBarSpace: Dp = BarHeight + BarBottomMargin + 8.dp

/** Одна вкладка панели. */
data class BarItem(val route: String, @param:StringRes val label: Int, @param:DrawableRes val icon: Int)

/**
 * Нижняя панель как в Telegram: отдельная "капсула" над содержимым (отступы со всех сторон, тень),
 * выбранная вкладка подсвечена таблеткой, которая плавно переезжает (~250 мс).
 * В стеклянном стиле капсула — размытое стекло: сквозь неё видно то, что под ней ([hazeState]).
 */
@Composable
fun FloatingNavBar(
    items: List<BarItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val glass = LocalGlass.current
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

    Surface(
        shape = RoundedCornerShape(50),
        // Чуть светлее карточек: в тёмной теме капсула не сливается с карточкой под ней
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceBright,
        border = if (glass) BorderStroke(1.dp, glassEdge()) else null,
        shadowElevation = 8.dp,
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = BarBottomMargin)
            .fillMaxWidth()
            .height(BarHeight)
    ) {
        if (glass) {
            // Стекло: размытое содержимое под капсулой (обрезается по её форме)
            val style = glassBarStyle()
            Box(Modifier.fillMaxSize().hazeEffect(state = hazeState, style = style))
        }
        BoxWithConstraints {
            val itemWidth = maxWidth / items.size
            // Подсветка выбранной вкладки едет к новой вкладке, а не прыгает
            val indicatorX by animateDpAsState(itemWidth * selectedIndex, tween(250), label = "indicator")
            Box(
                Modifier
                    .offset { IntOffset(indicatorX.roundToPx(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(6.dp)
                    // На стекле подсветка — полупрозрачная "капля"
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (glass) 0.9f else 1f),
                        RoundedCornerShape(50)
                    )
            )
            Row(Modifier.fillMaxHeight()) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(250),
                        label = "tabColor"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .selectable(selected = selected, role = Role.Tab) {
                                if (!selected) {
                                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(item.route)
                                }
                            }
                            .padding(top = 6.dp)
                    ) {
                        Icon(painterResource(item.icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                        // Длинные слова ("Приложения") не переносим, а чуть уменьшаем шрифт
                        Text(
                            text = stringResource(item.label),
                            color = color,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
