package com.takeyourbrain.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Запросы к таблицам правил. */
@Dao
interface RuleDao {

    /** Все правила. Flow сам присылает новый список, когда правила меняются. */
    @Transaction
    @Query("SELECT * FROM app_rules")
    fun observeAll(): Flow<List<RuleWithIntervals>>

    @Transaction
    @Query("SELECT * FROM app_rules")
    suspend fun getAll(): List<RuleWithIntervals>

    /** Правила, у которых назначено снятие полной блокировки. */
    @Transaction
    @Query("SELECT * FROM app_rules WHERE fullBlockOffAt IS NOT NULL")
    suspend fun getPendingUnblocks(): List<RuleWithIntervals>

    @Transaction
    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun get(packageName: String): RuleWithIntervals?

    @Upsert
    suspend fun upsertRule(rule: AppRule)

    @Insert
    suspend fun insertIntervals(intervals: List<ScheduleInterval>)

    @Query("DELETE FROM schedule_intervals WHERE packageName = :packageName")
    suspend fun deleteIntervals(packageName: String)

    @Query("DELETE FROM app_rules WHERE packageName = :packageName")
    suspend fun deleteRule(packageName: String)

    /** Сохранить правило и заменить все его периоды. Всё или ничего (одна транзакция). */
    @Transaction
    suspend fun replace(rule: AppRule, intervals: List<ScheduleInterval>) {
        upsertRule(rule)
        deleteIntervals(rule.packageName)
        insertIntervals(intervals)
    }
}
