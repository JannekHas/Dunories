package de.kalorienapp.domain

import de.kalorienapp.domain.model.GoalEndReason
import de.kalorienapp.domain.model.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GoalSuccessTest {
    private val start = LocalDate.of(2026, 7, 1)
    private val end = LocalDate.of(2026, 10, 30)

    private val goal = WeightGoal(
        id = 1,
        startDate = start,
        startWeightKg = 98.0,
        targetWeightKg = 80.0,
        targetDate = end,
        endedDate = end,
        endWeightKg = 79.8,
        endReason = GoalEndReason.REACHED,
    )

    @Test
    fun `veraenderung und dauer`() {
        val s = GoalSuccess.of(goal, emptyList(), onTargetRate = 0.87)
        assertEquals(-18.2, s.changeKg, 0.001)
        assertEquals(121, s.days)
        assertEquals(1.0, s.progress, 0.001)
    }

    @Test
    fun `verlauf nur im zielzeitraum, tagesmittel, start und ende exakt`() {
        val m = listOf(
            start.minusDays(3) to 100.0, // vor dem Ziel – fällt raus
            start to 97.0, // Starttag: gespeichertes Startgewicht gewinnt
            start.plusDays(10) to 96.0,
            start.plusDays(10) to 95.0, // zwei Messungen am Tag → Mittel
            end to 81.0, // Endtag: gespeichertes Endgewicht gewinnt
            end.plusDays(2) to 79.0, // nach dem Ziel – fällt raus
        )
        val s = GoalSuccess.of(goal, m, null)
        assertEquals(
            listOf(start to 98.0, start.plusDays(10) to 95.5, end to 79.8),
            s.weightPoints,
        )
    }

    @Test
    fun `datum erreicht - anteil des geplanten wegs`() {
        val passed = goal.copy(endWeightKg = 86.0, endReason = GoalEndReason.DATE_PASSED)
        assertEquals(12.0 / 18.0, GoalSuccess.of(passed, emptyList(), null).progress, 0.001)
    }

    @Test
    fun `ziel am selben tag beendet hat trotzdem start und ende im verlauf`() {
        val sameDay = goal.copy(endedDate = start, endWeightKg = 96.9)
        val s = GoalSuccess.of(sameDay, emptyList(), onTargetRate = null)
        assertEquals(listOf(start to 98.0, start to 96.9), s.weightPoints)
    }
}
