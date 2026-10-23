package de.kalorienapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WaterTest {
    @Test
    fun `ziel automatisch aus gewicht, gerundet und begrenzt`() {
        // 98 kg × 35 = 3430 ml → 3500 ml
        assertEquals(3500, Water.goalMl(null, 98.0))
        assertEquals(1500, Water.goalMl(null, 30.0))
        assertEquals(4000, Water.goalMl(null, 150.0))
        assertEquals(2000, Water.goalMl(2000, 98.0))
    }
}
