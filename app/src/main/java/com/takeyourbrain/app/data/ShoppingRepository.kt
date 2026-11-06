package com.takeyourbrain.app.data

import com.takeyourbrain.app.data.db.ShoppingDao
import com.takeyourbrain.app.data.db.ShoppingHistory
import com.takeyourbrain.app.data.db.ShoppingItem
import com.takeyourbrain.app.data.db.ShoppingList
import com.takeyourbrain.app.domain.ShoppingText
import kotlinx.coroutines.flow.Flow

/** Списки покупок, их пункты и история введённого (для подсказок). */
class ShoppingRepository(private val dao: ShoppingDao) {

    val lists: Flow<List<ShoppingList>> = dao.observeLists()

    /** Сколько всего нужно купить во всех списках. */
    val toBuyCount: Flow<Int> = dao.observeToBuyCount()

    /** Что вводили раньше, частое сверху. */
    val history: Flow<List<ShoppingHistory>> = dao.observeHistory()

    fun items(listId: Long): Flow<List<ShoppingItem>> = dao.observeItems(listId)

    /** Если списков ещё нет — создать первый с названием [name]. */
    suspend fun ensureList(name: String) {
        if (dao.listCount() == 0) createList(name)
    }

    suspend fun createList(name: String): Long =
        dao.insertList(ShoppingList(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun renameList(id: Long, name: String) = dao.renameList(id, name.trim())

    suspend fun deleteList(id: Long) {
        dao.deleteItemsOf(id)
        dao.deleteList(id)
    }

    /**
     * Добавить пункт. Такой уже есть и не куплен — ничего не делаем; есть, но куплен —
     * снова "нужно купить" (в конец списка). Запоминаем в истории для подсказок.
     */
    suspend fun addItem(listId: Long, text: String) {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return
        val normalized = ShoppingText.normalize(clean)
        val same = dao.itemsOf(listId).firstOrNull { ShoppingText.normalize(it.text) == normalized }
        when {
            same == null -> dao.insertItem(ShoppingItem(listId = listId, text = clean, position = dao.maxPosition(listId) + 1))
            same.checked -> dao.updateItem(same.copy(checked = false, checkedAt = null, position = dao.maxPosition(listId) + 1))
        }
        val now = System.currentTimeMillis()
        val old = dao.historyEntry(normalized)
        dao.upsertHistory(ShoppingHistory(normalized, clean, (old?.useCount ?: 0) + 1, now))
    }

    /** Отметить купленным / вернуть в "нужно купить". */
    suspend fun toggle(item: ShoppingItem) {
        dao.updateItem(
            if (item.checked) item.copy(checked = false, checkedAt = null)
            else item.copy(checked = true, checkedAt = System.currentTimeMillis())
        )
    }

    suspend fun update(item: ShoppingItem, text: String, quantity: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        dao.updateItem(item.copy(text = clean, quantity = quantity.trim()))
    }

    suspend fun delete(item: ShoppingItem) = dao.deleteItem(item.id)

    /** Убрать купленные. Возвращает убранное — чтобы можно было "Отменить". */
    suspend fun clearChecked(listId: Long): List<ShoppingItem> {
        val checked = dao.itemsOf(listId).filter { it.checked }
        checked.forEach { dao.deleteItem(it.id) }
        return checked
    }

    /**
     * Вернуть удалённые пункты (кнопка "Отменить"). Возвращаем с новым id: иначе список вспомнит
     * у пункта состояние "смахнут" и сразу удалит его снова.
     */
    suspend fun restore(items: List<ShoppingItem>) = dao.insertItems(items.map { it.copy(id = 0) })
}
