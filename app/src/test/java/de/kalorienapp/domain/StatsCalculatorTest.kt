package de.kalorienapp.domain

import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.DayStatus
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {

    private val targets = DailyTargets(kcal = 2000, proteinG = 150, carbsG = 200, fatG = 60)
    private val d1 = LocalDate.of(2026, 10, 19)

    @Test
    fun `status nach budget`() {
        assertEquals(DayStatus.NO_DATA, StatsCalculator.status(0.0, 2000, hasEntries = false))
        assertEquals(DayStatus.ON_TARGET, StatsCalculator.status(2000.0, 2000, hasEntries = true))
        assertEquals(DayStatus.SLIGHTLY_OVER, StatsCalculator.status(2200.0, 2000, hasEntries = true))
        assertEquals(DayStatus.OVER, StatsCalculator.status(2201.0, 2000, hasEntries = true))
    }

    @Test
    fun `aktivitaet erhoeht das budget eines tages`() {
        val days = StatsCalculator.days(
            from = d1, to = d1,
            eatenByDay = mapOf(d1 to Nutrients(kcal = 2300.0)),
            activityByDay = mapOf(d1 to 400),
            targetsFor = { targets },
        )
        assertEquals(2400, days.single().budgetKcal)
        assertEquals(DayStatus.ON_TARGET, days.single().status)
    }

    @Test
    fun `tage ohne eintraege werden mitgefuehrt aber nicht gemittelt`() {
        val d2 = d1.plusDays(1)
        val d3 = d1.plusDays(2)
        val days = StatsCalculator.days(
            from = d1, to = d3,
            eatenByDay = mapOf(
                d1 to Nutrients(kcal = 1800.0, proteinG = 160.0, sugarG = 40.0),
                d3 to Nutrients(kcal = 2400.0, proteinG = 100.0),
            ),
            activityByDay = emptyMap(),
            targetsFor = { targets },
        )
        assertEquals(3, days.size)
        assertEquals(DayStatus.NO_DATA, days[1].status)

        val period = StatsCalculator.period(days)
        assertEquals(2, period.loggedDays)
        assertEquals(2100.0, period.averageEaten!!.kcal, 0.001)
        assertEquals(0.5, period.proteinHitRate!!, 0.001) // nur Tag 1 erreicht 150 g
        assertEquals(0.5, period.onTargetRate!!, 0.001) // Tag 3 ist 20 % drüber
        assertEquals(40.0, period.averageEaten.sugarG!!, 0.001) // nur Tage mit Angabe
        assertNull(period.averageEaten.fiberG)
    }

    @Test
    fun `zeitraum ohne daten`() {
        val period = StatsCalculator.period(
            StatsCalculator.days(d1, d1.plusDays(6), emptyMap(), emptyMap()) { targets },
        )
        assertEquals(0, period.loggedDays)
        assertNull(period.averageEaten)
        assertNull(period.proteinHitRate)
    }
}
