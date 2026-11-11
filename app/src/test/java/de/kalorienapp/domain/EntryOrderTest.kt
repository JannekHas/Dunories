package de.kalorienapp.domain

import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntryOrderTest {
    private val day = LocalDate.of(2026, 11, 11)

    private fun entry(id: Long, meal: MealType, createdAt: Long, sortKey: Long? = null) = FoodEntry(
        id = id,
        date = day,
        mealType = meal,
        name = "e$id",
        amount = 100.0,
        nutrients = Nutrients(100.0, 1.0, 1.0, 1.0),
        createdAt = createdAt,
        sortKey = sortKey,
    )

    @Test
    fun `zwischen zwei eintraegen einsortieren`() {
        val a = entry(1, MealType.BREAKFAST, 1000)
        val b = entry(2, MealType.BREAKFAST, 2000)
        val moved = entry(3, MealType.LUNCH, 5000)
        val result = EntryOrder.place(listOf(a, b), moved, MealType.BREAKFAST, 1)
        assertEquals(listOf(1L, 3L, 2L), result.map { it.id })
        assertTrue(result.all { it.mealType == MealType.BREAKFAST })
        // Schlüssel aufsteigend in Anzeige-Reihenfolge
        assertEquals(result.map { it.orderKey }, result.map { it.orderKey }.sorted())
    }

    @Test
    fun `schluessel bleiben im alten bereich - neue eintraege landen unten`() {
        val a = entry(1, MealType.LUNCH, 1000)
        val moved = entry(2, MealType.LUNCH, 3000)
        val result = EntryOrder.place(listOf(a), moved, MealType.LUNCH, 0)
        assertEquals(listOf(2L, 1L), result.map { it.id })
        val later = entry(9, MealType.LUNCH, 4000)
        assertTrue(result.maxOf { it.orderKey } < later.orderKey)
    }

    @Test
    fun `gleiche zeitstempel werden eindeutig`() {
        val a = entry(1, MealType.SNACK, 1000)
        val b = entry(2, MealType.SNACK, 1000)
        val moved = entry(3, MealType.SNACK, 1000)
        val result = EntryOrder.place(listOf(a, b), moved, MealType.SNACK, 2)
        assertEquals(3, result.map { it.orderKey }.toSet().size)
        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
    }

    @Test
    fun `index ausserhalb wird begrenzt`() {
        val a = entry(1, MealType.DINNER, 1000)
        val moved = entry(2, MealType.LUNCH, 500)
        assertEquals(listOf(1L, 2L), EntryOrder.place(listOf(a), moved, MealType.DINNER, 99).map { it.id })
    }
}
