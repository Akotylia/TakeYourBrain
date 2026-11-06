package com.takeyourbrain.app.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.takeyourbrain.app.alarms.Alarms
import com.takeyourbrain.app.service.BlockerAccessibilityService

/** Какие разрешения сейчас выданы. */
data class PermissionsState(
    val usageAccess: Boolean = false,
    val accessibility: Boolean = false,
    val notifications: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val exactAlarms: Boolean = false,
    /** Установлено из файла, и Android пока не даёт включить статистику и службу ("ограниченные настройки"). */
    val restrictedSettings: Boolean = false,
) {
    /** Без этих двух приложение не может работать. */
    val allRequiredGranted: Boolean get() = usageAccess && accessibility

    val grantedCount: Int
        get() = listOf(usageAccess, accessibility, notifications, batteryUnrestricted, exactAlarms).count { it }

    val totalCount: Int get() = 5
}

/** Проверяет разрешения. Ничего не запрашивает, только смотрит текущее состояние. */
class PermissionChecker(private val context: Context) {

    fun check() = PermissionsState(
        usageAccess = hasUsageAccess(),
        accessibility = isAccessibilityServiceEnabled(),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        exactAlarms = Alarms.canScheduleExact(context),
        restrictedSettings = areSettingsRestricted(),
    )

    /**
     * Установлено из файла (не из магазина) — тогда Android 13+ включает "ограниченные настройки".
     * Снять их — Сведения о приложении → ⋮ → "Разрешить ограниченные настройки". Спросить у системы,
     * сняты ли они, обычному приложению нельзя (Android 13 роняет приложение), поэтому смотрим источник.
     */
    private fun areSettingsRestricted(): Boolean {
        val source = try {
            context.packageManager.getInstallSourceInfo(context.packageName).packageSource
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        return source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE ||
            source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
    }

    /** Доступ к статистике использования (Настройки → Доступ к данным об использовании). */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        // MODE_DEFAULT значит "решает обычное разрешение" (например, если выдано через adb)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * Включена ли наша служба специальных возможностей.
     * Читаем саму системную настройку: список "работающих служб" от AccessibilityManager
     * сразу после запуска приложения бывает ещё пустым, и проверка ошибочно говорила "выключена".
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val ours = ComponentName(context, BlockerAccessibilityService::class.java)
        // Формат: "пакет/класс:пакет/класс:..."
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }
}
