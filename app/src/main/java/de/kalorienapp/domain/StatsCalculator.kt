package de.kalorienapp.domain

import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.DayStats
import de.kalorienapp.domain.model.DayStatus
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.PeriodStats
import de.kalorienapp.domain.model.sum
import java.time.LocalDate
import kotlin.math.roundToInt

/** Berechnungen für Kalender und Statistiken (ohne Android-Abhängigkeiten). */
object StatsCalculator {

    /** Bis zu diesem Anteil über dem Budget gilt ein Tag als „knapp drüber“ (gelb). */
    const val SLIGHTLY_OVER_LIMIT = 1.10

    fun status(eatenKcal: Double, budgetKcal: Int, hasEntries: Boolean): DayStatus = when {
        !hasEntries -> DayStatus.NO_DATA
        eatenKcal <= budgetKcal -> DayStatus.ON_TARGET
        eatenKcal <= budgetKcal * SLIGHTLY_OVER_LIMIT -> DayStatus.SLIGHTLY_OVER
        else -> DayStatus.OVER
    }

    /**
     * Baut die Tageswerte für jeden Tag in [from]..[to], auch für Tage ohne Einträge.
     * [targetsFor] liefert die Tagesziele (aus dem aktuellen Profil).
     */
    fun days(
        from: LocalDate,
        to: LocalDate,
        eatenByDay: Map<LocalDate, Nutrients>,
        activityByDay: Map<LocalDate, Int>,
        targetsFor: (LocalDate) -> DailyTargets,
    ): List<DayStats> {
        val result = mutableListOf<DayStats>()
        var date = from
        while (!date.isAfter(to)) {
            val eaten = eatenByDay[date]
            val activity = activityByDay[date] ?: 0
            val targets = targetsFor(date)
            val budget = targets.kcal + activity
            result += DayStats(
                date = date,
                eaten = eaten ?: Nutrients.ZERO,
                activityKcal = activity,
                budgetKcal = budget,
                proteinTargetG = targets.proteinG,
                hasEntries = eaten != null,
                status = status(eaten?.kcal ?: 0.0, budget, eaten != null),
            )
            date = date.plusDays(1)
        }
        return result
    }

    fun period(days: List<DayStats>): PeriodStats {
        val logged = days.filter { it.hasEntries }
        if (logged.isEmpty()) {
            return PeriodStats(days, 0, null, 0, 0, null, null)
        }
        val n = logged.size.toDouble()
        val total = logged.map { it.eaten }.sum()
        return PeriodStats(
            days = days,
            loggedDays = logged.size,
            averageEaten = Nutrients(
                kcal = total.kcal / n,
                proteinG = total.proteinG / n,
                carbsG = total.carbsG / n,
                fatG = total.fatG / n,
                // Zusatzwerte: Durchschnitt nur über Tage, an denen sie angegeben sind.
                saturatedFatG = logged.averageOf { it.eaten.saturatedFatG },
                sugarG = logged.averageOf { it.eaten.sugarG },
                fiberG = logged.averageOf { it.eaten.fiberG },
                saltG = logged.averageOf { it.eaten.saltG },
            ),
            averageActivityKcal = (logged.sumOf { it.activityKcal } / n).roundToInt(),
            averageBudgetKcal = (logged.sumOf { it.budgetKcal } / n).roundToInt(),
            proteinHitRate = logged.count { it.eaten.proteinG >= it.proteinTargetG } / n,
            onTargetRate = logged.count { it.status == DayStatus.ON_TARGET } / n,
        )
    }

    private fun List<DayStats>.averageOf(value: (DayStats) -> Double?): Double? {
        val values = mapNotNull(value)
        return if (values.isEmpty()) null else values.average()
    }
}
