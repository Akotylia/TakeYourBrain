package com.takeyourbrain.app

import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.data.db.RuleWithIntervals
import com.takeyourbrain.app.data.db.ScheduleInterval
import com.takeyourbrain.app.data.db.ScheduleMode
import com.takeyourbrain.app.data.db.daysToMask
import com.takeyourbrain.app.domain.BankPricing
import com.takeyourbrain.app.domain.BlockReason
import com.takeyourbrain.app.domain.RuleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Лимит будни/выходные, ожидание снятия полной блокировки, цены и начисление банка. */
class RulesV2Test {

    private val zone: ZoneId = ZoneOffset.UTC

    // 27 октября 2026 — вторник, 31 октября — суббота
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private fun ms(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

    private fun rule(
        weekday: Int? = null,
        weekend: Int? = null,
        fullBlock: Boolean = false,
        offAt: Long? = null,
        mode: ScheduleMode = ScheduleMode.NONE,
        intervals: List<ScheduleInterval> = emptyList(),
    ) = RuleWithIntervals(AppRule("pkg", fullBlock, weekday, weekend, mode, offAt), intervals)

    private fun interval(start: Int, end: Int) =
        ScheduleInterval(packageName = "pkg", startMinute = start, endMinute = end, daysMask = daysToMask(DayOfWeek.entries.toSet()))

    @Test
    fun weekdayAndWeekendLimitsAreSeparate() {
        val r = rule(weekday = 30, weekend = 120)
        val used = 60 * 60_000L
        // Вторник: лимит 30 мин — блок
        assertEquals(BlockReason.LimitReached(30), RuleEvaluator.evaluate(r, used, at(27, 12), zone)!!.reason)
        // Суббота: лимит 2 ч — ещё можно
        assertNull(RuleEvaluator.evaluate(r, used, at(31, 12), zone))
    }

    @Test
    fun noWeekendLimitMeansFreeOnWeekend() {
        val r = rule(weekday = 30, weekend = null)
        assertNull(RuleEvaluator.evaluate(r, 5 * 60 * 60_000L, at(31, 12), zone))
    }

    @Test
    fun fullBlockStaysUntilWaitEnds() {
        val r = rule(fullBlock = true, offAt = ms(at(27, 12, 10)), weekday = 600)
        // В 12:05 ещё блок, и показываем время снятия
        val decision = RuleEvaluator.evaluate(r, 0, at(27, 12, 5), zone)!!
        assertEquals(BlockReason.FullBlock, decision.reason)
        assertEquals(at(27, 12, 10), decision.unlockAt)
        // В 12:10 ожидание прошло — полная блокировка больше не действует
        assertNull(RuleEvaluator.evaluate(r, 0, at(27, 12, 10), zone))
    }

    @Test
    fun fullBlockWinsOverLongerReasons() {
        // Полная блокировка снимется через 5 минут, а лимит держит до полуночи — всё равно показываем полную
        val r = rule(fullBlock = true, offAt = ms(at(27, 12, 10)), weekday = 10)
        val decision = RuleEvaluator.evaluate(r, 60 * 60_000L, at(27, 12, 5), zone)!!
        assertEquals(BlockReason.FullBlock, decision.reason)
    }

    @Test
    fun pricingTakesMostExpensiveReason() {
        val limit = BlockReason.LimitReached(30)
        val outside = BlockReason.OutsideAllowed(null)
        val inside = BlockReason.InsideBlocked(at(27, 20))
        assertEquals(1, BankPricing.multiplier(listOf(limit), insideBlockedBuyable = false))
        assertEquals(2, BankPricing.multiplier(listOf(limit, outside), insideBlockedBuyable = false))
        assertNull(BankPricing.multiplier(listOf(limit, inside), insideBlockedBuyable = false))
        assertEquals(3, BankPricing.multiplier(listOf(limit, inside), insideBlockedBuyable = true))
        assertNull(BankPricing.multiplier(listOf(BlockReason.FullBlock), insideBlockedBuyable = true))
        assertNull(BankPricing.multiplier(listOf(BlockReason.FocusMode(at(27, 20))), insideBlockedBuyable = true))
    }

    @Test
    fun earningRespectsCapAndRoundsUsageUp() {
        // Лимит 30, использовано 10 мин 30 с (= 11 мин) — сэкономлено 19
        assertEquals(19, BankPricing.dailyEarning(30, 10 * 60_000L + 30_000L, balance = 0, cap = 180))
        // Баланс 170 из 180 — влезает только 10
        assertEquals(10, BankPricing.dailyEarning(30, 0, balance = 170, cap = 180))
        // Перерасход — ничего
        assertEquals(0, BankPricing.dailyEarning(30, 45 * 60_000L, balance = 0, cap = 180))
    }

    @Test
    fun earnableMinutesLimitedBySchedule() {
        val tuesday = LocalDate.of(2026, 10, 27)
        // Разрешено только 18:00–19:00, лимит 2 ч — сэкономить можно не больше часа
        val allowHour = rule(weekday = 120, mode = ScheduleMode.ALLOW_ONLY, intervals = listOf(interval(18 * 60, 19 * 60)))
        assertEquals(60, RuleEvaluator.earnableMinutes(allowHour, tuesday))
        // Без лимита и при полной блокировке банк не копится
        assertNull(RuleEvaluator.earnableMinutes(rule(), tuesday))
        assertNull(RuleEvaluator.earnableMinutes(rule(weekday = 30, fullBlock = true), tuesday))
        // Лимит только в будни: в субботу не копится
        assertNull(RuleEvaluator.earnableMinutes(rule(weekday = 30), LocalDate.of(2026, 10, 31)))
        assertEquals(30, RuleEvaluator.earnableMinutes(rule(weekday = 30), tuesday))
    }
}
