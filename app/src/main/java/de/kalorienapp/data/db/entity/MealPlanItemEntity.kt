package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.kalorienapp.data.db.DishComponentCodec
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients

/** Ein Lebensmittel/Gericht im Ernährungsplan; Nährwerte pro 100 g/ml. */
@Entity(tableName = "meal_plan_item", indices = [Index("dayIndex")])
data class MealPlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayIndex: Int,
    val mealType: MealType,
    val position: Int,
    val name: String,
    val amount: Double,
    val unit: AmountUnit,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val fiberG: Double?,
    val saltG: Double?,
    val micros: String? = null,
    /** Zutaten bei Gerichten (siehe [DishComponentCodec]). */
    val components: String? = null,
) {
    fun toDomain() = MealPlanItem(
        id = id,
        dayIndex = dayIndex,
        mealType = mealType,
        position = position,
        name = name,
        amount = amount,
        unit = unit,
        per100 = Nutrients(kcal, proteinG, carbsG, fatG, saturatedFatG, sugarG, fiberG, saltG, Micro.decode(micros)),
        components = DishComponentCodec.decode(components),
    )

    companion object {
        fun fromDomain(i: MealPlanItem) = MealPlanItemEntity(
            id = i.id,
            dayIndex = i.dayIndex,
            mealType = i.mealType,
            position = i.position,
            name = i.name,
            amount = i.amount,
            unit = i.unit,
            kcal = i.per100.kcal,
            proteinG = i.per100.proteinG,
            carbsG = i.per100.carbsG,
            fatG = i.per100.fatG,
            saturatedFatG = i.per100.saturatedFatG,
            sugarG = i.per100.sugarG,
            fiberG = i.per100.fiberG,
            saltG = i.per100.saltG,
            micros = Micro.encode(i.per100.micros),
            components = DishComponentCodec.encode(i.components),
        )
    }
}
