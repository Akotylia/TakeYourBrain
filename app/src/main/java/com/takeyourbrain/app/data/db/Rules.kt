package com.takeyourbrain.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.DayOfWeek
import java.time.LocalDate

/** Вид расписания. */
enum class ScheduleMode {
    NONE,          // без расписания
    ALLOW_ONLY,    // разрешено только в указанные периоды
    BLOCK_DURING,  // заблокировано в указанные периоды
}

/** Правило блокировки для одного приложения (одна строка таблицы app_rules). */
@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val fullBlock: Boolean,
    // Лимит в будни (пн–пт). Колонка называется по-старому: до версии 4 это был лимит на каждый день
    @ColumnInfo(name = "dailyLimitMinutes") val weekdayLimitMinutes: Int?, // null — в будни лимита нет
    val weekendLimitMinutes: Int? = null, // лимит в сб–вс, null — в выходные лимита нет
    val scheduleMode: ScheduleMode,
    /**
     * Когда снимется полная блокировка (миллисекунды). Пока это время не наступило, блокировка действует.
     * null — снимать не просили.
     */
    val fullBlockOffAt: Long? = null,
    /**
     * Заблокированные части приложения через запятую: "youtube_shorts" — всегда,
     * "youtube_shorts:15" — после 15 минут в день (id из BlockableElement).
     */
    @ColumnInfo(defaultValue = "") val blockedElements: String = "",
) {
    /** Заблокированные части приложения: id → лимит в минутах в день (null — заблокированы всегда). */
    val elementLimits: Map<String, Int?>
        get() = blockedElements.split(',').filter { it.isNotBlank() }.associate { item ->
            val parts = item.split(':')
            parts[0] to parts.getOrNull(1)?.toIntOrNull()
        }

    /** Заблокированные части приложения (без лимитов). */
    val elements: Set<String>
        get() = elementLimits.keys

    /** Лимит в минутах на дату [date] (будни или выходные) или null, если в этот день лимита нет. */
    fun limitOn(date: LocalDate): Int? =
        if (date.dayOfWeek in WEEKEND) weekendLimitMinutes else weekdayLimitMinutes

    /** Действует ли полная блокировка в момент [nowMs] (с учётом ожидания снятия). */
    fun isFullBlockActive(nowMs: Long): Boolean = fullBlock && (fullBlockOffAt == null || nowMs < fullBlockOffAt)

    /** Есть ли в правиле что-то кроме полной блокировки. */
    val hasOtherConditions: Boolean
        get() = weekdayLimitMinutes != null || weekendLimitMinutes != null || scheduleMode != ScheduleMode.NONE ||
            elements.isNotEmpty()

    companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/**
 * Период расписания. Время — минуты от начала суток (0..1439).
 * Если end <= start, период переходит через полночь (например, 23:00–07:00).
 * Дни недели — это дни, в которые период НАЧИНАЕТСЯ.
 */
@Entity(
    tableName = "schedule_intervals",
    foreignKeys = [
        // Удалили правило — его периоды удалятся сами
        ForeignKey(
            entity = AppRule::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("packageName")],
)
data class ScheduleInterval(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int, // бит 0 — понедельник ... бит 6 — воскресенье
) {
    val days: Set<DayOfWeek> get() = daysFromMask(daysMask)
}

/** Правило вместе с его периодами (Room собирает их из двух таблиц). */
data class RuleWithIntervals(
    @Embedded val rule: AppRule,
    @Relation(parentColumn = "packageName", entityColumn = "packageName")
    val intervals: List<ScheduleInterval>,
)

/** Набор дней недели → число-маска для хранения в базе. */
fun daysToMask(days: Set<DayOfWeek>): Int =
    days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

/** Число-маска из базы → набор дней недели. */
fun daysFromMask(mask: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
