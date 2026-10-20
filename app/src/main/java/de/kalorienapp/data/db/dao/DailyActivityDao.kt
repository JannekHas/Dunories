package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.DailyActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyActivityDao {
    @Query("SELECT * FROM daily_activity WHERE dateEpochDay = :epochDay")
    fun observe(epochDay: Long): Flow<DailyActivityEntity?>

    @Upsert
    suspend fun upsert(activity: DailyActivityEntity)
}
