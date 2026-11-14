package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.DishComponent
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EntryMergeTest {
    private val day = LocalDate.of(2026, 11, 14)
    private val min = 60_000L

    private fun stick(id: Long, at: Long, meal: MealType = MealType.SNACK, grams: Double = 10.0, lastAdded: Long? = null) = FoodEntry(
        id = id, date = day, mealType = meal, name = "Salami-Stick", amount = grams,
        nutrients = Nutrients(kcal = 50.0, proteinG = 2.5, carbsG = 0.1, fatG = 4.5, saltG = 0.4).times(grams / 10.0),
        createdAt = at, lastAddedAt = lastAdded,
    )

    @Test
    fun `gleicher snack kurz danach wird addiert`() {
        val first = stick(1, at = 0)
        val second = stick(0, at = 5 * min)
        val target = EntryMerge.findTarget(listOf(first), second, now = 5 * min)!!
        val merged = EntryMerge.merge(target, second, now = 5 * min)
        assertEquals(20.0, merged.amount!!, 0.001)
        assertEquals(100.0, merged.nutrients.kcal, 0.001)
        assertEquals(0.8, merged.nutrients.saltG!!, 0.001)
        assertEquals(0L, merged.createdAt)
        assertEquals(5 * min, merged.lastAddedAt)
    }

    @Test
    fun `fenster zaehlt ab der letzten ergaenzung`() {
        val merged = stick(1, at = 0, grams = 20.0, lastAdded = 25 * min)
        assertEquals(1L, EntryMerge.findTarget(listOf(merged), stick(0, 50 * min), now = 50 * min)?.id)
    }

    @Test
    fun `nach mehr als 30 minuten bleibt es getrennt`() {
        assertNull(EntryMerge.findTarget(listOf(stick(1, at = 0)), stick(0, 31 * min), now = 31 * min))
    }

    @Test
    fun `andere mahlzeit, anderer name oder andere naehrwerte bleiben getrennt`() {
        val existing = listOf(stick(1, at = 0))
        assertNull(EntryMerge.findTarget(existing, stick(0, min, meal = MealType.DINNER), now = min))
        assertNull(EntryMerge.findTarget(existing, stick(0, min).copy(name = "Salami"), now = min))
        val light = stick(0, min).let { it.copy(nutrients = it.nutrients.copy(fatG = 2.0)) }
        assertNull(EntryMerge.findTarget(existing, light, now = min))
    }

    @Test
    fun `gross und kleinschreibung und andere menge sind egal`() {
        val other = stick(0, min, grams = 25.0).copy(name = " salami-stick")
        assertEquals(1L, EntryMerge.findTarget(listOf(stick(1, at = 0)), other, now = min)?.id)
    }

    @Test
    fun `gerichte werden nie zusammengefasst`() {
        val component = DishComponent("Nudeln", 100.0, AmountUnit.GRAM, Nutrients(kcal = 150.0))
        val dish = stick(1, at = 0).copy(components = listOf(component))
        assertNull(EntryMerge.findTarget(listOf(dish), stick(0, min), now = min))
        assertNull(EntryMerge.findTarget(listOf(stick(1, at = 0)), stick(0, min).copy(components = listOf(component)), now = min))
    }
}
