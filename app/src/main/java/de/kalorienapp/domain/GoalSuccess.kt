package de.kalorienapp.domain

import de.kalorienapp.domain.model.WeightGoal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Zahlen für den Ziel-Moment (Feier-Bildschirm und teilbares Bild): Veränderung, Dauer, Anteil der
 * Tage im Budget und der Gewichtsverlauf während des Ziels.
 */
data class GoalSuccess(
    val goal: WeightGoal,
    val endWeightKg: Double,
    /** Negativ = abgenommen. */
    val changeKg: Double,
    val days: Int,
    /** Anteil der erfassten Tage im Budget (0..1); null ohne Einträge. */
    val onTargetRate: Double?,
    /** Tagesmittel der Messungen im Zielzeitraum, mit Start- und Endgewicht, nach Datum sortiert. */
    val weightPoints: List<Pair<LocalDate, Double>>,
) {
    /** Bei „Datum erreicht“: wie viel vom geplanten Weg geschafft wurde (0..1). */
    val progress: Double
        get() {
            val planned = goal.targetWeightKg - goal.startWeightKg
            return if (planned == 0.0) 1.0 else (changeKg / planned).coerceIn(0.0, 1.0)
        }

    companion object {
        fun of(
            goal: WeightGoal,
            measurements: List<Pair<LocalDate, Double>>,
            onTargetRate: Double?,
            today: LocalDate = LocalDate.now(),
        ): GoalSuccess {
            val end = goal.endedDate ?: today
            val endWeight = goal.endWeightKg ?: measurements.lastOrNull()?.second ?: goal.startWeightKg
            val inPeriod = measurements.filter { (date, _) -> !date.isBefore(goal.startDate) && !date.isAfter(end) }
            val daily = (inPeriod + (goal.startDate to goal.startWeightKg) + (end to endWeight))
                .groupBy({ it.first }, { it.second })
                .map { (date, values) -> date to values.average() }
                .sortedBy { it.first }
            // Start- und Endpunkt exakt wie im Ziel gespeichert (nicht mit anderen Messungen des Tages gemittelt).
            val points = daily.map { (date, value) ->
                when (date) {
                    goal.startDate -> date to goal.startWeightKg
                    end -> date to endWeight
                    else -> date to value
                }
            }
            // Ziel am selben Tag beendet: Start und Ende trotzdem als zwei Punkte (sonst kein Verlauf).
            val chartPoints = if (points.size < 2) listOf(goal.startDate to goal.startWeightKg, end to endWeight) else points
            return GoalSuccess(
                goal = goal,
                endWeightKg = endWeight,
                changeKg = endWeight - goal.startWeightKg,
                days = ChronoUnit.DAYS.between(goal.startDate, end).toInt().coerceAtLeast(1),
                onTargetRate = onTargetRate,
                weightPoints = chartPoints,
            )
        }
    }
}
