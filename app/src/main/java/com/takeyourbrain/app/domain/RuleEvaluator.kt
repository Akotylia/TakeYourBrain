package com.takeyourbrain.app.domain

import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.data.db.RuleWithIntervals
import com.takeyourbrain.app.data.db.ScheduleInterval
import com.takeyourbrain.app.data.db.ScheduleMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Почему приложение заблокировано. */
sealed interface BlockReason {
    /** Включена полная блокировка. Пропуски и банк на неё не действуют. */
    data object FullBlock : BlockReason

    /** Дневной лимит исчерпан. */
    data class LimitReached(val limitMinutes: Int) : BlockReason

    /** "Разрешено только в эти периоды", а сейчас не такой период. next — ближайший разрешённый (или null). */
    data class OutsideAllowed(val next: Occurrence?) : BlockReason

    /** "Заблокировано в эти периоды", и сейчас идёт такой период до [until]. */
    data class InsideBlocked(val until: LocalDateTime) : BlockReason

    /** Включён режим фокуса до [until]: приложение заблокировано полностью, пропуски не действуют. */
    data class FocusMode(val until: LocalDateTime) : BlockReason
}

/** О чём предупредить заранее, пока приложение ещё можно использовать. */
sealed interface BlockWarning {
    /** До конца дневного лимита осталось [remainingMs]. */
    data class LimitEndsSoon(val remainingMs: Long) : BlockWarning

    /** Разрешённый период закончится в [at]. */
    data class AllowedPeriodEnds(val at: LocalDateTime) : BlockWarning

    /** Заблокированный период начнётся в [at]. */
    data class BlockedPeriodStarts(val at: LocalDateTime) : BlockWarning
}

/** Решение "заблокировать": причина и когда блокировка закончится сама (null — не закончится). */
data class BlockDecision(val reason: BlockReason, val unlockAt: LocalDateTime?)

/** Конкретный период расписания на конкретную дату: с [start] до [end]. */
data class Occurrence(val start: LocalDateTime, val end: LocalDateTime)

/** Проверка правил. Чистая логика без Android, поэтому её легко проверять тестами. */
object RuleEvaluator {

