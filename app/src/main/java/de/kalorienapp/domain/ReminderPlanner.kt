package de.kalorienapp.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Wann an die Waage erinnert wird: kurz vor der üblichen ersten Mahlzeit, wenn die letzte Messung alt genug ist. */
object ReminderPlanner {

    val DEFAULT_TIME: LocalTime = LocalTime.of(7, 30)
    private val EARLIEST: LocalTime = LocalTime.of(5, 0)
    private val LATEST: LocalTime = LocalTime.of(11, 0)
    private const val LEAD_MINUTES = 30L
    private const val MIN_DAYS = 3

    /** Übliche Uhrzeit des ersten Eintrags (Median); null bei zu wenig Daten. */
    fun typicalFirstEntryTime(firstEntryTimes: List<LocalTime>): LocalTime? {
        if (firstEntryTimes.size < MIN_DAYS) return null
        val sorted = firstEntryTimes.map { it.toSecondOfDay() }.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        return LocalTime.ofSecondOfDay(median.toLong())
    }

    /** Erinnerungszeit: 30 min vor dem üblichen ersten Eintrag, zwischen 5 und 11 Uhr. */
    fun reminderTime(typicalFirstEntry: LocalTime?): LocalTime {
        val time = typicalFirstEntry?.minusMinutes(LEAD_MINUTES) ?: return DEFAULT_TIME
        return when {
            time.isBefore(EARLIEST) -> EARLIEST
            time.isAfter(LATEST) -> LATEST
            else -> time
        }
    }

    /** Fällig, wenn noch nie gewogen oder die letzte Messung mindestens [intervalDays] Tage alt ist. */
    fun isWeighInDue(lastMeasured: LocalDate?, today: LocalDate, intervalDays: Int): Boolean =
        lastMeasured == null || ChronoUnit.DAYS.between(lastMeasured, today) >= intervalDays
}
