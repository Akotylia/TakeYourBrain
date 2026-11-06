package com.takeyourbrain.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Зачем изменился банк. */
enum class BankTxReason {
    EARN_DAILY, // сэкономленное за день время
    SPEND,      // взяли минуты на экране блокировки
    REFUND,     // вернули неиспользованный остаток
    EXPIRE,     // банк сгорел в ночь на понедельник
}

/**
 * Одна операция банка времени: [amount] минут (плюс — пришло, минус — ушло).
 * Баланс приложения — сумма всех его операций.
 */
@Entity(tableName = "bank_tx", indices = [Index("packageName")])
data class BankTx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val time: Long,
    val amount: Int,
    val reason: BankTxReason,
    val day: String? = null, // за какой день начислено/сгорело ("2026-10-27")
)

/**
 * "Снимок" лимита на день: сколько минут в этот день можно было сэкономить в банк.
 * Записывается, когда правило меняют, — берётся меньшее из старого и нового значения.
 * Так нельзя "накрутить" банк, подняв лимит вечером. null — в этот день банк не копится.
 */
@Entity(tableName = "limit_snapshots", primaryKeys = ["packageName", "day"])
data class LimitSnapshot(
    val packageName: String,
    val day: String,
    val earnableMinutes: Int?,
)

/**
 * Время, взятое из банка: приложение открыто до [endsAt].
 * [multiplier] — сколько минут банка стоила минута (1, 2 или 3), нужно для возврата остатка.
 */
@Entity(tableName = "bank_sessions")
data class BankSession(
    @PrimaryKey val packageName: String,
    val startedAt: Long,
    val endsAt: Long,
    val multiplier: Int,
)

@Dao
interface BankDao {
    @Insert
    suspend fun insertTx(tx: BankTx)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM bank_tx WHERE packageName = :packageName")
    suspend fun balance(packageName: String): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM bank_tx WHERE packageName = :packageName")
    fun observeBalance(packageName: String): Flow<Int>

    /** Балансы всех приложений, у которых есть операции. */
    @Query("SELECT packageName, SUM(amount) AS balance FROM bank_tx GROUP BY packageName")
    fun observeBalances(): Flow<List<PackageBalance>>

    @Query("SELECT packageName, SUM(amount) AS balance FROM bank_tx GROUP BY packageName")
    suspend fun balances(): List<PackageBalance>

    /** Сколько минут банка потрачено каждым приложением в промежутке [from, to) (траты минус возвраты). */
    @Query(
        "SELECT packageName, -SUM(amount) AS balance FROM bank_tx " +
            "WHERE reason IN ('SPEND', 'REFUND') AND time >= :from AND time < :to GROUP BY packageName"
    )
    suspend fun spentBetween(from: Long, to: Long): List<PackageBalance>

    /** Сумма операций одного вида в промежутке [from, to) — для итогов недели. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM bank_tx WHERE reason IN (:reasons) AND time >= :from AND time < :to")
    suspend fun sumBetween(reasons: List<BankTxReason>, from: Long, to: Long): Int

    /** Последние операции, новые сверху. */
    @Query("SELECT * FROM bank_tx ORDER BY time DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BankTx>>

    @Query("SELECT * FROM limit_snapshots WHERE packageName = :packageName AND day = :day")
    suspend fun snapshot(packageName: String, day: String): LimitSnapshot?

    @Query("SELECT * FROM limit_snapshots WHERE day = :day")
    suspend fun snapshotsFor(day: String): List<LimitSnapshot>

    @Upsert
    suspend fun upsertSnapshot(snapshot: LimitSnapshot)

    @Query("DELETE FROM limit_snapshots WHERE day < :day")
    suspend fun deleteSnapshotsBefore(day: String)

    @Query("SELECT * FROM bank_sessions")
    fun observeSessions(): Flow<List<BankSession>>

    @Query("SELECT * FROM bank_sessions WHERE packageName = :packageName")
    suspend fun session(packageName: String): BankSession?

    @Upsert
    suspend fun upsertSession(session: BankSession)

    @Query("DELETE FROM bank_sessions WHERE packageName = :packageName")
    suspend fun deleteSession(packageName: String)
}

/** Баланс одного приложения (результат запроса с SUM). */
data class PackageBalance(val packageName: String, val balance: Int)
