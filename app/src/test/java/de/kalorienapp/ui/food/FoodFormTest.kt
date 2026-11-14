package de.kalorienapp.ui.food

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodFormTest {

    @Test
    fun `nullwerte und unbekannte werte bleiben leere felder`() {
        val form = FoodForm.of(
            name = "Cola Zero",
            amount = 330.0,
            unit = AmountUnit.MILLILITER,
            per100 = Nutrients(kcal = 0.3, proteinG = 0.0, carbsG = 0.0, fatG = 0.0, sugarG = 0.0, saltG = 0.02),
        )
        assertEquals("0,3", form.value(NutrientField.KCAL))
        assertEquals("", form.value(NutrientField.FAT))
        assertEquals("", form.value(NutrientField.SUGAR))
        assertEquals("", form.value(NutrientField.FIBER))
        assertEquals("0,02", form.value(NutrientField.SALT))
    }

    @Test
    fun `leere felder werden als null gespeichert`() {
        val form = FoodForm(name = "Wasser", amount = "500", per100 = mapOf(NutrientField.FAT to "6,5"))
        assertTrue(form.isValid)
        val n = form.per100Nutrients()
        assertEquals(0.0, n.kcal, 0.0)
        assertEquals(6.5, n.fatG, 0.0)
        assertEquals(0.0, n.sugarG!!, 0.0)
        assertEquals(0.0, n.saturatedFatG!!, 0.0)
        assertEquals(0.0, n.saltG!!, 0.0)
    }
}
