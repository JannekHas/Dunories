package de.kalorienapp.domain.model

/** Feste Antwortmöglichkeiten zur Ernährungsweise (bewusst kein freier KI-Chat). */
enum class DietTag {
    VEGETARIAN, VEGAN, NO_PORK, NO_FISH, LACTOSE_FREE, GLUTEN_FREE, LITTLE_TIME, LOW_BUDGET,
}

/**
 * Was die KI bei Lebensmittel-Vorschlägen berücksichtigen soll: Ernährungsweise + Lebensmittel,
 * die man nicht mag (per „Mag ich nicht“ an einem Vorschlag gemerkt).
 */
data class FoodPreferences(
    val tags: Set<DietTag> = emptySet(),
    val dislikes: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = tags.isEmpty() && dislikes.isEmpty()

    fun dislikes(name: String): Boolean = dislikes.any { it.equals(name.trim(), ignoreCase = true) }
}
