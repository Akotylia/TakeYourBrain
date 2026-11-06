package com.takeyourbrain.app.data

import android.content.Context
import com.takeyourbrain.app.data.db.ReminderType
import com.takeyourbrain.app.data.db.Task
import com.takeyourbrain.app.data.db.TaskDao
import com.takeyourbrain.app.data.db.daysFromMask
import com.takeyourbrain.app.data.db.daysToMask
import com.takeyourbrain.app.domain.QuietHours
import com.takeyourbrain.app.domain.ReminderRule
import com.takeyourbrain.app.domain.Reminders
import com.takeyourbrain.app.notifications.Notifications
import com.takeyourbrain.app.tasks.TaskAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Задачи ежедневника и их напоминания.
 * Для всех задач ставится один будильник — на самое раннее напоминание. Когда он срабатывает,
 * [onAlarm] показывает уведомления, считает следующие разы и ставит будильник заново.
 */
class TaskRepository(
    private val context: Context,
    private val dao: TaskDao,
    private val settingsRepository: SettingsRepository,
) {
    // Будильник, кнопки уведомления и экран могут менять задачи одновременно
    private val mutex = Mutex()

    val tasks: Flow<List<Task>> = dao.observeAll()

    suspend fun get(id: Long): Task? = dao.get(id)

    /** Сохранить задачу (новую — id 0). Расписание напоминаний считается заново. Возвращает id. */
    suspend fun save(task: Task): Long = mutex.withLock {
        val now = LocalDateTime.now()
        val occurrence = task.rule()?.let { Reminders.next(it, now) }
        val prepared = withNextFire(
            task.copy(occurrenceAt = occurrence?.toMillis(), snoozedUntil = null, firedAt = null),
            settingsRepository.quietHours.first(),
        )
        val id = if (task.id == 0L) dao.insert(prepared) else prepared.id.also { dao.update(prepared) }
        Notifications.cancelTask(context, id)
        scheduleLocked()
        id
    }

    suspend fun delete(id: Long) = mutex.withLock {
        dao.delete(id)
        Notifications.cancelTask(context, id)
        scheduleLocked()
    }

    suspend fun deleteDone() = mutex.withLock { dao.deleteDone() }

    /**
     * Отметить задачу выполненной ([done] = true) или вернуть в работу.
     * Повторяющаяся задача не исчезает: отмечается этот раз, и возвращается следующее напоминание.
     */
    suspend fun setDone(id: Long, done: Boolean): LocalDateTime? = mutex.withLock {
        val task = dao.get(id) ?: return@withLock null
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.now()
        val rule = task.rule()
        val updated = when {
            !done -> {
                // Вернули разовую задачу в работу: если её время ещё впереди — напомним
                val occurrence = rule?.let { Reminders.next(it, now) }
                task.copy(done = false, doneAt = null, occurrenceAt = occurrence?.toMillis())
            }
            rule == null || rule is ReminderRule.Once ->
                task.copy(done = true, doneAt = nowMs, occurrenceAt = null, snoozedUntil = null, firedAt = null)
            rule is ReminderRule.Interval && rule.fromDone ->
                // "Каждые N часов от выполнения": следующий раз — через N часов от сейчас
                task.copy(lastDoneAt = nowMs, firedAt = null, snoozedUntil = null,
                    occurrenceAt = Reminders.afterDone(rule, now).toMillis())
            task.firedAt != null || task.snoozedUntil != null ->
                // Напоминание уже было — отмечаем этот раз, следующий уже запланирован
                task.copy(lastDoneAt = nowMs, firedAt = null, snoozedUntil = null)
            else -> {
                // Сделали заранее — ближайшее напоминание пропускаем
                val after = maxOf(task.occurrenceAt?.toLocalDateTime() ?: now, now)
                task.copy(lastDoneAt = nowMs, occurrenceAt = Reminders.next(rule, after)?.toMillis())
            }
        }
        val final = withNextFire(updated, settingsRepository.quietHours.first())
        dao.update(final)
        Notifications.cancelTask(context, id)
        scheduleLocked()
        if (done && rule != null && rule !is ReminderRule.Once) final.occurrenceAt?.toLocalDateTime() else null
    }

    /** Отложить напоминание на [minutes] минут. */
    suspend fun snooze(id: Long, minutes: Int) = mutex.withLock {
        val task = dao.get(id) ?: return@withLock
        val snoozed = task.copy(snoozedUntil = System.currentTimeMillis() + minutes * 60_000L)
        dao.update(withNextFire(snoozed, settingsRepository.quietHours.first()))
        Notifications.cancelTask(context, id)
        scheduleLocked()
    }

    /** Сработал будильник: показать наступившие напоминания и запланировать следующие. */
    suspend fun onAlarm() = mutex.withLock { fireDueLocked() }

    /**
     * Пересчитать все напоминания: после перезагрузки, смены времени или тихих часов.
     * Пропущенные (пока телефон был выключен) покажутся сразу.
     */
    suspend fun rescheduleAll() = mutex.withLock {
        val quiet = settingsRepository.quietHours.first()
        dao.getActive().forEach { dao.update(withNextFire(it, quiet)) }
        fireDueLocked()
    }

    private suspend fun fireDueLocked() {
        val quiet = settingsRepository.quietHours.first()
        val nowMs = System.currentTimeMillis() + 1_000 // будильник может прийти на долю секунды раньше
        val now = LocalDateTime.now()
        for (original in dao.getActive()) {
            val fireAt = original.nextFireAt ?: continue
            if (fireAt > nowMs) continue
            var task = original
            var notify = false
            val snoozed = task.snoozedUntil
            if (snoozed != null && snoozed <= nowMs) {
                // "Напомнил" показывает время последнего уведомления, а не исходное
                task = task.copy(snoozedUntil = null, firedAt = snoozed)
                notify = true
            }
            val occurrence = task.occurrenceAt
            if (occurrence != null && quietAdjusted(occurrence, quiet) <= nowMs) {
                notify = true
                // Повторяющееся правило сразу получает следующий раз (пропущенные за время выключения не копятся)
                val rule = task.rule()
                val following = if (rule != null && Reminders.repeatsByItself(rule)) {
                    Reminders.next(rule, maxOf(occurrence.toLocalDateTime(), now))
                } else {
                    null
                }
                task = task.copy(firedAt = occurrence, occurrenceAt = following?.toMillis())
            }
            task = withNextFire(task, quiet)
            dao.update(task)
            if (notify) Notifications.showTask(context, task)
        }
        scheduleLocked()
    }

    /** Будильник — на самое раннее напоминание среди всех задач. */
    private suspend fun scheduleLocked() {
        val next = dao.earliestFireAt()
        if (next == null) TaskAlarm.cancel(context) else TaskAlarm.schedule(context, next)
    }

    /** Когда звонить: ближайшее из "по правилу" (с учётом тихих часов) и "отложено до". */
    private fun withNextFire(task: Task, quiet: QuietHours?): Task {
        if (task.done) return task.copy(nextFireAt = null)
        val byRule = task.occurrenceAt?.let { quietAdjusted(it, quiet) }
        return task.copy(nextFireAt = listOfNotNull(byRule, task.snoozedUntil).minOrNull())
    }

    private fun quietAdjusted(ms: Long, quiet: QuietHours?): Long =
        Reminders.applyQuietHours(ms.toLocalDateTime(), quiet).toMillis()
}

