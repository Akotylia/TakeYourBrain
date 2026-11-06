package com.takeyourbrain.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.BankRepository
import com.takeyourbrain.app.data.StreakInfo
import com.takeyourbrain.app.data.StreakRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val streak: StreakInfo? = null, // null — ещё считается
    val bankTotal: Int = 0,         // минут во всех счетах банка
)

class ProfileViewModel(
    private val streakRepository: StreakRepository,
    bankRepository: BankRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            bankRepository.balances.collect { balances ->
                _state.update { it.copy(bankTotal = balances.values.sum().coerceAtLeast(0)) }
            }
        }
    }

    /** Пересчитать серию (при каждом открытии: за это время мог наступить новый день). */
    fun refresh() {
        viewModelScope.launch {
            val info = streakRepository.info()
            _state.update { it.copy(streak = info) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as TakeYourBrainApp).container
                ProfileViewModel(c.streakRepository, c.bankRepository)
            }
        }
    }
}
