package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.kalorienapp.data.db.entity.MealComboEntity
import de.kalorienapp.data.db.entity.MealComboItemEntity
import de.kalorienapp.data.db.entity.MealComboWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface MealComboDao {
    @Transaction
    @Query("SELECT * FROM meal_combo ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MealComboWithItems>>

    @Insert
    suspend fun insertCombo(combo: MealComboEntity): Long

    @Insert
    suspend fun insertItems(items: List<MealComboItemEntity>)

    @Query("DELETE FROM meal_combo WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    suspend fun save(combo: MealComboEntity, items: (Long) -> List<MealComboItemEntity>) {
        val id = insertCombo(combo)
        insertItems(items(id))
    }
}
