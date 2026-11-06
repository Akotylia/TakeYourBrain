package com.takeyourbrain.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.takeyourbrain.app.R

// Общие "кирпичики" нового дизайна (docs/design/TYB-interface.md, п. 0): всё строим только из них,
// тогда приложение выглядит одинаково. Отступы: от краёв экрана 16, между карточками 12, внутри карточки 16.

/** Скругление карточки. */
val CardShape = RoundedCornerShape(24.dp)

/** Скругление элементов внутри карточки (кнопки, плитки, картинки). */
val InnerShape = RoundedCornerShape(16.dp)

/** Карточка — главный элемент: скругление 24, внутри отступ 16. [onClick] — если вся карточка нажимается. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = CardShape, color = color, modifier = modifier.fillMaxWidth(), content = inner)
    } else {
        Surface(shape = CardShape, color = color, modifier = modifier.fillMaxWidth(), content = inner)
    }
}

/** Поиск-"таблетка" как в Telegram: без рамки, залит цветом карточки, слева лупа, справа крестик "очистить". */
@Composable
fun SearchPill(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.apps_search_hint)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.apps_clear_search))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        // Кнопка "Поиск" на клавиатуре прячет клавиатуру
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    )
}

/** Иконка в скруглённом квадратике с бледным фоном (строки настроек, варианты выбора). */
@Composable
fun IconBadge(@DrawableRes icon: Int, highlighted: Boolean = false) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Строка настроек внутри карточки: иконка в квадратике, название, пояснение, справа — значение,
 * переключатель или стрелка ([trailing]).
 */
@Composable
fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
    // Фон даёт карточка вокруг строки
    if (onClick != null) Surface(onClick = onClick, color = Color.Transparent, modifier = modifier, content = row)
    else Surface(color = Color.Transparent, modifier = modifier, content = row)
}

/**
 * Один пункт карточки-группы: заголовок и строки лежат в одной скруглённой карточке.
 * У первого пункта ([first]) скруглён верх, у последнего ([last]) — низ. Для длинных списков в LazyColumn.
 */
@Composable
fun CardGroupItem(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(
        topStart = if (first) 24.dp else 0.dp,
        topEnd = if (first) 24.dp else 0.dp,
        bottomStart = if (last) 24.dp else 0.dp,
        bottomEnd = if (last) 24.dp else 0.dp
    )
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) { content() }
}

/** Пустой экран: мини-мозг из логотипа и одна спокойная фраза (вместо пустоты). */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brain),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
