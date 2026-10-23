package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.RecipeIngredient
import de.kalorienapp.domain.model.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListTest {

    @Test
    fun `zutat mit portionsfaktor`() {
        val eggs = RecipeIngredient(name = "Eier", quantity = 2.0, quantityUnit = "", amount = 110.0)
        val item = ShoppingList.fromIngredient(eggs, 1.5, "Rührei")
        assertEquals(3.0, item.quantity!!, 0.001)
        assertEquals("", item.unit)
        assertEquals(listOf("Rührei"), item.sources)
    }

    @Test
    fun `ohne rezeptmenge zaehlt das gewicht, nach geschmack bleibt leer`() {
        val milk = RecipeIngredient(name = "Milch", amount = 200.0, unit = AmountUnit.MILLILITER)
        assertEquals("ml", ShoppingList.fromIngredient(milk, 1.0, "X").unit)
        val salt = RecipeIngredient(name = "Salz", quantityUnit = "Prise")
        assertNull(ShoppingList.fromIngredient(salt, 2.0, "X").quantity)
    }

    @Test
    fun `gleiche artikel werden zusammengerechnet`() {
        val existing = listOf(
            ShoppingItem(id = 1, name = "Feta", quantity = 200.0, unit = "g", sources = listOf("Feta-Nudeln")),
            ShoppingItem(id = 2, name = "Milch", quantity = 1.0, unit = "l", checked = true),
        )
        val merge = ShoppingList.merge(
            existing,
            listOf(
                ShoppingItem(name = "feta ", quantity = 150.0, unit = "G", sources = listOf("Salat")),
                ShoppingItem(name = "Milch", quantity = 1.0, unit = "l", sources = listOf("Pfannkuchen")),
                ShoppingItem(name = "Eier", quantity = 2.0, sources = listOf("Pfannkuchen")),
                ShoppingItem(name = "Eier", quantity = 3.0, sources = listOf("Rührei")),
            ),
        )
        // Feta addiert, Quellen gesammelt
        assertEquals(1, merge.updated.size)
        assertEquals(350.0, merge.updated.single().quantity!!, 0.001)
        assertEquals(listOf("Feta-Nudeln", "Salat"), merge.updated.single().sources)
        // Abgehakte Milch bleibt unberührt → neue Milch; Eier aus zwei Rezepten zusammen
        assertEquals(listOf("Milch", "Eier"), merge.inserted.map { it.name })
        assertEquals(5.0, merge.inserted[1].quantity!!, 0.001)
    }

    @Test
    fun `andere einheit bleibt getrennt`() {
        val merge = ShoppingList.merge(
            listOf(ShoppingItem(id = 1, name = "Zucker", quantity = 1.0, unit = "EL")),
            listOf(ShoppingItem(name = "Zucker", quantity = 100.0, unit = "g")),
        )
        assertTrue(merge.updated.isEmpty())
        assertEquals(1, merge.inserted.size)
    }

    @Test
    fun `teilen nur offene artikel`() {
        val text = ShoppingList.shareText(
            listOf(ShoppingItem(name = "Feta", quantity = 350.0, unit = "g"), ShoppingItem(name = "Milch", checked = true)),
            "Einkaufsliste",
        ) { "${it.quantity?.toInt()} ${it.unit} ${it.name}" }
        assertEquals("Einkaufsliste\n• 350 g Feta", text)
    }
}
