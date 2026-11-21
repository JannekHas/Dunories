package de.kalorienapp.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Zeitplan der automatischen KI-Wochenauswertung: montags [TIME] für Mo–So der Vorwoche.
 * Fiel der Montag aus (Handy aus, Akku-Sparen, App-Start hat den Termin verdrängt), wird sie nachgeholt.
 */
object WeeklySchedule {
    val TIME: LocalTime = LocalTime.of(7, 0)

    /** Montag der Vorwoche, die zu [now] ausgewertet wird (erst ab Montag [TIME] die gerade vergangene). */
    fun dueWeekStart(now: LocalDateTime): LocalDate {
        val thisMonday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val reached = !now.isBefore(thisMonday.atTime(TIME))
        return (if (reached) thisMonday else thisMonday.minusWeeks(1)).minusWeeks(1)
    }

    /** Muss jetzt nachgeholt werden? [doneFor] = Wochenbeginn der zuletzt erledigten Auswertung (null = noch nie). */
    fun needsCatchUp(now: LocalDateTime, doneFor: LocalDate?): Boolean =
        doneFor == null || doneFor.isBefore(dueWeekStart(now))

    /** Nächster regulärer Termin nach [now]. */
    fun nextRun(now: LocalDateTime): LocalDateTime {
        val next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).atTime(TIME)
        return if (next.isAfter(now)) next else next.plusWeeks(1)
    }
}
