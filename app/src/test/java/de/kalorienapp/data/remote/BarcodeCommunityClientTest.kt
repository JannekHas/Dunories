package de.kalorienapp.data.remote

import de.kalorienapp.domain.model.AmountUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeCommunityClientTest {

    @Test
    fun `gefundenes produkt wird zum vorschlag ohne menge`() {
        val d = BarcodeCommunityClient.parse(
            """{"found":true,"name":"Dinkel-Toast","unit":"g","per100":{"kcal":248,"proteinG":10.5,"carbsG":42,"fatG":3.1,"sugarG":null,"saltG":1.1},"count":2}""",
        )!!
        assertEquals("Dinkel-Toast", d.name)
        assertNull(d.amount)
        assertEquals(AmountUnit.GRAM, d.unit)
        assertEquals(248.0, d.per100.kcal, 0.0)
        assertNull(d.per100.sugarG)
        assertEquals(1.1, d.per100.saltG!!, 0.0)
    }

    @Test
    fun `unbekannt oder kaputt ergibt null`() {
        assertNull(BarcodeCommunityClient.parse("""{"found":false}"""))
        assertNull(BarcodeCommunityClient.parse("kaputt"))
        assertNull(BarcodeCommunityClient.parse("""{"found":true,"name":"x","per100":{}}"""))
    }
}
