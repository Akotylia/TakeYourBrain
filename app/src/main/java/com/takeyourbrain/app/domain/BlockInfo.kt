package com.takeyourbrain.app.domain

/** Что показать на экране блокировки: какое приложение и почему. */
data class BlockInfo(
    val packageName: String,
    val decision: BlockDecision,
    /** Сколько минут банка стоит минута сейчас (1, 2, 3) или null, если банк здесь не работает. */
    val bankMultiplier: Int? = null,
)
