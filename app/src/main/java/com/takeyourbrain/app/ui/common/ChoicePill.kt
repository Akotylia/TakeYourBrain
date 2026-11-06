package com.takeyourbrain.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Вариант выбора в виде "таблетки" (полностью скруглённые края, как в новом дизайне).
 * Выбранный залит основным цветом, остальные — [unselectedColor]: по умолчанию фон экрана,
 * чтобы таблетка была видна на карточке. Вне карточки передать цвет карточки.
 */
@Composable
fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    unselectedColor: Color = MaterialTheme.colorScheme.surface,
    horizontalPadding: Dp = 8.dp,
) {
    Surface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else unselectedColor,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            // Крупный шрифт в телефоне: текст не переносится, а чуть уменьшается
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp)
            )
        }
    }
}
