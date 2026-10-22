package de.kalorienapp.domain.model

import java.time.LocalDate

data class WeightEntry(val date: LocalDate, val weightKg: Double)

/** Zielerreichung eines Tages – bestimmt die Farbe im Kalender. */
enum class DayStatus {
    /** Nichts eingetragen. */
    NO_DATA,

    /** Im Budget (Ziel + Aktivitäten). */
    ON_TARGET,

    /** Bis 10 % über dem Budget. */
    SLIGHTLY_OVER,

    /** Mehr als 10 % über dem Budget. */
    OVER,
}

/** Ein Tag im Kalender bzw. in der Statistik. */
data class DayStats(
    val date: LocalDate,
    val eaten: Nutrients,
    val activityKcal: Int,
    /** Tagesziel + Aktivitäten. */
    val budgetKcal: Int,
    val proteinTargetG: Int,
    val hasEntries: Boolean,
    val status: DayStatus,
)

/** Durchschnitte über einen Zeitraum – nur Tage mit Einträgen zählen. */
data class PeriodStats(
    val days: List<DayStats>,
    val loggedDays: Int,
    val averageEaten: Nutrients?,
    val averageActivityKcal: Int,
    val averageBudgetKcal: Int,
    /** Anteil der erfassten Tage, an denen das Proteinziel erreicht wurde (0..1), null ohne Daten. */
    val proteinHitRate: Double?,
    /** Anteil der erfassten Tage im Budget (0..1), null ohne Daten. */
    val onTargetRate: Double?,
)
