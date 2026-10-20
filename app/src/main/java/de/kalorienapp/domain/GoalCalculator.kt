package de.kalorienapp.domain

import de.kalorienapp.domain.model.ActivityLevel
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Pace
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

    /**
     * Mit Health Connect wird immer „sitzend“ gerechnet – die Bewegung kommt
     * über die aktiven Kalorien dazu und soll nicht doppelt zählen.
     */
    fun activityFactor(profile: Profile): Double =
        if (profile.useHealthConnect) ActivityLevel.SEDENTARY.factor else profile.activityLevel.factor

    fun goalAdjustment(goal: Goal, pace: Pace): Int = when (goal) {
        Goal.LOSE -> if (pace == Pace.SLOW) -300 else -500
        Goal.MAINTAIN -> 0
        Goal.GAIN -> if (pace == Pace.SLOW) 250 else 400
    }

    fun defaultProteinPerKg(goal: Goal): Double = when (goal) {
        Goal.MAINTAIN -> 1.6
        Goal.LOSE, Goal.GAIN -> 1.8
    }

    /** Gesamtumsatz (ohne Ziel-Anpassung). */
    fun maintenanceKcal(profile: Profile, currentYear: Int): Double =
        bmr(profile.sex, profile.weightKg, profile.heightCm, profile.ageIn(currentYear)) *
            activityFactor(profile)

    fun calculatedKcalTarget(profile: Profile, currentYear: Int): Int =
        (maintenanceKcal(profile, currentYear) + goalAdjustment(profile.goal, profile.pace))
            .roundToInt()
            .coerceAtLeast(MIN_KCAL_TARGET)

    /** Tagesziele; manuelle Overrides haben Vorrang vor den berechneten Werten. */
    fun targets(profile: Profile, currentYear: Int): DailyTargets {
        val kcal = profile.kcalTargetOverride ?: calculatedKcalTarget(profile, currentYear)
        val proteinPerKg = profile.proteinPerKg ?: defaultProteinPerKg(profile.goal)
        val protein = profile.proteinOverrideG ?: (proteinPerKg * profile.weightKg).roundToInt()
        val fat = profile.fatOverrideG ?: (kcal * FAT_KCAL_SHARE / KCAL_PER_G_FAT).roundToInt()
        val carbs = profile.carbsOverrideG
            ?: ((kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT).toDouble() / KCAL_PER_G_CARBS)
                .roundToInt()
                .coerceAtLeast(0)
        return DailyTargets(kcal = kcal, proteinG = protein, carbsG = carbs, fatG = fat)
    }

    fun creditedActiveKcal(activeKcal: Int, creditPercent: Int): Int =
        (activeKcal * creditPercent.coerceIn(0, 100) / 100.0).roundToInt()

    /** Verbleibend = Ziel + gutgeschriebene aktive Kalorien − Gegessenes. */
    fun daySummary(
        targets: DailyTargets,
        eaten: Nutrients,
        activeKcal: Int,
        creditPercent: Int,
    ): DaySummary {
        val credited = creditedActiveKcal(activeKcal, creditPercent)
        return DaySummary(
            targets = targets,
            eaten = eaten,
            activeKcal = activeKcal,
            creditedActiveKcal = credited,
            remainingKcal = (targets.kcal + credited - eaten.kcal).roundToInt(),
        )
    }
}
