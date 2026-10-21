package de.kalorienapp.domain

import kotlin.math.roundToInt

/** Ein Training mit den Kalorien aus seinem Zeitraum (null = keine Daten). */
data class Workout(
    val durationMinutes: Double,
    val totalKcal: Double?,
    val activeKcal: Double?,
)

/**
 * Trainingskalorien, die dem Tagesziel gutgeschrieben werden (100 %).
 *
 * Samsung Health meldet „verbrannte Kalorien insgesamt“ – darin steckt der Grundumsatz für die
 * Trainingsdauer, der bereits im Tagesziel enthalten ist. Er wird deshalb abgezogen.
 * Liefert eine Quelle direkt „aktive Kalorien“, werden diese ohne Abzug verwendet.
 */
object TrainingCalories {

    private const val MINUTES_PER_DAY = 24 * 60

    fun activeKcal(workout: Workout, bmrPerDay: Double): Double {
        workout.activeKcal?.takeIf { it > 0 }?.let { return it }
        val total = workout.totalKcal?.takeIf { it > 0 } ?: return 0.0
        val basal = bmrPerDay / MINUTES_PER_DAY * workout.durationMinutes.coerceAtLeast(0.0)
        return (total - basal).coerceAtLeast(0.0)
    }

    fun dayTotal(workouts: List<Workout>, bmrPerDay: Double): Int =
        workouts.sumOf { activeKcal(it, bmrPerDay) }.roundToInt()
}
