package com.takeyourbrain.app.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context

/**
 * Будильники приложения (напоминания, конец фокуса, снятие полной блокировки).
 * Если пользователь разрешил "точные будильники" — срабатывают минута в минуту,
 * иначе Android может сдвинуть их на несколько минут (это тоже работает, просто менее точно).
 */
object Alarms {

    /** Разрешены ли точные будильники (Настройки → Приложения → Будильники и напоминания). */
    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Поставить будильник на момент [at] (миллисекунды). */
    fun set(context: Context, at: Long, operation: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                return
            } catch (_: SecurityException) {
                // Разрешение успели отозвать — ставим обычный
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
    }

    fun cancel(context: Context, operation: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(operation)
    }
}
