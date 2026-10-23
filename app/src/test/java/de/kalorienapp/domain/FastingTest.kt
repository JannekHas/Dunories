package de.kalorienapp.domain

import de.kalorienapp.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class FastingTest {
    private val config = FastingConfig(FastingPlan.H16_8, windowStart = LocalTime.of(12, 0))
    private val day = LocalDate.of(2026, 10, 22) // Donnerstag

    @Test
    fun `essensfenster 12 bis 20 uhr`() {
        assertEquals(LocalTime.of(20, 0), config.windowEnd)
        assertTrue(Fasting.isInEatingWindow(config, LocalTime.of(12, 0)))
        assertTrue(Fasting.isInEatingWindow(config, LocalTime.of(19, 59)))
        assertFalse(Fasting.isInEatingWindow(config, LocalTime.of(20, 0)))
        assertFalse(Fasting.isInEatingWindow(config, LocalTime.of(8, 0)))
    }

    @Test
    fun `fenster ueber mitternacht`() {
        val late = FastingConfig(FastingPlan.H20_4, windowStart = LocalTime.of(22, 0))
        assertTrue(Fasting.isInEatingWindow(late, LocalTime.of(23, 30)))
        assertTrue(Fasting.isInEatingWindow(late, LocalTime.of(1, 0)))
        assertFalse(Fasting.isInEatingWindow(late, LocalTime.of(2, 0)))
    }

    @Test
    fun `status morgens fasten, mittags essen`() {
        val morning = Fasting.status(config, day.atTime(8, 0))
        assertEquals(FastingPhase.FASTING, morning.phase)
        assertEquals(day.atTime(12, 0), morning.phaseEnd)
        // 20 → 8 Uhr = 12 von 16 Stunden
        assertEquals(0.75f, morning.progress, 0.01f)
        val noon = Fasting.status(config, day.atTime(14, 0))
        assertEquals(FastingPhase.EATING, noon.phase)
        assertEquals(day.atTime(20, 0), noon.phaseEnd)
    }

    @Test
    fun `naechster wechsel`() {
        assertEquals(day.atTime(12, 0) to true, Fasting.nextTransition(config, day.atTime(8, 0)))
        assertEquals(day.atTime(20, 0) to false, Fasting.nextTransition(config, day.atTime(13, 0)))
        assertEquals(day.plusDays(1).atTime(12, 0) to true, Fasting.nextTransition(config, day.atTime(21, 0)))
    }

    @Test
    fun `statistik eingehalten und fastenpause`() {
        val d1 = day.minusDays(1)
        val stats = Fasting.stats(
            config,
            days = listOf(d1, day),
            entryTimes = mapOf(
                d1 to listOf(LocalTime.of(12, 30), LocalTime.of(19, 30)),
                day to listOf(LocalTime.of(11, 30), LocalTime.of(18, 0)), // 11:30 zu früh
            ),
            kcalByDay = emptyMap(),
            sex = Sex.MALE,
        )
        assertEquals(2, stats.evaluatedDays)
        assertEquals(1, stats.keptDays)
        // 19:30 → 11:30 = 16 h
        assertEquals(16.0, stats.averageFastHours!!, 0.001)
    }

    @Test
    fun `fuenf zwei fastentag unter limit`() {
        val fiveTwo = FastingConfig(FastingPlan.FIVE_TWO, fastDays = setOf(DayOfWeek.THURSDAY))
        val stats = Fasting.stats(
            fiveTwo,
            days = listOf(day, day.plusDays(1)),
            entryTimes = mapOf(day to listOf(LocalTime.of(12, 0)), day.plusDays(1) to listOf(LocalTime.of(9, 0))),
            kcalByDay = mapOf(day to 750.0, day.plusDays(1) to 2200.0),
            sex = Sex.MALE,
        )
        // Donnerstag 750 > 600 → nicht eingehalten; Freitag normaler Tag
        assertEquals(1, stats.keptDays)
        assertNull(Fasting.nextTransition(fiveTwo, day.atTime(8, 0)))
        assertEquals(FastingPhase.FAST_DAY, Fasting.status(fiveTwo, day.atTime(8, 0)).phase)
    }
}
