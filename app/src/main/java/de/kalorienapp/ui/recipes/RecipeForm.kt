package de.kalorienapp.ui.recipes

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.Effort
import de.kalorienapp.domain.model.Recipe
import de.kalorienapp.domain.model.RecipeIngredient
import de.kalorienapp.domain.model.RecipeSource
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.food.FoodForm
import de.kalorienapp.ui.food.NutrientField

/**
 * Zutat im Editor. [food] enthält Name, Gewicht in g/ml für die Nährwerte und Werte pro 100;
 * [quantity]/[quantityUnit] die Angabe wie im Rezept („2 Stück“).
 */
data class IngredientForm(
    val key: Long,
    val quantity: String = "",
    val quantityUnit: String = "",
    val food: FoodForm = FoodForm(),
    val note: String = "",
) {
    val quantityValue: Double? get() = quantity.parseDecimal()?.takeIf { it > 0 }
    val quantityError: Boolean get() = quantity.isNotBlank() && quantityValue == null

    val isValid: Boolean
        get() = food.name.isNotBlank() && !quantityError && !food.amountError && NutrientField.entries.none(food::isError)

    /** Bei Einheit g/ml ist die Rezeptmenge gleich dem Gewicht für die Nährwerte. */
    fun withQuantity(quantity: String, unit: String): IngredientForm {
        val weightUnit = weightUnitOf(unit)
        return copy(
            quantity = quantity,
            quantityUnit = unit,
            food = if (weightUnit != null) food.copy(amount = quantity, unit = weightUnit) else food,
        )
    }

    fun toDomain() = RecipeIngredient(
        name = food.name.trim(),
        quantity = quantityValue,
        quantityUnit = quantityUnit.trim(),
        amount = food.amountValue,
        unit = food.unit,
        per100 = food.per100Nutrients(),
        note = note.trim().takeIf { it.isNotEmpty() },
    )

    companion object {
        fun weightUnitOf(unit: String): AmountUnit? = when (unit.trim().lowercase()) {
            "g", "gramm" -> AmountUnit.GRAM
            "ml", "milliliter" -> AmountUnit.MILLILITER
            else -> null
        }

        fun from(key: Long, i: RecipeIngredient) = IngredientForm(
            key = key,
            quantity = i.quantity?.toInputString() ?: "",
            quantityUnit = i.quantityUnit,
            food = FoodForm.of(i.name, i.amount, i.unit, i.per100),
            note = i.note.orEmpty(),
        )
    }
}

data class StepForm(val key: Long, val text: String = "")

data class RecipeForm(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val servings: String = "2",
    val minutes: String = "",
    val effort: Effort? = null,
    val categoryIds: Set<Long> = emptySet(),
    /** Vorgeschlagene neue Kategorien (aus dem Import), werden beim Speichern angelegt. */
    val newCategoryNames: Set<String> = emptySet(),
    val imagePath: String? = null,
    val sourceUrl: String? = null,
    val source: RecipeSource = RecipeSource.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
    val ingredients: List<IngredientForm> = emptyList(),
    val steps: List<StepForm> = emptyList(),
) {
    val servingsValue: Int? get() = servings.toIntOrNull()?.takeIf { it in 1..50 }
    val minutesValue: Int? get() = minutes.toIntOrNull()?.takeIf { it in 1..5000 }
    val servingsError: Boolean get() = servings.isNotBlank() && servingsValue == null
    val minutesError: Boolean get() = minutes.isNotBlank() && minutesValue == null

    val isValid: Boolean
        get() = name.isNotBlank() && servingsValue != null && !minutesError && ingredients.all(IngredientForm::isValid)

    /** Aktueller Stand als Rezept (für die Live-Nährwertübersicht und zum Speichern). */
    fun toRecipe(extraCategoryIds: Set<Long> = emptySet()) = Recipe(
        id = id,
        name = name.trim(),
        description = description.trim(),
        servings = servingsValue ?: 1,
        totalMinutes = minutesValue,
        effort = effort,
        imagePath = imagePath,
        sourceUrl = sourceUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" },
        source = source,
        categoryIds = categoryIds + extraCategoryIds,
        ingredients = ingredients.filter { it.food.name.isNotBlank() }.map(IngredientForm::toDomain),
        steps = steps.map { it.text.trim() }.filter(String::isNotEmpty),
        createdAt = createdAt,
    )
}
