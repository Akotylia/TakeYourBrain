package com.takeyourbrain.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.takeyourbrain.app.data.db.BankDao
import com.takeyourbrain.app.data.db.DayResult
import com.takeyourbrain.app.data.db.DayResultDao
import com.takeyourbrain.app.data.db.PassDao
import com.takeyourbrain.app.data.db.RuleDao
import com.takeyourbrain.app.domain.DayStatus
import com.takeyourbrain.app.domain.Streak
import com.takeyourbrain.app.widget.StreakWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

// Отдельный файл DataStore для серии: до какого дня уже подведены итоги
private val Context.streakStore by preferencesDataStore(name = "streak")

/** Серия-"огонёк": текущая, лучшая и как идёт сегодня. */
data class StreakInfo(val current: Int, val best: Int, val today: DayStatus)

/**
 * Серия дней без превышения лимитов. Итоги прошедших дней подводятся "лениво" ([settle]) и хранятся в базе,
 * сегодняшний день считается на лету.
 */
class StreakRepository(
    private val context: Context,
    private val dao: DayResultDao,
    private val ruleDao: RuleDao,
    private val passDao: PassDao,
    private val bankDao: BankDao,
    private val usageRepository: UsageRepository,
) {
    private val mutex = Mutex()
    private val lastDayKey = stringPreferencesKey("last_day")
    private val celebratedKey = intPreferencesKey("celebrated")

    /** Итоги прошедших дней, от старых к новым. */
    val pastDays: Flow<List<DayStatus>> = dao.observeAll().map { list -> list.map { it.status } }

    /** Подвести итоги прошедших дней, которые ещё не посчитаны. */
    suspend fun settle() = mutex.withLock {
        val today = LocalDate.now()
        val store = context.streakStore
        val lastDay = store.data.first()[lastDayKey]?.let(LocalDate::parse)
        if (lastDay == null) {
            // Серия только появилась: считаем с сегодняшнего дня
            store.edit { it[lastDayKey] = today.minusDays(1).toString() }
            return@withLock
        }
        var day = lastDay.plusDays(1)
        while (day.isBefore(today)) {
            // Android помнит время около недели: более старые дни посчитать нельзя — они нейтральные
            val status = if (day.isBefore(today.minusDays(USAGE_HISTORY_DAYS))) DayStatus.NEUTRAL else statusOf(day)
            dao.upsert(DayResult(day.toString(), status))
            day = day.plusDays(1)
        }
        store.edit { it[lastDayKey] = today.minusDays(1).toString() }
    }

    /** Серия вместе с сегодняшним днём (сначала подводит итоги прошедших). */
    suspend fun info(): StreakInfo {
        settle()
        val past = pastDays.first()
        val today = statusOf(LocalDate.now())
        val current = Streak.current(past, today)
        StreakWidget.show(context, current)
        return StreakInfo(current, Streak.best(past, today), today)
    }

    /** Какой рубеж серии пора отпраздновать ([current] — серия сейчас); null — пока нечего. */
    suspend fun milestoneToCelebrate(current: Int): Int? {
        val saved = context.streakStore.data.first()[celebratedKey] ?: 0
        val celebrated = Streak.celebratedAfter(current, saved)
        if (celebrated != saved) context.streakStore.edit { it[celebratedKey] = celebrated }
        return Streak.milestoneToCelebrate(current, celebrated)
    }

    suspend fun markCelebrated(milestone: Int) {
        context.streakStore.edit { it[celebratedKey] = milestone }
    }

    /** Итог дня [day]: лимиты по нынешним правилам, время, траты банка и пропуски за этот день. */
    private suspend fun statusOf(day: LocalDate): DayStatus {
        val rules = ruleDao.getAll()
        val limits = rules.filterNot { it.rule.fullBlock }
            .mapNotNull { r -> r.rule.limitOn(day)?.let { r.rule.packageName to it } }
            .toMap()
        val from = startOf(day)
        val to = startOf(day.plusDays(1))
        return Streak.dayStatus(
            hasRules = rules.isNotEmpty(),
            limits = limits,
            usageMs = if (limits.isEmpty()) emptyMap() else usageRepository.getUsageOn(day),
            bankSpent = bankDao.spentBetween(from, to).associate { it.packageName to it.balance },
            passes = passDao.countBetween(from, to),
        )
    }

    private fun startOf(day: LocalDate): Long = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val USAGE_HISTORY_DAYS = 7L
    }
}
