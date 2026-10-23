package de.kalorienapp.domain.model

/**
 * Eigene Mahlzeiten-Kombination (z. B. „Mein Frühstück“: Skyr + Haferflocken + Banane),
 * mit einem Tipp als Vorschlag eintragbar. Teile wie Favoriten: Werte pro 100 g/ml + übliche Menge.
 */
data class MealCombo(
    val id: Long = 0,
    val name: String,
    val items: List<FavoriteFood>,
) {
    val totalKcal: Double get() = items.sumOf { it.per100.per100ToAmount(it.amount).kcal }
}
