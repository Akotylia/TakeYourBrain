package com.takeyourbrain.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.takeyourbrain.app.data.db.AppRule
import com.takeyourbrain.app.data.db.daysFromMask
import com.takeyourbrain.app.data.db.daysToMask
import com.takeyourbrain.app.domain.QuietHours
import com.takeyourbrain.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

// Файл настроек DataStore (один на всё приложение)
private val Context.dataStore by preferencesDataStore(name = "settings")

/** Настройки приложения (хранятся в DataStore и переживают перезапуск). */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val INTRO_SEEN = booleanPreferencesKey("intro_seen")
        val TOUR_SEEN = booleanPreferencesKey("tour_seen")
        val BLOCKED_SORT = stringPreferencesKey("blocked_sort")
        val ALL_SORT = stringPreferencesKey("all_sort")
        val WARNING_MINUTES = intPreferencesKey("warning_minutes")
        val PASSES_PER_DAY = intPreferencesKey("passes_per_day")
        val BANK_CAP = intPreferencesKey("bank_cap")
        val BANK_WEEKLY_RESET = booleanPreferencesKey("bank_weekly_reset")
        val BANK_SPEND_DAYS = intPreferencesKey("bank_spend_days")
        val BANK_INSIDE_BLOCKED_BUYABLE = booleanPreferencesKey("bank_inside_blocked_buyable")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val SHOPPING_LIST = longPreferencesKey("shopping_list")
        val DEBUG_DUMP = booleanPreferencesKey("debug_dump")
        val THEME = stringPreferencesKey("theme")
        val HAPTICS = booleanPreferencesKey("haptics")
        val GLASS = booleanPreferencesKey("glass")
        val USER_NAME = stringPreferencesKey("user_name")
        val BANK_INTRO_SEEN = booleanPreferencesKey("bank_intro_seen")
    }

    /** Тихие часы для напоминаний: включены ли и с какого по какое время (минуты от полуночи). */
    val quietEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.QUIET_ENABLED] ?: false }
    val quietStart: Flow<Int> = context.dataStore.data.map { it[Keys.QUIET_START] ?: DEFAULT_QUIET_START }
    val quietEnd: Flow<Int> = context.dataStore.data.map { it[Keys.QUIET_END] ?: DEFAULT_QUIET_END }

    /** Тихие часы, если они включены (иначе null). */
    val quietHours: Flow<QuietHours?> = context.dataStore.data.map { prefs ->
        if (prefs[Keys.QUIET_ENABLED] != true) {
            null
        } else {
            QuietHours(prefs[Keys.QUIET_START] ?: DEFAULT_QUIET_START, prefs[Keys.QUIET_END] ?: DEFAULT_QUIET_END)
        }
    }

    suspend fun setQuietHours(enabled: Boolean, startMinute: Int, endMinute: Int) {
        context.dataStore.edit {
            it[Keys.QUIET_ENABLED] = enabled
            it[Keys.QUIET_START] = startMinute
            it[Keys.QUIET_END] = endMinute
        }
    }

    /** Потолок банка времени (минут на одно приложение). */
    /** Тема: как в телефоне / светлая / тёмная. */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    /** Видел ли пользователь "Как это работает" в банке (потом объяснение прячется за "ⓘ"). */
    val bankIntroSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.BANK_INTRO_SEEN] ?: false }

    suspend fun setBankIntroSeen() {
        context.dataStore.edit { it[Keys.BANK_INTRO_SEEN] = true }
    }

    /** Как обращаться к пользователю ("Здравствуйте, Егор"); пусто — просто "Здравствуйте". */
    val userName: Flow<String> = context.dataStore.data.map { it[Keys.USER_NAME].orEmpty() }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name.trim() }
    }

    /** Лёгкая вибрация на вкладках, цифрах PIN и переключателях. */
    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTICS] ?: true }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    /** Стеклянный стиль: панель размывает то, что под ней, карточки полупрозрачные. По умолчанию выключен. */
    val glassEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.GLASS] ?: false }

    suspend fun setGlassEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GLASS] = enabled }
    }

    /** Отладочный дамп экрана в Logcat (виден только в отладочной сборке). */
    val debugDump: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEBUG_DUMP] ?: false }

    suspend fun setDebugDump(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEBUG_DUMP] = enabled }
    }

    /** Какой список покупок был открыт последним (null — ещё не открывали). */
    val shoppingListId: Flow<Long?> = context.dataStore.data.map { it[Keys.SHOPPING_LIST] }

    suspend fun setShoppingListId(id: Long) {
        context.dataStore.edit { it[Keys.SHOPPING_LIST] = id }
    }

    val bankCap: Flow<Int> = context.dataStore.data.map { it[Keys.BANK_CAP] ?: DEFAULT_BANK_CAP }

    suspend fun setBankCap(minutes: Int) {
        context.dataStore.edit { it[Keys.BANK_CAP] = minutes }
    }

    /** Сгорает ли банк в ночь на понедельник (иначе копится дальше, но не выше потолка). */
    val bankWeeklyReset: Flow<Boolean> = context.dataStore.data.map { it[Keys.BANK_WEEKLY_RESET] ?: true }

    suspend fun setBankWeeklyReset(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BANK_WEEKLY_RESET] = enabled }
    }

    /** В какие дни можно тратить банк (по умолчанию суббота и воскресенье). */
    val bankSpendDays: Flow<Set<DayOfWeek>> = context.dataStore.data.map { prefs ->
        prefs[Keys.BANK_SPEND_DAYS]?.let(::daysFromMask) ?: AppRule.WEEKEND
    }

    suspend fun setBankSpendDays(days: Set<DayOfWeek>) {
        context.dataStore.edit { it[Keys.BANK_SPEND_DAYS] = daysToMask(days) }
    }

    /** Можно ли выкупать время внутри окна "заблокировано" (дороже, ×3). По умолчанию нельзя. */
    val bankInsideBlockedBuyable: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BANK_INSIDE_BLOCKED_BUYABLE] ?: false }

    suspend fun setBankInsideBlockedBuyable(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BANK_INSIDE_BLOCKED_BUYABLE] = enabled }
    }

    /** Сколько разовых пропусков можно взять за день (на все приложения вместе). */
    val passesPerDay: Flow<Int> = context.dataStore.data.map { it[Keys.PASSES_PER_DAY] ?: DEFAULT_PASSES_PER_DAY }

    suspend fun setPassesPerDay(count: Int) {
        context.dataStore.edit { it[Keys.PASSES_PER_DAY] = count }
    }

    /** За сколько минут до блокировки присылать предупреждение. */
    val warningMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.WARNING_MINUTES] ?: DEFAULT_WARNING_MINUTES }

    suspend fun setWarningMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.WARNING_MINUTES] = minutes }
    }

    /** Пройден ли стартовый помощник по разрешениям. */
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /** Заставку с логотипом уже показали (она идёт один раз, перед помощником). */
    val introSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.INTRO_SEEN] ?: false }

    suspend fun setIntroSeen() {
        context.dataStore.edit { it[Keys.INTRO_SEEN] = true }
    }

    /** Знакомство с приложением уже показали (карточки при первом входе на "Приложения"). */
    val tourSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.TOUR_SEEN] ?: false }

    suspend fun setTourSeen() {
        context.dataStore.edit { it[Keys.TOUR_SEEN] = true }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    /** Сортировка раздела "Заблокированные". */
    val blockedSort: Flow<AppSort> = sortFlow(Keys.BLOCKED_SORT)

    /** Сортировка раздела "Все приложения". */
    val allSort: Flow<AppSort> = sortFlow(Keys.ALL_SORT)

    suspend fun setBlockedSort(sort: AppSort) = saveSort(Keys.BLOCKED_SORT, sort)

    suspend fun setAllSort(sort: AppSort) = saveSort(Keys.ALL_SORT, sort)

    // Сортировку храним как текст (название варианта); по умолчанию — по времени использования
    private fun sortFlow(key: Preferences.Key<String>): Flow<AppSort> = context.dataStore.data.map { prefs ->
        AppSort.entries.firstOrNull { it.name == prefs[key] } ?: AppSort.USAGE
    }

    private suspend fun saveSort(key: Preferences.Key<String>, sort: AppSort) {
        context.dataStore.edit { it[key] = sort.name }
    }

    companion object {
        const val DEFAULT_WARNING_MINUTES = 5

        /** Варианты времени предупреждения в настройках. */
        val WARNING_OPTIONS = listOf(1, 3, 5, 10, 15)

        const val DEFAULT_PASSES_PER_DAY = 3

        /** Варианты числа пропусков в день. */
        val PASSES_OPTIONS = listOf(0, 1, 2, 3, 5, 10)

        const val DEFAULT_BANK_CAP = 180

        const val DEFAULT_QUIET_START = 23 * 60
        const val DEFAULT_QUIET_END = 7 * 60

        /** Варианты потолка банка в минутах. */
        val BANK_CAP_OPTIONS = listOf(60, 120, 180, 240, 300)
    }
}
