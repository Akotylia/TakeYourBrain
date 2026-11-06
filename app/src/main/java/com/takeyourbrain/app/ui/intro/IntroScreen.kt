package com.takeyourbrain.app.ui.intro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.R
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.notifications.AppSound
import com.takeyourbrain.app.ui.common.AnimatedLogo
import com.takeyourbrain.app.ui.common.LOGO_ANIMATION_MS
import com.takeyourbrain.app.ui.common.LogoBackdrop
import com.takeyourbrain.app.ui.common.rememberLogoTime
import com.takeyourbrain.app.ui.theme.LogoAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Запоминает, что заставку и знакомство с приложением уже показали. */
class IntroViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val tourSeen = settingsRepository.tourSeen

    fun markSeen() {
        viewModelScope.launch { settingsRepository.setIntroSeen() }
    }

    fun markTourSeen() {
        viewModelScope.launch { settingsRepository.setTourSeen() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TakeYourBrainApp
                IntroViewModel(app.container.settingsRepository)
            }
        }
    }
}

/**
 * Заставка при самом первом запуске (и по кнопке в Настройки → Дополнительно): логотип рисуется со звуком,
 * затем название. "Пропустить" — сразу дальше. Потом [onDone].
 */
@Composable
fun IntroScreen(onDone: () -> Unit, viewModel: IntroViewModel = viewModel(factory = IntroViewModel.Factory)) {
    val context = LocalContext.current
    val time = rememberLogoTime()
    var finished by remember { mutableStateOf(false) }
    val finish = {
        // Кнопка и таймер могут совпасть — переходим только один раз
        if (!finished) {
            finished = true
            viewModel.markSeen()
            onDone()
        }
    }
    DisposableEffect(Unit) {
        AppSound.play(context, R.raw.tyb_logo_sound)
        onDispose { AppSound.stop() }
    }
    LaunchedEffect(Unit) {
        delay(LOGO_ANIMATION_MS + HOLD_MS)
        finish()
    }

    LogoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedLogo(timeMs = { time.value }, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            val name = stringResource(R.string.app_name)
            val lastWord = name.lastIndexOf(' ') + 1
            Text(
                // Последнее слово названия — зелёное, как стрелка
                text = buildAnnotatedString {
                    append(name.substring(0, lastWord))
                    withStyle(SpanStyle(color = LogoAccent)) { append(name.substring(lastWord)) }
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    val appear = ((time.value - WORD_START_MS) / WORD_DURATION_MS).coerceIn(0f, 1f)
                    alpha = appear
                    translationY = (1 - appear) * 8.dp.toPx()
                }
            )
        }
        TextButton(
            onClick = finish,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.75f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 16.dp)
        ) { Text(stringResource(R.string.intro_skip)) }
    }
}

private const val HOLD_MS = 700L // логотип ещё немного стоит, чтобы успеть рассмотреть
private const val WORD_START_MS = 1800f
private const val WORD_DURATION_MS = 600f
