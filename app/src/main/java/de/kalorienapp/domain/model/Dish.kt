package de.kalorienapp.domain.model

/** Eine Zutat eines Gerichts; Nährwerte pro 100 g/ml wie im Formular. */
data class DishComponent(
    val name: String,
    val amount: Double,
    val unit: AmountUnit,
    val per100: Nutrients,
) {
    val nutrients: Nutrients get() = per100.per100ToAmount(amount)
}

/** Rechnungen für Gerichte aus mehreren Zutaten. */
object Dish {
    fun total(components: List<DishComponent>): Nutrients = components.map { it.nutrients }.sum()

    /** Gesamtmenge (g und ml zählen gleich – bei Gerichten reicht das). */
    fun totalAmount(components: List<DishComponent>): Double = components.sumOf { it.amount }

    /** ml nur, wenn alle Zutaten flüssig sind (z. B. Smoothie), sonst g. */
    fun unit(components: List<DishComponent>): AmountUnit =
        if (components.isNotEmpty() && components.all { it.unit == AmountUnit.MILLILITER }) AmountUnit.MILLILITER else AmountUnit.GRAM

    /** Alle Zutaten im gleichen Verhältnis auf [newTotal] bringen („nur die Hälfte gegessen“). */
    fun scale(components: List<DishComponent>, newTotal: Double): List<DishComponent> {
        val current = totalAmount(components)
        if (current <= 0 || newTotal <= 0) return components
        val factor = newTotal / current
        return components.map { it.copy(amount = it.amount * factor) }
    }
}