    /**
     * Нужно ли блокировать приложение прямо сейчас.
     * @param usageTodayMs сколько приложение уже использовали сегодня
     * @return решение о блокировке или null, если можно пользоваться
     */
    fun evaluate(
        rule: RuleWithIntervals,
        usageTodayMs: Long,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BlockDecision? = pick(evaluateAll(rule, usageTodayMs, now, zone))

    /** Из всех нарушенных условий выбрать то, что показать на экране блокировки. */
    fun pick(decisions: List<BlockDecision>): BlockDecision? {
        // Полная блокировка главнее всего: пока она действует, остальные причины не важны
        decisions.firstOrNull { it.reason == BlockReason.FullBlock }?.let { return it }
        // Нарушено несколько условий — показываем то, что держит дольше всех (null = бессрочно)
        return decisions.maxByOrNull { it.unlockAt ?: LocalDateTime.MAX }
    }

    /** Все нарушенные сейчас условия (пустой список — можно пользоваться). Нужен, чтобы посчитать цену в банке. */
    fun evaluateAll(
        rule: RuleWithIntervals,
        usageTodayMs: Long,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<BlockDecision> {
        return buildList {
            if (isFullBlockActive(rule.rule, now, zone)) {
                // Если попросили снять блокировку — она закончится в назначенное время, иначе бессрочно
                val offAt = rule.rule.fullBlockOffAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
                add(BlockDecision(BlockReason.FullBlock, unlockAt = offAt))
            }

            rule.rule.limitOn(now.toLocalDate())?.let { limit ->
                if (usageTodayMs >= limit * 60_000L) {
                    // Счётчик сбрасывается в полночь
                    add(BlockDecision(BlockReason.LimitReached(limit), now.toLocalDate().plusDays(1).atStartOfDay()))
                }
            }

            when (rule.rule.scheduleMode) {
                ScheduleMode.ALLOW_ONLY -> if (activeOccurrences(rule.intervals, now).isEmpty()) {
                    val next = nextOccurrence(rule.intervals, now)
                    add(BlockDecision(BlockReason.OutsideAllowed(next), next?.start))
                }

                ScheduleMode.BLOCK_DURING -> activeUntil(rule.intervals, now)?.let { until ->
                    add(BlockDecision(BlockReason.InsideBlocked(until), until))
                }

                ScheduleMode.NONE -> Unit
            }
        }
    }

    /** Действует ли полная блокировка в момент [now] (снятие ждёт 10 минут — до этого она ещё действует). */
    fun isFullBlockActive(rule: AppRule, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        rule.isFullBlockActive(now.atZone(zone).toInstant().toEpochMilli())

    /**
     * Скоро ли блокировка: что из правила сработает в ближайшие [warnBeforeMs] миллисекунд.
     * Вызывается, когда приложение ещё не заблокировано.
     */
    fun upcomingWarnings(
        rule: RuleWithIntervals,
        usageTodayMs: Long,
        now: LocalDateTime,
        warnBeforeMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<BlockWarning> {
        if (isFullBlockActive(rule.rule, now, zone)) return emptyList() // и так заблокировано
        // Событие наступит не позже чем через warnBeforeMs (но ещё не наступило)
        fun isSoon(msLeft: Long) = msLeft in 1..warnBeforeMs
        fun msUntil(moment: LocalDateTime) = Duration.between(now, moment).toMillis()

        return buildList {
            rule.rule.limitOn(now.toLocalDate())?.let { limit ->
                val left = limit * 60_000L - usageTodayMs
                if (isSoon(left)) add(BlockWarning.LimitEndsSoon(left))
            }
            when (rule.rule.scheduleMode) {
                ScheduleMode.ALLOW_ONLY -> activeUntil(rule.intervals, now)?.let { end ->
                    if (isSoon(msUntil(end))) add(BlockWarning.AllowedPeriodEnds(end))
                }

                ScheduleMode.BLOCK_DURING -> nextOccurrence(rule.intervals, now)?.let { next ->
                    if (isSoon(msUntil(next.start))) add(BlockWarning.BlockedPeriodStarts(next.start))
                }

                ScheduleMode.NONE -> Unit
            }
        }
    }

    /**
     * Сколько минут в день [date] расписание НЕ блокирует (от 0 до 1440).
     * Нужно для банка: если разрешено только 18:00–19:00, а лимит 2 ч, сэкономить можно не больше часа.
     */
    fun freeMinutes(rule: RuleWithIntervals, date: LocalDate): Int {
        if (rule.rule.scheduleMode == ScheduleMode.NONE) return MINUTES_IN_DAY
        val start = date.atStartOfDay()
        return (0 until MINUTES_IN_DAY).count { minute ->
            val moment = start.plusMinutes(minute.toLong())
            val inside = activeOccurrences(rule.intervals, moment).isNotEmpty()
            if (rule.rule.scheduleMode == ScheduleMode.ALLOW_ONLY) inside else !inside
        }
    }

    /**
     * Сколько минут приложение могло "сэкономить" в день [date] для банка: лимит этого дня,
     * но не больше времени, которое оставляет расписание. null — в этот день банк не копится
     * (нет лимита или включена полная блокировка).
     */
    fun earnableMinutes(rule: RuleWithIntervals?, date: LocalDate): Int? {
        if (rule == null || rule.rule.fullBlock) return null
        val limit = rule.rule.limitOn(date) ?: return null
        return minOf(limit, freeMinutes(rule, date))
    }

    private const val MINUTES_IN_DAY = 24 * 60

    /** Период на дату [date] или null, если в этот день недели его нет. */
    private fun ScheduleInterval.occurrenceOn(date: LocalDate): Occurrence? {
        if (date.dayOfWeek !in days) return null
        val start = date.atStartOfDay().plusMinutes(startMinute.toLong())
        // Конец раньше начала — период заканчивается на следующий день
        val endDate = if (endMinute <= startMinute) date.plusDays(1) else date
        return Occurrence(start, endDate.atStartOfDay().plusMinutes(endMinute.toLong()))
    }

    /** Все периоды, начинающиеся со вчерашнего дня и ещё [daysAhead] дней вперёд. */
    private fun occurrences(intervals: List<ScheduleInterval>, now: LocalDateTime, daysAhead: Int): List<Occurrence> {
        val today = now.toLocalDate()
        return (-1..daysAhead).flatMap { offset ->
            intervals.mapNotNull { it.occurrenceOn(today.plusDays(offset.toLong())) }
        }
    }

    /** Периоды, внутри которых находится момент [now]. */
    fun activeOccurrences(intervals: List<ScheduleInterval>, now: LocalDateTime): List<Occurrence> =
        occurrences(intervals, now, daysAhead = 0).filter { now >= it.start && now < it.end }

    /** Ближайший период, который начнётся после [now] (ищем на неделю вперёд). */
    fun nextOccurrence(intervals: List<ScheduleInterval>, now: LocalDateTime): Occurrence? =
        occurrences(intervals, now, daysAhead = 8).filter { it.start > now }.minByOrNull { it.start }

    /**
     * До какого времени длится период расписания, в котором мы сейчас.
     * Если периоды идут подряд или пересекаются, склеиваем их. null — сейчас не внутри периода.
     */
    fun activeUntil(intervals: List<ScheduleInterval>, now: LocalDateTime): LocalDateTime? {
        var until: LocalDateTime? = null
        var moment = now
        repeat(30) { // ограничение на случай расписания "круглые сутки"
            val end = activeOccurrences(intervals, moment).maxOfOrNull { it.end } ?: return until
            until = end
            moment = end
        }
        return until
    }
}
