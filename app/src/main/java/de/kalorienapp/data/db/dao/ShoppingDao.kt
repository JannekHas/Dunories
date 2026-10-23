package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_item ORDER BY checked, createdAt, id")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_item")
    suspend fun all(): List<ShoppingItemEntity>

    @Upsert
    suspend fun upsert(items: List<ShoppingItemEntity>)

    @Query("UPDATE shopping_item SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM shopping_item WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM shopping_item WHERE checked = 1")
    suspend fun deleteChecked()

    @Query("DELETE FROM shopping_item")
    suspend fun deleteAll()

    @Transaction
    suspend fun apply(updated: List<ShoppingItemEntity>, inserted: List<ShoppingItemEntity>) {
        upsert(updated + inserted)
    }
}
