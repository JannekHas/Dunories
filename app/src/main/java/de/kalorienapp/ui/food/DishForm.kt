package de.kalorienapp.ui.food

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.Dish
import de.kalorienapp.domain.model.DishComponent
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.sum
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString

/** Eine Zutat im Gericht-Formular; [id] hält den Auf-/Zuklapp-Zustand stabil. */
data class DishPart(val id: Long, val form: FoodForm)

/**
 * Eingabezustand für ein Gericht aus Zutaten (z. B. Spaghetti Bolognese).
 * Die Gesamtmenge ist die Summe der Zutaten; wer sie ändert („nur die Hälfte gegessen“),
 * ändert alle Zutaten anteilig.
 */
data class DishForm(
    val name: String,
    val parts: List<DishPart>,
    /** Gerade getippte Gesamtmenge; null = aus den Zutaten berechnet. */
    val amountInput: String? = null,
    /** Zutaten vor dem Tippen der Gesamtmenge – Ausgangspunkt jeder Umrechnung, sonst summieren sich Rundungen
     *  (beim Tippen von „225“ käme sonst erst 2, dann 22 g heraus). */
    val scaleBase: List<DishPart>? = null,
) {
    val totalAmount: Double get() = parts.sumOf { it.form.amountValue ?: 0.0 }

    val amountText: String get() = amountInput ?: totalAmount.takeIf { it > 0 }?.toInputString().orEmpty()

    val unit: AmountUnit get() = Dish.unit(components())

    val isValid: Boolean
        get() = name.isNotBlank() && parts.isNotEmpty() && parts.all { it.form.isValid }

    val amountError: Boolean get() = amountInput != null && amountInput.isNotBlank() && amountInput.parseDecimal()?.takeIf { it > 0 } == null

    /** Neue Gesamtmenge: alle Zutaten im gleichen Verhältnis. */
    fun withAmount(text: String): DishForm {
        val base = scaleBase ?: parts
        val target = text.parseDecimal()?.takeIf { it > 0 }
        val current = base.sumOf { it.form.amountValue ?: 0.0 }
        if (target == null || current <= 0) return copy(amountInput = text, scaleBase = base)
        val factor = target / current
        return copy(
            amountInput = text,
            scaleBase = base,
            parts = base.map { part ->
                val amount = part.form.amountValue ?: return@map part
                part.copy(form = part.form.copy(amount = (amount * factor).toInputString()))
            },
        )
    }

    fun withPart(id: Long, form: FoodForm) =
        copy(parts = parts.map { if (it.id == id) it.copy(form = form) else it }, amountInput = null, scaleBase = null)

    fun withoutPart(id: Long) = copy(parts = parts.filterNot { it.id == id }, amountInput = null, scaleBase = null)

    fun withNewPart(id: Long) = copy(parts = parts + DishPart(id, FoodForm()), amountInput = null, scaleBase = null)

    /** Zutaten mit gültiger Menge (zum Speichern). */
    fun components(): List<DishComponent> = parts.mapNotNull { part ->
        val amount = part.form.amountValue ?: return@mapNotNull null
        DishComponent(part.form.name.trim(), amount, part.form.unit, part.form.per100Nutrients())
    }

    fun totalNutrients(): Nutrients = parts.mapNotNull { it.form.totalNutrients() }.sum()

    companion object {
        fun of(name: String, components: List<DishComponent>, nextId: () -> Long) = DishForm(
            name = name,
            parts = components.map { DishPart(nextId(), FoodForm.of(it.name, it.amount, it.unit, it.per100)) },
        )

        fun ofDraft(draft: FoodDraft, nextId: () -> Long) = DishForm(
            name = draft.name,
            parts = draft.components.map { DishPart(nextId(), FoodForm.of(it.name, it.amount, it.unit, it.per100)) },
        )
    }
}
