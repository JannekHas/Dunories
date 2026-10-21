package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.DailyTrainingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTrainingDao {
    @Query("SELECT * FROM daily_training WHERE dateEpochDay = :epochDay")
    fun observe(epochDay: Long): Flow<DailyTrainingEntity?>

    @Upsert
    suspend fun upsert(training: DailyTrainingEntity)
}
