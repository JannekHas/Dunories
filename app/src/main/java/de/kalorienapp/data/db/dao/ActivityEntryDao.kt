package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.ActivityEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEntryDao {
    @Query("SELECT * FROM activity_entry WHERE dateEpochDay = :epochDay ORDER BY createdAt")
    fun observeForDay(epochDay: Long): Flow<List<ActivityEntryEntity>>

    @Upsert
    suspend fun upsert(entry: ActivityEntryEntity)

    @Query("DELETE FROM activity_entry WHERE id = :id")
    suspend fun deleteById(id: Long)
}
