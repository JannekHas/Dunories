package de.kalorienapp.data.remote

import de.kalorienapp.data.remote.gemini.GeminiResponseParser
import de.kalorienapp.data.remote.openfoodfacts.OpenFoodFactsParser
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsParserTest {

    @Test
    fun `nutella - echte antwortstruktur`() {
        val body = """{"code":"3017620422003","status":1,"product":{"brands":"Nutella, Ferrero",
            "nutrition_data_per":"100g","product_name":"Nutella","product_name_de":"Nutella",
            "nutriments":{"carbohydrates_100g":57.5,"energy-kcal_100g":539,"energy_100g":2252,"fat_100g":30.9,
            "fiber_100g":0,"proteins_100g":6.3,"salt_100g":0.107,"saturated-fat_100g":10.6,"sugars_100g":56.3}}}"""
        val draft = OpenFoodFactsParser.parse(body)!!
        assertEquals("Nutella", draft.name) // Marke steckt schon im Namen
        assertEquals(AmountUnit.GRAM, draft.unit)
        assertEquals(539.0, draft.per100.kcal, 0.001)
        assertEquals(30.9, draft.per100.fatG, 0.001)
        assertEquals(10.6, draft.per100.saturatedFatG!!, 0.001)
        assertEquals(56.3, draft.per100.sugarG!!, 0.001)
        assertEquals(0.107, draft.per100.saltG!!, 0.0001)
        assertNull(draft.amount)
        assertEquals(EntrySource.BARCODE, draft.source)
    }

    @Test
    fun `unbekanntes produkt`() {
        assertNull(OpenFoodFactsParser.parse("""{"code":"0000","status":0,"status_verbose":"product not found"}"""))
    }

    @Test
    fun `nur kJ angegeben, getraenk, zahlen als strings, marke ergaenzt`() {
        val body = """{"status":1,"product":{"product_name":"Apfelschorle","brands":"Lieblings",
            "product_quantity_unit":"ml","serving_quantity":"330",
            "nutriments":{"energy-kj_100g":"104,6","carbohydrates_100g":"6"}}}"""
        val draft = OpenFoodFactsParser.parse(body)!!
        assertEquals("Apfelschorle (Lieblings)", draft.name)
        assertEquals(AmountUnit.MILLILITER, draft.unit)
        assertEquals(25.0, draft.per100.kcal, 0.01)
        assertEquals(330.0, draft.amount!!, 0.001)
        assertNull(draft.per100.sugarG)
    }

    @Test
    fun `kaputtes json`() {
        assertNull(OpenFoodFactsParser.parse("<html>Fehler</html>"))
    }
}

class GeminiResponseParserTest {

    private fun wrap(text: String, thought: String? = null): String {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val thoughtPart = thought?.let { """{"text":"$it","thought":true},""" }.orEmpty()
        return """{"candidates":[{"content":{"parts":[$thoughtPart{"text":"$escaped"}],"role":"model"}}]}"""
    }

    @Test
    fun `gueltige antwort mit mehreren komponenten`() {
        val json = """{"items":[
            {"name":"Vollkornbrot","amount":100,"unit":"g","kcal":213,"fat":1.4,"saturated_fat":0.3,
             "carbs":38.6,"sugar":2.5,"fiber":7,"protein":7.8,"salt":1.1},
            {"name":"Orangensaft","amount":250,"unit":"ml","kcal":45,"fat":0.2,"saturated_fat":null,
             "carbs":9.5,"sugar":9,"fiber":null,"protein":0.7,"salt":null}]}"""
        val drafts = GeminiResponseParser.parse(wrap(json), EntrySource.AI_TEXT)!!
        assertEquals(2, drafts.size)
        assertEquals("Vollkornbrot", drafts[0].name)
        assertEquals(213.0, drafts[0].per100.kcal, 0.001)
        assertEquals(AmountUnit.MILLILITER, drafts[1].unit)
        assertEquals(250.0, drafts[1].amount!!, 0.001)
        assertNull(drafts[1].per100.fiberG)
        assertEquals(EntrySource.AI_TEXT, drafts[1].source)
    }

    @Test
    fun `gedanken-teile werden ignoriert, code-fences entfernt`() {
        val json = "```json\n{\"items\":[{\"name\":\"Apfel\",\"amount\":150,\"unit\":\"g\",\"kcal\":52,\"fat\":0.2,\"carbs\":11.4,\"protein\":0.3}]}\n```"
        val drafts = GeminiResponseParser.parse(wrap(json, thought = "Ich denke nach"), EntrySource.AI_PHOTO)!!
        assertEquals(1, drafts.size)
        assertEquals("Apfel", drafts[0].name)
    }

