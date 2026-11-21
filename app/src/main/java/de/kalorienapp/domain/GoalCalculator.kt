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
     * Bedarf an einem Tag ohne Sport: Grundumsatz × Alltagsfaktor ([Profile.lifestyle], DGE-PAL).
     * Sport trägt der Nutzer selbst als Aktivität ein (siehe [daySummary]).
     */
    fun maintenanceKcal(profile: Profile, currentYear: Int): Double =
        bmr(profile.sex, profile.weightKg, profile.heightCm, profile.ageIn(currentYear)) * profile.lifestyle.pal

    /** Bedarf ohne Sport + Anpassung aus dem Gewichtsziel (siehe [GoalPlanner]), mindestens [MIN_KCAL_TARGET]. */
    fun calculatedKcalTarget(profile: Profile, currentYear: Int, adjustmentKcal: Int): Int =
        (maintenanceKcal(profile, currentYear) + adjustmentKcal)
            .roundToInt()
            .coerceAtLeast(MIN_KCAL_TARGET)

    /**
     * Tagesziele; manuelle Overrides haben Vorrang vor den berechneten Werten.
     * [direction] und [adjustmentKcal] kommen aus dem aktiven Gewichtsziel (ohne Ziel: Halten, 0).
     * Protein bezieht sich auf [proteinReferenceWeight] – sonst bekäme man bei viel Übergewicht so viel
     * Protein, dass für Kohlenhydrate kaum etwas bleibt.
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
        val proteinWeight = proteinReferenceWeight(profile, direction, targetWeightKg)
        val protein = profile.proteinOverrideG ?: (proteinPerKg * proteinWeight).roundToInt()
        val fat = profile.fatOverrideG ?: (kcal * FAT_KCAL_SHARE / KCAL_PER_G_FAT).roundToInt()
        val carbs = profile.carbsOverrideG
            ?: ((kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT).toDouble() / KCAL_PER_G_CARBS)
                .roundToInt()
                .coerceAtLeast(0)
        return DailyTargets(kcal = kcal, proteinG = protein, carbsG = carbs, fatG = fat)
    }

    /** Obergrenze „gesundes Gewicht“ ohne Körperfett-Messung: Gewicht bei BMI 25. */
    const val HEALTHY_BMI = 25.0

    /**
     * Gewicht, auf das sich „Protein pro kg“ bezieht (Nov. 2026, Wunsch des Nutzers: g/kg gilt bei Übergewicht
     * nicht fürs volle Körpergewicht – eigentlich zählt die fettfreie Masse). Das Kleinste aus:
     * - aktuellem Gewicht,
     * - „gesundem Gewicht“: mit Körperfett-Messung fettfreie Masse bei normalem Körperfett ([normalBodyFat]),
     *   sonst Gewicht bei BMI 25 (dann ist z. B. ein sehr muskulöser Mensch etwas zu niedrig – er kann
     *   Körperfett eintragen oder Protein pro kg selbst erhöhen),
     * - beim Abnehmen dem Wunschgewicht.
     */
    fun proteinReferenceWeight(profile: Profile, direction: Goal, targetWeightKg: Double?): Double {
        val weight = profile.weightKg
        val bodyFat = profile.bodyFatPercent?.takeIf { it in 3.0..70.0 }
        val healthy = if (bodyFat != null) {
            weight * (1 - bodyFat / 100) / (1 - normalBodyFat(profile.sex))
        } else {
            val heightM = profile.heightCm / 100
            HEALTHY_BMI * heightM * heightM
        }
        var reference = minOf(weight, healthy)
        if (direction == Goal.LOSE && targetWeightKg != null) reference = minOf(reference, targetWeightKg)
        return reference
    }

    /** Normaler Körperfettanteil (als Anteil) für die Umrechnung der fettfreien Masse. */
    fun normalBodyFat(sex: Sex): Double = when (sex) {
        Sex.MALE -> 0.15
        Sex.FEMALE -> 0.25
    }

    /** Verbleibend = Ziel + selbst eingetragene Aktivitäten − Gegessenes. */
    fun daySummary(targets: DailyTargets, eaten: Nutrients, activityKcal: Int = 0): DaySummary = DaySummary(
        targets = targets,
        eaten = eaten,
        activityKcal = activityKcal,
        remainingKcal = (targets.kcal + activityKcal - eaten.kcal).roundToInt(),
    )
}
