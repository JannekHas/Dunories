package de.kalorienapp.domain

import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType

/**
 * Reihenfolge der Einträge innerhalb einer Mahlzeit. Ohne eigene Sortierung zählt die Erfassungszeit
 * ([FoodEntry.orderKey]). Beim Verschieben werden nur die vorhandenen Schlüssel der Ziel-Mahlzeit neu
 * verteilt – sie bleiben dadurch in der Vergangenheit, und später erfasste Einträge landen weiter unten.
 */
object EntryOrder {

    /**
     * [moved] in die Mahlzeit [meal] an Position [index] von [target] setzen ([target] = aktuelle Einträge dieser
     * Mahlzeit in Anzeige-Reihenfolge, ohne [moved]). Liefert alle Einträge der Mahlzeit mit neuen Schlüsseln.
     */
    fun place(target: List<FoodEntry>, moved: FoodEntry, meal: MealType, index: Int): List<FoodEntry> {
        val order = target.toMutableList().apply { add(index.coerceIn(0, size), moved.copy(mealType = meal)) }
        // Vorhandene Schlüssel aufsteigend und eindeutig neu verteilen.
        val keys = (target + moved).map(FoodEntry::orderKey).sorted().toMutableList()
        for (i in 1 until keys.size) if (keys[i] <= keys[i - 1]) keys[i] = keys[i - 1] + 1
        return order.mapIndexed { i, entry -> entry.copy(sortKey = keys[i]) }
    }
}
