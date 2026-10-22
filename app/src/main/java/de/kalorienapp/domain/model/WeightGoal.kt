package de.kalorienapp.domain.model

import java.time.LocalDate

/** Warum ein Ziel beendet wurde. */
enum class GoalEndReason {
    /** Wunschgewicht erreicht (ggf. vor dem Zieldatum). */
    REACHED,

    /** Zieldatum erreicht. */
    DATE_PASSED,

    /** Durch ein neues Ziel ersetzt. */
    REPLACED,
}

/**
 * Gewichtsziel: von [startWeightKg] am [startDate] zu [targetWeightKg] bis [targetDate].
 * „Halten“ (Wunschgewicht = Startgewicht) hat kein Datum. Beendete Ziele bleiben als Historie erhalten.
 */
data class WeightGoal(
    val id: Long = 0,
    val startDate: LocalDate,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val targetDate: LocalDate?,
    val endedDate: LocalDate? = null,
    val endWeightKg: Double? = null,
    val endReason: GoalEndReason? = null,
) {
    val isActive: Boolean get() = endedDate == null

    val direction: Goal
        get() = when {
            targetWeightKg < startWeightKg - MAINTAIN_TOLERANCE_KG -> Goal.LOSE
            targetWeightKg > startWeightKg + MAINTAIN_TOLERANCE_KG -> Goal.GAIN
            else -> Goal.MAINTAIN
        }

    companion object {
        /** Unterschied, ab dem nicht mehr „Halten“ gilt. */
        const val MAINTAIN_TOLERANCE_KG = 0.1
    }
}

/** Tagesplan für ein aktives Ziel. */
data class GoalPlan(
    /** Kalorien pro Tag zusätzlich (+) bzw. weniger (−) als der Grundumsatz. */
    val dailyAdjustmentKcal: Int,
    /** Geplantes Tempo in kg pro Woche (negativ = Abnahme). */
    val kgPerWeek: Double,
    /** true, wenn das gewünschte Tempo auf das gesunde Maximum begrenzt wurde. */
    val limited: Boolean,
    /** Frühestes realistisch erreichbares Datum mit Maximaltempo (nur bei Begrenzung). */
    val realisticDate: LocalDate?,
    val remainingDays: Long?,
)
