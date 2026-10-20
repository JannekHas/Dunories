package de.kalorienapp.domain

import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.sum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutrientsTest {

    private val oats = Nutrients(kcal = 370.0, proteinG = 13.0, carbsG = 59.0, fatG = 7.0, saltG = 0.02)

    @Test
    fun `pro 100 auf menge umrechnen`() {
        val total = oats.per100ToAmount(150.0)
        assertEquals(555.0, total.kcal, 0.001)
        assertEquals(19.5, total.proteinG, 0.001)
        assertEquals(88.5, total.carbsG, 0.001)
        assertEquals(10.5, total.fatG, 0.001)
        assertEquals(0.03, total.saltG!!, 0.0001)
        assertNull(total.sugarG)
    }

    @Test
    fun `menge zurueck auf pro 100 umrechnen`() {
        val per100 = oats.per100ToAmount(250.0).amountToPer100(250.0)
        assertEquals(oats.kcal, per100.kcal, 0.001)
        assertEquals(oats.fatG, per100.fatG, 0.001)
    }

    @Test
    fun `summe zusatzwerte - null nur wenn nirgends angegeben`() {
        val a = Nutrients(kcal = 100.0, sugarG = 5.0)
        val b = Nutrients(kcal = 50.0)
        val total = listOf(a, b).sum()
        assertEquals(150.0, total.kcal, 0.001)
        assertEquals(5.0, total.sugarG!!, 0.001)
        assertNull(total.fiberG)
    }
}
