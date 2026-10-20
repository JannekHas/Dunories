package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import java.time.LocalDate

@Entity(tableName = "food_entry", indices = [Index("dateEpochDay")])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val mealType: MealType,
    val name: String,
    val amountG: Double?,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: EntrySource,
    val createdAt: Long,
) {
    fun toDomain() = FoodEntry(
        id = id,
        date = LocalDate.ofEpochDay(dateEpochDay),
        mealType = mealType,
        name = name,
        amountG = amountG,
        nutrients = Nutrients(kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG),
        source = source,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(e: FoodEntry) = FoodEntryEntity(
            id = e.id,
            dateEpochDay = e.date.toEpochDay(),
            mealType = e.mealType,
            name = e.name,
            amountG = e.amountG,
            kcal = e.nutrients.kcal,
            proteinG = e.nutrients.proteinG,
            carbsG = e.nutrients.carbsG,
            fatG = e.nutrients.fatG,
            source = e.source,
            createdAt = e.createdAt,
        )
    }
}
