package com.takeyourbrain.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Считает, сколько времени приложения были на экране. Данные берёт из UsageStatsManager. */
class UsageRepository(context: Context) {

    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    /** Время на экране за сегодня (с полуночи по местному времени) для каждого пакета, в миллисекундах. */
    suspend fun getTodayUsage(): Map<String, Long> = withContext(Dispatchers.IO) {
        computeUsage(from = startOfToday(), to = System.currentTimeMillis())
    }

    /**
     * Время на экране по дням за последние [days] дней (последний элемент — сегодня).
     * Каждый день считается отдельно, с полуночи до полуночи.
     */
    suspend fun getUsageByDay(days: Int): List<Pair<LocalDate, Map<String, Long>>> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        (days - 1 downTo 0).map { ago ->
            val date = today.minusDays(ago.toLong())
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
            date to computeUsage(from, to)
        }
    }

    /** Время на экране за день [date] (с полуночи до полуночи) для каждого пакета. */
    suspend fun getUsageOn(date: LocalDate): Map<String, Long> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        computeUsage(from, to)
    }

    /**
     * Время на экране для каждого пакета в промежутке [from, to).
     * Сессия начинается событием ACTIVITY_RESUMED (приложение вышло на экран)
     * и заканчивается ACTIVITY_PAUSED/STOPPED (ушло с экрана).
     * Если сессия ещё не закончилась (приложение открыто прямо сейчас), она считается до [to].
     */
    private fun computeUsage(from: Long, to: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val openedAt = HashMap<String, Long>() // пакет -> когда вышел на экран (сессия идёт)
        val seen = HashSet<String>()           // пакеты, у которых уже были события в этом промежутке

        // Закрыть все идущие сессии в момент time
        fun closeAll(time: Long) {
            openedAt.forEach { (pkg, start) -> totals.merge(pkg, time - start, Long::plus) }
            openedAt.clear()
        }

        val events = usageStatsManager.queryEvents(from, to)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    openedAt.putIfAbsent(pkg, time)
                    seen += pkg
                }

                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    // Если приложение открыли ещё до начала промежутка (например, до полуночи),
                    // первым его событием будет "ушло с экрана": считаем с начала промежутка
                    val start = openedAt.remove(pkg)
                        ?: if (pkg !in seen && event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) from else null
                    if (start != null) totals.merge(pkg, time - start, Long::plus)
                    seen += pkg
                }

                // Экран погас или телефон выключили: время дальше не идёт
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN -> closeAll(time)
            }
        }
        // Приложение, которое сейчас на экране: добавляем текущую "живую" сессию
        closeAll(to)
        return totals
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
