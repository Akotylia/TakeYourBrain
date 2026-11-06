package com.takeyourbrain.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Светлая тема: мятный фон, белые карточки (они "всплывают" над фоном, как в тёмной) и глубокий зелёный.
// Карточки — это surfaceContainerHighest, поэтому самый "высокий" слой здесь белый.
private val LightColorScheme = lightColorScheme(
    primary = Forest40,
    onPrimary = Color.White,
    primaryContainer = Forest90,
    onPrimaryContainer = Forest30,
    inversePrimary = Sage80,
    secondary = Moss40,
    onSecondary = Color.White,
    secondaryContainer = Moss90,
    onSecondaryContainer = Moss30,
    tertiary = Water40,
    onTertiary = Color.White,
    tertiaryContainer = Water90,
    onTertiaryContainer = Lake30,
    background = Mint,
    onBackground = Ink,
    surface = Mint,
    onSurface = Ink,
    surfaceVariant = MintDeep,
    onSurfaceVariant = InkSoft,
    surfaceTint = Forest40,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    outline = InkLine,
    outlineVariant = MintCardEdge,
    surfaceBright = Color.White,
    surfaceDim = MintDeep,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

// Тёмная тема: "ночной лес" — глубокий зелёно-графитовый фон и мягкий светлый зелёный
private val DarkColorScheme = darkColorScheme(
    primary = Sage80,
    onPrimary = Sage20,
    primaryContainer = Sage30,
    onPrimaryContainer = Sage90,
    inversePrimary = Sage40,
    secondary = Moss80,
    onSecondary = Moss20,
    secondaryContainer = Moss30,
    onSecondaryContainer = Moss90,
    tertiary = Lake80,
    onTertiary = Lake20,
    tertiaryContainer = Lake30,
    onTertiaryContainer = Lake90,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceTint = Sage80,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    surfaceBright = Neutral24,
    surfaceDim = Neutral6,
    surfaceContainerLowest = Neutral4,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,
)

/**
 * Тема приложения. Светлая или тёмная — как выбрано в Настройках ([themeMode]), по умолчанию как в телефоне.
 * Цвета из обоев (Material You) выключены, чтобы всегда была наша спокойная зелёная палитра.
 * [glass] — стеклянный стиль: карточки (surfaceContainerHighest) полупрозрачные, сквозь них видны пятна фона.
 */
@Composable
fun TakeYourBrainTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = themeMode.isDark(),
    dynamicColor: Boolean = false,
    glass: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = if (glass) {
        baseScheme.copy(surfaceContainerHighest = baseScheme.surfaceContainerHighest.copy(alpha = 0.7f))
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
