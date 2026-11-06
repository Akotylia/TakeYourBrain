package com.takeyourbrain.app.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Открывает нужные экраны системных настроек.
 * Если телефон не знает "точный" экран, открываем более общий (запасной вариант).
 */
object SystemSettings {

    fun openUsageAccess(context: Context) = context.openFirstAvailable(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(context)), // сразу наше приложение
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),                      // общий список
    )

    fun openAccessibility(context: Context) = context.openFirstAvailable(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
    )

    fun openNotificationSettings(context: Context) = context.openFirstAvailable(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetailsIntent(context),
    )

    /**
     * Список "Оптимизация батареи": человек сам выбирает наше приложение. Системный диалог-запрос
     * Google Play разрешает только отдельным видам приложений, поэтому его не используем.
     */
    fun openBatteryOptimization(context: Context) = context.openFirstAvailable(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetailsIntent(context),
    )

    /** Экран "Батарея" Samsung (там "Ограничения в фоновом режиме"); на других телефонах — общий. */
    fun openSamsungBattery(context: Context) = context.openFirstAvailable(
        Intent("com.samsung.android.sm.ACTION_BATTERY"),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    /** "Сведения о приложении" (там же "Разрешить ограниченные настройки" и батарея). */
    /** "Будильники и напоминания": разрешить точные будильники. */
    fun openExactAlarms(context: Context) = context.openFirstAvailable(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)),
        appDetailsIntent(context),
    )

    fun openAppDetails(context: Context) = context.openFirstAvailable(appDetailsIntent(context))

    private fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    /** Пробует открыть экраны по очереди, пока какой-нибудь не откроется. */
    private fun Context.openFirstAvailable(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Такого экрана нет на этом телефоне, пробуем следующий
            }
        }
    }
}
