package com.takeyourbrain.app.data

import com.takeyourbrain.app.data.db.Pass
import com.takeyourbrain.app.data.db.PassDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** Разовые пропуски на 10 минут (не больше N в день на все приложения вместе). */
class PassRepository(
    private val dao: PassDao,
    private val settingsRepository: SettingsRepository,
) {
    /** Пропуски за последние сутки — по ним служба понимает, действует ли сейчас пропуск. */
    val recentPasses: Flow<List<Pass>> = dao.observeSince(System.currentTimeMillis() - DAY_MS)

    /** Сколько пропусков ещё можно взять сегодня. */
    suspend fun passesLeftToday(): Int {
        val perDay = settingsRepository.passesPerDay.first()
        return (perDay - dao.countSince(startOfToday())).coerceAtLeast(0)
    }

    /**
     * Последний выданный пропуск. Служба смотрит и сюда: база сообщает о новой записи
     * с небольшой задержкой, а приложение открывается сразу после выдачи пропуска.
     */
    @Volatile
    var lastGranted: Pass? = null
        private set

    /** Выдать пропуск для приложения [packageName]. */
    suspend fun grant(packageName: String) {
        val now = System.currentTimeMillis()
        val pass = Pass(packageName = packageName, startedAt = now, endsAt = now + PASS_DURATION_MS)
        lastGranted = pass
        dao.insert(pass)
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        const val PASS_DURATION_MS = 10 * 60_000L
        const val UNLOCK_WAIT_SECONDS = 30
        private const val DAY_MS = 24 * 60 * 60_000L
    }
}
