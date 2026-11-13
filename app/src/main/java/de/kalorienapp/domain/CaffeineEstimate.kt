package de.kalorienapp.domain

/**
 * Koffein pro 100 g/ml nach Produktart (Open-Food-Facts-Kategorien), wenn das Produkt selbst keinen
 * Wert hat – dort fehlt er bei fast allen Kaffees, Colas und Energy Drinks (Stichprobe Nov. 2026).
 * Typische Werte; spezifischere Kategorien zuerst.
 */
object CaffeineEstimate {

    private val BY_CATEGORY: List<Pair<String, Double>> = listOf(
        "en:energy-drinks" to 32.0,
        "en:iced-coffees" to 45.0,
        "en:coffee-drinks" to 45.0,
        "en:cold-brew-coffees" to 60.0,
        "en:colas" to 10.0,
        "en:mate-drinks" to 20.0,
        "en:iced-teas" to 8.0,
        "en:dark-chocolates" to 60.0,
        "en:milk-chocolates" to 20.0,
    )

    /** Schätzwert in mg pro 100 g/ml oder null, wenn keine passende Kategorie. */
    fun forCategories(tags: Collection<String>): Double? =
        BY_CATEGORY.firstOrNull { (tag, _) -> tag in tags }?.second

    /**
     * Wert aus Open Food Facts bevorzugen; fehlt er oder steht dort 0 bei einer typisch koffeinhaltigen
     * Kategorie (häufiger Platzhalter), wird geschätzt.
     */
    fun resolve(offMg: Double?, tags: Collection<String>): Double? {
        val estimate = forCategories(tags)
        return when {
            offMg != null && (offMg > 0 || estimate == null) -> offMg
            else -> estimate
        }
    }
}
