package com.takeyourbrain.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Вид напоминания у задачи. */
enum class ReminderType { NONE, ONCE, INTERVAL, WEEKLY, MONTHLY }

/**
 * Задача ежедневника. Правило напоминания хранится в отдельных колонках (какие нужны — зависит от типа).
 * Время — в миллисекундах (как System.currentTimeMillis).
 */
@Entity(tableName = "tasks", indices = [Index("nextFireAt")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val createdAt: Long,
    val done: Boolean = false,       // разовая задача выполнена
    val doneAt: Long? = null,
    val lastDoneAt: Long? = null,    // повторяющуюся отмечали выполненной в последний раз
    // Правило напоминания
    val reminderType: ReminderType = ReminderType.NONE,
    val onceAt: Long? = null,             // ONCE
    val intervalMinutes: Int? = null,     // INTERVAL
    val intervalFrom: Long? = null,       // INTERVAL: первый раз
    val intervalFromDone: Boolean = false, // INTERVAL: отсчёт от выполнения
    val weekDaysMask: Int = 0,            // WEEKLY: бит 0 — понедельник
    val minuteOfDay: Int? = null,         // WEEKLY, MONTHLY: время (минуты от полуночи)
    val dayOfMonth: Int? = null,          // MONTHLY
    // Расписание срабатываний
    val occurrenceAt: Long? = null,  // следующее срабатывание по правилу (без учёта тихих часов)
    val snoozedUntil: Long? = null,  // отложено до этого момента
    val firedAt: Long? = null,       // напоминание сработало и ждёт "Готово"
    val nextFireAt: Long? = null,    // когда прозвонит будильник (с тихими часами и "отложить")
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    /** Задачи, у которых что-то запланировано или есть правило. */
    @Query("SELECT * FROM tasks WHERE done = 0")
    suspend fun getActive(): List<Task>

    @Query("SELECT MIN(nextFireAt) FROM tasks WHERE nextFireAt IS NOT NULL")
    suspend fun earliestFireAt(): Long?

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks WHERE done = 1")
    suspend fun deleteDone()
}
