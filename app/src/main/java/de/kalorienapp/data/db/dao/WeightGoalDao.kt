package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.WeightGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightGoalDao {
    @Query("SELECT * FROM weight_goal WHERE endedDate IS NULL ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<WeightGoalEntity?>

    @Query("SELECT * FROM weight_goal WHERE endedDate IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun active(): WeightGoalEntity?

    /** Nur erreichte Ziele – abgelaufene oder ersetzte werden nicht als Erfolg gezeigt. */
    @Query("SELECT * FROM weight_goal WHERE endReason = 'REACHED' ORDER BY endedDate DESC, id DESC")
    fun observeReached(): Flow<List<WeightGoalEntity>>

    @Query("DELETE FROM weight_goal WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM weight_goal WHERE id = :id")
    suspend fun get(id: Long): WeightGoalEntity?

    @Upsert
    suspend fun upsert(goal: WeightGoalEntity): Long
}
