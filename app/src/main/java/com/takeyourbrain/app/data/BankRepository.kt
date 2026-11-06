package com.takeyourbrain.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.takeyourbrain.app.data.db.BankDao
import com.takeyourbrain.app.data.db.BankSession
import com.takeyourbrain.app.data.db.BankTx
import com.takeyourbrain.app.data.db.BankTxReason
import com.takeyourbrain.app.data.db.LimitSnapshot
import com.takeyourbrain.app.data.db.RuleDao
import com.takeyourbrain.app.data.db.RuleWithIntervals
import com.takeyourbrain.app.domain.BankPricing
import com.takeyourbrain.app.domain.RuleEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

// Отдельный файл DataStore для банка: до какого дня уже всё начислено
private val Context.bankStore by preferencesDataStore(name = "bank")

/**
 * Банк времени: у каждого приложения с лимитом свой счёт.
 * Сэкономленное за день время начисляется на следующий день ("расчёт" — [settle]),
 * в ночь на понедельник банк сгорает (если так настроено), тратить можно в выбранные дни.
 */
class BankRepository(
    private val context: Context,
    private val dao: BankDao,
    private val ruleDao: RuleDao,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
) {
    // Расчёт, траты и изменения правил не должны идти одновременно — иначе можно начислить дважды
    private val mutex = Mutex()
    private val lastSettledKey = stringPreferencesKey("last_settled_day")

    /** Балансы приложений (только у кого были операции). */
    val balances: Flow<Map<String, Int>> = dao.observeBalances().map { list -> list.associate { it.packageName to it.balance } }

    /** Последние операции для истории. */
    val recentTx: Flow<List<BankTx>> = dao.observeRecent(HISTORY_SIZE)

    /** Идущие сессии "время из банка". */
    val sessions: Flow<List<BankSession>> = dao.observeSessions()

    /**
     * Последняя начатая сессия. Служба смотрит и сюда: база сообщает о новой записи
     * с небольшой задержкой, а приложение открывается сразу.
     */
    @Volatile
    var lastStarted: BankSession? = null
        private set

    fun observeBalance(packageName: String): Flow<Int> = dao.observeBalance(packageName)

    /** Баланс приложения (сначала начисляем всё, что накопилось за прошедшие дни). */
    suspend fun balance(packageName: String): Int {
        settle()
        return dao.balance(packageName)
    }

    /** Начислить сэкономленное за прошедшие дни и сжечь банк, если наступил понедельник. */
    suspend fun settle() = mutex.withLock { settleLocked() }

    private suspend fun settleLocked() {
        val today = LocalDate.now()
        val store = context.bankStore
        val lastSettled = store.data.first()[lastSettledKey]?.let(LocalDate::parse)
        if (lastSettled == null) {
            // Банк только что появился: копить начинаем с сегодняшнего дня
            store.edit { it[lastSettledKey] = today.minusDays(1).toString() }
            return
        }
        // Слишком давно не считали — дальше двух месяцев назад не смотрим
        var day = maxOf(lastSettled.plusDays(1), today.minusDays(MAX_DAYS_TO_SETTLE))
        if (!day.isBefore(today)) return

        val cap = settingsRepository.bankCap.first()
        val weeklyReset = settingsRepository.bankWeeklyReset.first()
        val rules = ruleDao.getAll().associateBy { it.rule.packageName }

        while (day.isBefore(today)) {
            // Android хранит подробную статистику около недели: старше — ничего не начисляем
            if (!day.isBefore(today.minusDays(USAGE_HISTORY_DAYS))) earnFor(day, rules, cap)
            val next = day.plusDays(1)
            if (weeklyReset && next.dayOfWeek == DayOfWeek.MONDAY) expireAll(next)
            day = next
        }
        store.edit { it[lastSettledKey] = today.minusDays(1).toString() }
        dao.deleteSnapshotsBefore(today.minusDays(USAGE_HISTORY_DAYS + 1).toString())
    }

    /** Начислить за день [day] каждому приложению: лимит дня минус использованное время. */
    private suspend fun earnFor(day: LocalDate, rules: Map<String, RuleWithIntervals>, cap: Int) {
        val snapshots = dao.snapshotsFor(day.toString()).associateBy { it.packageName }
        val packages = rules.keys + snapshots.keys
        if (packages.isEmpty()) return
        val usage = usageRepository.getUsageOn(day)
        for (pkg in packages) {
            // Если правило в этот день меняли — берём снимок, иначе правило не менялось с начала дня
            val earnable = if (pkg in snapshots) {
                snapshots.getValue(pkg).earnableMinutes
            } else {
                RuleEvaluator.earnableMinutes(rules[pkg], day)
            } ?: continue
            val earned = BankPricing.dailyEarning(earnable, usage[pkg] ?: 0L, dao.balance(pkg), cap)
            if (earned > 0) {
                dao.insertTx(BankTx(packageName = pkg, time = startOf(day.plusDays(1)), amount = earned,
                    reason = BankTxReason.EARN_DAILY, day = day.toString()))
            }
        }
    }

    /** Банк сгорает: обнуляем все балансы в начале дня [monday]. */
    private suspend fun expireAll(monday: LocalDate) {
        dao.balances().filter { it.balance > 0 }.forEach {
            dao.insertTx(BankTx(packageName = it.packageName, time = startOf(monday), amount = -it.balance,
                reason = BankTxReason.EXPIRE, day = monday.toString()))
        }
    }

    /**
     * Вызывать перед каждым изменением правила ([old] → [new], null — правила нет).
     * Запоминает на сегодня меньшее из "сколько можно сэкономить" до и после изменения:
     * поднять лимит вечером ради банка не получится.
     */
    suspend fun recordRuleChange(packageName: String, old: RuleWithIntervals?, new: RuleWithIntervals?) = mutex.withLock {
        settleLocked()
        val today = LocalDate.now()
        val existing = dao.snapshot(packageName, today.toString())
        val before = if (existing != null) existing.earnableMinutes else RuleEvaluator.earnableMinutes(old, today)
        val after = RuleEvaluator.earnableMinutes(new, today)
        val combined = if (before == null || after == null) null else minOf(before, after)
        dao.upsertSnapshot(LimitSnapshot(packageName, today.toString(), combined))
    }

    /**
     * Взять [minutes] минут из банка по цене [multiplier] за минуту.
     * Возвращает false, если минут не хватает.
     */
    suspend fun spend(packageName: String, minutes: Int, multiplier: Int): Boolean = mutex.withLock {
        settleLocked()
        val cost = minutes * multiplier
        if (dao.balance(packageName) < cost) return@withLock false
        val now = System.currentTimeMillis()
        dao.insertTx(BankTx(packageName = packageName, time = now, amount = -cost, reason = BankTxReason.SPEND))
        val session = BankSession(packageName, startedAt = now, endsAt = now + minutes * 60_000L, multiplier = multiplier)
        lastStarted = session
        dao.upsertSession(session)
        true
    }

    /** Закончить сессию (ушли из приложения): целые неиспользованные минуты возвращаются в банк. */
    suspend fun endSession(packageName: String, at: Long = System.currentTimeMillis()) = mutex.withLock {
        val session = dao.session(packageName) ?: lastStarted?.takeIf { it.packageName == packageName } ?: return@withLock
        dao.deleteSession(packageName)
        if (lastStarted?.packageName == packageName) lastStarted = null
        val refund = ((session.endsAt - at).coerceAtLeast(0) / 60_000).toInt() * session.multiplier
        if (refund > 0) {
            dao.insertTx(BankTx(packageName = packageName, time = at, amount = refund, reason = BankTxReason.REFUND))
        }
    }

    /** Сколько за промежуток [from, to) пришло в банк и сколько из него потрачено (траты минус возвраты). */
    suspend fun totalsBetween(from: Long, to: Long): Pair<Int, Int> {
        val earned = dao.sumBetween(listOf(BankTxReason.EARN_DAILY), from, to)
        val spent = -dao.sumBetween(listOf(BankTxReason.SPEND, BankTxReason.REFUND), from, to)
        return earned to spent
    }

    private fun startOf(day: LocalDate): Long = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val HISTORY_SIZE = 100
        const val USAGE_HISTORY_DAYS = 7L
        const val MAX_DAYS_TO_SETTLE = 60L
    }
}
