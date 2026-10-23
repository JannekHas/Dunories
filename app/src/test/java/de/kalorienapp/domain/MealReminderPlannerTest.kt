package de.kalorienapp.domain

import de.kalorienapp.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MealReminderPlannerTest {
    private val day = LocalDate.of(2026, 10, 20)

    @Test
    fun `uebliche zeiten und erinnerung eine stunde spaeter`() {
        val entries = listOf(
            Triple(day, MealType.LUNCH, LocalTime.of(12, 30)),
            Triple(day, MealType.LUNCH, LocalTime.of(13, 30)), // zweiter Eintrag am selben Tag zählt nicht
            Triple(day.plusDays(1), MealType.LUNCH, LocalTime.of(12, 0)),
            Triple(day.plusDays(2), MealType.LUNCH, LocalTime.of(13, 0)),
            Triple(day, MealType.DINNER, LocalTime.of(19, 0)), // nur 1 Tag → zu wenig
            Triple(day, MealType.SNACK, LocalTime.of(16, 0)),
            Triple(day.plusDays(1), MealType.SNACK, LocalTime.of(16, 0)),
            Triple(day.plusDays(2), MealType.SNACK, LocalTime.of(16, 0)),
        )
        val typical = MealReminderPlanner.typicalTimes(MealReminderPlanner.samples(entries))
        assertEquals(mapOf(MealType.LUNCH to LocalTime.of(12, 30)), typical) // Snacks ausgenommen
        assertEquals(LocalTime.of(13, 30), MealReminderPlanner.reminderTimes(typical)[MealType.LUNCH])
    }

    @Test
    fun `spaete mahlzeit wird auf 22 uhr 30 begrenzt`() {
        val reminders = MealReminderPlanner.reminderTimes(mapOf(MealType.DINNER to LocalTime.of(23, 15)))
        assertEquals(LocalTime.of(22, 30), reminders[MealType.DINNER])
    }

    @Test
    fun `naechste erinnerung heute oder morgen`() {
        val reminders = mapOf(MealType.BREAKFAST to LocalTime.of(9, 0), MealType.DINNER to LocalTime.of(20, 0))
        assertEquals(day.atTime(20, 0) to MealType.DINNER, MealReminderPlanner.next(reminders, day.atTime(10, 0)))
        assertEquals(day.plusDays(1).atTime(9, 0) to MealType.BREAKFAST, MealReminderPlanner.next(reminders, day.atTime(21, 0)))
        assertNull(MealReminderPlanner.next(emptyMap(), day.atTime(10, 0)))
    }

    @Test
    fun `nur erinnern wenn noch nichts eingetragen`() {
        assertTrue(MealReminderPlanner.isDue(MealType.LUNCH, setOf(MealType.BREAKFAST)))
        assertFalse(MealReminderPlanner.isDue(MealType.LUNCH, setOf(MealType.LUNCH)))
    }
}
