package com.takeyourbrain.app.focus

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.takeyourbrain.app.R
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.alarms.Alarms
import com.takeyourbrain.app.notifications.AppSound
import com.takeyourbrain.app.notifications.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Будильник на конец фокуса. Точный, если разрешены точные будильники, иначе может опоздать
 * на несколько минут. Сама блокировка в любом случае заканчивается вовремя — служба сверяет время,
 * а будильник только убирает сеанс и присылает "Фокус завершён".
 */
object FocusAlarm {

    fun schedule(context: Context, endsAt: Long) {
        Alarms.set(context, endsAt, pendingIntent(context))
    }

    fun cancel(context: Context) {
        Alarms.cancel(context, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, FocusEndReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Срабатывает, когда время фокуса вышло. */
class FocusEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val focus = (context.applicationContext as TakeYourBrainApp).container.focusRepository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = focus.session.first()
                when {
                    session == null -> Unit // фокус уже отменили
                    !session.isActive() -> {
                        focus.stop()
                        val played = AppSound.play(context, R.raw.tyb_logo_sound)
                        Notifications.showFocusFinished(context, session.taskId, session.taskTitle, silent = played)
                    }
                    // Сработал раньше времени — переставляем на конец фокуса
                    else -> FocusAlarm.schedule(context, session.endsAt)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
