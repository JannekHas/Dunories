package de.kalorienapp.data.remote

import de.kalorienapp.domain.CaffeineEstimate
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.MicroUnit
import kotlinx.serialization.json.JsonObject

/** JSON-Feldnamen der Vitamine/Mineralstoffe – die Einheit steht im Namen, damit die KI sie richtig liefert. */
internal object MicroFields {
    val KEYS: Map<Micro, String> = linkedMapOf(
        Micro.VITAMIN_D to "vitamin_d_ug",
        Micro.VITAMIN_B12 to "vitamin_b12_ug",
        Micro.VITAMIN_C to "vitamin_c_mg",
        Micro.FOLATE to "folate_ug",
        Micro.VITAMIN_A to "vitamin_a_ug",
        Micro.VITAMIN_E to "vitamin_e_mg",
        Micro.VITAMIN_K to "vitamin_k_ug",
        Micro.VITAMIN_B6 to "vitamin_b6_mg",
        Micro.VITAMIN_B1 to "vitamin_b1_mg",
        Micro.VITAMIN_B2 to "vitamin_b2_mg",
        Micro.IRON to "iron_mg",
        Micro.CALCIUM to "calcium_mg",
        Micro.MAGNESIUM to "magnesium_mg",
        Micro.POTASSIUM to "potassium_mg",
        Micro.ZINC to "zinc_mg",
        Micro.IODINE to "iodine_ug",
        Micro.SELENIUM to "selenium_ug",
        Micro.PHOSPHORUS to "phosphorus_mg",
        Micro.CAFFEINE to "caffeine_mg",
        Micro.CHOLESTEROL to "cholesterol_mg",
        Micro.OMEGA_3 to "omega3_g",
    )

    const val PROMPT_RULE = """Vitamine, Mineralstoffe und weitere Werte (Felder vitamin_d_ug … omega3_g) ebenfalls PRO 100 g bzw. PRO 100 ml,
in der Einheit aus dem Feldnamen (ug = Mikrogramm, mg = Milligramm, g = Gramm), typische Werte aus Nährwerttabellen; unbekannt null.
Koffein (caffeine_mg) immer angeben, wenn es drin ist: Kaffee, Espresso, Tee, Mate, Cola, Energy Drinks, dunkle Schokolade –
bei erkennbaren Marken (z. B. Monster, Red Bull) den bekannten Wert, steht er auf dem Etikett, den Etikettwert; koffeinfrei = 0."""

    /** Werte aus einer KI-Antwort; negative/unbrauchbare werden ignoriert. */
    fun read(item: JsonObject): Map<Micro, Double> =
        KEYS.mapNotNull { (micro, key) -> item.number(key)?.takeIf { it >= 0 }?.let { micro to it } }.toMap()

    private val OFF_KEYS: Map<Micro, List<String>> = mapOf(
        Micro.VITAMIN_D to listOf("vitamin-d_100g"),
        Micro.VITAMIN_B12 to listOf("vitamin-b12_100g"),
        Micro.VITAMIN_C to listOf("vitamin-c_100g"),
        Micro.FOLATE to listOf("vitamin-b9_100g", "folates_100g"),
        Micro.VITAMIN_A to listOf("vitamin-a_100g"),
        Micro.VITAMIN_E to listOf("vitamin-e_100g"),
        Micro.VITAMIN_K to listOf("vitamin-k_100g"),
        Micro.VITAMIN_B6 to listOf("vitamin-b6_100g"),
        Micro.VITAMIN_B1 to listOf("vitamin-b1_100g"),
        Micro.VITAMIN_B2 to listOf("vitamin-b2_100g"),
        Micro.IRON to listOf("iron_100g"),
        Micro.CALCIUM to listOf("calcium_100g"),
        Micro.MAGNESIUM to listOf("magnesium_100g"),
        Micro.POTASSIUM to listOf("potassium_100g"),
        Micro.ZINC to listOf("zinc_100g"),
        Micro.IODINE to listOf("iodine_100g"),
        Micro.SELENIUM to listOf("selenium_100g"),
        Micro.PHOSPHORUS to listOf("phosphorus_100g"),
        Micro.CAFFEINE to listOf("caffeine_100g"),
        Micro.CHOLESTEROL to listOf("cholesterol_100g"),
        Micro.OMEGA_3 to listOf("omega-3-fat_100g"),
    )

    /**
     * Open Food Facts speichert `…_100g` in Gramm → in mg bzw. µg umrechnen.
     * Folsäure heißt dort `vitamin-b9` oder `folates`. Koffein fehlt meist → Schätzung nach [categories].
     */
    fun readOpenFoodFacts(nutriments: JsonObject, categories: Collection<String> = emptyList()): Map<Micro, Double> {
        val values = OFF_KEYS.mapNotNull { (micro, keys) ->
            val grams = keys.firstNotNullOfOrNull { nutriments.number(it) }?.takeIf { it >= 0 } ?: return@mapNotNull null
            micro to grams * micro.unit.perGram
        }.toMap()
        val caffeine = CaffeineEstimate.resolve(values[Micro.CAFFEINE], categories)
        return if (caffeine == null) values - Micro.CAFFEINE else values + (Micro.CAFFEINE to caffeine)
    }

    private val MicroUnit.perGram: Double
        get() = when (this) {
            MicroUnit.MICROGRAM -> 1_000_000.0
            MicroUnit.MILLIGRAM -> 1_000.0
            MicroUnit.GRAM -> 1.0
        }
}
