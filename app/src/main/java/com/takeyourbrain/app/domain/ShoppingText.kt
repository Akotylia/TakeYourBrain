package com.takeyourbrain.app.domain

/** Чистая логика списка покупок: сравнение текста, подсказки, текст для "Поделиться". */
object ShoppingText {

    private val spaces = Regex("\\s+")

    /** "  Молоко   2,5% " → "молоко 2,5%": "Молоко" и "молоко" — одно и то же. */
    fun normalize(text: String): String = text.trim().replace(spaces, " ").lowercase()

    /**
     * Подсказки из ранее введённого ([history] уже отсортирована: частые сверху).
     * Сначала то, что начинается с [query], потом где [query] внутри. [exclude] — то, что уже в списке
     * (в виде [normalize]). Пустой запрос — просто самые частые.
     */
    fun suggest(history: List<String>, query: String, exclude: Set<String>, limit: Int = 6): List<String> {
        val q = normalize(query)
        val candidates = history.filter { normalize(it) !in exclude }
        if (q.isEmpty()) return candidates.take(limit)
        val starts = candidates.filter { normalize(it).let { n -> n.startsWith(q) && n != q } }
        val inside = candidates.filter { normalize(it).let { n -> !n.startsWith(q) && n.contains(q) } }
        return (starts + inside).take(limit)
    }

    /** Текст для "Поделиться": название списка и пункты (текст и количество). */
    fun share(listName: String, items: List<Pair<String, String>>): String = buildString {
        append(listName)
        items.forEach { (text, quantity) ->
            append("\n• ").append(text)
            if (quantity.isNotBlank()) append(" — ").append(quantity)
        }
    }
}
