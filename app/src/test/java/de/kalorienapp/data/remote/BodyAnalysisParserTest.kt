package de.kalorienapp.data.remote

import de.kalorienapp.data.remote.gemini.BodyAnalysisParser
import de.kalorienapp.domain.model.MeasureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyAnalysisParserTest {
    @Test
    fun `werte lesen und unplausible verwerfen`() {
        val values = BodyAnalysisParser.parse(
            """{"body_fat_percent":22.4,"muscle_mass_kg":"35,8","body_water_percent":null,"visceral_fat_level":9,"bone_mass_kg":3.1,"bmr_kcal":99999}""",
        )!!
        assertEquals(22.4, values[MeasureType.BODY_FAT]!!, 0.001)
        assertEquals(35.8, values[MeasureType.MUSCLE_MASS]!!, 0.001)
        assertEquals(9.0, values[MeasureType.VISCERAL_FAT]!!, 0.001)
        assertNull(values[MeasureType.BODY_WATER])
        // 99.999 kcal ist kein Grundumsatz → verworfen
        assertNull(values[MeasureType.BMR])
    }

    @Test
    fun `kaputtes json ergibt null`() {
        assertNull(BodyAnalysisParser.parse("kein json"))
    }

    @Test
    fun `koerperfett wandert mit koerperanalyse zur analyse`() {
        assertTrue(MeasureType.BODY_FAT in MeasureType.measures(analysisEnabled = false))
        assertTrue(MeasureType.BODY_FAT !in MeasureType.measures(analysisEnabled = true))
        assertTrue(MeasureType.MUSCLE_MASS !in MeasureType.measures(analysisEnabled = false))
        assertEquals(6, MeasureType.ANALYSIS.size)
    }
}
