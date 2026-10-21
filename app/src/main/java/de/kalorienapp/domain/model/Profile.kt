package de.kalorienapp.domain.model

enum class Sex { MALE, FEMALE }

enum class Goal { LOSE, MAINTAIN, GAIN }

enum class Pace { SLOW, NORMAL }

enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9),
}

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
    val activityLevel: ActivityLevel,
    /** Trainings aus Health Connect (z. B. Samsung Health) übernehmen; Aktivitätslevel dann ohne Sport. */
    val importWorkouts: Boolean = false,
    val proteinPerKg: Double? = null,
    val kcalTargetOverride: Int? = null,
    val proteinOverrideG: Int? = null,
    val fatOverrideG: Int? = null,
    val carbsOverrideG: Int? = null,
) {
    fun ageIn(year: Int): Int = year - birthYear
}
