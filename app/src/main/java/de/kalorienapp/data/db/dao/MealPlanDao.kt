package de.kalorienapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.kalorienapp.data.db.entity.MealPlanItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plan_item ORDER BY dayIndex, mealType, position, id")
    fun observeAll(): Flow<List<MealPlanItemEntity>>

    @Query("SELECT * FROM meal_plan_item WHERE dayIndex = :dayIndex ORDER BY position, id")
    suspend fun forDay(dayIndex: Int): List<MealPlanItemEntity>

    @Query("SELECT * FROM meal_plan_item WHERE id = :id")
    suspend fun get(id: Long): MealPlanItemEntity?

    @Insert
    suspend fun insertAll(items: List<MealPlanItemEntity>)

    @Query("DELETE FROM meal_plan_item WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM meal_plan_item WHERE dayIndex = :dayIndex")
    suspend fun deleteDay(dayIndex: Int)

    @Query("DELETE FROM meal_plan_item")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(position), -1) FROM meal_plan_item WHERE dayIndex = :dayIndex AND mealType = :mealType")
    suspend fun maxPosition(dayIndex: Int, mealType: String): Int

    @Transaction
    suspend fun replaceAll(items: List<MealPlanItemEntity>) {
        deleteAll()
        insertAll(items)
    }

    @Transaction
    suspend fun replaceDay(dayIndex: Int, items: List<MealPlanItemEntity>) {
        deleteDay(dayIndex)
        insertAll(items)
    }
}
