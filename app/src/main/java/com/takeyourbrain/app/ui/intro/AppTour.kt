package com.takeyourbrain.app.ui.intro

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.ui.common.CardShape
import com.takeyourbrain.app.ui.common.DialogBlurBehind
import kotlinx.coroutines.launch

/** Одна карточка знакомства: значок, заголовок, текст. */
private class TourPage(@param:DrawableRes val icon: Int, @param:StringRes val title: Int, @param:StringRes val text: Int)

private val TourPages = listOf(
    TourPage(R.drawable.ic_apps, R.string.tour_tabs_title, R.string.tour_tabs_text),
    TourPage(R.drawable.ic_lock, R.string.tour_rule_title, R.string.tour_rule_text),
    TourPage(R.drawable.ic_focus, R.string.tour_focus_title, R.string.tour_focus_text),
    TourPage(R.drawable.ic_bank, R.string.tour_bank_title, R.string.tour_bank_text),
    TourPage(R.drawable.ic_flame, R.string.tour_flame_title, R.string.tour_flame_text),
)

/** Показывает знакомство один раз: когда человек впервые попал на вкладку "Приложения". */
@Composable
fun AppTourOnce(viewModel: IntroViewModel = viewModel(factory = IntroViewModel.Factory)) {
    val seen by viewModel.tourSeen.collectAsStateWithLifecycle(initialValue = true)
    if (!seen) AppTour(onDone = viewModel::markTourSeen)
}

/** Знакомство с приложением: карточки можно листать, "Пропустить" — сразу закрыть. */
@Composable
fun AppTour(onDone: () -> Unit) {
    val pager = rememberPagerState { TourPages.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDone, properties = DialogProperties(dismissOnClickOutside = false)) {
        DialogBlurBehind()
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(340.dp)) { index ->
                    TourPageContent(TourPages[index])
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(TourPages.size) { index ->
                        val color by animateColorAsState(
                            if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            label = "dot"
                        )
                        Box(Modifier.size(8.dp).background(color, CircleShape))
                    }
                }
                Spacer(Modifier.height(16.dp))
                val last = pager.currentPage == TourPages.lastIndex
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // На последней карточке пропускать уже нечего
                    if (last) Spacer(Modifier) else TextButton(onClick = onDone) { Text(stringResource(R.string.intro_skip)) }
                    Button(
                        onClick = {
                            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }
                    ) { Text(stringResource(if (last) R.string.tour_done else R.string.tour_next)) }
                }
            }
        }
    }
}

@Composable
private fun TourPageContent(page: TourPage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(page.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(page.text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
