package com.takeyourbrain.app.notifications

import android.Manifest
import android.app.LocaleManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.takeyourbrain.app.MainActivity
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.db.Task
import com.takeyourbrain.app.tasks.TaskActionReceiver
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Всё про уведомления: каналы, предупреждения, фокус и напоминания. */
object Notifications {

    private const val OLD_CHANNEL_PROTECTION = "protection"
    private const val CHANNEL_WARNINGS = "warnings"
    private const val CHANNEL_FOCUS = "focus"
    private const val CHANNEL_REMINDERS = "reminders"

    private const val FOCUS_ID = 2
    private const val FOCUS_FINISHED_ID = 3
    private const val TASK_ID_BASE = 10_000 // уведомления задач: 10000 + номер задачи

    /**
     * Контекст с языком, выбранным в приложении (Настройки → Язык). Контекст приложения и служб
     * со временем может вернуться к языку телефона, поэтому тексты уведомлений берём через этот.
     */
    fun localized(context: Context): Context {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) return context
        val config = Configuration(context.resources.configuration).apply { setLocales(locales) }
        return context.createConfigurationContext(config)
    }

    /** Создать каналы уведомлений (можно вызывать много раз, система не создаст дубликаты). */
    fun createChannels(context: Context) {
        val strings = localized(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        // Канал постоянного уведомления был в старых версиях — убираем из настроек телефона
        manager.deleteNotificationChannel(OLD_CHANNEL_PROTECTION)
        val warnings = NotificationChannel(
            CHANNEL_WARNINGS,
            strings.getString(R.string.channel_warnings),
            NotificationManager.IMPORTANCE_HIGH // со звуком и всплыванием поверх экрана
        ).apply {
            description = strings.getString(R.string.channel_warnings_desc)
        }
        val focus = NotificationChannel(
            CHANNEL_FOCUS,
            strings.getString(R.string.channel_focus),
            NotificationManager.IMPORTANCE_LOW // тихо, но видно в шторке
        ).apply {
            description = strings.getString(R.string.channel_focus_desc)
            setShowBadge(false)
        }
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            strings.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH // напоминание должно быть заметным
        ).apply {
            description = strings.getString(R.string.channel_reminders_desc)
        }
        manager.createNotificationChannels(listOf(warnings, focus, reminders))
    }

    /** Уведомление "Фокус до 15:30" с обратным отсчётом; само исчезает в конце фокуса. [taskTitle] — фокус на задаче. */
    fun showFocus(context: Context, endsAt: Long, appsCount: Int, taskTitle: String? = null) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val strings = localized(context)
        val endTime = formatTimeOfDay(context, LocalDateTime.ofInstant(Instant.ofEpochMilli(endsAt), ZoneId.systemDefault()))
        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.focus_notif_title, endTime))
            .setContentText(taskTitle ?: strings.getString(R.string.focus_apps_blocked, appsCount))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setSilent(true)
            // Таймер в уведомлении сам считает назад до конца фокуса
            .setWhen(endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter((endsAt - System.currentTimeMillis()).coerceAtLeast(0))
            .build()
        NotificationManagerCompat.from(context).notify(FOCUS_ID, notification)
    }

    fun cancelFocus(context: Context) {
        NotificationManagerCompat.from(context).cancel(FOCUS_ID)
    }

    /** "Фокус завершён. Отлично!" Если это был фокус на задаче — кнопка "Задача выполнена". */
    fun showFocusFinished(context: Context, taskId: Long? = null, taskTitle: String? = null, silent: Boolean = false) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val strings = localized(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.focus_finished_title))
            .setContentText(taskTitle ?: strings.getString(R.string.focus_finished_text))
            .setSilent(silent) // свой звук уже играет — обычный сверху не нужен
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .apply {
                if (taskId != null) {
                    addAction(0, strings.getString(R.string.focus_task_done), taskAction(context, taskId, TaskActionReceiver.ACTION_DONE, 0))
                }
            }
            .build()
        NotificationManagerCompat.from(context).notify(FOCUS_FINISHED_ID, notification)
    }

    fun cancelFocusFinished(context: Context) {
        NotificationManagerCompat.from(context).cancel(FOCUS_FINISHED_ID)
    }

    /** "Полная блокировка снята: YouTube" — ожидание 10 минут закончилось. */
    fun showUnblocked(context: Context, packageName: String, label: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val strings = localized(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.unblocked_title, label))
            .setContentText(strings.getString(R.string.unblocked_text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify("unblocked:$packageName".hashCode(), notification)
    }

    /** Показать предупреждение "скоро блокировка". [id] — чтобы одинаковые не дублировались. */
    fun showWarning(context: Context, id: Int, title: String, text: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Напоминание о задаче с кнопками "Готово", "+10 мин", "+1 ч". */
    fun showTask(context: Context, task: Task) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val strings = localized(context)
        val text = task.note.ifBlank { strings.getString(R.string.task_notification_text) }
        val open = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TASKS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, strings.getString(R.string.task_action_done), taskAction(context, task.id, TaskActionReceiver.ACTION_DONE, 0))
            .addAction(0, strings.getString(R.string.task_action_snooze_10), taskAction(context, task.id, TaskActionReceiver.ACTION_SNOOZE, 10))
            .addAction(0, strings.getString(R.string.task_action_snooze_60), taskAction(context, task.id, TaskActionReceiver.ACTION_SNOOZE, 60))
            .build()
        NotificationManagerCompat.from(context).notify(TASK_ID_BASE + task.id.toInt(), notification)
    }

    fun cancelTask(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(TASK_ID_BASE + taskId.toInt())
    }

    /** Кнопка уведомления задачи. У каждой задачи и кнопки свой requestCode, чтобы они не путались. */
    private fun taskAction(context: Context, taskId: Long, action: String, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (taskId * 100 + minutes + if (action == TaskActionReceiver.ACTION_DONE) 1 else 0).toInt(),
            Intent(context, TaskActionReceiver::class.java)
                .setAction(action)
                .putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
                .putExtra(TaskActionReceiver.EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Нажатие на уведомление открывает приложение. */
    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE
    )
}
