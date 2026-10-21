package de.kalorienapp.domain

import de.kalorienapp.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveCaloriesTest {

    private fun resolve(active: Double? = null, total: Double? = null, steps: Long? = null, fraction: Double = 1.0) =
        ActiveCalories.resolve(
            activeKcal = active,
            totalKcal = total,
            steps = steps,
            bmrPerDay = 1800.0,
            elapsedFractionOfDay = fraction,
            heightCm = 180.0,
            weightKg = 80.0,
            sex = Sex.MALE,
        )

    @Test
    fun `direkter aktiv-wert hat vorrang vor gesamt und schritten`() {
        assertEquals(ActivityEstimate(420, ActivitySource.ACTIVE_CALORIES), resolve(active = 420.4, total = 2500.0, steps = 10_000))
    }

    @Test
    fun `nur gesamt - grundumsatz fuer vergangenen tagesanteil abziehen`() {
        // Halber Tag: 1800 * 0,5 = 900 Grundumsatz; 1300 gesamt → 400 aktiv
        assertEquals(ActivityEstimate(400, ActivitySource.TOTAL_CALORIES), resolve(total = 1300.0, fraction = 0.5))
    }

    @Test
    fun `gesamt unter grundumsatz faellt auf schritte zurueck`() {
        assertEquals(ActivitySource.STEPS, resolve(total = 500.0, steps = 5_000)!!.source)
    }

    @Test
    fun `schaetzung aus schritten`() {
        // 10.000 Schritte × 0,747 m = 7,47 km × 80 kg × 0,5 = 298,8 → 299 kcal
        assertEquals(ActivityEstimate(299, ActivitySource.STEPS), resolve(steps = 10_000))
        assertEquals(299, ActiveCalories.fromSteps(10_000, 180.0, 80.0, Sex.MALE))
    }

    @Test
    fun `keine daten ergibt null, explizit 0 ergibt 0`() {
        assertNull(resolve())
        assertEquals(0, resolve(active = 0.0)!!.activeKcal)
        assertEquals(0, resolve(steps = 0)!!.activeKcal)
    }
}
