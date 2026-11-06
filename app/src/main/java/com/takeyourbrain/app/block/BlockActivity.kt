package com.takeyourbrain.app.block

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeyourbrain.app.MainActivity
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.ui.theme.TakeYourBrainTheme
import com.takeyourbrain.app.ui.theme.ThemeMode

/**
 * Экран блокировки. Его открывает служба специальных возможностей,
 * когда пользователь заходит в заблокированное приложение.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TakeYourBrainApp).container
        // Нечего показывать (например, приложение перезапустилось) — просто закрываемся
        if (container.currentBlock.value == null) {
            finish()
            return
        }

        // Кнопка "Назад" ведёт на домашний экран, а не обратно в заблокированное приложение
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            val themeMode by container.settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dark = themeMode.isDark()
            // Значки строки состояния и навигации — под выбранную тему, а не под тему телефона
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            TakeYourBrainTheme(darkTheme = dark) {
                val block by container.currentBlock.collectAsStateWithLifecycle()
                block?.let { info ->
                    BlockScreen(
                        info = info,
                        container = container,
                        onGoHome = ::goHome,
                        onOpenApp = { openApp(info.packageName) },
                        onOpenTasks = ::openTasks
                    )
                }
            }
        }
    }

    /** Пропуск выдан — открываем приложение и закрываем экран блокировки. */
    private fun openApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    /** "Открыть задачи" — вместо заблокированного приложения в ежедневник. */
    private fun openTasks() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TASKS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
