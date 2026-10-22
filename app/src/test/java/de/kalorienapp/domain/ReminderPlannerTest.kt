package de.kalorienapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReminderPlannerTest {

    @Test
    fun `median der ersten eintraege`() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(9, 30), LocalTime.of(8, 20), LocalTime.of(14, 0))
        // sortiert 8:00, 8:20, 9:30, 14:00 → Mitte (8:20 + 9:30) / 2 = 8:55
        assertEquals(LocalTime.of(8, 55), ReminderPlanner.typicalFirstEntryTime(times))
        assertNull(ReminderPlanner.typicalFirstEntryTime(times.take(2)))
    }

    @Test
    fun `erinnerung eine halbe stunde vorher und begrenzt`() {
        assertEquals(LocalTime.of(8, 25), ReminderPlanner.reminderTime(LocalTime.of(8, 55)))
        assertEquals(LocalTime.of(5, 0), ReminderPlanner.reminderTime(LocalTime.of(4, 0)))
        assertEquals(LocalTime.of(11, 0), ReminderPlanner.reminderTime(LocalTime.of(13, 0)))
        assertEquals(ReminderPlanner.DEFAULT_TIME, ReminderPlanner.reminderTime(null))
    }

    @Test
    fun `wiegen faellig nach intervall`() {
        val today = LocalDate.of(2026, 10, 22)
        assertTrue(ReminderPlanner.isWeighInDue(null, today, 3))
        assertFalse(ReminderPlanner.isWeighInDue(today.minusDays(2), today, 3))
        assertTrue(ReminderPlanner.isWeighInDue(today.minusDays(3), today, 3))
        assertTrue(ReminderPlanner.isWeighInDue(today.minusDays(1), today, 1))
        assertFalse(ReminderPlanner.isWeighInDue(today, today, 1))
    }
}
