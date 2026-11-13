package de.kalorienapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientLimitsTest {

    @Test
    fun `gesaettigte fettsaeuren zehn prozent der kalorien`() {
        assertEquals(20, NutrientLimits.saturatedFat(1800).grams)
        assertEquals(22, NutrientLimits.saturatedFat(2000).grams)
    }

    @Test
    fun `obergrenze rot erst darueber, mindestziel gruen ab erreichen`() {
        assertFalse(NutrientLimits.SALT.exceeded(6.0))
        assertTrue(NutrientLimits.SALT.exceeded(6.1))
        assertFalse(NutrientLimits.SALT.exceeded(null))
        assertTrue(NutrientLimits.FIBER.reached(30.0))
        assertFalse(NutrientLimits.FIBER.reached(29.9))
        assertFalse(NutrientLimits.FIBER.exceeded(80.0))
    }
}
