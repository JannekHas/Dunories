package de.kalorienapp.domain.model

enum class Sex { MALE, FEMALE }

enum class Goal { LOSE, MAINTAIN, GAIN }

enum class Pace { SLOW, NORMAL }

/**
 * Nutzerprofil. `null`-Werte bei Protein/kg und den Overrides bedeuten „automatisch berechnen“.
 */
data class Profile(
    val sex: Sex,
    val birthYear: Int,
    val heightCm: Double,
    val weightKg: Double,
    val goal: Goal,
    val pace: Pace,
    val proteinPerKg: Double? = null,
    val kcalTargetOverride: Int? = null,
    val proteinOverrideG: Int? = null,
    val fatOverrideG: Int? = null,
    val carbsOverrideG: Int? = null,
) {
    fun ageIn(year: Int): Int = year - birthYear
}
