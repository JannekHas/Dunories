package de.kalorienapp.domain.model

enum class Sex { MALE, FEMALE }

/**
 * Alltag **ohne Sport** als PAL-Faktor nach DGE (Grundumsatz × Faktor = Bedarf an einem Tag ohne Training).
 * Sport trägt der Nutzer weiterhin selbst als Aktivität ein – so wird nichts doppelt gezählt.
 */
enum class Lifestyle(val pal: Double) {
    SEDENTARY(1.4),
    MIXED(1.6),
    STANDING(1.8),
    HEAVY(2.0),
}

/** Richtung eines Gewichtsziels (siehe [WeightGoal.direction]). */
enum class Goal { LOSE, MAINTAIN, GAIN }

/**
 * Nutzerprofil. `null`-Werte bei Protein/kg und den Overrides bedeuten „automatisch berechnen“.
 */
data class Profile(
    val sex: Sex,
    val birthYear: Int,
    val heightCm: Double,
    val weightKg: Double,
    val proteinPerKg: Double? = null,
    val kcalTargetOverride: Int? = null,
    val proteinOverrideG: Int? = null,
    val fatOverrideG: Int? = null,
    val carbsOverrideG: Int? = null,
    val lifestyle: Lifestyle = Lifestyle.SEDENTARY,
) {
    fun ageIn(year: Int): Int = year - birthYear
}
