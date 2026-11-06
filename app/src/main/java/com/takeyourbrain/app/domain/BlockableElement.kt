package com.takeyourbrain.app.domain

/**
 * Часть приложения, которую можно заблокировать, не блокируя приложение целиком.
 * Узнаём её на экране по id элементов (их видно через службу специальных возможностей).
 * Новый элемент — новая строка здесь, название и пояснение — в RuleEditScreen.
 *
 * Внимание: id — внутренние имена приложения, после его обновления они могут смениться.
 * Тогда смотрим новые через отладочный дамп (Настройки → Для разработчика) и дописываем сюда.
 */
enum class BlockableElement(
    val id: String,               // так элемент хранится в правиле
    val packageName: String,
    val viewIds: List<String>,    // любой из них виден на экране — значит, элемент открыт
) {
    YOUTUBE_SHORTS(
        id = "youtube_shorts",
        packageName = "com.google.android.youtube",
        viewIds = listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_player",
            "com.google.android.youtube:id/shorts_player_container",
        ),
    );

    companion object {
        /** Элементы, которые можно заблокировать в приложении [packageName]. */
        fun forPackage(packageName: String): List<BlockableElement> = entries.filter { it.packageName == packageName }

        fun byId(id: String): BlockableElement? = entries.firstOrNull { it.id == id }
    }
}
