package com.takeyourbrain.app.ui.bank

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.db.BankTxReason
import com.takeyourbrain.app.domain.BankPricing
import com.takeyourbrain.app.ui.common.AppAlertDialog
import com.takeyourbrain.app.ui.common.AppCard
import com.takeyourbrain.app.ui.common.ArrowLoader
import com.takeyourbrain.app.ui.common.BrainTank
import com.takeyourbrain.app.ui.common.CardGroupItem
import com.takeyourbrain.app.ui.common.clearTopBarColors
import com.takeyourbrain.app.ui.common.daysText
import com.takeyourbrain.app.ui.common.formatDuration
import com.takeyourbrain.app.ui.common.formatMinutes
import com.takeyourbrain.app.ui.common.formatTimeOfDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.abs

/**
 * Банк времени. Сверху — мозг из логотипа, который наливается по мере накопления (полный — потолок).
 * "Как это работает" видно только при первом открытии, потом — за кнопкой "ⓘ". История — по раскрытию.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(onBack: () -> Unit, viewModel: BankViewModel = viewModel(factory = BankViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showHow by remember { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    val howText = stringResource(
        R.string.bank_how_text,
        formatDuration(state.cap * 60_000L),
        daysText(state.spendDays),
        BankPricing.OUTSIDE_ALLOWED
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = { Text(stringResource(R.string.bank_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    // "ⓘ" — объяснение ещё раз, когда оно уже не на экране
                    if (!state.showIntro) {
                        IconButton(onClick = { showHow = true }) {
                            Icon(painterResource(R.drawable.ic_info), stringResource(R.string.bank_how_title))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ArrowLoader() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (state.showIntro) {
                item {
                    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.bank_how_title), style = MaterialTheme.typography.titleMedium)
                        Text(howText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                // Наливающийся мозг: всё накопленное относительно потолка
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BrainTank(
                            fill = if (state.cap > 0) state.total.toFloat() / state.cap else 0f,
                            modifier = Modifier.size(180.dp)
                        )
                        Text(formatMinutes(state.total), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = stringResource(R.string.bank_total_of_cap, formatDuration(state.cap * 60_000L)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.bank_accounts)) }
            if (state.accounts.isEmpty()) {
                item { EmptyText(stringResource(R.string.bank_no_accounts)) }
            }
            itemsIndexed(state.accounts, key = { _, account -> account.packageName }) { index, account ->
                CardGroupItem(first = index == 0, last = index == state.accounts.lastIndex) {
                    ListItem(
                        headlineContent = { Text(account.label) },
                        leadingContent = {
                            account.icon?.let {
                                Image(remember(it) { it.asImageBitmap() }, contentDescription = null, modifier = Modifier.size(40.dp))
                            }
                        },
                        trailingContent = {
                            Text(
                                text = formatMinutes(account.balance),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // История — только по нажатию, чтобы не мозолила глаза
            item {
                TextButton(
                    onClick = { historyOpen = !historyOpen },
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                ) {
                    Text(stringResource(R.string.bank_history))
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.rotate(if (historyOpen) 270f else 90f)
                    )
                }
            }
            if (historyOpen) {
                if (state.history.isEmpty()) {
                    item { EmptyText(stringResource(R.string.bank_no_history)) }
                }
                itemsIndexed(state.history, key = { _, item -> item.tx.id }) { index, item ->
                    CardGroupItem(first = index == 0, last = index == state.history.lastIndex) { HistoryRow(item) }
                }
            }
        }
    }

    if (showHow) {
        AppAlertDialog(
            onDismissRequest = { showHow = false },
            title = { Text(stringResource(R.string.bank_how_title)) },
            text = { Text(howText) },
            confirmButton = { TextButton(onClick = { showHow = false }) { Text(stringResource(R.string.dialog_ok)) } }
        )
    }
}

/** Строка истории: "YouTube · сэкономлено за пн" и "+25 мин". */
@Composable
private fun HistoryRow(item: BankHistoryItem) {
    val tx = item.tx
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(tx.time), ZoneId.systemDefault())
    val dayName = tx.day?.let { LocalDate.parse(it).dayOfWeek.getDisplayName(TextStyle.SHORT, locale) } ?: ""
    val what = when (tx.reason) {
        BankTxReason.EARN_DAILY -> stringResource(R.string.bank_tx_earn, dayName)
        BankTxReason.SPEND -> stringResource(R.string.bank_tx_spend)
        BankTxReason.REFUND -> stringResource(R.string.bank_tx_refund)
        BankTxReason.EXPIRE -> stringResource(R.string.bank_tx_expire)
    }
    val details = when (tx.reason) {
        BankTxReason.EARN_DAILY, BankTxReason.EXPIRE -> what
        else -> what + " · " + time.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + ", " + formatTimeOfDay(context, time)
    }
    val amount = formatMinutes(abs(tx.amount))
    ListItem(
        headlineContent = { Text(item.label) },
        supportingContent = { Text(details) },
        trailingContent = {
            Text(
                text = if (tx.amount >= 0) "+$amount" else "−$amount",
                color = if (tx.amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
