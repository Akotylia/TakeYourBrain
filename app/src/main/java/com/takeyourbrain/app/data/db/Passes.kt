package com.takeyourbrain.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Разовый пропуск: приложение можно открыть с [startedAt] до [endsAt] несмотря на правило. */
@Entity(tableName = "passes", indices = [Index("startedAt")])
data class Pass(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startedAt: Long,
    val endsAt: Long,
)

@Dao
interface PassDao {
    @Insert
    suspend fun insert(pass: Pass)

    /** Пропуски, выданные после момента [since]. Flow присылает новый список при каждом изменении. */
    @Query("SELECT * FROM passes WHERE startedAt >= :since")
    fun observeSince(since: Long): Flow<List<Pass>>

    @Query("SELECT COUNT(*) FROM passes WHERE startedAt >= :since")
    suspend fun countSince(since: Long): Int

    /** Сколько пропусков взято в промежутке [from, to). */
    @Query("SELECT COUNT(*) FROM passes WHERE startedAt >= :from AND startedAt < :to")
    suspend fun countBetween(from: Long, to: Long): Int
}
