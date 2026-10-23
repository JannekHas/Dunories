package de.kalorienapp.data.remote

import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.MicroUnit
import kotlinx.serialization.json.JsonObject

/** JSON-Feldnamen der Vitamine/Mineralstoffe – die Einheit steht im Namen, damit die KI sie richtig liefert. */
internal object MicroFields {
    val KEYS: Map<Micro, String> = linkedMapOf(
        Micro.VITAMIN_A to "vitamin_a_ug",
        Micro.VITAMIN_C to "vitamin_c_mg",
        Micro.VITAMIN_D to "vitamin_d_ug",
        Micro.VITAMIN_B12 to "vitamin_b12_ug",
        Micro.FOLATE to "folate_ug",
        Micro.CALCIUM to "calcium_mg",
        Micro.IRON to "iron_mg",
        Micro.MAGNESIUM to "magnesium_mg",
        Micro.ZINC to "zinc_mg",
        Micro.POTASSIUM to "potassium_mg",
    )

    const val PROMPT_RULE = """Vitamine und Mineralstoffe (Felder vitamin_a_ug … potassium_mg) ebenfalls PRO 100 g bzw. PRO 100 ml,
in der Einheit aus dem Feldnamen (ug = Mikrogramm, mg = Milligramm), typische Werte aus Nährwerttabellen; unbekannt null."""

    /** Werte aus einer KI-Antwort; negative/unbrauchbare werden ignoriert. */
    fun read(item: JsonObject): Map<Micro, Double> =
        KEYS.mapNotNull { (micro, key) -> item.number(key)?.takeIf { it >= 0 }?.let { micro to it } }.toMap()

    /**
     * Open Food Facts speichert `…_100g` in Gramm → in mg bzw. µg umrechnen.
     * Folsäure heißt dort `vitamin-b9` oder `folates`.
     */
    fun readOpenFoodFacts(nutriments: JsonObject): Map<Micro, Double> {
        val offKeys = mapOf(
            Micro.VITAMIN_A to listOf("vitamin-a_100g"),
            Micro.VITAMIN_C to listOf("vitamin-c_100g"),
            Micro.VITAMIN_D to listOf("vitamin-d_100g"),
            Micro.VITAMIN_B12 to listOf("vitamin-b12_100g"),
            Micro.FOLATE to listOf("vitamin-b9_100g", "folates_100g"),
            Micro.CALCIUM to listOf("calcium_100g"),
            Micro.IRON to listOf("iron_100g"),
            Micro.MAGNESIUM to listOf("magnesium_100g"),
            Micro.ZINC to listOf("zinc_100g"),
            Micro.POTASSIUM to listOf("potassium_100g"),
        )
        return offKeys.mapNotNull { (micro, keys) ->
            val grams = keys.firstNotNullOfOrNull { nutriments.number(it) }?.takeIf { it >= 0 } ?: return@mapNotNull null
            micro to grams * if (micro.unit == MicroUnit.MICROGRAM) 1_000_000.0 else 1_000.0
        }.toMap()
    }
}
