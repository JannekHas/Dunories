package de.kalorienapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaffeineEstimateTest {

    @Test
    fun `wert aus open food facts hat vorrang`() {
        assertEquals(30.0, CaffeineEstimate.resolve(30.0, listOf("en:energy-drinks"))!!, 1e-9)
    }

    @Test
    fun `fehlender oder null-wert bei energy drink wird geschaetzt`() {
        assertEquals(32.0, CaffeineEstimate.resolve(null, listOf("en:beverages", "en:energy-drinks"))!!, 1e-9)
        assertEquals(32.0, CaffeineEstimate.resolve(0.0, listOf("en:energy-drinks"))!!, 1e-9)
    }

    @Test
    fun `null bei koffeinfreiem produkt bleibt null`() {
        assertEquals(0.0, CaffeineEstimate.resolve(0.0, listOf("en:tomato-ketchup"))!!, 1e-9)
        assertNull(CaffeineEstimate.resolve(null, listOf("en:tomato-ketchup")))
    }
}
