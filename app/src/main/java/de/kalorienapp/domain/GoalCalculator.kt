package de.kalorienapp.domain

import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.domain.model.Sex
import kotlin.math.roundToInt

object GoalCalculator {

    const val FAT_KCAL_SHARE = 0.25
    const val KCAL_PER_G_PROTEIN = 4
    const val KCAL_PER_G_CARBS = 4
    const val KCAL_PER_G_FAT = 9
    const val MIN_KCAL_TARGET = 1200

    /** Grundumsatz nach Mifflin-St-Jeor. */
    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return when (sex) {
            Sex.MALE -> base + 5
            Sex.FEMALE -> base - 161
        }
    }

    fun defaultProteinPerKg(direction: Goal): Double = when (direction) {
        Goal.MAINTAIN -> 1.6
        Goal.LOSE, Goal.GAIN -> 1.8
    }

    /**
     * Basis des Tagesziels ist allein der Grundumsatz – so, als würde man sich nicht bewegen.
     * Bewegung trägt der Nutzer selbst als Aktivität ein (siehe [daySummary]).
     */
    fun maintenanceKcal(profile: Profile, currentYear: Int): Double =
        bmr(profile.sex, profile.weightKg, profile.heightCm, profile.ageIn(currentYear))

    /** Grundumsatz + Anpassung aus dem Gewichtsziel (siehe [GoalPlanner]), mindestens [MIN_KCAL_TARGET]. */
    fun calculatedKcalTarget(profile: Profile, currentYear: Int, adjustmentKcal: Int): Int =
        (maintenanceKcal(profile, currentYear) + adjustmentKcal)
            .roundToInt()
            .coerceAtLeast(MIN_KCAL_TARGET)

    /**
     * Tagesziele; manuelle Overrides haben Vorrang vor den berechneten Werten.
     * [direction] und [adjustmentKcal] kommen aus dem aktiven Gewichtsziel (ohne Ziel: Halten, 0).
     * Protein bezieht sich beim Abnehmen auf das Wunschgewicht ([targetWeightKg]) – sonst bekäme man bei
     * viel Übergewicht so viel Protein, dass für Kohlenhydrate kaum etwas bleibt.
     */
    fun targets(
        profile: Profile,
        currentYear: Int,
        direction: Goal = Goal.MAINTAIN,
        adjustmentKcal: Int = 0,
        targetWeightKg: Double? = null,
    ): DailyTargets {
        val kcal = profile.kcalTargetOverride ?: calculatedKcalTarget(profile, currentYear, adjustmentKcal)
        val proteinPerKg = profile.proteinPerKg ?: defaultProteinPerKg(direction)
        val proteinWeight = proteinReferenceWeight(profile.weightKg, direction, targetWeightKg)
        val protein = profile.proteinOverrideG ?: (proteinPerKg * proteinWeight).roundToInt()
        val fat = profile.fatOverrideG ?: (kcal * FAT_KCAL_SHARE / KCAL_PER_G_FAT).roundToInt()
        val carbs = profile.carbsOverrideG
            ?: ((kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT).toDouble() / KCAL_PER_G_CARBS)
                .roundToInt()
                .coerceAtLeast(0)
        return DailyTargets(kcal = kcal, proteinG = protein, carbsG = carbs, fatG = fat)
    }

    /** Gewicht, auf das sich „Protein pro kg“ bezieht: beim Abnehmen das (niedrigere) Wunschgewicht. */
    fun proteinReferenceWeight(weightKg: Double, direction: Goal, targetWeightKg: Double?): Double =
        if (direction == Goal.LOSE && targetWeightKg != null) minOf(weightKg, targetWeightKg) else weightKg

    /** Verbleibend = Ziel + selbst eingetragene Aktivitäten − Gegessenes. */
    fun daySummary(targets: DailyTargets, eaten: Nutrients, activityKcal: Int = 0): DaySummary = DaySummary(
        targets = targets,
        eaten = eaten,
        activityKcal = activityKcal,
        remainingKcal = (targets.kcal + activityKcal - eaten.kcal).roundToInt(),
    )
}
