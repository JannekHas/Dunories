package de.kalorienapp.domain

import de.kalorienapp.domain.model.Sex
import kotlin.math.roundToInt

/** Woher der Tageswert der aktiven Kalorien stammt. */
enum class ActivitySource {
    /** Direkter Wert „aktive Kalorien“ aus Health Connect. */
    ACTIVE_CALORIES,

    /** „Gesamt verbrannt“ minus anteiliger Grundumsatz. */
    TOTAL_CALORIES,

    /** Aus der Schrittzahl geschätzt. */
    STEPS,

    /** Vom Nutzer eingetragen – wird nie automatisch überschrieben. */
    MANUAL,
}

data class ActivityEstimate(val activeKcal: Int, val source: ActivitySource)

/**
 * Bestimmt die aktiven Kalorien eines Tages aus den Health-Connect-Rohwerten.
 *
 * Reihenfolge: echte „aktive Kalorien“ → „gesamt − Grundumsatz“ → Schätzung aus Schritten.
 * Samsung Health schreibt in der Praxis oft nur Schritte nach Health Connect.
 */
object ActiveCalories {

    /** Netto-Energie beim Gehen ≈ 0,5 kcal pro kg Körpergewicht und km (ohne Grundumsatz). */
    const val WALKING_KCAL_PER_KG_PER_KM = 0.5

    fun resolve(
        activeKcal: Double?,
        totalKcal: Double?,
        steps: Long?,
        bmrPerDay: Double,
        elapsedFractionOfDay: Double,
        heightCm: Double,
        weightKg: Double,
        sex: Sex,
    ): ActivityEstimate? {
        if (activeKcal != null && activeKcal > 0) {
            return ActivityEstimate(activeKcal.roundToInt(), ActivitySource.ACTIVE_CALORIES)
        }
        if (totalKcal != null && totalKcal > 0) {
            val basal = bmrPerDay * elapsedFractionOfDay.coerceIn(0.0, 1.0)
            val active = (totalKcal - basal).coerceAtLeast(0.0).roundToInt()
            if (active > 0) return ActivityEstimate(active, ActivitySource.TOTAL_CALORIES)
        }
        if (steps != null && steps > 0) {
            return ActivityEstimate(fromSteps(steps, heightCm, weightKg, sex), ActivitySource.STEPS)
        }
        // Explizit 0 gemeldet → 0; gar keine Daten → null.
        return if (activeKcal != null || totalKcal != null || steps != null) {
            ActivityEstimate(0, ActivitySource.ACTIVE_CALORIES)
        } else {
            null
        }
    }

    /** Schrittlänge ≈ 41,5 % (Männer) bzw. 41,3 % (Frauen) der Körpergröße. */
    fun stepLengthM(heightCm: Double, sex: Sex): Double =
        heightCm / 100 * if (sex == Sex.MALE) 0.415 else 0.413

    /** Aktive Kalorien aus Schritten: Strecke (km) × Gewicht × 0,5 kcal/(kg·km). */
    fun fromSteps(steps: Long, heightCm: Double, weightKg: Double, sex: Sex): Int {
        val distanceKm = steps * stepLengthM(heightCm, sex) / 1000
        return (distanceKm * weightKg * WALKING_KCAL_PER_KG_PER_KM).roundToInt()
    }
}
