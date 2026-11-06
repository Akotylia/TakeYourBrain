package com.takeyourbrain.app.domain

/** Итог дня для серии-"огонька". */
enum class DayStatus {
    CLEAN,   // правила соблюдены
    BROKEN,  // взят пропуск или время сверх лимита
    NEUTRAL, // правил не было или день не удалось посчитать — серию не продлевает и не обрывает
}

/** Серия-"огонёк": сколько дней подряд без превышения лимитов. Чистая логика, без Android. */
object Streak {
    /** Сколько минут сверх лимита прощаем: время считается с точностью до минуты. */
    const val TOLERANCE_MINUTES = 1

    /** На сколько дней серии устраиваем маленький праздник. */
    val MILESTONES = listOf(7, 30)

    /** Какой рубеж пора отпраздновать; null — пока нечего. [celebrated] — последний отпразднованный. */
    fun milestoneToCelebrate(current: Int, celebrated: Int): Int? =
        MILESTONES.lastOrNull { it <= current }?.takeIf { it > celebrated }

    /** Серия оборвалась — рубежи можно праздновать заново. */
    fun celebratedAfter(current: Int, celebrated: Int): Int = if (current < celebrated) 0 else celebrated

    /**
     * Итог дня. [limits] — лимиты приложений в этот день (минуты), [usageMs] — сколько ими пользовались,
     * [bankSpent] — сколько минут куплено из банка (это честное время, не нарушение), [passes] — сколько взято пропусков.
     */
    fun dayStatus(
        hasRules: Boolean,
        limits: Map<String, Int>,
        usageMs: Map<String, Long>,
        bankSpent: Map<String, Int>,
        passes: Int,
    ): DayStatus {
        if (!hasRules) return DayStatus.NEUTRAL
        if (passes > 0) return DayStatus.BROKEN
        val over = limits.any { (pkg, limit) ->
            val usedMinutes = (usageMs[pkg] ?: 0L) / 60_000
            usedMinutes > limit + (bankSpent[pkg] ?: 0) + TOLERANCE_MINUTES
        }
        return if (over) DayStatus.BROKEN else DayStatus.CLEAN
    }

    /**
     * Серия, которую показываем: чистые дни подряд до вчера (нейтральные пропускаем) плюс сегодня,
     * если сегодня всё хорошо. Сегодня нарушили — огонёк погас, 0. [days] — прошедшие дни, от старых к новым.
     */
    fun current(days: List<DayStatus>, today: DayStatus): Int {
        if (today == DayStatus.BROKEN) return 0
        var count = if (today == DayStatus.CLEAN) 1 else 0
        for (status in days.asReversed()) {
            when (status) {
                DayStatus.CLEAN -> count++
                DayStatus.BROKEN -> return count
                DayStatus.NEUTRAL -> Unit
            }
        }
        return count
    }

    /** Лучшая серия за всю историю (вместе с сегодняшним днём). */
    fun best(days: List<DayStatus>, today: DayStatus): Int {
        var best = 0
        var run = 0
        for (status in days + today) {
            when (status) {
                DayStatus.CLEAN -> {
                    run++
                    best = maxOf(best, run)
                }
                DayStatus.BROKEN -> run = 0
                DayStatus.NEUTRAL -> Unit
            }
        }
        return best
    }
}
