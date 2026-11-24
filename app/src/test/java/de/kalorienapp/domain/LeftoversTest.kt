package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.DishComponent
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.Leftover
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LeftoversTest {
    private val day = LocalDate.of(2026, 11, 23)

    private fun entry(id: Long, name: String, amount: Double?, kcal: Double, source: EntrySource = EntrySource.BARCODE, created: Long = id) =
        FoodEntry(
            id = id, date = day, mealType = MealType.DINNER, name = name, amount = amount,
            nutrients = Nutrients(kcal = kcal, proteinG = kcal / 10), source = source, createdAt = created,
        )

    private val mince = entry(1, "Hackfleisch", 500.0, 1200.0, created = 100)
    private val beans = entry(2, "Kidneybohnen", 400.0, 400.0, created = 200)
    private val rice = entry(3, "Reis", 600.0, 800.0, created = 300)

    @Test
    fun `zusammenfassen mit haelfte gegessen`() {
        val split = Leftovers.group(listOf(beans, mince, rice), "Chili", eatenAmount = 750.0, now = 999)!!
        assertEquals("Chili", split.eaten.name)
        assertTrue(split.eaten.isDish)
        assertEquals(3, split.eaten.components.size)
        assertEquals(750.0, split.eaten.amount!!, 0.001)
        assertEquals(1200.0, split.eaten.nutrients.kcal, 0.001)
        assertEquals(100L, split.eaten.createdAt) // Fasten-Uhrzeit des ersten Eintrags
        assertEquals(EntrySource.BARCODE, split.eaten.source)
        val rest = split.leftover!!
        assertEquals(750.0, rest.amount, 0.001)
        assertEquals(1200.0, rest.nutrients.kcal, 0.001)
        assertEquals(day, rest.createdDay)
        assertEquals(MealType.DINNER, rest.mealType)
        assertEquals(750.0, rest.portionAmount!!, 0.001)
    }

    @Test
    fun `alles gegessen laesst keinen rest`() {
        val split = Leftovers.group(listOf(mince, beans), "Chili", eatenAmount = 900.0, now = 0)!!
        assertNull(split.leftover)
        assertEquals(1600.0, split.eaten.nutrients.kcal, 0.001)
    }

    @Test
    fun `winziger rest zaehlt als aufgegessen`() {
        val (_, rest) = Leftovers.split(listOf(DishComponent("Suppe", 1000.0, AmountUnit.MILLILITER, Nutrients(kcal = 50.0))), 995.0)
        assertTrue(rest.isEmpty())
    }

    @Test
    fun `eintraege ohne menge werden nicht zusammengefasst`() {
        val legacy = entry(4, "Alt", null, 300.0)
        val split = Leftovers.group(listOf(mince, legacy, beans), "Chili", 900.0, 0)!!
        assertEquals(1600.0, split.eaten.nutrients.kcal, 0.001)
        assertEquals(2, Leftovers.groupable(listOf(mince, legacy, beans)).size)
    }

    @Test
    fun `gerichte werden in ihre zutaten aufgeloest`() {
        val dish = Leftovers.group(listOf(mince, beans), "Basis", 900.0, 0)!!.eaten.copy(id = 9)
        val split = Leftovers.group(listOf(dish, rice), "Chili", 1500.0, 0)!!
        assertEquals(3, split.eaten.components.size)
        assertEquals(2400.0, split.eaten.nutrients.kcal, 0.001)
    }

    @Test
    fun `gemischte quellen`() {
        assertEquals(EntrySource.MANUAL, Leftovers.combinedSource(listOf(mince, mince.copy(source = EntrySource.LABEL))))
        assertEquals(EntrySource.AI_TEXT, Leftovers.combinedSource(listOf(mince, mince.copy(source = EntrySource.AI_PHOTO))))
    }

    @Test
    fun `nicht alles gegessen bei einzelnem lebensmittel`() {
        val split = Leftovers.partial(mince.copy(sortKey = 5), eatenAmount = 200.0, now = 0)!!
        assertEquals(1L, split.eaten.id)
        assertFalse(split.eaten.isDish)
        assertEquals(200.0, split.eaten.amount!!, 0.001)
        assertEquals(480.0, split.eaten.nutrients.kcal, 0.001)
        assertEquals(5L, split.eaten.sortKey)
        assertEquals(720.0, split.leftover!!.nutrients.kcal, 0.001)
        assertEquals("Hackfleisch", split.leftover!!.name)
    }

    @Test
    fun `rest teilweise und dann ganz uebernehmen`() {
        val rest = Leftovers.group(listOf(mince, beans, rice), "Chili", 750.0, 0)!!.leftover!!.copy(id = 7, dismissedDay = day)
        val next = day.plusDays(1)
        val first = Leftovers.take(rest, 300.0, next, MealType.LUNCH, now = 50)
        assertEquals(next, first.entry.date)
        assertEquals(MealType.LUNCH, first.entry.mealType)
        assertEquals("Chili", first.entry.name)
        assertEquals(480.0, first.entry.nutrients.kcal, 0.001)
        val remaining = first.remaining!!
        assertEquals(7L, remaining.id)
        assertEquals(450.0, remaining.amount, 0.001)
        assertEquals(300.0, remaining.portionAmount!!, 0.001)
        assertNull(remaining.dismissedDay)
        val second = Leftovers.take(remaining, 450.0, next, MealType.DINNER, now = 60)
        assertNull(second.remaining)
        assertEquals(720.0, second.entry.nutrients.kcal, 0.001)
    }

    @Test
    fun `vorauswahl ist die letzte portion hoechstens der rest`() {
        val rest = Leftover(name = "Chili", components = listOf(DishComponent("Chili", 400.0, AmountUnit.GRAM, Nutrients())), createdDay = day, mealType = MealType.DINNER, portionAmount = 750.0)
        assertEquals(400.0, Leftovers.defaultPortion(rest), 0.001)
        assertEquals(250.0, Leftovers.defaultPortion(rest.copy(portionAmount = 250.0)), 0.001)
        assertEquals(400.0, Leftovers.defaultPortion(rest.copy(portionAmount = null)), 0.001)
    }

    private val open = Leftover(name = "Chili", components = emptyList(), createdDay = day, mealType = MealType.LUNCH)

    @Test
    fun `vorschlag erst nach der eigenen mahlzeit`() {
        assertFalse(Leftovers.suggestOn(open, day, MealType.LUNCH))
        assertTrue(Leftovers.suggestOn(open, day, MealType.DINNER))
        assertTrue(Leftovers.suggestOn(open, day.plusDays(1), MealType.LUNCH))
        assertFalse(Leftovers.suggestOn(open, day.minusDays(1), MealType.DINNER))
        assertFalse(Leftovers.suggestOn(open.copy(frozen = true), day.plusDays(1), MealType.LUNCH))
        assertFalse(Leftovers.suggestOn(open.copy(dismissedDay = day.plusDays(1)), day.plusDays(1), MealType.LUNCH))
        assertTrue(Leftovers.suggestOn(open.copy(dismissedDay = day), day.plusDays(1), MealType.LUNCH))
    }

    @Test
    fun `vorschlag in leerer mittag- oder abendmahlzeit`() {
        val all = setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
        assertEquals(MealType.LUNCH, Leftovers.suggestionMeal(11, all))
        assertEquals(MealType.DINNER, Leftovers.suggestionMeal(11, all - MealType.LUNCH))
        assertEquals(MealType.DINNER, Leftovers.suggestionMeal(16, all))
        assertNull(Leftovers.suggestionMeal(16, all - MealType.DINNER))
        assertNull(Leftovers.suggestionMeal(9, setOf(MealType.BREAKFAST, MealType.SNACK)))
    }

    @Test
    fun `nach vier tagen fragen ob noch da`() {
        assertFalse(Leftovers.needsCheck(open, day.plusDays(3)))
        assertTrue(Leftovers.needsCheck(open, day.plusDays(4)))
        assertFalse(Leftovers.needsCheck(open.copy(frozen = true), day.plusDays(10)))
        assertFalse(Leftovers.needsCheck(open.copy(checkedDay = day.plusDays(4)), day.plusDays(5)))
        assertNotNull(Leftovers.daysOpen(open, day.plusDays(5)))
    }
}
