package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.FavoriteFood
import de.kalorienapp.domain.model.Nutrients

/** Gespeicherter Favorit. Nährwertspalten gelten pro 100 g/ml, [amount] ist die übliche Portion. */
@Entity(tableName = "favorite_food")
data class FavoriteFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val createdAt: Long,
) {
    fun toDomain() = FavoriteFood(
        id = id,
        name = name,
        amount = amount,
        unit = unit,
        per100 = Nutrients(
            kcal = kcal,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            saturatedFatG = saturatedFatG,
            sugarG = sugarG,
            fiberG = fiberG,
            saltG = saltG,
        ),
    )

    companion object {
        fun fromDomain(f: FavoriteFood, createdAt: Long = System.currentTimeMillis()) = FavoriteFoodEntity(
            id = f.id,
            name = f.name,
            amount = f.amount,
            unit = f.unit,
            kcal = f.per100.kcal,
            proteinG = f.per100.proteinG,
            carbsG = f.per100.carbsG,
            fatG = f.per100.fatG,
            saturatedFatG = f.per100.saturatedFatG,
            sugarG = f.per100.sugarG,
            fiberG = f.per100.fiberG,
            saltG = f.per100.saltG,
            createdAt = createdAt,
        )
    }
}
