package de.kalorienapp.domain.model

/** Welches der mitgeschickten Bilder das fertige Gericht zeigt und wo (Box normiert 0–1000, [ymin, xmin, ymax, xmax]). */
data class DishImageHint(val index: Int, val box: List<Int>)

/**
 * KI-Vorschlag für ein Rezept – wird immer im Editor zur Bearbeitung gezeigt, nie ungefragt gespeichert.
 * [categoryNames] sind Vorschläge (vorhandene oder neue Kategorien).
 */
data class RecipeDraft(
    val recipe: Recipe,
    val categoryNames: List<String> = emptyList(),
    val image: DishImageHint? = null,
)
