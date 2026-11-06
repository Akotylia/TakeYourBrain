package com.takeyourbrain.app.ui.shopping

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.takeyourbrain.app.TakeYourBrainApp
import com.takeyourbrain.app.data.SettingsRepository
import com.takeyourbrain.app.data.ShoppingRepository
import com.takeyourbrain.app.data.db.ShoppingItem
import com.takeyourbrain.app.data.db.ShoppingList
import com.takeyourbrain.app.domain.ShoppingText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val lists: List<ShoppingList> = emptyList(),
    val selected: ShoppingList? = null,
    val toBuy: List<ShoppingItem> = emptyList(),  // по порядку добавления
    val bought: List<ShoppingItem> = emptyList(), // свежекупленные сверху
    val history: List<String> = emptyList(),      // что вводили раньше, частое сверху
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModel(
    private val shoppingRepository: ShoppingRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Текст в поле "Добавить покупку". */
    var input by mutableStateOf("")
        private set

    val state: StateFlow<ShoppingUiState> =
        combine(shoppingRepository.lists, settingsRepository.shoppingListId) { lists, savedId ->
            // Открываем последний список; если его удалили — первый
            lists to (lists.firstOrNull { it.id == savedId } ?: lists.firstOrNull())
        }.flatMapLatest { (lists, selected) ->
            if (selected == null) {
                flowOf(ShoppingUiState(isLoading = false, lists = lists))
            } else {
                combine(shoppingRepository.items(selected.id), shoppingRepository.history) { items, history ->
                    ShoppingUiState(
                        isLoading = false,
                        lists = lists,
                        selected = selected,
                        toBuy = items.filterNot { it.checked }.sortedBy { it.position },
                        bought = items.filter { it.checked }.sortedByDescending { it.checkedAt ?: 0L },
                        history = history.map { it.text },
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    /** Подсказки под полем ввода: из прошлых покупок, без того, что уже нужно купить. */
    fun suggestions(): List<String> {
        val s = state.value
        val inList = s.toBuy.map { ShoppingText.normalize(it.text) }.toSet()
        return ShoppingText.suggest(s.history, input, inList, limit = 8)
    }

    fun onInputChange(text: String) {
        input = text
    }

    /** Добавить из поля (или нажатую подсказку). Клавиатура остаётся открытой — можно вводить дальше. */
    fun add(text: String = input) {
        val listId = state.value.selected?.id ?: return
        if (text.isBlank()) return
        input = ""
        viewModelScope.launch { shoppingRepository.addItem(listId, text) }
    }

    fun ensureList(defaultName: String) {
        viewModelScope.launch { shoppingRepository.ensureList(defaultName) }
    }

    fun selectList(id: Long) {
        viewModelScope.launch { settingsRepository.setShoppingListId(id) }
    }

    fun createList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = shoppingRepository.createList(name)
            settingsRepository.setShoppingListId(id)
        }
    }

    fun renameSelected(name: String) {
        val id = state.value.selected?.id ?: return
        if (name.isBlank()) return
        viewModelScope.launch { shoppingRepository.renameList(id, name) }
    }

    fun deleteSelected() {
        val id = state.value.selected?.id ?: return
        viewModelScope.launch { shoppingRepository.deleteList(id) }
    }

    fun toggle(item: ShoppingItem) {
        viewModelScope.launch { shoppingRepository.toggle(item) }
    }

    fun update(item: ShoppingItem, text: String, quantity: String) {
        viewModelScope.launch { shoppingRepository.update(item, text, quantity) }
    }

    fun delete(item: ShoppingItem) {
        viewModelScope.launch { shoppingRepository.delete(item) }
    }

    /** Убрать купленные; [onCleared] получит убранное (для "Отменить"). */
    fun clearBought(onCleared: (List<ShoppingItem>) -> Unit) {
        val listId = state.value.selected?.id ?: return
        viewModelScope.launch { onCleared(shoppingRepository.clearChecked(listId)) }
    }

    fun restore(items: List<ShoppingItem>) {
        viewModelScope.launch { shoppingRepository.restore(items) }
    }

    /** Текст списка для "Поделиться" (только то, что нужно купить). */
    fun shareText(): String? {
        val s = state.value
        val list = s.selected ?: return null
        return ShoppingText.share(list.name, s.toBuy.map { it.text to it.quantity })
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TakeYourBrainApp).container
                ShoppingViewModel(container.shoppingRepository, container.settingsRepository)
            }
        }
    }
}
