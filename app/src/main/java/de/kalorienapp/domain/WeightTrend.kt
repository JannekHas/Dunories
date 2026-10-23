package de.kalorienapp.domain

import de.kalorienapp.domain.model.WeightEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** Ein Punkt der geglätteten Gewichtskurve. */
data class TrendPoint(val date: LocalDate, val weightKg: Double)

/**
 * Gewichtstrend als exponentiell geglätteter Mittelwert („Hacker's Diet“): Jeder Tag zieht den Trend um
 * [SMOOTHING] in Richtung der Messung – Wasserschwankungen fallen kaum ins Gewicht. Mehrere Messungen an
 * einem Tag zählen gemittelt; Tage ohne Messung werden übersprungen (Faktor wächst mit der Lücke).
 */
object WeightTrend {
    const val SMOOTHING = 0.1

    fun compute(entries: List<WeightEntry>, zone: ZoneId = ZoneId.systemDefault()): List<TrendPoint> {
        val daily = entries.groupBy { it.measuredAt.atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.map { it.weightKg }.average() }
            .toSortedMap()
        var trend: Double? = null
        var lastDate: LocalDate? = null
        return daily.map { (date, weight) ->
            val previous = trend
            trend = if (previous == null) {
                weight
            } else {
                // Lücke von n Tagen: Gewicht der alten Kurve ist (1 − α)^n.
                val gap = ChronoUnit.DAYS.between(lastDate, date).coerceAtLeast(1).toDouble()
                val keep = (1 - SMOOTHING).pow(gap)
                previous * keep + weight * (1 - keep)
            }
            lastDate = date
            TrendPoint(date, trend!!)
        }
    }

    /** Veränderung des Trends in kg pro Woche über die letzten [days] Tage; null bei zu wenig Daten. */
    fun weeklyChange(points: List<TrendPoint>, days: Long = 14): Double? {
        val last = points.lastOrNull() ?: return null
        val from = points.lastOrNull { !it.date.isAfter(last.date.minusDays(days)) } ?: points.first()
        val span = ChronoUnit.DAYS.between(from.date, last.date)
        if (span < 3) return null
        return (last.weightKg - from.weightKg) / span * 7
    }
}
