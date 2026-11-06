package com.takeyourbrain.app

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeyourbrain.app.ui.common.LocalGlass
import com.takeyourbrain.app.ui.navigation.AppNavigation
import com.takeyourbrain.app.ui.navigation.LocalHapticsEnabled
import com.takeyourbrain.app.ui.navigation.Routes
import com.takeyourbrain.app.ui.theme.TakeYourBrainTheme
import com.takeyourbrain.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) holdSplash()
        val container = (application as TakeYourBrainApp).container
        val settingsRepository = container.settingsRepository

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dark = themeMode.isDark()
            // Значки строки состояния и навигации — под выбранную тему, а не под тему телефона
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            val haptics by settingsRepository.hapticsEnabled.collectAsStateWithLifecycle(initialValue = true)
            val glass by settingsRepository.glassEnabled.collectAsStateWithLifecycle(initialValue = false)
            TakeYourBrainTheme(darkTheme = dark, glass = glass) {
            CompositionLocalProvider(LocalHapticsEnabled provides haptics, LocalGlass provides glass) {
                // Выбираем первый экран: помощник по разрешениям → создание PIN → список приложений.
                // Пока читаем настройки (доли секунды), показывается фон окна.
                val startRoute by produceState<String?>(initialValue = null) {
                    value = when {
                        !settingsRepository.onboardingDone.first() ->
                            if (settingsRepository.introSeen.first()) Routes.PERMISSIONS else Routes.INTRO
                        !container.pinRepository.isPinSet.first() -> Routes.PIN_SETUP
                        else -> Routes.APPS
                    }
                }
                val pinSet by container.pinRepository.isPinSet.collectAsStateWithLifecycle(initialValue = true)
                // Открыли из уведомления-напоминания — сразу показываем задачи (только при первом создании экрана)
                val openTasks = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_TASKS, false)
                startRoute?.let { AppNavigation(startRoute = it, pinSet = pinSet, openTasks = openTasks) }
            }
            }
        }
    }

    /** Заставка Android исчезает с первым кадром: придерживаем его, пока не доиграет её анимация. */
    private fun holdSplash() {
        val startedAt = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (SystemClock.uptimeMillis() - startedAt < SPLASH_MS) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    companion object {
        /** Сколько идёт анимация заставки (как в res/drawable/avd_tyb_splash.xml). */
        private const val SPLASH_MS = 800L

        /** Флаг в Intent: открыть список задач (нажали на напоминание). */
        const val EXTRA_OPEN_TASKS = "open_tasks"
    }
}
