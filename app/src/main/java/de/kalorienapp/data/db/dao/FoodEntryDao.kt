package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entry WHERE dateEpochDay = :epochDay ORDER BY createdAt")
    fun observeForDay(epochDay: Long): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entry WHERE id = :id")
    suspend fun getById(id: Long): FoodEntryEntity?

    @Upsert
    suspend fun upsert(entry: FoodEntryEntity)

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun deleteById(id: Long)
}
