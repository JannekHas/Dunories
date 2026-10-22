package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.WeeklyReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReportDao {
    @Query("SELECT * FROM weekly_report ORDER BY toEpochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<WeeklyReportEntity>>

    @Query("SELECT * FROM weekly_report WHERE id = :id")
    fun observe(id: Long): Flow<WeeklyReportEntity?>

    @Query("SELECT COUNT(*) FROM weekly_report WHERE fromEpochDay = :fromEpochDay AND toEpochDay = :toEpochDay")
    suspend fun countFor(fromEpochDay: Long, toEpochDay: Long): Int

    @Upsert
    suspend fun upsert(report: WeeklyReportEntity): Long

    @Query("DELETE FROM weekly_report WHERE id = :id")
    suspend fun delete(id: Long)
}
