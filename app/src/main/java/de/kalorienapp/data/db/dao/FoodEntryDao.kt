package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import de.kalorienapp.data.db.entity.FoodEntryEntity
import de.kalorienapp.domain.model.MealType
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entry WHERE dateEpochDay = :epochDay ORDER BY COALESCE(sortKey, createdAt), id")
    fun observeForDay(epochDay: Long): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entry WHERE id = :id")
    suspend fun getById(id: Long): FoodEntryEntity?

    @Upsert
    suspend fun upsert(entry: FoodEntryEntity)

    /** Mehrere Einträge in einem Schritt (Verschieben ändert die ganze Ziel-Mahlzeit). */
    @Upsert
    suspend fun upsertAll(entries: List<FoodEntryEntity>)

    @Query("SELECT * FROM food_entry WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY dateEpochDay, createdAt")
    suspend fun between(fromEpochDay: Long, toEpochDay: Long): List<FoodEntryEntity>

    @Query("SELECT dateEpochDay, mealType, createdAt FROM food_entry WHERE dateEpochDay >= :fromEpochDay")
    suspend fun mealTimes(fromEpochDay: Long): List<MealTimeRow>

    @Query("SELECT DISTINCT mealType FROM food_entry WHERE dateEpochDay = :epochDay")
    suspend fun mealsForDay(epochDay: Long): List<MealType>

    @Query("SELECT COUNT(*) FROM food_entry WHERE dateEpochDay = :epochDay")
    suspend fun countForDay(epochDay: Long): Int

    /** Je Tag der Zeitpunkt des ersten Eintrags (für die übliche Uhrzeit der ersten Mahlzeit). */
    @Query("SELECT dateEpochDay, MIN(createdAt) AS firstCreatedAt FROM food_entry WHERE dateEpochDay >= :fromEpochDay GROUP BY dateEpochDay")
    suspend fun firstEntryPerDay(fromEpochDay: Long): List<FirstEntryRow>

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Je Lebensmittel (Name + Einheit) der jüngste Eintrag, sortiert nach Häufigkeit. */
    @Query(
        """
        SELECT e.*, g.cnt AS useCount FROM food_entry e
        JOIN (
            SELECT MAX(id) AS maxId, COUNT(*) AS cnt FROM food_entry
            WHERE amountG > 0
            GROUP BY name COLLATE NOCASE, unit
        ) g ON e.id = g.maxId
        ORDER BY g.cnt DESC, e.createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeFrequent(limit: Int): Flow<List<FrequentEntryRow>>
}

data class MealTimeRow(val dateEpochDay: Long, val mealType: MealType, val createdAt: Long)

data class FirstEntryRow(val dateEpochDay: Long, val firstCreatedAt: Long)

data class FrequentEntryRow(
    @Embedded val entry: FoodEntryEntity,
    val useCount: Int,
)
