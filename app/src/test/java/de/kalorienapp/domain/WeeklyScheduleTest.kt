package de.kalorienapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WeeklyScheduleTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `ab montag 7 uhr ist die vorwoche faellig`() {
        assertEquals(monday.minusWeeks(1), WeeklySchedule.dueWeekStart(monday.atTime(7, 0)))
        assertEquals(monday.minusWeeks(2), WeeklySchedule.dueWeekStart(monday.atTime(6, 59)))
        assertEquals(monday.minusWeeks(1), WeeklySchedule.dueWeekStart(monday.plusDays(5).atTime(12, 0)))
    }

    @Test
    fun `verpasster montag wird nachgeholt, erledigter nicht`() {
        val saturday = LocalDateTime.of(2026, 11, 21, 10, 0)
        assertTrue(WeeklySchedule.needsCatchUp(saturday, doneFor = monday.minusWeeks(2)))
        assertTrue(WeeklySchedule.needsCatchUp(saturday, doneFor = null))
        assertFalse(WeeklySchedule.needsCatchUp(saturday, doneFor = monday.minusWeeks(1)))
    }

    @Test
    fun `naechster termin ist der naechste montag 7 uhr`() {
        assertEquals(monday.atTime(7, 0), WeeklySchedule.nextRun(monday.minusDays(1).atTime(20, 0)))
        assertEquals(monday.plusWeeks(1).atTime(7, 0), WeeklySchedule.nextRun(monday.atTime(7, 0)))
    }
}
