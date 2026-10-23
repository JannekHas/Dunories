package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.RecipeIngredient
import de.kalorienapp.domain.model.ShoppingItem

/** Einkaufsliste: Rezeptzutaten übernehmen und gleiche Artikel zusammenrechnen. Reine Logik. */
object ShoppingList {

    /**
     * Zutat → Artikel für [factor] (Portionsumrechnung). Ohne Rezeptmenge wird – falls vorhanden –
     * das Gewicht in g/ml genommen; „nach Geschmack“ bleibt ohne Menge.
     */
    fun fromIngredient(ingredient: RecipeIngredient, factor: Double, recipeName: String): ShoppingItem {
        val (quantity, unit) = when {
            ingredient.quantity != null -> ingredient.quantity * factor to ingredient.quantityUnit.trim()
            ingredient.amount != null -> ingredient.amount * factor to if (ingredient.unit == AmountUnit.MILLILITER) "ml" else "g"
            else -> null to ingredient.quantityUnit.trim()
        }
        return ShoppingItem(name = ingredient.name.trim(), quantity = quantity, unit = unit, sources = listOf(recipeName))
    }

    /**
     * Neue Artikel in die Liste einsortieren: Gibt es denselben Artikel (Name + Einheit, ohne Groß-/Kleinschreibung)
     * noch nicht abgehakt, werden die Mengen addiert; sonst kommt er neu dazu.
     * @return geänderte bestehende Artikel und neu anzulegende Artikel
     */
    fun merge(existing: List<ShoppingItem>, additions: List<ShoppingItem>): Merge {
        val updated = LinkedHashMap<Long, ShoppingItem>()
        val inserted = mutableListOf<ShoppingItem>()
        additions.forEach { add ->
            val key = keyOf(add)
            val target = (updated.values + existing.filter { it.id !in updated })
                .firstOrNull { !it.checked && keyOf(it) == key && canAdd(it, add) }
            val insertTarget = inserted.indexOfFirst { keyOf(it) == key && canAdd(it, add) }
            when {
                target != null -> updated[target.id] = combine(target, add)
                insertTarget >= 0 -> inserted[insertTarget] = combine(inserted[insertTarget], add)
                else -> inserted += add
            }
        }
        return Merge(updated.values.toList(), inserted)
    }

    data class Merge(val updated: List<ShoppingItem>, val inserted: List<ShoppingItem>)

    /** Text zum Teilen (z. B. per WhatsApp): nur offene Artikel. */
    fun shareText(items: List<ShoppingItem>, title: String, format: (ShoppingItem) -> String): String =
        buildString {
            appendLine(title)
            items.filter { !it.checked }.forEach { appendLine("• " + format(it)) }
        }.trimEnd()

    private fun keyOf(item: ShoppingItem) = item.name.trim().lowercase() to item.unit.trim().lowercase()

    /** Mengen lassen sich nur addieren, wenn beide eine haben (oder beide keine). */
    private fun canAdd(a: ShoppingItem, b: ShoppingItem) = (a.quantity == null) == (b.quantity == null)

    private fun combine(a: ShoppingItem, b: ShoppingItem) = a.copy(
        quantity = if (a.quantity != null && b.quantity != null) a.quantity + b.quantity else a.quantity,
        sources = (a.sources + b.sources).distinct(),
    )
}
