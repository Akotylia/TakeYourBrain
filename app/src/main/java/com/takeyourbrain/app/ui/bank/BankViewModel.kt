package com.takeyourbrain.app.ui.bank

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.AppsRepository
import com.takeyourbrain.app.data.BankRepository
import com.takeyourbrain.app.data.RulesRepository
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.data.db.BankTx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/** Счёт одного приложения. */
data class BankAccount(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val balance: Int,
)

/** Операция в истории с названием приложения. */
data class BankHistoryItem(val tx: BankTx, val label: String)

data class BankUiState(
    val isLoading: Boolean = true,
    val accounts: List<BankAccount> = emptyList(),
    val history: List<BankHistoryItem> = emptyList(),
    val cap: Int = SettingsRepository.DEFAULT_BANK_CAP,
    val spendDays: Set<DayOfWeek> = emptySet(),
    val showIntro: Boolean = false, // "Как это работает" открытым — только при первом открытии банка
) {
    /** Всё накопленное время, минут (для наливающегося мозга). */
    val total: Int get() = accounts.sumOf { it.balance.coerceAtLeast(0) }
}

class BankViewModel(
    private val bankRepository: BankRepository,
    private val rulesRepository: RulesRepository,
    private val appsRepository: AppsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BankUiState())
    val state: StateFlow<BankUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Сначала начисляем всё, что накопилось за прошедшие дни
            bankRepository.settle()
            combine(bankRepository.balances, bankRepository.recentTx, rulesRepository.rules) { balances, history, rules ->
                // Счета: приложения с лимитом и те, у кого остались минуты
                val withLimit = rules.values
                    .filter { it.rule.weekdayLimitMinutes != null || it.rule.weekendLimitMinutes != null }
                    .map { it.rule.packageName }
                val packages = (withLimit + balances.filterValues { it != 0 }.keys).distinct()
                val accounts = packages.map { pkg ->
                    val app = appsRepository.getApp(pkg)
                    BankAccount(pkg, app?.label ?: pkg, app?.icon, balances[pkg] ?: 0)
                }.sortedWith(compareByDescending<BankAccount> { it.balance }.thenBy { it.label.lowercase() })
                val labels = accounts.associate { it.packageName to it.label }
                val items = history.map { tx ->
                    BankHistoryItem(tx, labels[tx.packageName] ?: appsRepository.getApp(tx.packageName)?.label ?: tx.packageName)
                }
                accounts to items
            }.collect { (accounts, items) ->
                _state.update { it.copy(isLoading = false, accounts = accounts, history = items) }
            }
        }
        viewModelScope.launch {
            // Первый раз показываем объяснение и сразу запоминаем: в следующий раз оно будет за "ⓘ"
            if (!settingsRepository.bankIntroSeen.first()) {
                _state.update { it.copy(showIntro = true) }
                settingsRepository.setBankIntroSeen()
            }
        }
        viewModelScope.launch {
            settingsRepository.bankCap.collect { cap -> _state.update { it.copy(cap = cap) } }
        }
        viewModelScope.launch {
            settingsRepository.bankSpendDays.collect { days -> _state.update { it.copy(spendDays = days) } }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as TakeYourBrainApp).container
                BankViewModel(c.bankRepository, c.rulesRepository, c.appsRepository, c.settingsRepository)
            }
        }
    }
}
