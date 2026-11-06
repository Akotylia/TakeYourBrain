package com.takeyourbrain.app.domain

/** Сколько минут банка стоит одна минута в заблокированном приложении. */
object BankPricing {
    /** Вне окна "разрешено только": 1 минута стоит 2 минуты банка. */
    const val OUTSIDE_ALLOWED = 2

    /** Внутри окна "заблокировано" (если в настройках разрешено выкупать): 1 минута = 3 минуты банка. */
    const val INSIDE_BLOCKED = 3

    /** Варианты, сколько минут можно взять из банка за раз. */
    val SPEND_OPTIONS = listOf(10, 15, 30)

    /**
     * Цена минуты для всех нарушенных сейчас условий [reasons]: берём самое дорогое.
     * null — банк здесь не работает (полная блокировка, фокус, запрещённое окно без разрешения выкупа).
     */
    fun multiplier(reasons: List<BlockReason>, insideBlockedBuyable: Boolean): Int? {
        if (reasons.isEmpty()) return null
        var result = 1
        for (reason in reasons) {
            val price = when (reason) {
                BlockReason.FullBlock, is BlockReason.FocusMode -> return null
                is BlockReason.LimitReached -> 1
                is BlockReason.OutsideAllowed -> OUTSIDE_ALLOWED
                is BlockReason.InsideBlocked -> if (insideBlockedBuyable) INSIDE_BLOCKED else return null
            }
            result = maxOf(result, price)
        }
        return result
    }

    /**
     * Сколько минут добавить в банк за прошедший день: сэкономленное время ([earnableMinutes] минус
     * использованное), но так, чтобы баланс не превысил потолок [cap].
     */
    fun dailyEarning(earnableMinutes: Int, usedMs: Long, balance: Int, cap: Int): Int {
        val usedMinutes = ((usedMs + 59_999) / 60_000).toInt() // неполная минута считается использованной
        val saved = (earnableMinutes - usedMinutes).coerceAtLeast(0)
        return saved.coerceAtMost((cap - balance).coerceAtLeast(0))
    }
}
