package com.takeyourbrain.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.takeyourbrain.app.domain.DayStatus
import kotlinx.coroutines.flow.Flow

/**
 * Итог прошедшего дня для серии-"огонька". Храним, потому что Android помнит время использования
 * только около недели, а серия может быть длиннее.
 */
@Entity(tableName = "day_results")
data class DayResult(
    @PrimaryKey val day: String, // "2026-10-28"
    val status: DayStatus,
)

@Dao
interface DayResultDao {
    @Upsert
    suspend fun upsert(result: DayResult)

    /** Все дни, от старых к новым. */
    @Query("SELECT * FROM day_results ORDER BY day")
    fun observeAll(): Flow<List<DayResult>>
}
