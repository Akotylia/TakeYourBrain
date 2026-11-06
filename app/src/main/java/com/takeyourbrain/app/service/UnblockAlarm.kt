package com.takeyourbrain.app.service

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

/**
 * Будильник на снятие полной блокировки (через 10 минут после просьбы).
 * Без разрешения на точные будильники может опоздать, но это не страшно: служба сама сверяет время
 * и перестаёт блокировать ровно в срок, а будильник только убирает блокировку из правила и присылает уведомление.
 */
object UnblockAlarm {

    fun schedule(context: Context, at: Long) {
        Alarms.set(context, at, pendingIntent(context))
    }

    fun cancel(context: Context) {
        Alarms.cancel(context, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, UnblockReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Срабатывает, когда подошло время снять полную блокировку. */
class UnblockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as TakeYourBrainApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.rulesRepository.finishPendingUnblocks().forEach { pkg ->
                    val label = container.appsRepository.getApp(pkg)?.label ?: pkg
                    Notifications.showUnblocked(context, pkg, label)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
