package de.kalorienapp.domain

import kotlin.math.roundToInt

/**
 * Bestimmt die aktiven Kalorien eines Tages aus den Health-Connect-Rohwerten.
 *
 * Bevorzugt den direkten Wert „aktive Kalorien“. Manche Quellen (z. B. Samsung Health) liefern nur
 * „gesamt verbrannt“ – dann gilt: aktiv = gesamt − Grundumsatz für den bisher vergangenen Tagesanteil.
 */
object ActiveCalories {

    fun resolve(
        activeKcal: Double?,
        totalKcal: Double?,
        bmrPerDay: Double,
        elapsedFractionOfDay: Double,
    ): Int? {
        if (activeKcal != null && activeKcal > 0) return activeKcal.roundToInt()
        if (totalKcal != null && totalKcal > 0) {
            val basal = bmrPerDay * elapsedFractionOfDay.coerceIn(0.0, 1.0)
            return (totalKcal - basal).coerceAtLeast(0.0).roundToInt()
        }
        // Explizit 0 gemeldet → 0; gar keine Daten → null.
        return if (activeKcal != null || totalKcal != null) 0 else null
    }
}
