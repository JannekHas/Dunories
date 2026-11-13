package de.kalorienapp.domain.model

/** Ernährungsweise – genau eine Auswahl. */
enum class DietStyle { ALL, FLEXITARIAN, PESCETARIAN, VEGETARIAN, VEGAN }

/** „Ich esse kein …“ – nur sinnvoll, solange die Ernährungsweise das Lebensmittel überhaupt erlaubt. */
enum class ExcludedFood { PORK, BEEF, POULTRY, FISH, SEAFOOD }

/** Unverträglichkeiten ([allergen] = false) und Allergien (EU-Hauptallergene, [allergen] = true). */
enum class Intolerance(val allergen: Boolean) {
    LACTOSE(false), GLUTEN(false), FRUCTOSE(false), HISTAMINE(false),
    NUTS(true), PEANUTS(true), EGG(true), SOY(true), FISH(true), SHELLFISH(true), CELERY(true), MUSTARD(true), SESAME(true),
}

enum class CookingTime { LITTLE, NORMAL, LIKES }

enum class Budget { SMALL, NORMAL }

/**
 * Was die KI bei Vorschlägen berücksichtigen soll (feste Antworten statt freiem Chat, in Ebenen):
 * Ernährungsweise, Allergien & Unverträglichkeiten, Alltag und Lebensmittel, die man nicht mag.
 */
data class FoodPreferences(
    val style: DietStyle = DietStyle.ALL,
    val excluded: Set<ExcludedFood> = emptySet(),
    val intolerances: Set<Intolerance> = emptySet(),
    val cooking: CookingTime = CookingTime.NORMAL,
    val budget: Budget = Budget.NORMAL,
    val mealPrep: Boolean = false,
    val dislikes: List<String> = emptyList(),
) {
    /** Nur die Ausschlüsse, die zur Ernährungsweise passen (vegetarisch schließt Fleisch ohnehin aus). */
    val relevantExcluded: Set<ExcludedFood>
        get() = excluded.filterTo(mutableSetOf()) { it in allowedExclusions(style) }

    val isEmpty: Boolean
        get() = style == DietStyle.ALL && relevantExcluded.isEmpty() && intolerances.isEmpty() &&
            cooking == CookingTime.NORMAL && budget == Budget.NORMAL && !mealPrep && dislikes.isEmpty()

    fun dislikes(name: String): Boolean = dislikes.any { it.equals(name.trim(), ignoreCase = true) }

    companion object {
        /** Welche „Ich esse kein …“-Punkte bei einer Ernährungsweise noch zur Wahl stehen. */
        fun allowedExclusions(style: DietStyle): List<ExcludedFood> = when (style) {
            DietStyle.ALL, DietStyle.FLEXITARIAN -> ExcludedFood.entries
            DietStyle.PESCETARIAN -> listOf(ExcludedFood.FISH, ExcludedFood.SEAFOOD)
            DietStyle.VEGETARIAN, DietStyle.VEGAN -> emptyList()
        }
    }
}
