package com.takeyourbrain.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

// Отдельный файл DataStore: сколько сегодня смотрели части приложений (например, YouTube Shorts)
private val Context.elementUsageStore by preferencesDataStore(name = "element_usage")

/**
 * Время в частях приложений за сегодня (для лимита на Shorts). Служба считает в памяти
 * и время от времени сохраняет сюда — так счёт переживает перезапуск приложения. В полночь начинается заново.
 */
class ElementUsageRepository(private val context: Context) {

    private val dayKey = stringPreferencesKey("day")

    private fun usageKey(elementId: String) = longPreferencesKey("ms_$elementId")

    /** Сколько миллисекунд сегодня открыта часть [elementId]. */
    suspend fun usedToday(elementId: String): Long {
        val prefs = context.elementUsageStore.data.first()
        if (prefs[dayKey] != LocalDate.now().toString()) return 0L
        return prefs[usageKey(elementId)] ?: 0L
    }

    /** Сохранить счёт за день [day] (служба передаёт всё, что насчитала). */
    suspend fun save(day: LocalDate, usage: Map<String, Long>) {
        context.elementUsageStore.edit { prefs ->
            if (prefs[dayKey] != day.toString()) {
                prefs.clear()
                prefs[dayKey] = day.toString()
            }
            usage.forEach { (id, ms) -> prefs[usageKey(id)] = ms }
        }
    }
}
