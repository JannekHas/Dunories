package de.kalorienapp.data.remote

import de.kalorienapp.domain.model.Micro
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MicroFieldsTest {
    private fun json(text: String) = lenientJson.parseToJsonElement(text) as JsonObject

    @Test
    fun `open food facts gramm in mg und mikrogramm`() {
        val micros = MicroFields.readOpenFoodFacts(
            json("""{"calcium_100g":0.12,"vitamin-d_100g":0.0000015,"folates_100g":0.00005,"iron_100g":"0,0021","zinc_100g":-1}"""),
        )
        assertEquals(120.0, micros[Micro.CALCIUM]!!, 0.0001)
        assertEquals(1.5, micros[Micro.VITAMIN_D]!!, 0.0001)
        assertEquals(50.0, micros[Micro.FOLATE]!!, 0.0001)
        assertEquals(2.1, micros[Micro.IRON]!!, 0.0001)
        assertNull(micros[Micro.ZINC])
    }

    @Test
    fun `ki antwort mit einheit im feldnamen`() {
        val micros = MicroFields.read(json("""{"vitamin_c_mg":53.2,"iron_mg":null,"potassium_mg":"358"}"""))
        assertEquals(mapOf(Micro.VITAMIN_C to 53.2, Micro.POTASSIUM to 358.0), micros)
    }

    @Test
    fun `niacin pantothensaeure kupfer mangan`() {
        val off = MicroFields.readOpenFoodFacts(
            json("""{"vitamin-pp_100g":0.0045,"pantothenic-acid_100g":0.0011,"copper_100g":0.0004,"manganese_100g":0.0029}"""),
        )
        assertEquals(4.5, off[Micro.VITAMIN_B3]!!, 0.0001)
        assertEquals(1.1, off[Micro.VITAMIN_B5]!!, 0.0001)
        assertEquals(0.4, off[Micro.COPPER]!!, 0.0001)
        assertEquals(2.9, off[Micro.MANGANESE]!!, 0.0001)
        val ai = MicroFields.read(json("""{"niacin_mg":9.7,"pantothenic_acid_mg":1.2,"copper_mg":0.3,"manganese_mg":1.1}"""))
        assertEquals(4, ai.size)
        // Kupfer über 5 mg = über der EFSA-Höchstmenge
        assertEquals(true, Micro.COPPER.tooMuch(5.5, de.kalorienapp.domain.model.Sex.MALE))
        assertEquals(false, Micro.MANGANESE.alwaysShown)
    }
}
