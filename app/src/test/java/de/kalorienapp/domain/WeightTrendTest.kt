package de.kalorienapp.domain

import de.kalorienapp.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WeightTrendTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 10, 1)

    private fun at(day: Long, kg: Double, hour: Int = 7) =
        WeightEntry(measuredAt = start.plusDays(day).atTime(hour, 0).toInstant(zone), weightKg = kg)

    @Test
    fun `erster wert ist der start, ausreisser wirkt nur gedaempft`() {
        val trend = WeightTrend.compute(listOf(at(0, 100.0), at(1, 102.0)), zone)
        assertEquals(100.0, trend[0].weightKg, 0.001)
        // +2 kg Wasser am nächsten Tag → Trend nur +0,2 kg
        assertEquals(100.2, trend[1].weightKg, 0.001)
    }

    @Test
    fun `mehrere messungen am tag werden gemittelt`() {
        val trend = WeightTrend.compute(listOf(at(0, 100.0, 7), at(0, 101.0, 20)), zone)
        assertEquals(1, trend.size)
        assertEquals(100.5, trend.single().weightKg, 0.001)
    }

    @Test
    fun `luecke zieht den trend staerker zur messung`() {
        // 0,9^10 ≈ 0,349 → 100 × 0,349 + 95 × 0,651 ≈ 96,74
        val trend = WeightTrend.compute(listOf(at(0, 100.0), at(10, 95.0)), zone)
        assertEquals(96.74, trend[1].weightKg, 0.01)
    }

    @Test
    fun `woechentliche veraenderung`() {
        val entries = (0..14L).map { at(it, 100.0 - it * 0.1) }
        val change = WeightTrend.weeklyChange(WeightTrend.compute(entries, zone))!!
        assertEquals(true, change < 0)
        assertNull(WeightTrend.weeklyChange(WeightTrend.compute(listOf(at(0, 100.0)), zone)))
    }
}
