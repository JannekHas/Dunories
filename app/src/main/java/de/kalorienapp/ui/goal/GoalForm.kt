package de.kalorienapp.ui.goal

import de.kalorienapp.data.repository.GoalState
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.GoalPlanner
import de.kalorienapp.domain.GoalRecommendation
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.domain.model.WeightGoal
import de.kalorienapp.ui.components.parseDecimal
import java.time.LocalDate

/** Eingabe eines neuen Gewichtsziels: Wunschgewicht + Zieldatum (bei „Halten“ ohne Datum). */
data class GoalFormState(
    val targetWeight: String = "",
    val targetDate: LocalDate? = null,
) {
    val targetValue: Double? get() = targetWeight.parseDecimal()?.takeIf { it in 30.0..300.0 }
    val targetError: Boolean get() = targetWeight.isNotBlank() && targetValue == null

    /** Ziel ab heute; null solange die Eingabe unvollständig ist. */
    fun toGoal(currentWeightKg: Double, today: LocalDate = LocalDate.now()): WeightGoal? {
        val target = targetValue ?: return null
        val goal = WeightGoal(
            startDate = today,
            startWeightKg = currentWeightKg,
            targetWeightKg = target,
            targetDate = targetDate,
        )
        if (goal.direction == Goal.MAINTAIN) return goal.copy(targetDate = null)
        val date = targetDate ?: return null
        return goal.takeIf { date.isAfter(today) }
    }

    fun isMaintain(currentWeightKg: Double?): Boolean {
        val target = targetValue ?: return false
        currentWeightKg ?: return false
        return kotlin.math.abs(target - currentWeightKg) <= WeightGoal.MAINTAIN_TOLERANCE_KG
    }

    /** Empfohlenes Zieldatum für das eingegebene Wunschgewicht (null bei „Halten“ oder unvollständig). */
    fun recommendation(profile: Profile?, today: LocalDate = LocalDate.now()): GoalRecommendation? {
        profile ?: return null
        val target = targetValue ?: return null
        return GoalPlanner.recommendation(profile.weightKg, target, GoalCalculator.maintenanceKcal(profile, today.year), today)
    }

    /** Vorschau: Tagesziel, das sich mit diesem Ziel heute ergäbe. */
    fun preview(profile: Profile?): GoalState? {
        profile ?: return null
        val goal = toGoal(profile.weightKg) ?: return null
        return GoalState.of(profile, goal, LocalDate.now())
    }
}
