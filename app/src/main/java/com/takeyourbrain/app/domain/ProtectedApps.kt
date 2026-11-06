package com.takeyourbrain.app.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager

/**
 * Приложения, которые нельзя заблокировать, чтобы случайно не "запереть" себя:
 * без них не позвонить (в том числе в экстренные службы), не попасть на домашний экран
 * и не исправить настройки. Названия пакетов проверены на Galaxy A56 (One UI),
 * плюс стандартные варианты Android на случай другого телефона.
 */
object ProtectedApps {

    private val ALWAYS = setOf(
        // Настройки и системный интерфейс (шторка, экран блокировки)
        "com.android.settings",
        "com.android.systemui",
        // Телефон и звонки
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        // Контакты (на Samsung звонят и отсюда)
        "com.samsung.android.app.contacts",
        "com.android.contacts",
        "com.google.android.contacts",
        // Экстренные вызовы и SOS
        "com.samsung.android.emergency",
        "com.sec.android.app.safetyassurance",
        "com.sec.android.emergencylauncher",
        "com.android.emergency",
        // Домашний экран
        "com.sec.android.app.launcher",
    )

    /** Можно ли поставить правило на это приложение. */
    fun isProtected(context: Context, packageName: String): Boolean =
        packageName == context.packageName ||
            packageName in ALWAYS ||
            packageName == defaultDialer(context) ||
            packageName == defaultLauncher(context)

    // Звонилка, выбранная по умолчанию (если пользователь поставил другую)
    private fun defaultDialer(context: Context): String? =
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

    // Лаунчер, выбранный по умолчанию
    private fun defaultLauncher(context: Context): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(
            homeIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )?.activityInfo?.packageName
    }
}
