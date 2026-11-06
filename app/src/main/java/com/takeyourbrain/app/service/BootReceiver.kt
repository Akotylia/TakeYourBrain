package com.takeyourbrain.app.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.takeyourbrain.app.TakeYourBrainApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Срабатывает после включения телефона, после обновления приложения, при смене времени
 * или часового пояса и когда пользователь разрешил точные будильники.
 * Служба специальных возможностей запускается системой сама. Здесь восстанавливаем будильники
 * фокуса, снятия полной блокировки и напоминаний (после перезагрузки они теряются).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = (context.applicationContext as TakeYourBrainApp).container
        // Настройки читаем в фоне; goAsync даёт на это время
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.focusRepository.restoreAfterReboot()
                // Будильник снятия полной блокировки после перезагрузки тоже теряется
                container.rulesRepository.finishPendingUnblocks()
                // Напоминания: пропущенные покажем, следующие переставим (в том числе после смены времени)
                container.taskRepository.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
