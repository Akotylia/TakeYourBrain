package com.takeyourbrain.app

import com.takeyourbrain.app.domain.DayStatus.BROKEN
import com.takeyourbrain.app.domain.DayStatus.CLEAN
import com.takeyourbrain.app.domain.DayStatus.NEUTRAL
import com.takeyourbrain.app.domain.Streak
import org.junit.Assert.assertEquals
import org.junit.Test

/** Серия-"огонёк": итог дня и подсчёт дней подряд. */
class StreakTest {

    private val min = 60_000L

    @Test
    fun dayWithinLimitsIsClean() {
        val status = Streak.dayStatus(true, mapOf("yt" to 45), mapOf("yt" to 45 * min), emptyMap(), passes = 0)
        assertEquals(CLEAN, status)
    }

    @Test
    fun overLimitBreaks_butBankTimeIsAllowed() {
        val usage = mapOf("yt" to 60 * min)
        assertEquals(BROKEN, Streak.dayStatus(true, mapOf("yt" to 45), usage, emptyMap(), passes = 0))
        // 15 минут куплены из банка — это честно
        assertEquals(CLEAN, Streak.dayStatus(true, mapOf("yt" to 45), usage, mapOf("yt" to 15), passes = 0))
        // Минуту сверх прощаем (неточность подсчёта)
        assertEquals(CLEAN, Streak.dayStatus(true, mapOf("yt" to 45), mapOf("yt" to 46 * min), emptyMap(), passes = 0))
    }

    @Test
    fun passBreaks_noRulesIsNeutral() {
        assertEquals(BROKEN, Streak.dayStatus(true, emptyMap(), emptyMap(), emptyMap(), passes = 1))
        assertEquals(NEUTRAL, Streak.dayStatus(false, emptyMap(), emptyMap(), emptyMap(), passes = 0))
    }

    @Test
    fun currentCountsBackFromToday() {
        val days = listOf(CLEAN, BROKEN, CLEAN, NEUTRAL, CLEAN)
        assertEquals(3, Streak.current(days, today = CLEAN)) // сегодня + 2 чистых дня, нейтральный пропущен
        assertEquals(2, Streak.current(days, today = NEUTRAL))
        assertEquals(0, Streak.current(days, today = BROKEN)) // сегодня нарушили — огонёк погас
    }

    @Test
    fun bestIsLongestRun() {
        val days = listOf(CLEAN, CLEAN, CLEAN, BROKEN, CLEAN)
        assertEquals(3, Streak.best(days, today = CLEAN))
        assertEquals(4, Streak.best(listOf(CLEAN, CLEAN, NEUTRAL, CLEAN), today = CLEAN))
    }

    @Test
    fun milestonesAreCelebratedOnce() {
        assertEquals(null, Streak.milestoneToCelebrate(current = 6, celebrated = 0))
        assertEquals(7, Streak.milestoneToCelebrate(current = 7, celebrated = 0))
        assertEquals(null, Streak.milestoneToCelebrate(current = 8, celebrated = 7)) // уже праздновали
        assertEquals(30, Streak.milestoneToCelebrate(current = 30, celebrated = 7))
        assertEquals(30, Streak.milestoneToCelebrate(current = 40, celebrated = 0)) // только самый большой
    }

    @Test
    fun brokenStreakAllowsCelebratingAgain() {
        assertEquals(0, Streak.celebratedAfter(current = 2, celebrated = 7))
        assertEquals(7, Streak.celebratedAfter(current = 9, celebrated = 7))
        assertEquals(7, Streak.milestoneToCelebrate(current = 7, Streak.celebratedAfter(current = 7, celebrated = 30)))
    }
}
