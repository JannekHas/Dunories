package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.kalorienapp.data.db.entity.FavoriteFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteFoodDao {
    @Query("SELECT * FROM favorite_food ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FavoriteFoodEntity>>

    @Query("SELECT COUNT(*) FROM favorite_food WHERE name = :name COLLATE NOCASE")
    fun observeCountByName(name: String): Flow<Int>

    @Insert
    suspend fun insert(favorite: FavoriteFoodEntity)

    @Query("DELETE FROM favorite_food WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM favorite_food WHERE name = :name COLLATE NOCASE")
    suspend fun deleteByName(name: String)
}
