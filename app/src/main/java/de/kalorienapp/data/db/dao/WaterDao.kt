package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.kalorienapp.data.db.entity.WaterEntryEntity
import kotlinx.coroutines.flow.Flow

data class DailyWaterRow(val dateEpochDay: Long, val ml: Int)

@Dao
interface WaterDao {
    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_entry WHERE dateEpochDay = :epochDay")
    fun observeTotal(epochDay: Long): Flow<Int>

    @Query("SELECT dateEpochDay, SUM(ml) AS ml FROM water_entry WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay GROUP BY dateEpochDay")
    suspend fun dailyTotals(fromEpochDay: Long, toEpochDay: Long): List<DailyWaterRow>

    @Insert
    suspend fun insert(entry: WaterEntryEntity)

    /** Letzten Eintrag des Tages entfernen (Rückgängig). */
    @Query("DELETE FROM water_entry WHERE id = (SELECT id FROM water_entry WHERE dateEpochDay = :epochDay ORDER BY createdAt DESC, id DESC LIMIT 1)")
    suspend fun deleteLast(epochDay: Long)
}
