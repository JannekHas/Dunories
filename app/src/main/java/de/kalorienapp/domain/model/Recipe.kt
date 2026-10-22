package de.kalorienapp.domain.model

/** Aufwand eines Rezepts. */
enum class Effort { EASY, MEDIUM, HARD }

/** Woher ein Rezept stammt (als Text gespeichert – neue Werte brauchen keine Migration). */
enum class RecipeSource { MANUAL, TIKTOK, INSTAGRAM, VIDEO, IMAGES, TEXT }

data class RecipeCategory(
    val id: Long = 0,
    val name: String,
    val position: Int = 0,
)

/**
 * Zutat. [quantity] + [quantityUnit] ist die Angabe wie im Rezept („2 Stück“, „1 EL“, „200 g“) –
 * auch für die spätere Einkaufsliste. Für die Nährwerte zählt [amount] in g/ml ([unit]) mit
 * Werten pro 100 g/ml; [amount] = null → die Zutat zählt nicht (z. B. „Salz nach Geschmack“).
 */
data class RecipeIngredient(
    val id: Long = 0,
    val name: String,
    val quantity: Double? = null,
    val quantityUnit: String = "",
    val amount: Double? = null,
    val unit: AmountUnit = AmountUnit.GRAM,
    val per100: Nutrients = Nutrients.ZERO,
    val note: String? = null,
) {
    val nutrients: Nutrients get() = amount?.let { per100.per100ToAmount(it) } ?: Nutrients.ZERO
}

data class Recipe(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val servings: Int = 1,
    val totalMinutes: Int? = null,
    val effort: Effort? = null,
    /** Lokaler Pfad des Rezeptbilds (App-Speicher) oder null. */
    val imagePath: String? = null,
    val sourceUrl: String? = null,
    val source: RecipeSource = RecipeSource.MANUAL,
    val categoryIds: Set<Long> = emptySet(),
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val safeServings: Int get() = servings.coerceAtLeast(1)

    /** Summe aller Zutaten. */
    val totalNutrients: Nutrients get() = ingredients.map(RecipeIngredient::nutrients).sum()

    val perServing: Nutrients get() = totalNutrients * (1.0 / safeServings)

    /** Gesamtgewicht der gezählten Zutaten (g und ml gleich behandelt); 0 = unbekannt. */
    val totalAmount: Double get() = ingredients.sumOf { it.amount ?: 0.0 }
}
