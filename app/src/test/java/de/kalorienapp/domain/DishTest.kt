package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.Dish
import de.kalorienapp.domain.model.DishComponent
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Test

class DishTest {
    private val pasta = DishComponent("Spaghetti gekocht", 250.0, AmountUnit.GRAM, Nutrients(kcal = 150.0, carbsG = 30.0))
    private val sauce = DishComponent("Bolognese-Soße", 200.0, AmountUnit.GRAM, Nutrients(kcal = 100.0, proteinG = 8.0, saltG = 1.0))

    @Test
    fun `summe aus allen zutaten`() {
        val total = Dish.total(listOf(pasta, sauce))
        assertEquals(575.0, total.kcal, 0.001)
        assertEquals(75.0, total.carbsG, 0.001)
        assertEquals(2.0, total.saltG!!, 0.001)
        assertEquals(450.0, Dish.totalAmount(listOf(pasta, sauce)), 0.001)
    }

    @Test
    fun `gesamtmenge aendern skaliert alle zutaten`() {
        val half = Dish.scale(listOf(pasta, sauce), 225.0)
        assertEquals(125.0, half[0].amount, 0.001)
        assertEquals(100.0, half[1].amount, 0.001)
    }

    @Test
    fun `einheit ml nur wenn alles fluessig ist`() {
        assertEquals(AmountUnit.GRAM, Dish.unit(listOf(pasta, sauce.copy(unit = AmountUnit.MILLILITER))))
        assertEquals(AmountUnit.MILLILITER, Dish.unit(listOf(pasta.copy(unit = AmountUnit.MILLILITER))))
    }
}
