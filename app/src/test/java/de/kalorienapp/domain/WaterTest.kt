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

    @Test
    fun `getraenke in ml zaehlen mit, alkohol und gramm nicht`() {
        val day = java.time.LocalDate.of(2026, 11, 14)
        fun entry(name: String, amount: Double, unit: de.kalorienapp.domain.model.AmountUnit) = de.kalorienapp.domain.model.FoodEntry(
            date = day, mealType = de.kalorienapp.domain.model.MealType.SNACK, name = name, amount = amount, unit = unit,
            nutrients = de.kalorienapp.domain.model.Nutrients(),
        )
        val ml = de.kalorienapp.domain.model.AmountUnit.MILLILITER
        val g = de.kalorienapp.domain.model.AmountUnit.GRAM
        val entries = listOf(
            entry("Diet-Limo", 750.0, ml),
            entry("Kaffee", 200.0, ml),
            entry("Pils", 500.0, ml),
            entry("Alkoholfreies Weizen", 500.0, ml),
            entry("Joghurt", 150.0, g),
        )
        org.junit.Assert.assertEquals(1450, Water.drinksMl(entries))
        org.junit.Assert.assertTrue(Water.isAlcoholic("Rotwein trocken"))
        org.junit.Assert.assertTrue(Water.isAlcoholic("Weißbier"))
        org.junit.Assert.assertFalse(Water.isAlcoholic("Ginger Ale"))
        org.junit.Assert.assertFalse(Water.isAlcoholic("Weintraubensaft"))
    }
}
