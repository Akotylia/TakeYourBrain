package com.takeyourbrain.app

import android.app.Application
import android.content.Context
import com.takeyourbrain.app.data.AppsRepository
import com.takeyourbrain.app.data.BankRepository
import com.takeyourbrain.app.data.BlockLogRepository
import com.takeyourbrain.app.data.ElementUsageRepository
import com.takeyourbrain.app.data.FocusRepository
import com.takeyourbrain.app.data.PassRepository
import com.takeyourbrain.app.data.PinRepository
import com.takeyourbrain.app.data.RulesRepository
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.data.ShoppingRepository
import com.takeyourbrain.app.data.StreakRepository
import com.takeyourbrain.app.data.TaskRepository
import com.takeyourbrain.app.data.UsageRepository
import com.takeyourbrain.app.data.db.AppDatabase
import com.takeyourbrain.app.domain.BlockInfo
import com.takeyourbrain.app.notifications.Notifications
import com.takeyourbrain.app.permissions.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow

/** Класс приложения. Создаётся один раз при запуске и хранит общие объекты. */
class TakeYourBrainApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}

/**
 * "Контейнер" со всеми репозиториями. Так ViewModel получают нужные объекты,
 * и каждый репозиторий существует в одном экземпляре на всё приложение.
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val settingsRepository = SettingsRepository(context)
    val usageRepository = UsageRepository(context)
    val bankRepository = BankRepository(context, database.bankDao(), database.ruleDao(), usageRepository, settingsRepository)
    val rulesRepository = RulesRepository(context, database.ruleDao(), bankRepository)
    val passRepository = PassRepository(database.passDao(), settingsRepository)
    val pinRepository = PinRepository(context)
    val focusRepository = FocusRepository(context)
    val blockLogRepository = BlockLogRepository(database.blockEventDao())
    val taskRepository = TaskRepository(context, database.taskDao(), settingsRepository)
    val shoppingRepository = ShoppingRepository(database.shoppingDao())
    val elementUsageRepository = ElementUsageRepository(context)
    val streakRepository = StreakRepository(
        context, database.dayResultDao(), database.ruleDao(), database.passDao(), database.bankDao(), usageRepository,
    )
    val appsRepository = AppsRepository(context)
    val permissionChecker = PermissionChecker(context)

    /** Последняя блокировка: служба кладёт сюда, экран блокировки читает отсюда. */
    val currentBlock = MutableStateFlow<BlockInfo?>(null)
}
