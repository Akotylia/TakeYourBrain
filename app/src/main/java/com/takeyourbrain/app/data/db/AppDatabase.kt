package com.takeyourbrain.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * База данных приложения.
 * При каждом изменении таблиц увеличиваем version и добавляем миграцию,
 * чтобы не потерять уже созданные правила.
 * Версия 2: добавлена таблица пропусков (passes).
 * Версия 3: добавлена таблица срабатываний блокировки (block_events).
 * Версия 4: лимит отдельно на выходные, ожидание снятия полной блокировки, банк времени.
 * Версия 5: задачи ежедневника (tasks).
 * Версия 6: списки покупок (shopping_lists, shopping_items, shopping_history).
 * Версия 7: итоги дней для серии-"огонька" (day_results).
 * Версия 8: блокировка частей приложения (app_rules.blockedElements, например YouTube Shorts).
 */
@Database(
    entities = [
        AppRule::class, ScheduleInterval::class, Pass::class, BlockEvent::class,
        BankTx::class, LimitSnapshot::class, BankSession::class, Task::class,
        ShoppingList::class, ShoppingItem::class, ShoppingHistory::class, DayResult::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = AppDatabase.Migration3To4::class),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8),
    ],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao

    abstract fun passDao(): PassDao

    abstract fun blockEventDao(): BlockEventDao

    abstract fun bankDao(): BankDao

    abstract fun taskDao(): TaskDao

    abstract fun shoppingDao(): ShoppingDao

    abstract fun dayResultDao(): DayResultDao

    /** Раньше лимит был один на все дни — теперь он же становится лимитом и на выходные. */
    class Migration3To4 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL("UPDATE app_rules SET weekendLimitMinutes = dailyLimitMinutes")
        }
    }

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "take_your_brain.db").build()
    }
}
