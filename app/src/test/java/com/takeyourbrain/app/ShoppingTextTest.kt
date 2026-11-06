package com.takeyourbrain.app

import com.takeyourbrain.app.domain.ShoppingText
import org.junit.Assert.assertEquals
import org.junit.Test

/** Логика списка покупок: сравнение, подсказки, текст для "Поделиться". */
class ShoppingTextTest {

    @Test
    fun normalizeIgnoresCaseAndSpaces() {
        assertEquals("молоко 2,5%", ShoppingText.normalize("  Молоко   2,5% "))
        assertEquals(ShoppingText.normalize("Хлеб"), ShoppingText.normalize("хлеб "))
    }

    @Test
    fun suggestStartsFirstThenInside() {
        // История уже отсортирована: частое сверху
        val history = listOf("Сыр", "Молоко", "Масло сливочное", "Йогурт", "Кокосовое молоко")
        assertEquals(
            listOf("Молоко", "Масло сливочное", "Кокосовое молоко"),
            ShoppingText.suggest(history, "м", exclude = emptySet())
        )
        // Уже в списке — не подсказываем; точное совпадение тоже не нужно
        assertEquals(
            listOf("Кокосовое молоко"),
            ShoppingText.suggest(history, "молоко", exclude = emptySet())
        )
        assertEquals(
            listOf("Масло сливочное", "Кокосовое молоко"),
            ShoppingText.suggest(history, "М", exclude = setOf("молоко"))
        )
    }

    @Test
    fun emptyQueryGivesMostFrequent() {
        val history = listOf("Сыр", "Молоко", "Хлеб")
        assertEquals(listOf("Сыр", "Хлеб"), ShoppingText.suggest(history, " ", exclude = setOf("молоко"), limit = 2))
    }

    @Test
    fun shareText() {
        assertEquals(
            "Продукты\n• Молоко — 2 л\n• Хлеб",
            ShoppingText.share("Продукты", listOf("Молоко" to "2 л", "Хлеб" to ""))
        )
    }
}
