package de.kalorienapp.ui.food

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString

/**
 * Nährwertfelder in der Reihenfolge der Nährwerttabelle auf Verpackungen,
 * damit man sie direkt abtippen kann. [subItem] = „davon …“-Zeile (eingerückt).
 */
enum class NutrientField(val subItem: Boolean = false) {
    KCAL, FAT, SATURATED_FAT(subItem = true), CARBS, SUGAR(subItem = true), FIBER, PROTEIN, SALT,
}

/**
 * Eingabezustand für ein Lebensmittel (manueller Eintrag, Barcode- oder KI-Vorschlag).
 * Alle Nährwertfelder gelten pro 100 g bzw. 100 ml.
 */
data class FoodForm(
    val name: String = "",
    val amount: String = "",
    val unit: AmountUnit = AmountUnit.GRAM,
    val per100: Map<NutrientField, String> = emptyMap(),
    /** Vitamine/Mineralstoffe pro 100 g/ml (optional). */
    val micros: Map<Micro, String> = emptyMap(),
) {
    val amountValue: Double? get() = amount.parseDecimal()?.takeIf { it > 0 }
    val amountError get() = amount.isNotBlank() && amountValue == null

    fun value(field: NutrientField): String = per100[field].orEmpty()

    fun isError(field: NutrientField): Boolean {
        val text = value(field)
        return text.isNotBlank() && text.parseDecimal() == null
    }

    fun microValue(micro: Micro): String = micros[micro].orEmpty()

    fun isMicroError(micro: Micro): Boolean {
        val text = microValue(micro)
        return text.isNotBlank() && text.parseDecimal()?.takeIf { it >= 0 } == null
    }

    fun withMicro(micro: Micro, text: String) = copy(micros = micros + (micro to text))

    val isValid: Boolean
        get() = name.isNotBlank() && amountValue != null &&
            value(NutrientField.KCAL).parseDecimal() != null &&
            NutrientField.entries.none(::isError) && Micro.entries.none(::isMicroError)

    fun withNutrient(field: NutrientField, text: String) = copy(per100 = per100 + (field to text))

    /** Werte pro 100 g/ml; leere Hauptfelder zählen als 0, leere Zusatzfelder als „nicht angegeben“. */
    fun per100Nutrients() = Nutrients(
        kcal = number(NutrientField.KCAL) ?: 0.0,
        proteinG = number(NutrientField.PROTEIN) ?: 0.0,
        carbsG = number(NutrientField.CARBS) ?: 0.0,
        fatG = number(NutrientField.FAT) ?: 0.0,
        saturatedFatG = number(NutrientField.SATURATED_FAT),
        sugarG = number(NutrientField.SUGAR),
        fiberG = number(NutrientField.FIBER),
        saltG = number(NutrientField.SALT),
        micros = micros.mapNotNull { (micro, text) -> text.parseDecimal()?.takeIf { it >= 0 }?.let { micro to it } }.toMap(),
    )

    /** Werte für die eingegebene Menge, oder null solange die Menge fehlt. */
    fun totalNutrients(): Nutrients? = amountValue?.let { per100Nutrients().per100ToAmount(it) }

    private fun number(field: NutrientField) = value(field).parseDecimal()

    companion object {
        fun of(name: String, amount: Double?, unit: AmountUnit, per100: Nutrients) = FoodForm(
            name = name,
            amount = amount?.toInputString() ?: "",
            unit = unit,
            per100 = buildMap {
                put(NutrientField.KCAL, per100.kcal.toInputString())
                put(NutrientField.FAT, per100.fatG.toInputString())
                per100.saturatedFatG?.let { put(NutrientField.SATURATED_FAT, it.toInputString()) }
                put(NutrientField.CARBS, per100.carbsG.toInputString())
                per100.sugarG?.let { put(NutrientField.SUGAR, it.toInputString()) }
                per100.fiberG?.let { put(NutrientField.FIBER, it.toInputString()) }
                put(NutrientField.PROTEIN, per100.proteinG.toInputString())
                per100.saltG?.let { put(NutrientField.SALT, it.toInputString(2)) }
            },
            micros = per100.micros.mapValues { (_, v) -> v.toInputString(if (v < 10) 2 else 0) },
        )
    }
}
