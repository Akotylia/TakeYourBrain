package com.takeyourbrain.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.takeyourbrain.app.R
import com.takeyourbrain.app.ui.apps.AppsScreen
import com.takeyourbrain.app.ui.bank.BankScreen
import com.takeyourbrain.app.ui.common.GlassBackdrop
import com.takeyourbrain.app.ui.common.LocalGlass
import com.takeyourbrain.app.ui.common.LocalOpenDialogs
import com.takeyourbrain.app.ui.focus.FocusScreen
import com.takeyourbrain.app.ui.intro.AppTourOnce
import com.takeyourbrain.app.ui.intro.IntroScreen
import com.takeyourbrain.app.ui.permissions.PermissionsScreen
import com.takeyourbrain.app.ui.pin.PinSetupScreen
import com.takeyourbrain.app.ui.profile.ProfileScreen
import com.takeyourbrain.app.ui.rules.RuleEditScreen
import com.takeyourbrain.app.ui.settings.MoreSettingsScreen
import com.takeyourbrain.app.ui.settings.PrivacyScreen
import com.takeyourbrain.app.ui.settings.SettingsScreen
import com.takeyourbrain.app.ui.shopping.ShoppingScreen
import com.takeyourbrain.app.ui.stats.StatsScreen
import com.takeyourbrain.app.ui.tasks.TaskEditScreen
import com.takeyourbrain.app.ui.tasks.TasksScreen
import com.takeyourbrain.app.ui.tasks.TodoScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Адреса (маршруты) экранов приложения. */
object Routes {
    const val INTRO = "intro"   // заставка с логотипом: первый запуск или из "Дополнительно"
    const val PERMISSIONS = "permissions"
    const val PIN_SETUP = "pin_setup"   // создание PIN при первом запуске
    const val PIN_CHANGE = "pin_change" // смена PIN из Настроек
    // Вкладки
    const val APPS = "apps"
    const val FOCUS = "focus"
    const val TODO = "todo"     // "Дела": плитки "Задачи" и "Покупки"
    const val PROFILE = "profile" // огонёк серии, банк, статистика, настройки

    // Экраны внутри вкладок
    const val STATS = "stats"   // открывается карточкой "Сегодня" на вкладке "Приложения" и из профиля
    const val BANK = "bank"           // из профиля
    const val SETTINGS = "settings"   // из профиля
    const val SETTINGS_MORE = "settings_more" // Настройки → Дополнительно
    const val PRIVACY = "privacy"     // политика конфиденциальности
    const val TASKS = "tasks"   // список задач
    const val ARG_TASK_ID = "taskId"
    const val TASK = "task/{$ARG_TASK_ID}" // задача (0 — новая)
    fun task(id: Long) = "task/$id"
    const val FOCUS_TASK = "focus_task/{$ARG_TASK_ID}" // фокус на задаче
    fun focusTask(id: Long) = "focus_task/$id"
    const val SHOPPING = "shopping" // список покупок

    // Экран правила: в адресе передаём имя пакета приложения, например "rule/com.google.android.youtube"
    const val ARG_PACKAGE_NAME = "packageName"
    const val RULE = "rule/{$ARG_PACKAGE_NAME}"
    fun rule(packageName: String) = "rule/$packageName"
}

/** Вкладки нижней навигации. */
private enum class Tab(val route: String, @param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    APPS(Routes.APPS, R.string.nav_apps, R.drawable.ic_apps),
    FOCUS(Routes.FOCUS, R.string.nav_focus, R.drawable.ic_focus),
    TODO(Routes.TODO, R.string.nav_todo, R.drawable.ic_todo),
    PROFILE(Routes.PROFILE, R.string.nav_profile, R.drawable.ic_profile),
}

/**
 * Корневой экран: нижняя навигация и область, где показываются экраны.
 * [startRoute] — первый экран: помощник по разрешениям при первом запуске, иначе список приложений.
 * [openTasks] — сразу открыть список задач (нажали на напоминание).
 */
