package com.takeyourbrain.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Одно срабатывание блокировки: приложение [packageName] закрыто в момент [time] по причине [kind]. */
@Entity(tableName = "block_events", indices = [Index("time")])
data class BlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val time: Long,
    val kind: String, // "full", "limit", "schedule", "focus" — пригодится для подробной статистики
)

@Dao
interface BlockEventDao {
    @Insert
    suspend fun insert(event: BlockEvent)

    /** Срабатывания начиная с момента [since]. Flow присылает новый список при каждом изменении. */
    @Query("SELECT * FROM block_events WHERE time >= :since")
    fun observeSince(since: Long): Flow<List<BlockEvent>>

    /** Удалить старые записи, чтобы таблица не росла бесконечно. */
    @Query("DELETE FROM block_events WHERE time < :before")
    suspend fun deleteBefore(before: Long)
}
