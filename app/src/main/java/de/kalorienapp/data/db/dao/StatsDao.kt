package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Tagessummen der gegessenen Nährwerte (Zusatzwerte null, wenn an dem Tag nirgends angegeben). */
data class DailyNutritionRow(
    val dateEpochDay: Long,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val fiberG: Double?,
    val saltG: Double?,
    val entryCount: Int,
)

data class DailyActivityRow(val dateEpochDay: Long, val kcal: Int)

/** Abfragen für Kalender und Statistiken – nur lesend, über Zeiträume gruppiert nach Tag. */
@Dao
interface StatsDao {
    @Query(
        """
        SELECT dateEpochDay,
               SUM(kcal) AS kcal, SUM(proteinG) AS proteinG, SUM(carbsG) AS carbsG, SUM(fatG) AS fatG,
               SUM(saturatedFatG) AS saturatedFatG, SUM(sugarG) AS sugarG,
               SUM(fiberG) AS fiberG, SUM(saltG) AS saltG,
               COUNT(*) AS entryCount
        FROM food_entry
        WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        GROUP BY dateEpochDay
        """,
    )
    fun observeDailyNutrition(fromEpochDay: Long, toEpochDay: Long): Flow<List<DailyNutritionRow>>

    @Query(
        """
        SELECT dateEpochDay, SUM(kcal) AS kcal
        FROM activity_entry
        WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        GROUP BY dateEpochDay
        """,
    )
    fun observeDailyActivity(fromEpochDay: Long, toEpochDay: Long): Flow<List<DailyActivityRow>>
}
