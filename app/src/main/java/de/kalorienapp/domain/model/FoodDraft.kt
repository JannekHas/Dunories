package de.kalorienapp.domain.model

/**
 * Vorschlag für ein Lebensmittel (aus Barcode oder KI). Wird dem Nutzer immer
 * zur Bearbeitung angezeigt und erst nach Bestätigung zu einem [FoodEntry].
 */
data class FoodDraft(
    val name: String,
    /** Geschätzte bzw. bekannte Menge; null = muss der Nutzer eintragen (z. B. Barcode). */
    val amount: Double?,
    val unit: AmountUnit,
    val per100: Nutrients,
    val source: EntrySource,
    /** Zutaten, wenn die KI ein Gericht erkannt hat (z. B. Spaghetti Bolognese); leer = einzelnes Lebensmittel. */
    val components: List<FoodDraft> = emptyList(),
)
