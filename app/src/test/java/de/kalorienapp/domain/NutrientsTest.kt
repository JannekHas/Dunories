package de.kalorienapp.domain

import de.kalorienapp.domain.model.Micro
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

    @Test
    fun `vitamine summieren und skalieren, fehlende bleiben weg`() {
        val milk = Nutrients(kcal = 47.0, micros = mapOf(Micro.CALCIUM to 120.0, Micro.VITAMIN_B12 to 0.4))
        val oj = Nutrients(kcal = 45.0, micros = mapOf(Micro.VITAMIN_C to 40.0))
        val total = listOf(milk.per100ToAmount(200.0), oj.per100ToAmount(250.0)).sum()
        assertEquals(240.0, total.micros[Micro.CALCIUM]!!, 0.001)
        assertEquals(100.0, total.micros[Micro.VITAMIN_C]!!, 0.001)
        assertNull(total.micros[Micro.IRON])
    }

    @Test
    fun `vitamine speichern und lesen`() {
        val values = mapOf(Micro.IRON to 3.25, Micro.FOLATE to 120.0)
        assertEquals(values, Micro.decode(Micro.encode(values)))
        assertNull(Micro.encode(emptyMap()))
        // Unbekanntes/Kaputtes wird ignoriert
        assertEquals(mapOf(Micro.ZINC to 1.0), Micro.decode("ZINC=1.0;JOD=5;IRON=abc;;"))
        assertEquals(emptyMap<Micro, Double>(), Micro.decode(null))
    }
}
