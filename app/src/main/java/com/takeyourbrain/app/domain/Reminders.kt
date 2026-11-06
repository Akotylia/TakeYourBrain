package com.takeyourbrain.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

/** Когда напоминать о задаче. */
sealed interface ReminderRule {
    /** Один раз в момент [at]. ("Через 2 часа" при сохранении тоже превращается в Once.) */
    data class Once(val at: LocalDateTime) : ReminderRule

    /**
     * Каждые [everyMinutes] минут, первый раз в [from].
     * [fromDone] = false — по сетке от [from] (10:00, 17:00, 00:00…);
     * true — следующий раз отсчитывается от момента, когда задачу отметили выполненной.
     */
    data class Interval(val everyMinutes: Int, val from: LocalDateTime, val fromDone: Boolean) : ReminderRule

    /** По дням недели [days] в [minuteOfDay] (минуты от полуночи). */
    data class Weekly(val days: Set<DayOfWeek>, val minuteOfDay: Int) : ReminderRule

    /** Каждый месяц [dayOfMonth] числа в [minuteOfDay]. Если в месяце меньше дней — в последний день. */
    data class Monthly(val dayOfMonth: Int, val minuteOfDay: Int) : ReminderRule
}

/** Тихие часы: напоминание, выпавшее на них, переносится на их конец. Время — минуты от полуночи. */
data class QuietHours(val startMinute: Int, val endMinute: Int)

/** Расчёт времени напоминаний. Чистая логика без Android — проверяется тестами. */
object Reminders {

    /**
     * Ближайшее срабатывание правила строго после момента [after] (null — больше не будет).
     * Для Interval "от выполнения" после первого раза следующий считается в [afterDone].
     */
    fun next(rule: ReminderRule, after: LocalDateTime): LocalDateTime? = when (rule) {
        is ReminderRule.Once -> rule.at.takeIf { it.isAfter(after) }

        is ReminderRule.Interval -> {
            if (rule.from.isAfter(after)) {
                rule.from
            } else {
                // Сколько целых шагов прошло с начала — следующий шаг после них
                val step = rule.everyMinutes.toLong().coerceAtLeast(1)
                val passed = Duration.between(rule.from, after).toMinutes() / step + 1
                var candidate = rule.from.plusMinutes(passed * step)
                // Защита от неточности при секундах: шаг должен быть строго позже
                while (!candidate.isAfter(after)) candidate = candidate.plusMinutes(step)
                candidate
            }
        }

        is ReminderRule.Weekly -> {
            if (rule.days.isEmpty()) {
                null
            } else {
                (0..7L).asSequence()
                    .map { after.toLocalDate().plusDays(it) }
                    .filter { it.dayOfWeek in rule.days }
                    .map { it.atStartOfDay().plusMinutes(rule.minuteOfDay.toLong()) }
                    .firstOrNull { it.isAfter(after) }
            }
        }

        is ReminderRule.Monthly -> (0..13L).asSequence()
            .map { after.toLocalDate().withDayOfMonth(1).plusMonths(it) }
            .map { month -> month.withDayOfMonth(minOf(rule.dayOfMonth, month.lengthOfMonth())) }
            .map { it.atStartOfDay().plusMinutes(rule.minuteOfDay.toLong()) }
            .firstOrNull { it.isAfter(after) }
    }

    /** Следующий раз после выполнения задачи в момент [doneAt] для правила "от выполнения". */
    fun afterDone(rule: ReminderRule.Interval, doneAt: LocalDateTime): LocalDateTime =
        doneAt.plusMinutes(rule.everyMinutes.toLong())

    /** Повторяется ли правило (после срабатывания будет следующий раз сам). */
    fun repeatsByItself(rule: ReminderRule): Boolean = when (rule) {
        is ReminderRule.Once -> false
        is ReminderRule.Interval -> !rule.fromDone
        is ReminderRule.Weekly, is ReminderRule.Monthly -> true
    }

    /** Перенести момент [time] на конец тихих часов, если он на них попадает. */
    fun applyQuietHours(time: LocalDateTime, quiet: QuietHours?): LocalDateTime {
        if (quiet == null || quiet.startMinute == quiet.endMinute) return time
        val minute = time.hour * 60 + time.minute
        val day = time.toLocalDate().atStartOfDay()
        val end = quiet.endMinute.toLong()
        return if (quiet.startMinute < quiet.endMinute) {
            // Например, 13:00–15:00 в пределах одного дня
            if (minute in quiet.startMinute until quiet.endMinute) day.plusMinutes(end) else time
        } else {
            // Через полночь, например 23:00–07:00
            when {
                minute >= quiet.startMinute -> day.plusDays(1).plusMinutes(end)
                minute < quiet.endMinute -> day.plusMinutes(end)
                else -> time
            }
        }
    }
}
