package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Recipe
import de.kalorienapp.domain.model.RecipeIngredient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecipeCalculatorTest {

    private val recipe = Recipe(
        name = "Hähnchen-Pasta",
        servings = 4,
        ingredients = listOf(
            // 400 g Nudeln: 350 kcal / 100 g → 1400 kcal, 52 g Protein
            RecipeIngredient(name = "Nudeln", quantity = 400.0, quantityUnit = "g", amount = 400.0, per100 = Nutrients(kcal = 350.0, proteinG = 13.0, carbsG = 70.0, fatG = 2.0)),
            // 600 g Hähnchen: 110 kcal / 100 g → 660 kcal, 138 g Protein
            RecipeIngredient(name = "Hähnchenbrust", quantity = 600.0, quantityUnit = "g", amount = 600.0, per100 = Nutrients(kcal = 110.0, proteinG = 23.0, fatG = 1.5, sugarG = 0.0)),
            // „Salz nach Geschmack“ zählt nicht
            RecipeIngredient(name = "Salz", quantityUnit = "Prise", amount = null, per100 = Nutrients(saltG = 100.0)),
        ),
    )

    @Test
    fun `summe und pro portion`() {
        assertEquals(2060.0, recipe.totalNutrients.kcal, 0.001)
        assertEquals(190.0, recipe.totalNutrients.proteinG, 0.001)
        assertEquals(515.0, recipe.perServing.kcal, 0.001)
        assertEquals(47.5, recipe.perServing.proteinG, 0.001)
        // Zusatzwert nur bei gezählten Zutaten vorhanden; Salz ohne Menge zählt nicht
        assertNull(recipe.totalNutrients.saltG)
        assertEquals(1000.0, recipe.totalAmount, 0.001)
    }

    @Test
    fun `skalierung auf andere portionszahl`() {
        assertEquals(0.5, RecipeCalculator.scaleFactor(recipe, 2), 0.001)
        assertEquals(1.5, RecipeCalculator.scaleFactor(recipe, 6), 0.001)
    }

    @Test
    fun `tagebucheintrag fuer anderthalb portionen`() {
        val entry = RecipeCalculator.toFoodEntry(recipe, 1.5, LocalDate.of(2026, 10, 22), MealType.DINNER)
        assertEquals("Hähnchen-Pasta", entry.name)
        assertEquals(772.5, entry.nutrients.kcal, 0.001)
        assertEquals(375.0, entry.amount!!, 0.001) // 1000 g / 4 × 1,5
        assertEquals(AmountUnit.GRAM, entry.unit)
        assertEquals(EntrySource.RECIPE, entry.source)
        assertEquals(MealType.DINNER, entry.mealType)
    }

    @Test
    fun `ausschnitt um das gericht im 4 zu 3 format`() {
        // TikTok-Cover 540×960, Gericht unten (y 419–951, volle Breite) → Creator oben wird abgeschnitten
        val crop = RecipeCalculator.cropRect(listOf(419, 0, 951, 1000), 540, 960)!!
        val (l, t, r, b) = crop.toList()
        assertEquals(4.0 / 3.0, (r - l).toDouble() / (b - t), 0.02)
        assertEquals(0, l)
        assertEquals(540, r)
        assert(t > 400) { "oben soll der Creator wegfallen, top=$t" }
        assert(b <= 960)
    }

    @Test
    fun `ausschnitt bleibt im bild`() {
        val crop = RecipeCalculator.cropRect(listOf(0, 0, 200, 200), 1000, 1000)!!
        assertEquals(0, crop[0])
        assertEquals(0, crop[1])
        assertEquals(4.0 / 3.0, (crop[2] - crop[0]).toDouble() / (crop[3] - crop[1]), 0.02)
    }

    @Test
    fun `unbrauchbare box ergibt keinen ausschnitt`() {
        assertNull(RecipeCalculator.cropRect(listOf(1, 2, 3), 100, 100))
        assertNull(RecipeCalculator.cropRect(listOf(500, 500, 510, 510), 100, 100))
        // Ganzes Bild → mittiger 4:3-Streifen
        assertArrayEquals(intArrayOf(0, 13, 100, 88), RecipeCalculator.cropRect(listOf(0, 0, 1000, 1000), 100, 100))
    }
}
