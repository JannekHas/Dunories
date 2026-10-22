package de.kalorienapp.data.remote

import de.kalorienapp.data.remote.gemini.GeminiRecipeParser
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.DishImageHint
import de.kalorienapp.domain.model.Effort
import de.kalorienapp.domain.model.RecipeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRecipeParserTest {

    @Test
    fun `vollstaendiges rezept`() {
        val json = """
            ```json
            {"found":true,"name":"Cremige Hähnchen-Pasta","description":"Scharf und proteinreich.","servings":4,
             "total_minutes":"30","effort":"easy","categories":["Hauptgericht","Meal Prep","Hauptgericht"],
             "ingredients":[
               {"name":"Hähnchenbrust","quantity":650,"quantity_unit":"g","amount":650,"unit":"g","kcal":110,"fat":1.5,"carbs":0,"protein":23,"salt":null,"note":"gewürfelt"},
               {"name":"Kondensmilch light","quantity":200,"quantity_unit":"ml","amount":200,"unit":"ml","kcal":"110,5","fat":4,"carbs":10,"protein":7.5},
               {"name":"Knoblauchzehe","quantity":2,"quantity_unit":"Stück","amount":6,"unit":"g","kcal":140,"fat":0.5,"carbs":28,"protein":6},
               {"name":"Salz","quantity":null,"quantity_unit":"Prise","amount":null,"unit":"g","kcal":0,"fat":0,"carbs":0,"protein":0},
               {"quantity":1}
             ],
             "steps":["Hähnchen würfeln.","  ","Nudeln kochen."],
             "image":{"index":0,"dish_visible":true,"box_2d":[419,0,951,1000]}}
            ```
        """.trimIndent()
        val draft = GeminiRecipeParser.parse(json, RecipeSource.TIKTOK, "https://t/1")!!
        val r = draft.recipe
        assertEquals("Cremige Hähnchen-Pasta", r.name)
        assertEquals(4, r.servings)
        assertEquals(30, r.totalMinutes)
        assertEquals(Effort.EASY, r.effort)
        assertEquals(RecipeSource.TIKTOK, r.source)
        assertEquals("https://t/1", r.sourceUrl)
        assertEquals(4, r.ingredients.size) // Eintrag ohne Namen wird verworfen
        assertEquals("gewürfelt", r.ingredients[0].note)
        assertEquals(AmountUnit.MILLILITER, r.ingredients[1].unit)
        assertEquals(110.5, r.ingredients[1].per100.kcal, 0.001)
        assertEquals("Stück", r.ingredients[2].quantityUnit)
        assertNull(r.ingredients[3].amount)
        assertEquals(listOf("Hähnchen würfeln.", "Nudeln kochen."), r.steps)
        assertEquals(listOf("Hauptgericht", "Meal Prep"), draft.categoryNames)
        assertEquals(DishImageHint(0, listOf(419, 0, 951, 1000)), draft.image)
    }

    @Test
    fun `kein rezept erkannt`() {
        val draft = GeminiRecipeParser.parse("""{"found":false,"name":"","servings":1,"ingredients":[],"steps":[]}""", RecipeSource.TIKTOK, null)!!
        assertTrue(draft.recipe.ingredients.isEmpty())
        assertTrue(draft.recipe.steps.isEmpty())
    }

    @Test
    fun `kein gericht sichtbar ergibt kein bild`() {
        val draft = GeminiRecipeParser.parse(
            """{"found":true,"name":"X","servings":0,"ingredients":[],"steps":["a"],"image":{"index":-1,"dish_visible":false,"box_2d":[]}}""",
            RecipeSource.IMAGES,
            null,
        )!!
        assertNull(draft.image)
        assertEquals(1, draft.recipe.servings) // 0 Portionen → mindestens 1
    }

    @Test
    fun `kaputtes json`() {
        assertNull(GeminiRecipeParser.parse("kein json", RecipeSource.TEXT, null))
    }
}
