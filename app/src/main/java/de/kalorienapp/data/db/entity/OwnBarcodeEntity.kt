package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients

/**
 * Eigener Barcode (Tester-Wunsch Nov. 2026, „Sonderverpackungs-Toast“): Produkt, das Open Food Facts nicht kennt,
 * einmal per Etikett/KI/Eintippen erfasst → beim nächsten Scan sofort da, ohne KI und ohne Internet.
 * Nährwertspalten pro 100 g/ml, [amount] = zuletzt gegessene Menge (Vorschlag).
 */
@Entity(tableName = "own_barcode")
data class OwnBarcodeEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val amount: Double?,
    val unit: AmountUnit,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val fiberG: Double?,
    val saltG: Double?,
    val micros: String?,
    val updatedAt: Long,
) {
    fun toDraft() = FoodDraft(
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
            micros = Micro.decode(micros),
        ),
        source = EntrySource.BARCODE,
    )

    companion object {
        fun of(barcode: String, name: String, amount: Double?, unit: AmountUnit, per100: Nutrients, now: Long = System.currentTimeMillis()) =
            OwnBarcodeEntity(
                barcode = barcode,
                name = name,
                amount = amount,
                unit = unit,
                kcal = per100.kcal,
                proteinG = per100.proteinG,
                carbsG = per100.carbsG,
                fatG = per100.fatG,
                saturatedFatG = per100.saturatedFatG,
                sugarG = per100.sugarG,
                fiberG = per100.fiberG,
                saltG = per100.saltG,
                micros = Micro.encode(per100.micros),
                updatedAt = now,
            )
    }
}
