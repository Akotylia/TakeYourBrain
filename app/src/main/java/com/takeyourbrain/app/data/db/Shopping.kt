package com.takeyourbrain.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Список покупок ("Продукты", "Хозтовары"). */
@Entity(tableName = "shopping_lists")
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** Пункт списка. Некупленные идут по [position], купленные — по [checkedAt], свежие сверху. */
@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("listId")],
)
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val text: String,
    val quantity: String = "", // "2 л", "3 шт" — как написал человек
    val checked: Boolean = false,
    val position: Int,
    val checkedAt: Long? = null,
)

/** Что когда-то добавляли в списки — для подсказок при вводе. Хранится, даже когда пункт удалён. */
@Entity(tableName = "shopping_history")
data class ShoppingHistory(
    @PrimaryKey val normalized: String, // текст маленькими буквами, без лишних пробелов
    val text: String,                   // как написали в последний раз
    val useCount: Int,
    val lastUsedAt: Long,
)

@Dao
interface ShoppingDao {
    // Списки
    @Query("SELECT * FROM shopping_lists ORDER BY createdAt")
    fun observeLists(): Flow<List<ShoppingList>>

    @Query("SELECT COUNT(*) FROM shopping_lists")
    suspend fun listCount(): Int

    @Insert
    suspend fun insertList(list: ShoppingList): Long

    @Query("UPDATE shopping_lists SET name = :name WHERE id = :id")
    suspend fun renameList(id: Long, name: String)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    // Пункты
    @Query("SELECT * FROM shopping_items WHERE listId = :listId")
    fun observeItems(listId: Long): Flow<List<ShoppingItem>>

    /** Сколько всего нужно купить (во всех списках) — для плитки на вкладке "Дела". */
    @Query("SELECT COUNT(*) FROM shopping_items WHERE checked = 0")
    fun observeToBuyCount(): Flow<Int>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId")
    suspend fun itemsOf(listId: Long): List<ShoppingItem>

    @Query("SELECT COALESCE(MAX(position), -1) FROM shopping_items WHERE listId = :listId")
    suspend fun maxPosition(listId: Long): Int

    @Insert
    suspend fun insertItem(item: ShoppingItem): Long

    @Insert
    suspend fun insertItems(items: List<ShoppingItem>)

    @Update
    suspend fun updateItem(item: ShoppingItem)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM shopping_items WHERE listId = :listId")
    suspend fun deleteItemsOf(listId: Long)

    // История для подсказок
    @Query("SELECT * FROM shopping_history WHERE normalized = :normalized")
    suspend fun historyEntry(normalized: String): ShoppingHistory?

    @Upsert
    suspend fun upsertHistory(entry: ShoppingHistory)

    @Query("SELECT * FROM shopping_history ORDER BY useCount DESC, lastUsedAt DESC LIMIT 300")
    fun observeHistory(): Flow<List<ShoppingHistory>>
}