@Composable
fun AppNavigation(startRoute: String, pinSet: Boolean, openTasks: Boolean = false) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // PIN пропал (например, после очистки данных), а помощник уже пройден — сначала создаём PIN.
    // Проверяем всё время, а не только при запуске: Android может восстановить старую навигацию.
    LaunchedEffect(pinSet, currentRoute) {
        if (!pinSet && currentRoute != null && currentRoute !in listOf(Routes.INTRO, Routes.PERMISSIONS, Routes.PIN_SETUP)) {
            navController.navigate(Routes.PIN_SETUP) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }
    LaunchedEffect(openTasks) {
        if (openTasks && startRoute == Routes.APPS) navController.navigate(Routes.TASKS)
    }
    // Нижнюю панель показываем только на экранах-вкладках
    val showBottomBar = Tab.entries.any { it.route == currentRoute }

    // То, что лежит под панелью (фон и экраны), — "источник" для стекла панели
    val hazeState = rememberHazeState()
    // Открыто окно или шторка — в стеклянном стиле размываем весь экран за ними
    val openDialogs = remember { mutableIntStateOf(0) }
    val blurBehind by animateDpAsState(
        targetValue = if (LocalGlass.current && openDialogs.intValue > 0) 16.dp else 0.dp,
        animationSpec = tween(200),
        label = "blurBehind"
    )
    CompositionLocalProvider(LocalOpenDialogs provides openDialogs) {
    Box(Modifier.fillMaxSize().blur(blurBehind)) {
        // На вкладках содержимое прокручивается под панелью: экраны добавляют этот запас в конец своих списков
        CompositionLocalProvider(LocalBottomBarSpace provides if (showBottomBar) FloatingBarSpace else 0.dp) {
            GlassBackdrop(Modifier.fillMaxSize().hazeSource(hazeState)) {
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.INTRO) {
                    // Открыта из "Дополнительно" (есть куда вернуться) или это первый запуск
                    val replay = navController.previousBackStackEntry != null
                    IntroScreen(
                        onDone = {
                            if (replay) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Routes.PERMISSIONS) {
                                    popUpTo(Routes.INTRO) { inclusive = true }
                                }
                            }
                        }
                    )
                }
                composable(Routes.PERMISSIONS) {
                    // Открыт из Настроек (есть куда вернуться) или это первый запуск
                    val openedFromSettings = navController.previousBackStackEntry != null
                    PermissionsScreen(
                        showBack = openedFromSettings,
                        onBack = { navController.popBackStack() },
                        onDone = {
                            if (openedFromSettings) {
                                navController.popBackStack()
                            } else {
                                // Первый запуск: дальше создание PIN, помощник убираем из истории "Назад"
                                navController.navigate(Routes.PIN_SETUP) {
                                    popUpTo(Routes.PERMISSIONS) { inclusive = true }
                                }
                            }
                        }
                    )
                }
                composable(Routes.PIN_SETUP) {
                    PinSetupScreen(
                        isChange = false,
                        onDone = {
                            navController.navigate(Routes.APPS) {
                                popUpTo(Routes.PIN_SETUP) { inclusive = true }
                            }
                        },
                        onBack = {}
                    )
                }
                composable(Routes.PIN_CHANGE) {
                    PinSetupScreen(
                        isChange = true,
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.APPS) {
                    AppsScreen(
                        onAppClick = { packageName -> navController.navigate(Routes.rule(packageName)) },
                        onOpenStats = { navController.navigate(Routes.STATS) }
                    )
                }
                composable(
                    route = Routes.RULE,
                    arguments = listOf(navArgument(Routes.ARG_PACKAGE_NAME) { type = NavType.StringType })
                ) {
                    RuleEditScreen(onClose = { navController.popBackStack() })
                }
                composable(Routes.FOCUS) { FocusScreen() }
                composable(Routes.STATS) { StatsScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.TODO) {
                    TodoScreen(
                        onOpenTasks = { navController.navigate(Routes.TASKS) },
                        onOpenShopping = { navController.navigate(Routes.SHOPPING) }
                    )
                }
                composable(Routes.SHOPPING) { ShoppingScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.TASKS) {
                    TasksScreen(
                        onBack = { navController.popBackStack() },
                        onOpenTask = { id -> navController.navigate(Routes.task(id)) }
                    )
                }
                composable(
                    route = Routes.TASK,
                    arguments = listOf(navArgument(Routes.ARG_TASK_ID) { type = NavType.LongType })
                ) {
                    TaskEditScreen(
                        onClose = { navController.popBackStack() },
                        onFocus = { id -> navController.navigate(Routes.focusTask(id)) }
                    )
                }
                composable(
                    route = Routes.FOCUS_TASK,
                    arguments = listOf(navArgument(Routes.ARG_TASK_ID) { type = NavType.LongType })
                ) {
                    FocusScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        onOpenBank = { navController.navigate(Routes.BANK) },
                        onOpenStats = { navController.navigate(Routes.STATS) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                        onOpenMore = { navController.navigate(Routes.SETTINGS_MORE) },
                        onChangePin = { navController.navigate(Routes.PIN_CHANGE) }
                    )
                }
                composable(Routes.SETTINGS_MORE) {
                    MoreSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                        onReplayIntro = { navController.navigate(Routes.INTRO) },
                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) }
                    )
                }
                composable(Routes.PRIVACY) { PrivacyScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.BANK) { BankScreen(onBack = { navController.popBackStack() }) }
            }
            }
        }
        if (currentRoute == Routes.APPS) AppTourOnce()
        if (showBottomBar) {
            FloatingNavBar(
                items = Tab.entries.map { BarItem(it.route, it.label, it.icon) },
                selectedRoute = currentRoute,
                onSelect = { navController.openTab(it) },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
    }
}

/**
 * Переход на вкладку так, чтобы вкладки не копились в истории "Назад",
 * а состояние каждой вкладки (например, прокрутка списка) сохранялось.
 */
private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(Routes.APPS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
