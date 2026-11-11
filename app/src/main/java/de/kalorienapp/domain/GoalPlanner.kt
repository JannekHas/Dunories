package de.kalorienapp.domain

import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.GoalEndReason
import de.kalorienapp.domain.model.GoalPlan
import de.kalorienapp.domain.model.WeightGoal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Plant die tägliche Kalorienanpassung für ein Gewichtsziel.
 *
 * Wird täglich neu aus aktuellem Gewicht und verbleibenden Tagen berechnet. Liegt man hinter dem Plan,
 * steigt die Anpassung; liegt man vorn, bleibt das ursprünglich geplante Tempo (nie langsamer werden).
 * Das Tempo ist begrenzt auf [MAX_LOSS_KG_PER_WEEK] Abnahme bzw. [MAX_GAIN_KG_PER_WEEK] Zunahme.
 */
/** Empfohlenes Zieldatum und das Tempo dahinter. */
data class GoalRecommendation(val date: LocalDate, val kgPerWeek: Double)

object GoalPlanner {

    /** Faustwert: Energie in 1 kg Körpergewicht. */
    const val KCAL_PER_KG = 7700.0
    const val MAX_LOSS_KG_PER_WEEK = 1.0
    const val MAX_GAIN_KG_PER_WEEK = 0.5

    private val maxDeficitPerDay = MAX_LOSS_KG_PER_WEEK * KCAL_PER_KG / 7 // ≈ 1100
    private val maxSurplusPerDay = MAX_GAIN_KG_PER_WEEK * KCAL_PER_KG / 7 // ≈ 550

    fun plan(goal: WeightGoal, currentWeightKg: Double, today: LocalDate): GoalPlan {
        val targetDate = goal.targetDate
        val diffKg = goal.targetWeightKg - currentWeightKg
        if (goal.direction == Goal.MAINTAIN || targetDate == null || isReached(goal, currentWeightKg)) {
            return GoalPlan(0, 0.0, limited = false, realisticDate = null, remainingDays = null)
        }
        val remainingDays = ChronoUnit.DAYS.between(today, targetDate).coerceAtLeast(1)
        val needed = diffKg * KCAL_PER_KG / remainingDays
        // Ursprünglich geplantes Tempo (Start → Ziel über die ganze Laufzeit) ist die Untergrenze.
        val totalDays = ChronoUnit.DAYS.between(goal.startDate, targetDate).coerceAtLeast(1)
        val planned = (goal.targetWeightKg - goal.startWeightKg) * KCAL_PER_KG / totalDays
        val wanted = if (goal.direction == Goal.LOSE) minOf(needed, planned) else maxOf(needed, planned)
        val limited = wanted < -maxDeficitPerDay || wanted > maxSurplusPerDay
        val adjustment = wanted.coerceIn(-maxDeficitPerDay, maxSurplusPerDay)
        return GoalPlan(
            dailyAdjustmentKcal = adjustment.roundToInt(),
            kgPerWeek = adjustment * 7 / KCAL_PER_KG,
            limited = limited,
            realisticDate = if (limited) realisticDate(currentWeightKg, goal.targetWeightKg, today) else null,
            remainingDays = remainingDays,
        )
    }

    /** Frühestes Datum, an dem das Wunschgewicht mit dem Maximaltempo erreichbar ist. */
    fun realisticDate(currentWeightKg: Double, targetWeightKg: Double, today: LocalDate): LocalDate {
        val diff = targetWeightKg - currentWeightKg
        val perWeek = if (diff < 0) MAX_LOSS_KG_PER_WEEK else MAX_GAIN_KG_PER_WEEK
        val weeks = abs(diff) / perWeek
        return today.plusDays(ceil(weeks * 7).toLong())
    }

    const val RECOMMENDED_LOSS_KG_PER_WEEK = 0.5
    const val RECOMMENDED_GAIN_KG_PER_WEEK = 0.25
    private const val MIN_RECOMMENDED_LOSS_KG_PER_WEEK = 0.25

    /** Abstand zur Untergrenze, den die Empfehlung mindestens lässt. */
    private const val FLOOR_MARGIN_KCAL = 100

    /**
     * Empfohlenes Zieldatum für ein Wunschgewicht: abnehmen mit 0,5 kg/Woche – langsamer, falls das Tagesziel
     * sonst unter die Untergrenze (+ Puffer) fiele, aber nicht unter 0,25 kg/Woche; zunehmen mit 0,25 kg/Woche.
     * null bei „Halten“.
     */
    fun recommendation(
        currentWeightKg: Double,
        targetWeightKg: Double,
        maintenanceKcal: Double,
        today: LocalDate,
    ): GoalRecommendation? {
        val diff = targetWeightKg - currentWeightKg
        if (abs(diff) <= WeightGoal.MAINTAIN_TOLERANCE_KG) return null
        val kgPerWeek = if (diff < 0) {
            val byFloor = (maintenanceKcal - GoalCalculator.MIN_KCAL_TARGET - FLOOR_MARGIN_KCAL) * 7 / KCAL_PER_KG
            byFloor.coerceIn(MIN_RECOMMENDED_LOSS_KG_PER_WEEK, RECOMMENDED_LOSS_KG_PER_WEEK)
        } else {
            RECOMMENDED_GAIN_KG_PER_WEEK
        }
        val days = ceil(abs(diff) / kgPerWeek * 7).toLong().coerceAtLeast(1)
        return GoalRecommendation(today.plusDays(days), kgPerWeek)
    }

    /** Wunschgewicht erreicht? (Abnehmen: ≤ Ziel, Zunehmen: ≥ Ziel; Halten endet nie von selbst.) */
    fun isReached(goal: WeightGoal, currentWeightKg: Double): Boolean = when (goal.direction) {
        Goal.LOSE -> currentWeightKg <= goal.targetWeightKg
        Goal.GAIN -> currentWeightKg >= goal.targetWeightKg
        Goal.MAINTAIN -> false
    }

    /** Ist ein aktives Ziel beendet – und warum? null = läuft weiter. */
    fun endReason(goal: WeightGoal, currentWeightKg: Double, today: LocalDate): GoalEndReason? = when {
        !goal.isActive -> null
        isReached(goal, currentWeightKg) -> GoalEndReason.REACHED
        goal.targetDate != null && !today.isBefore(goal.targetDate) -> GoalEndReason.DATE_PASSED
        else -> null
    }
}
