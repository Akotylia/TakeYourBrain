package com.takeyourbrain.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.takeyourbrain.app.R

/** Какая тема выбрана в Настройках → Внешний вид. */
enum class ThemeMode(@param:StringRes val label: Int) {
    SYSTEM(R.string.theme_system), // как в телефоне
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark);

    /** Тёмная ли тема сейчас (для "как в телефоне" смотрим на телефон). */
    @Composable
    fun isDark(): Boolean = when (this) {
        SYSTEM -> isSystemInDarkTheme()
        LIGHT -> false
        DARK -> true
    }
}
