package com.takeyourbrain.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.takeyourbrain.app.notifications.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Приложение, которое можно запустить с экрана телефона. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
    val installTime: Long, // когда приложение впервые установили (мс)
)

/** Получает список приложений с иконками из PackageManager. */
class AppsRepository(private val context: Context) {

    // Иконки загружаются медленно, поэтому храним уже загруженные в памяти
    private val iconCache = ConcurrentHashMap<String, Bitmap>()

    // Размер иконки в пикселях: 40dp с учётом плотности экрана
    private val iconSizePx = (40 * context.resources.displayMetrics.density).toInt()

    /** Все приложения с иконкой на экране, отсортированные по названию. */
    suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        // Те же приложения, что видны в меню приложений (их разрешает увидеть <queries> в манифесте)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            .distinctBy { it.activityInfo.packageName } // у приложения может быть несколько иконок
            .map { info ->
                InstalledApp(
                    packageName = info.activityInfo.packageName,
                    label = label(info.activityInfo, info.activityInfo.applicationInfo, pm),
                    icon = loadIcon(info, pm),
                    installTime = installTime(info.activityInfo.packageName, pm),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Одно приложение по имени пакета или null, если его уже удалили. */
    suspend fun getApp(packageName: String): InstalledApp? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        try {
            val info = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            InstalledApp(
                packageName = packageName,
                label = label(info, info, pm),
                icon = iconCache.getOrPut(packageName) { info.loadIcon(pm).toBitmap(iconSizePx, iconSizePx) },
                installTime = installTime(packageName, pm),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Название на языке, выбранном в приложении. Обычный loadLabel берёт язык текущего процесса, а он бывает
     * языком телефона (процесс запустила служба): тогда одно и то же приложение то "2ГИС", то "2GIS".
     */
    private fun label(item: PackageItemInfo, app: ApplicationInfo, pm: PackageManager): String {
        val labelRes = if (item.labelRes != 0) item.labelRes else app.labelRes
        if (item.nonLocalizedLabel == null && labelRes != 0) {
            val config = Notifications.localized(context).resources.configuration
            try {
                return pm.getResourcesForApplication(app, config).getString(labelRes)
            } catch (_: Exception) {
                // Нет такого ресурса или приложение только что удалили — возьмём обычное название
            }
        }
        return item.loadLabel(pm).toString()
    }

    // У встроенных приложений это обычно дата прошивки телефона
    private fun installTime(packageName: String, pm: PackageManager): Long = try {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).firstInstallTime
    } catch (_: PackageManager.NameNotFoundException) {
        0L
    }

    private fun loadIcon(info: ResolveInfo, pm: PackageManager): Bitmap =
        iconCache.getOrPut(info.activityInfo.packageName) {
            info.loadIcon(pm).toBitmap(iconSizePx, iconSizePx)
        }
}
