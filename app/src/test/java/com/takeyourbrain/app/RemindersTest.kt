package com.takeyourbrain.app

import com.takeyourbrain.app.domain.QuietHours
import com.takeyourbrain.app.domain.ReminderRule
import com.takeyourbrain.app.domain.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Проверяем расчёт напоминаний: разовые, интервалы, по дням недели, по месяцам, тихие часы. */
class RemindersTest {

    // 27 октября 2026 — вторник
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, month, day, hour, minute)

    @Test
    fun onceOnlyInFuture() {
        val rule = ReminderRule.Once(at(10, 27, 18))
        assertEquals(at(10, 27, 18), Reminders.next(rule, at(10, 27, 12)))
        assertNull(Reminders.next(rule, at(10, 27, 18)))
    }

    @Test
    fun intervalFollowsGrid() {
        // Каждые 7 часов с 10:00: 10, 17, 00, 07...
        val rule = ReminderRule.Interval(7 * 60, at(10, 27, 10), fromDone = false)
        assertEquals(at(10, 27, 10), Reminders.next(rule, at(10, 27, 9)))
        assertEquals(at(10, 27, 17), Reminders.next(rule, at(10, 27, 10)))
        assertEquals(at(10, 28, 0), Reminders.next(rule, at(10, 27, 20)))
        // Телефон был выключен двое суток — следующий шаг по той же сетке
        assertEquals(at(10, 29, 18), Reminders.next(rule, at(10, 29, 15)))
    }

    @Test
    fun intervalFromDone() {
        val rule = ReminderRule.Interval(120, at(10, 27, 10), fromDone = true)
        assertEquals(at(10, 27, 15, 30), Reminders.afterDone(rule, at(10, 27, 13, 30)))
    }

    @Test
    fun weeklyPicksNextDay() {
        // По субботам в 21:00; сегодня вторник
        val rule = ReminderRule.Weekly(setOf(DayOfWeek.SATURDAY), 21 * 60)
        assertEquals(at(10, 31, 21), Reminders.next(rule, at(10, 27, 12)))
        // Ровно в момент срабатывания — следующая суббота
        assertEquals(at(11, 7, 21), Reminders.next(rule, at(10, 31, 21)))
        // Пн/ср/пт в 8:00, сейчас вторник 12:00 — среда
        val workdays = ReminderRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 8 * 60)
        assertEquals(at(10, 28, 8), Reminders.next(workdays, at(10, 27, 12)))
    }

    @Test
    fun monthlyUsesLastDayInShortMonth() {
        val rule = ReminderRule.Monthly(31, 9 * 60)
        // 31 октября есть
        assertEquals(at(10, 31, 9), Reminders.next(rule, at(10, 27, 12)))
        // В ноябре 30 дней — 30 ноября
        assertEquals(at(11, 30, 9), Reminders.next(rule, at(10, 31, 9)))
        val first = ReminderRule.Monthly(1, 10 * 60)
        assertEquals(at(11, 1, 10), Reminders.next(first, at(10, 27, 12)))
    }

    @Test
    fun quietHoursMoveToTheirEnd() {
        val night = QuietHours(23 * 60, 7 * 60)
        assertEquals(at(10, 28, 7), Reminders.applyQuietHours(at(10, 27, 23, 30), night))
        assertEquals(at(10, 28, 7), Reminders.applyQuietHours(at(10, 28, 2), night))
        assertEquals(at(10, 28, 12), Reminders.applyQuietHours(at(10, 28, 12), night))
        // Без тихих часов ничего не меняется
        assertEquals(at(10, 28, 2), Reminders.applyQuietHours(at(10, 28, 2), null))
        val lunch = QuietHours(13 * 60, 14 * 60)
        assertEquals(at(10, 28, 14), Reminders.applyQuietHours(at(10, 28, 13, 15), lunch))
    }
}
