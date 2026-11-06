package com.takeyourbrain.app.data

import android.content.Context
import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.data.db.RuleDao
import com.takeyourbrain.app.data.db.RuleWithIntervals
import com.takeyourbrain.app.data.db.ScheduleInterval
import com.takeyourbrain.app.data.db.ScheduleMode
import com.takeyourbrain.app.service.UnblockAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Работа с правилами блокировки. */
class RulesRepository(
    private val context: Context,
    private val dao: RuleDao,
    private val bankRepository: BankRepository,
) {

    /** Все правила по имени пакета. Обновляется автоматически при изменениях. */
    val rules: Flow<Map<String, RuleWithIntervals>> =
        dao.observeAll().map { list -> list.associateBy { it.rule.packageName } }

    suspend fun getRule(packageName: String): RuleWithIntervals? = dao.get(packageName)

    /**
     * Сохранить правило. Если в нём выключили действующую полную блокировку, она снимется
     * не сразу, а через [UNBLOCK_DELAY_MS] — до этого правило хранит полную блокировку с временем снятия.
     */
    suspend fun saveRule(rule: AppRule, intervals: List<ScheduleInterval>) {
        val old = dao.get(rule.packageName)
        val now = System.currentTimeMillis()
        val final = when {
            // Включили (или оставили) полную блокировку — назначенное снятие отменяется
            rule.fullBlock -> rule.copy(fullBlockOffAt = null)
            // Выключили действующую — ждём; если ожидание уже идёт, оно не начинается заново
            old != null && old.rule.isFullBlockActive(now) ->
                rule.copy(fullBlock = true, fullBlockOffAt = old.rule.fullBlockOffAt ?: (now + UNBLOCK_DELAY_MS))
            else -> rule.copy(fullBlockOffAt = null)
        }
        bankRepository.recordRuleChange(rule.packageName, old, RuleWithIntervals(final, intervals))
        dao.replace(final, intervals)
        scheduleNextUnblock()
    }

    /**
     * Удалить правило. Если действует полная блокировка — сразу удалить нельзя:
     * остаётся только она, снимется через 10 минут, и тогда правило удалится само.
     */
    suspend fun deleteRule(packageName: String) {
        val old = dao.get(packageName) ?: return
        if (old.rule.isFullBlockActive(System.currentTimeMillis())) {
            val onlyFullBlock = old.rule.copy(
                fullBlock = false, weekdayLimitMinutes = null, weekendLimitMinutes = null, scheduleMode = ScheduleMode.NONE,
                blockedElements = "",
            )
            saveRule(onlyFullBlock, emptyList())
        } else {
            bankRepository.recordRuleChange(packageName, old, null)
            dao.deleteRule(packageName)
        }
    }

    /** Передумали снимать полную блокировку. */
    suspend fun cancelUnblock(packageName: String) {
        val old = dao.get(packageName) ?: return
        dao.upsertRule(old.rule.copy(fullBlockOffAt = null))
        scheduleNextUnblock()
    }

    /**
     * Снять полные блокировки, ожидание которых закончилось. Если в правиле больше ничего нет — удалить его.
     * Возвращает пакеты, с которых блокировка снята.
     */
    suspend fun finishPendingUnblocks(): List<String> {
        val now = System.currentTimeMillis()
        val finished = dao.getPendingUnblocks().filter { (it.rule.fullBlockOffAt ?: Long.MAX_VALUE) <= now }
        for (old in finished) {
            val updated = old.rule.copy(fullBlock = false, fullBlockOffAt = null)
            if (updated.hasOtherConditions) {
                bankRepository.recordRuleChange(updated.packageName, old, RuleWithIntervals(updated, old.intervals))
                dao.upsertRule(updated)
            } else {
                bankRepository.recordRuleChange(updated.packageName, old, null)
                dao.deleteRule(updated.packageName)
            }
        }
        scheduleNextUnblock()
        return finished.map { it.rule.packageName }
    }

    /** Поставить будильник на ближайшее снятие полной блокировки (или убрать, если снимать нечего). */
    suspend fun scheduleNextUnblock() {
        val next = dao.getPendingUnblocks().mapNotNull { it.rule.fullBlockOffAt }.minOrNull()
        if (next == null) UnblockAlarm.cancel(context) else UnblockAlarm.schedule(context, next)
    }

    companion object {
        /** Сколько ждать снятия полной блокировки. Пропустить ожидание нельзя. */
        const val UNBLOCK_DELAY_MS = 10 * 60_000L
    }
}
