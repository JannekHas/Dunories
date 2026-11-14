package de.kalorienapp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.kalorienapp.data.db.DishComponentCodec
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients
import java.time.LocalDate

/** Nährwertspalten enthalten Absolutwerte für die eingetragene Menge. */
@Entity(tableName = "food_entry", indices = [Index("dateEpochDay")])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val mealType: MealType,
    val name: String,
    // Spaltenname aus Version 1 beibehalten (SQLite auf API 28 kann keine Spalten umbenennen).
    @ColumnInfo(name = "amountG") val amount: Double?,
    @ColumnInfo(defaultValue = "GRAM") val unit: AmountUnit,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val fiberG: Double?,
    val saltG: Double?,
    val source: EntrySource,
    val createdAt: Long,
    /** Vitamine/Mineralstoffe für die eingetragene Menge (siehe [Micro.encode]). */
    val micros: String? = null,
    /** Reihenfolge innerhalb der Mahlzeit nach Verschieben; null = Erfassungszeit. */
    val sortKey: Long? = null,
    /** Zutaten, wenn der Eintrag ein Gericht ist (JSON, siehe [DishComponentCodec]); null = kein Gericht. */
    val components: String? = null,
    /** Zuletzt per Zusammenfassen ergänzt; null = nie. */
    val lastAddedAt: Long? = null,
) {
    fun toDomain() = FoodEntry(
        id = id,
        date = LocalDate.ofEpochDay(dateEpochDay),
        mealType = mealType,
        name = name,
        amount = amount,
        unit = unit,
        nutrients = Nutrients(
            kcal = kcal,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            saturatedFatG = saturatedFatG,
            sugarG = sugarG,
            fiberG = fiberG,
            saltG = saltG,
            micros = Micro.decode(micros),
        ),
        source = source,
        createdAt = createdAt,
        sortKey = sortKey,
        components = DishComponentCodec.decode(components),
        lastAddedAt = lastAddedAt,
    )

    companion object {
        fun fromDomain(e: FoodEntry) = FoodEntryEntity(
            id = e.id,
            dateEpochDay = e.date.toEpochDay(),
            mealType = e.mealType,
            name = e.name,
            amount = e.amount,
            unit = e.unit,
            kcal = e.nutrients.kcal,
            proteinG = e.nutrients.proteinG,
            carbsG = e.nutrients.carbsG,
            fatG = e.nutrients.fatG,
            saturatedFatG = e.nutrients.saturatedFatG,
            sugarG = e.nutrients.sugarG,
            fiberG = e.nutrients.fiberG,
            saltG = e.nutrients.saltG,
            source = e.source,
            createdAt = e.createdAt,
            micros = Micro.encode(e.nutrients.micros),
            sortKey = e.sortKey,
            components = DishComponentCodec.encode(e.components),
            lastAddedAt = e.lastAddedAt,
        )
    }
}
