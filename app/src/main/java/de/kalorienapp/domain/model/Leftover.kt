package de.kalorienapp.domain.model

import java.time.LocalDate

/**
 * Offene Menge („Rest“, Nov. 2026): Teil eines Gerichts, der noch nicht gegessen ist – z. B. die zweite Hälfte
 * vom großen Topf Chili, gern auch eingefroren. Zählt für keinen Tag, bis er übernommen wird.
 */
data class Leftover(
    val id: Long = 0,
    val name: String,
    /** Was noch da ist (Mengen = Restmengen, Werte pro 100 g/ml). */
    val components: List<DishComponent>,
    /** Tag, an dem gekocht/eingetragen wurde. */
    val createdDay: LocalDate,
    /** Mahlzeit, aus der der Rest stammt. */
    val mealType: MealType,
    val source: EntrySource = EntrySource.MANUAL,
    /** Zuletzt gegessene Portion (g/ml) – Vorauswahl beim Übernehmen. */
    val portionAmount: Double? = null,
    /** Eingefroren → kein Vorschlag auf „Heute“, keine Rückfrage „Noch da?“. */
    val frozen: Boolean = false,
    /** ✕ auf „Heute“ → an diesem Tag nicht mehr vorschlagen. */
    val dismissedDay: LocalDate? = null,
    /** „Noch gut“ bei der Rückfrage → ab diesem Tag neu zählen. */
    val checkedDay: LocalDate? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val amount: Double get() = Dish.totalAmount(components)
    val unit: AmountUnit get() = Dish.unit(components)
    val nutrients: Nutrients get() = Dish.total(components)
}
