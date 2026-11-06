package com.takeyourbrain.app

import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.data.db.RuleWithIntervals
import com.takeyourbrain.app.data.db.ScheduleInterval
import com.takeyourbrain.app.data.db.ScheduleMode
import com.takeyourbrain.app.data.db.daysToMask
import com.takeyourbrain.app.domain.BlockReason
import com.takeyourbrain.app.domain.BlockWarning
import com.takeyourbrain.app.domain.RuleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Проверяем логику правил: лимит, расписания, переход через полночь. */
class RuleEvaluatorTest {

    // 27 октября 2026 — вторник
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private fun rule(
        fullBlock: Boolean = false,
        limit: Int? = null,
        mode: ScheduleMode = ScheduleMode.NONE,
        vararg intervals: ScheduleInterval,
    ) = RuleWithIntervals(
        AppRule(
            packageName = "pkg",
            fullBlock = fullBlock,
            weekdayLimitMinutes = limit,
            weekendLimitMinutes = limit,
            scheduleMode = mode,
        ),
        intervals.toList(),
    )

    private fun interval(start: Int, end: Int, days: Set<DayOfWeek> = DayOfWeek.entries.toSet()) =
        ScheduleInterval(packageName = "pkg", startMinute = start, endMinute = end, daysMask = daysToMask(days))

    @Test
    fun noConditionsMeansNoBlock() {
        assertNull(RuleEvaluator.evaluate(rule(limit = 30), usageTodayMs = 10 * 60_000L, now = at(27, 12)))
    }

    @Test
    fun limitReachedBlocksUntilMidnight() {
        val decision = RuleEvaluator.evaluate(rule(limit = 30), usageTodayMs = 30 * 60_000L, now = at(27, 12))!!
        assertEquals(BlockReason.LimitReached(30), decision.reason)
        assertEquals(at(28, 0), decision.unlockAt)
    }

    @Test
    fun fullBlockHasNoUnlockTime() {
        val decision = RuleEvaluator.evaluate(rule(fullBlock = true, limit = 30), 60 * 60_000L, at(27, 12))!!
        assertEquals(BlockReason.FullBlock, decision.reason)
        assertNull(decision.unlockAt)
    }

    @Test
    fun blockedPeriodAcrossMidnight() {
        val r = rule(mode = ScheduleMode.BLOCK_DURING, intervals = arrayOf(interval(23 * 60, 7 * 60)))
        // 02:00 ночи — внутри периода, начавшегося вчера в 23:00
        val decision = RuleEvaluator.evaluate(r, 0, at(27, 2))!!
        assertEquals(BlockReason.InsideBlocked(at(27, 7)), decision.reason)
        // 12:00 — свободно
        assertNull(RuleEvaluator.evaluate(r, 0, at(27, 12)))
    }

    @Test
    fun crossMidnightUsesStartDay() {
        // Период только по пятницам 23:00–07:00: в субботу в 03:00 ещё заблокировано
        val r = rule(mode = ScheduleMode.BLOCK_DURING, intervals = arrayOf(interval(23 * 60, 7 * 60, setOf(DayOfWeek.FRIDAY))))
        assertTrue(RuleEvaluator.evaluate(r, 0, at(31, 3)) != null) // 31.10 — суббота
        assertNull(RuleEvaluator.evaluate(r, 0, at(27, 3)))          // вторник
    }

    @Test
    fun allowOnlyShowsNextPeriod() {
        val r = rule(mode = ScheduleMode.ALLOW_ONLY, intervals = arrayOf(interval(18 * 60, 20 * 60)))
        assertNull(RuleEvaluator.evaluate(r, 0, at(27, 19)))
        val decision = RuleEvaluator.evaluate(r, 0, at(27, 12))!!
        val reason = decision.reason as BlockReason.OutsideAllowed
        assertEquals(at(27, 18), reason.next!!.start)
        assertEquals(at(27, 20), reason.next.end)
        assertEquals(at(27, 18), decision.unlockAt)
    }

    @Test
    fun adjacentBlockedPeriodsAreJoined() {
        val r = rule(
            mode = ScheduleMode.BLOCK_DURING,
            intervals = arrayOf(interval(9 * 60, 12 * 60), interval(12 * 60, 14 * 60))
        )
        assertEquals(at(27, 14), RuleEvaluator.evaluate(r, 0, at(27, 10))!!.unlockAt)
    }

    @Test
    fun warnsBeforeLimitEnds() {
        val fiveMin = 5 * 60_000L
        // Лимит 30 мин, использовано 27 — осталось 3 минуты, предупреждаем
        val warnings = RuleEvaluator.upcomingWarnings(rule(limit = 30), 27 * 60_000L, at(27, 12), fiveMin)
        assertEquals(listOf(BlockWarning.LimitEndsSoon(3 * 60_000L)), warnings)
        // Использовано 20 — ещё рано
        assertTrue(RuleEvaluator.upcomingWarnings(rule(limit = 30), 20 * 60_000L, at(27, 12), fiveMin).isEmpty())
    }

    @Test
    fun warnsBeforeAllowedPeriodEnds() {
        val r = rule(mode = ScheduleMode.ALLOW_ONLY, intervals = arrayOf(interval(18 * 60, 20 * 60)))
        val warnings = RuleEvaluator.upcomingWarnings(r, 0, at(27, 19, 57), 5 * 60_000L)
        assertEquals(listOf(BlockWarning.AllowedPeriodEnds(at(27, 20))), warnings)
    }

    @Test
    fun warnsBeforeBlockedPeriodStarts() {
        val r = rule(mode = ScheduleMode.BLOCK_DURING, intervals = arrayOf(interval(23 * 60, 7 * 60)))
        val warnings = RuleEvaluator.upcomingWarnings(r, 0, at(27, 22, 58), 5 * 60_000L)
        assertEquals(listOf(BlockWarning.BlockedPeriodStarts(at(27, 23))), warnings)
    }

    @Test
    fun longestBlockWins() {
        // Лимит исчерпан (до полуночи) и идёт блок-период до 14:00 — показываем лимит
        val r = rule(limit = 10, mode = ScheduleMode.BLOCK_DURING, intervals = arrayOf(interval(9 * 60, 14 * 60)))
        val decision = RuleEvaluator.evaluate(r, 20 * 60_000L, at(27, 10))!!
        assertEquals(BlockReason.LimitReached(10), decision.reason)
    }
}
