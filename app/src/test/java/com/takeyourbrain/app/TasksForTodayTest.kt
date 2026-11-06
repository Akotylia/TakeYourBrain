package com.takeyourbrain.app

import com.takeyourbrain.app.data.db.ReminderType
import com.takeyourbrain.app.data.db.Task
import com.takeyourbrain.app.data.tasksForToday
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Экран блокировки показывает только дела на сегодня. */
class TasksForTodayTest {

    private val zone = ZoneOffset.UTC
    private fun ms(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).toInstant(zone).toEpochMilli()

    // Сейчас четверг, 29 октября, 12:00
    private val now = ms(29, 12)

    private fun task(id: Long, type: ReminderType, nextFireAt: Long? = null, firedAt: Long? = null, done: Boolean = false) =
        Task(id = id, title = "t$id", createdAt = 0, reminderType = type, nextFireAt = nextFireAt, firedAt = firedAt, done = done)

    @Test
    fun onlyTodaysTasks() {
        val english = task(1, ReminderType.WEEKLY, nextFireAt = ms(30, 18))     // пятница — не сегодня
        val laterToday = task(2, ReminderType.ONCE, nextFireAt = ms(29, 20))    // сегодня вечером
        val fired = task(3, ReminderType.WEEKLY, nextFireAt = ms(2, 9) + 0, firedAt = ms(29, 9)) // сработала, ждёт "Готово"
        val noReminder = task(4, ReminderType.NONE)                              // без даты — всегда к делу
        val done = task(5, ReminderType.NONE, done = true)
        val tomorrowMorning = task(6, ReminderType.ONCE, nextFireAt = ms(30, 0)) // ровно полночь — уже завтра

        val result = tasksForToday(listOf(english, laterToday, fired, noReminder, done, tomorrowMorning), now, zone)
        assertEquals(listOf(3L, 2L, 4L), result.map { it.id })
    }
}
