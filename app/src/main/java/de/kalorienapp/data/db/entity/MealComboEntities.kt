package de.kalorienapp.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.FavoriteFood
import de.kalorienapp.domain.model.MealCombo
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients

@Entity(tableName = "meal_combo")
data class MealComboEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** Teil einer Kombi; Nährwerte pro 100 g/ml, [amount] = Menge in der Kombi. */
@Entity(
    tableName = "meal_combo_item",
    foreignKeys = [ForeignKey(MealComboEntity::class, ["id"], ["comboId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("comboId")],
)
data class MealComboItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comboId: Long,
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
    /** Vitamine/Mineralstoffe pro 100 g/ml (siehe [Micro.encode]). */
    val micros: String? = null,
) {
    fun toDomain() = FavoriteFood(
        id = id,
        name = name,
        amount = amount,
        unit = unit,
        per100 = Nutrients(kcal, proteinG, carbsG, fatG, saturatedFatG, sugarG, fiberG, saltG, Micro.decode(micros)),
    )

    companion object {
        fun fromDomain(comboId: Long, position: Int, f: FavoriteFood) = MealComboItemEntity(
            comboId = comboId,
            position = position,
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
            micros = Micro.encode(f.per100.micros),
        )
    }
}

data class MealComboWithItems(
    @Embedded val combo: MealComboEntity,
    @Relation(parentColumn = "id", entityColumn = "comboId")
    val items: List<MealComboItemEntity>,
) {
    fun toDomain() = MealCombo(combo.id, combo.name, items.sortedBy { it.position }.map { it.toDomain() })
}
