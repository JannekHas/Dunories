package de.kalorienapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveCaloriesTest {

    @Test
    fun `direkter aktiv-wert hat vorrang`() {
        assertEquals(420, ActiveCalories.resolve(activeKcal = 420.4, totalKcal = 2500.0, bmrPerDay = 1800.0, elapsedFractionOfDay = 1.0))
    }

    @Test
    fun `nur gesamt - grundumsatz fuer vergangenen tagesanteil abziehen`() {
        // Halber Tag: 1800 * 0,5 = 900 Grundumsatz; 1300 gesamt → 400 aktiv
        assertEquals(400, ActiveCalories.resolve(activeKcal = null, totalKcal = 1300.0, bmrPerDay = 1800.0, elapsedFractionOfDay = 0.5))
    }

    @Test
    fun `gesamt unter grundumsatz ergibt 0, nicht negativ`() {
        assertEquals(0, ActiveCalories.resolve(activeKcal = 0.0, totalKcal = 500.0, bmrPerDay = 1800.0, elapsedFractionOfDay = 1.0))
    }

    @Test
    fun `keine daten ergibt null, explizit 0 ergibt 0`() {
        assertNull(ActiveCalories.resolve(activeKcal = null, totalKcal = null, bmrPerDay = 1800.0, elapsedFractionOfDay = 1.0))
        assertEquals(0, ActiveCalories.resolve(activeKcal = 0.0, totalKcal = null, bmrPerDay = 1800.0, elapsedFractionOfDay = 1.0))
    }
}
