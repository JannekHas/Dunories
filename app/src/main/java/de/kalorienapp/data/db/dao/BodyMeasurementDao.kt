package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.kalorienapp.data.db.entity.BodyMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {
    @Query("SELECT * FROM body_measurement ORDER BY measuredAt")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    /** Neueste Messung eines Typs (z. B. Körperfett für das Protein-Bezugsgewicht). */
    @Query("SELECT * FROM body_measurement WHERE type = :type ORDER BY measuredAt DESC LIMIT 1")
    fun observeLatest(type: String): Flow<BodyMeasurementEntity?>

    @Insert
    suspend fun insert(items: List<BodyMeasurementEntity>)

    @Query("DELETE FROM body_measurement WHERE id = :id")
    suspend fun delete(id: Long)
}
