package de.kalorienapp.ui.food

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.DishComponent
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Test

class DishFormTest {
    private var id = 1L
    private val dish = DishForm.of(
        "Spaghetti Bolognese",
        listOf(
            DishComponent("Spaghetti", 250.0, AmountUnit.GRAM, Nutrients(kcal = 158.0)),
            DishComponent("Soße", 200.0, AmountUnit.GRAM, Nutrients(kcal = 100.0)),
        ),
    ) { id++ }

    @Test
    fun `gesamtmenge tippen rechnet immer vom ausgangspunkt`() {
        // Tippen von „225“: erst 2, dann 22, dann 225 – darf keine Rundungsfehler aufsummieren
        val typed = dish.withAmount("").withAmount("2").withAmount("22").withAmount("225")
        assertEquals("125", typed.parts[0].form.amount)
        assertEquals("100", typed.parts[1].form.amount)
        assertEquals(225.0, typed.totalAmount, 0.001)
        assertEquals("225", typed.amountText)
    }

    @Test
    fun `zutat aendern beendet die umrechnung`() {
        val typed = dish.withAmount("225")
        val changed = typed.withPart(typed.parts[1].id, typed.parts[1].form.copy(amount = "150"))
        assertEquals(275.0, changed.totalAmount, 0.001)
        assertEquals("275", changed.amountText)
        assertEquals("550", changed.withAmount("550").parts[0].form.amount.let { (it.toDouble() + changed.withAmount("550").parts[1].form.amount.toDouble()).toInt().toString() })
    }
}
