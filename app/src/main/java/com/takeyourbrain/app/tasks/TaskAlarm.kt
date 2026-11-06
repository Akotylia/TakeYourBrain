package com.takeyourbrain.app.tasks

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.alarms.Alarms
import com.takeyourbrain.app.notifications.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Один будильник на самое раннее напоминание среди всех задач. */
object TaskAlarm {

    fun schedule(context: Context, at: Long) {
        Alarms.set(context, at, pendingIntent(context))
    }

    fun cancel(context: Context) {
        Alarms.cancel(context, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TaskAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Будильник сработал: показать напоминания, время которых пришло. */
class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tasks = (context.applicationContext as TakeYourBrainApp).container.taskRepository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                tasks.onAlarm()
            } finally {
                pending.finish()
            }
        }
    }
}

/** Кнопки в уведомлении: "Готово", "+10 мин", "+1 ч". */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (id == 0L) return
        val tasks = (context.applicationContext as TakeYourBrainApp).container.taskRepository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> {
                        tasks.setDone(id, true)
                        // Нажали в уведомлении "Фокус завершён" — его тоже убираем
                        Notifications.cancelFocusFinished(context)
                    }
                    ACTION_SNOOZE -> tasks.snooze(id, intent.getIntExtra(EXTRA_MINUTES, 10))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.takeyourbrain.app.TASK_DONE"
        const val ACTION_SNOOZE = "com.takeyourbrain.app.TASK_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_MINUTES = "minutes"
    }
}
