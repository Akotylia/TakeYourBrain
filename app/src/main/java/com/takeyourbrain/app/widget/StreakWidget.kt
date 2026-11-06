package com.takeyourbrain.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.takeyourbrain.app.MainActivity
import com.takeyourbrain.app.R
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.notifications.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Виджет "Огонёк" на экране телефона: сколько дней подряд без превышения лимитов. */
class StreakWidgetProvider : AppWidgetProvider() {
    // Система просит обновить виджет: считаем серию, а info() сама перерисует его через StreakWidget.show
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val streak = (context.applicationContext as TakeYourBrainApp).container.streakRepository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                streak.info()
            } finally {
                pending.finish()
            }
        }
    }
}

object StreakWidget {
    /** Показывает серию [days] на всех виджетах (если их нет, ничего не делает). */
    fun show(context: Context, days: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StreakWidgetProvider::class.java))
        if (ids.isEmpty()) return
        // Контекст приложения может быть на языке телефона, а не на выбранном
        val strings = Notifications.localized(context)
        val views = RemoteViews(context.packageName, R.layout.widget_streak).apply {
            setTextViewText(R.id.widget_days, strings.resources.getQuantityString(R.plurals.streak_days, days, days))
            setTextViewText(R.id.widget_caption, strings.getString(R.string.streak_title))
            // Серии нет — огонёк серый
            setColorStateList(R.id.widget_flame, "setImageTintList", if (days > 0) R.color.widget_flame else R.color.widget_flame_off)
            setOnClickPendingIntent(R.id.widget_root, openApp(context))
        }
        manager.updateAppWidget(ids, views)
    }

    /** Системное окно "Добавить виджет на экран" (если лаунчер это умеет). */
    fun requestPin(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(context, StreakWidgetProvider::class.java), null, null)
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