    @Test
    fun `negative werte, fehlende namen und ungueltige mengen werden bereinigt`() {
        val json = """{"items":[
            {"name":"","amount":10,"unit":"g","kcal":1,"fat":0,"carbs":0,"protein":0},
            {"name":"Suppe","amount":-5,"unit":"ML","kcal":-40,"fat":"2,5","carbs":5,"protein":1}]}"""
        val drafts = GeminiResponseParser.parse(wrap(json), EntrySource.AI_PHOTO)!!
        assertEquals(1, drafts.size)
        val soup = drafts.single()
        assertNull(soup.amount)
        assertEquals(AmountUnit.MILLILITER, soup.unit)
        assertEquals(0.0, soup.per100.kcal, 0.001)
        assertEquals(2.5, soup.per100.fatG, 0.001)
    }

    @Test
    fun `leere liste ist gueltig, unsinn ist null`() {
        assertTrue(GeminiResponseParser.parse(wrap("""{"items":[]}"""), EntrySource.AI_PHOTO)!!.isEmpty())
        assertNull(GeminiResponseParser.parse(wrap("Ich kann das nicht erkennen."), EntrySource.AI_PHOTO))
        assertNull(GeminiResponseParser.parse("""{"error":{"code":500}}""", EntrySource.AI_PHOTO))
    }

    @Test
    fun `gericht mit zutaten wird aus den zutaten berechnet, getraenk bleibt einzeln`() {
        val json = """{"items":[
            {"name":"Spaghetti Bolognese","amount":400,"unit":"g","kcal":999,"fat":0,"carbs":0,"protein":0,
             "components":[
               {"name":"Spaghetti gekocht","amount":250,"unit":"g","kcal":150,"fat":1,"carbs":30,"protein":5},
               {"name":"Bolognese-Soße","amount":200,"unit":"g","kcal":100,"fat":6,"carbs":4,"protein":8,"salt":1}]},
            {"name":"Cola Zero","amount":330,"unit":"ml","kcal":0.3,"fat":0,"carbs":0,"protein":0,"components":null}]}"""
        val drafts = GeminiResponseParser.parse(wrap(json), EntrySource.AI_PHOTO)!!
        assertEquals(2, drafts.size)
        val dish = drafts[0]
        assertEquals(2, dish.components.size)
        assertEquals(450.0, dish.amount!!, 0.001)
        // (250 × 150 + 200 × 100) / 450 = 127,8 kcal pro 100 g – nicht die 999 der KI
        assertEquals(127.78, dish.per100.kcal, 0.01)
        assertEquals(AmountUnit.GRAM, dish.unit)
        assertTrue(drafts[1].components.isEmpty())
        assertEquals(AmountUnit.MILLILITER, drafts[1].unit)
    }

    @Test
    fun `gericht mit nur einer zutat wird ein normales lebensmittel`() {
        val json = """{"items":[{"name":"Apfel","amount":150,"unit":"g","kcal":52,"fat":0.2,"carbs":12,"protein":0.3,
            "components":[{"name":"Apfel","amount":150,"unit":"g","kcal":52,"fat":0.2,"carbs":12,"protein":0.3}]}]}"""
        val apple = GeminiResponseParser.parse(wrap(json), EntrySource.AI_PHOTO)!!.single()
        assertTrue(apple.components.isEmpty())
        assertEquals(52.0, apple.per100.kcal, 0.001)
    }
}

class MealPlanParserTest {
    @Test
    fun `tage und mahlzeiten werden plan-punkte`() {
        val json = """{"days":[
          {"day":1,"meals":[
            {"meal":"BREAKFAST","items":[{"name":"Haferflocken","amount":60,"unit":"g","kcal":370,"fat":7,"carbs":59,"protein":13.5},
                                         {"name":"Milch","amount":200,"unit":"ml","kcal":64,"fat":3.5,"carbs":4.8,"protein":3.3}]},
            {"meal":"LUNCH","items":[{"name":"Spaghetti Bolognese","amount":400,"unit":"g","kcal":130,"fat":5,"carbs":15,"protein":7,
               "components":[{"name":"Spaghetti","amount":250,"unit":"g","kcal":150,"fat":1,"carbs":30,"protein":5},
                             {"name":"Soße","amount":150,"unit":"g","kcal":100,"fat":6,"carbs":4,"protein":8}]}]}]},
          {"day":2,"meals":[{"meal":"snack","items":[{"name":"Apfel","amount":150,"unit":"g","kcal":52,"fat":0.2,"carbs":12,"protein":0.3}]}]},
          {"day":40,"meals":[{"meal":"SNACK","items":[{"name":"zu weit","amount":1,"unit":"g","kcal":1,"fat":0,"carbs":0,"protein":0}]}]}]}"""
        val items = de.kalorienapp.data.remote.gemini.MealPlanParser.parse(json)!!
        assertEquals(4, items.size)
        val milk = items[1]
        assertEquals(0, milk.dayIndex)
        assertEquals(de.kalorienapp.domain.model.MealType.BREAKFAST, milk.mealType)
        assertEquals(1, milk.position)
        assertEquals(AmountUnit.MILLILITER, milk.unit)
        val dish = items[2]
        assertEquals(2, dish.components.size)
        assertEquals(400.0, dish.amount, 0.001)
        assertEquals(1, items[3].dayIndex)
        assertEquals(de.kalorienapp.domain.model.MealType.SNACK, items[3].mealType)
    }
}
