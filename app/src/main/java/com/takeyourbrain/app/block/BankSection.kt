package com.takeyourbrain.app.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.BankRepository
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.domain.BankPricing
import com.takeyourbrain.app.ui.common.daysText
import com.takeyourbrain.app.ui.common.formatMinutes
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * "Взять из банка": в дни трат можно открыть приложение на 10/15/30 минут за счёт сэкономленного времени.
 * Без PIN — это честно накопленные минуты. [multiplier] — цена минуты (вне разрешённого окна дороже).
 */
@Composable
fun BankSection(
    packageName: String,
    multiplier: Int,
    bankRepository: BankRepository,
    settingsRepository: SettingsRepository,
    onOpenApp: () -> Unit,
) {
    // Баланс (перед этим начисляется всё, что накопилось за прошедшие дни); null — ещё читаем
    val balance by produceState<Int?>(null, packageName) { value = bankRepository.balance(packageName) }
    val spendDays by settingsRepository.bankSpendDays.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val minutes = balance ?: return
    if (minutes <= 0) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.bank_balance, formatMinutes(minutes)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (LocalDate.now().dayOfWeek !in spendDays) {
            // Сегодня не день трат — просто сообщаем, когда можно
            HintText(stringResource(R.string.bank_spend_only_on, daysText(spendDays)))
            return@Column
        }
        if (multiplier > 1) HintText(pluralStringResource(R.plurals.bank_price, multiplier, multiplier))
        val options = BankPricing.SPEND_OPTIONS.filter { it * multiplier <= minutes }
        if (options.isEmpty()) {
            HintText(stringResource(R.string.bank_not_enough))
            return@Column
        }
        options.forEach { option ->
            FilledTonalButton(
                onClick = {
                    busy = true
                    scope.launch {
                        if (bankRepository.spend(packageName, option, multiplier)) onOpenApp() else busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                val label = if (multiplier > 1) {
                    stringResource(R.string.bank_take_priced, option, option * multiplier)
                } else {
                    stringResource(R.string.bank_take, option)
                }
                Text(label)
            }
        }
        HintText(stringResource(R.string.bank_refund_hint))
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}