/**
 * Дела на сегодня (для экрана блокировки): сработавшие и ждущие "Готово", с напоминанием на сегодня
 * и задачи без напоминания. Задачи с напоминанием на другой день (английский в пятницу, а сегодня четверг)
 * не показываем. Порядок: сначала сработавшие, потом по времени напоминания.
 */
fun tasksForToday(tasks: List<Task>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Task> {
    val endOfToday = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return tasks.filter { task ->
        val fireAt = task.nextFireAt
        !task.done && (task.firedAt != null || task.reminderType == ReminderType.NONE || (fireAt != null && fireAt < endOfToday))
    }.sortedWith(compareByDescending<Task> { it.firedAt != null }.thenBy { it.nextFireAt ?: Long.MAX_VALUE })
}

private fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

private fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Правило напоминания задачи (null — без напоминания). */
fun Task.rule(): ReminderRule? = when (reminderType) {
    ReminderType.NONE -> null
    ReminderType.ONCE -> onceAt?.let { ReminderRule.Once(it.toLocalDateTime()) }
    ReminderType.INTERVAL -> {
        val every = intervalMinutes
        val from = intervalFrom
        if (every == null || from == null) null else ReminderRule.Interval(every, from.toLocalDateTime(), intervalFromDone)
    }
    ReminderType.WEEKLY -> minuteOfDay?.let { ReminderRule.Weekly(daysFromMask(weekDaysMask), it) }
    ReminderType.MONTHLY -> {
        val day = dayOfMonth
        val minute = minuteOfDay
        if (day == null || minute == null) null else ReminderRule.Monthly(day, minute)
    }
}

/** Задача с новым правилом напоминания (остальные поля не меняются). */
fun Task.withRule(rule: ReminderRule?): Task {
    val cleared = copy(
        reminderType = ReminderType.NONE, onceAt = null, intervalMinutes = null, intervalFrom = null,
        intervalFromDone = false, weekDaysMask = 0, minuteOfDay = null, dayOfMonth = null,
    )
    return when (rule) {
        null -> cleared
        is ReminderRule.Once -> cleared.copy(reminderType = ReminderType.ONCE, onceAt = rule.at.toMillis())
        is ReminderRule.Interval -> cleared.copy(
            reminderType = ReminderType.INTERVAL, intervalMinutes = rule.everyMinutes,
            intervalFrom = rule.from.toMillis(), intervalFromDone = rule.fromDone,
        )
        is ReminderRule.Weekly -> cleared.copy(
            reminderType = ReminderType.WEEKLY, weekDaysMask = daysToMask(rule.days), minuteOfDay = rule.minuteOfDay,
        )
        is ReminderRule.Monthly -> cleared.copy(
            reminderType = ReminderType.MONTHLY, dayOfMonth = rule.dayOfMonth, minuteOfDay = rule.minuteOfDay,
        )
    }
}
