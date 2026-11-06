package com.takeyourbrain.app.data

import com.takeyourbrain.app.data.db.BlockEvent
import com.takeyourbrain.app.data.db.BlockEventDao
import com.takeyourbrain.app.domain.BlockReason
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/** Журнал срабатываний блокировки (для статистики). */
class BlockLogRepository(private val dao: BlockEventDao) {

    /** Записать, что приложение [packageName] только что заблокировано. */
    suspend fun log(packageName: String, reason: BlockReason) {
        val kind = when (reason) {
            BlockReason.FullBlock -> "full"
            is BlockReason.LimitReached -> "limit"
            is BlockReason.OutsideAllowed, is BlockReason.InsideBlocked -> "schedule"
            is BlockReason.FocusMode -> "focus"
        }
        val now = System.currentTimeMillis()
        dao.insert(BlockEvent(packageName = packageName, time = now, kind = kind))
        // Храним только последние 90 дней — статистике больше не нужно
        dao.deleteBefore(now - KEEP_DAYS * 24 * 60 * 60 * 1000L)
    }

    /** Записать, что в приложении [packageName] закрыта заблокированная часть (например, YouTube Shorts). */
    suspend fun logElement(packageName: String) {
        dao.insert(BlockEvent(packageName = packageName, time = System.currentTimeMillis(), kind = "element"))
    }

    /** Срабатывания за последние [days] дней, включая сегодня. */
    fun eventsForLastDays(days: Int): Flow<List<BlockEvent>> {
        val since = LocalDate.now().minusDays(days - 1L).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dao.observeSince(since)
    }

    private companion object {
        const val KEEP_DAYS = 90
    }
}
