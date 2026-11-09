package de.kalorienapp.domain

import de.kalorienapp.domain.model.DayStats
import de.kalorienapp.domain.model.DayStatus
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun day(daysAgo: Long, status: DayStatus) = DayStats(
        date = today.minusDays(daysAgo),
        eaten = Nutrients(),
        activityKcal = 0,
        budgetKcal = 2000,
        proteinTargetG = 100,
        hasEntries = status != DayStatus.NO_DATA,
        status = status,
    )

    @Test
    fun `zaehlt zusammenhaengende tage bis gestern`() {
        val days = listOf(day(1, DayStatus.ON_TARGET), day(2, DayStatus.ON_TARGET), day(3, DayStatus.OVER), day(4, DayStatus.ON_TARGET))
        assertEquals(2, Streak.current(days, today))
    }

    @Test
    fun `heute zaehlt nicht mit und gestern ohne eintrag beendet die serie`() {
        val days = listOf(day(0, DayStatus.ON_TARGET), day(1, DayStatus.NO_DATA), day(2, DayStatus.ON_TARGET))
        assertEquals(0, Streak.current(days, today))
    }

    @Test
    fun `knapp drueber unterbricht die serie`() {
        assertEquals(0, Streak.current(listOf(day(1, DayStatus.SLIGHTLY_OVER)), today))
    }
}
