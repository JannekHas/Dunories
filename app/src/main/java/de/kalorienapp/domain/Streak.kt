package de.kalorienapp.domain

import de.kalorienapp.domain.model.DayStats
import de.kalorienapp.domain.model.DayStatus
import java.time.LocalDate

/** Serie „im Ziel“: wie viele Tage direkt vor [today] hintereinander im Budget lagen (heute läuft noch). */
object Streak {
    fun current(days: List<DayStats>, today: LocalDate): Int {
        val byDate = days.associateBy { it.date }
        var count = 0
        var day = today.minusDays(1)
        while (byDate[day]?.status == DayStatus.ON_TARGET) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
