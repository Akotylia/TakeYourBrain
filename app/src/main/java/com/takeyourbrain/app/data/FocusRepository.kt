package com.takeyourbrain.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.takeyourbrain.app.focus.FocusAlarm
import com.takeyourbrain.app.notifications.Notifications
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Отдельный файл DataStore для режима фокуса
private val Context.focusStore by preferencesDataStore(name = "focus")

/** Сеанс фокуса: какие приложения заблокированы и до какого момента. [taskId] — фокус на задаче (или null). */
data class FocusSession(
    val apps: Set<String>,
    val startedAt: Long,
    val endsAt: Long,
    val taskId: Long? = null,
    val taskTitle: String? = null,
) {
    fun isActive(now: Long = System.currentTimeMillis()) = now < endsAt
}

/**
 * Режим фокуса. Сеанс хранится в DataStore, поэтому переживает перезапуск приложения
 * и перезагрузку телефона. Будильник в конце сеанса снимает фокус и присылает уведомление.
 */
class FocusRepository(private val context: Context) {

    private object Keys {
        val APPS = stringSetPreferencesKey("focus_apps")
        val STARTED_AT = longPreferencesKey("focus_started_at")
        val ENDS_AT = longPreferencesKey("focus_ends_at")
        val LAST_APPS = stringSetPreferencesKey("focus_last_apps") // прошлый выбор, чтобы не выбирать заново
        val TASK_ID = longPreferencesKey("focus_task_id")
        val TASK_TITLE = stringPreferencesKey("focus_task_title")
    }

    /** Текущий сеанс (или null). Может быть уже закончившимся — проверяйте isActive(). */
    val session: Flow<FocusSession?> = context.focusStore.data.map { prefs ->
        val endsAt = prefs[Keys.ENDS_AT] ?: return@map null
        FocusSession(prefs[Keys.APPS].orEmpty(), prefs[Keys.STARTED_AT] ?: endsAt, endsAt, prefs[Keys.TASK_ID], prefs[Keys.TASK_TITLE])
    }

    /** Приложения, выбранные в прошлый раз. */
    val lastSelection: Flow<Set<String>> = context.focusStore.data.map { it[Keys.LAST_APPS].orEmpty() }

    /**
     * Начать фокус: [apps] блокируются до момента [endsAt] (миллисекунды, как System.currentTimeMillis).
     * [taskId] и [taskTitle] — если это фокус на задаче.
     */
    suspend fun start(apps: Set<String>, endsAt: Long, taskId: Long? = null, taskTitle: String? = null) {
        val now = System.currentTimeMillis()
        context.focusStore.edit {
            it[Keys.APPS] = apps
            it[Keys.STARTED_AT] = now
            it[Keys.ENDS_AT] = endsAt
            it[Keys.LAST_APPS] = apps
            if (taskId != null && taskTitle != null) {
                it[Keys.TASK_ID] = taskId
                it[Keys.TASK_TITLE] = taskTitle
            } else {
                it.remove(Keys.TASK_ID)
                it.remove(Keys.TASK_TITLE)
            }
        }
        FocusAlarm.schedule(context, endsAt)
        Notifications.showFocus(context, endsAt, apps.size, taskTitle)
    }

    /** Завершить фокус (досрочно через PIN или когда время вышло). */
    suspend fun stop() {
        context.focusStore.edit {
            it.remove(Keys.APPS)
            it.remove(Keys.STARTED_AT)
            it.remove(Keys.ENDS_AT)
            it.remove(Keys.TASK_ID)
            it.remove(Keys.TASK_TITLE)
        }
        FocusAlarm.cancel(context)
        Notifications.cancelFocus(context)
    }

    /**
     * Восстановить будильник и уведомление после перезагрузки телефона.
     * Если фокус закончился, пока телефон был выключен, — просто убрать его.
     */
    suspend fun restoreAfterReboot() {
        val current = session.first() ?: return
        if (current.isActive()) {
            FocusAlarm.schedule(context, current.endsAt)
            Notifications.showFocus(context, current.endsAt, current.apps.size, current.taskTitle)
        } else {
            stop()
        }
    }
}
