package de.kalorienapp.domain

import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BehaviorTipsTest {

    private val today = LocalDate.of(2026, 11, 21)

    private fun entry(
        name: String,
        date: LocalDate = today,
        meal: MealType = MealType.BREAKFAST,
        createdAt: Long = 0,
        source: EntrySource = EntrySource.MANUAL,
    ) = FoodEntry(date = date, mealType = meal, name = name, amount = 100.0, nutrients = Nutrients.ZERO, source = source, createdAt = createdAt)

    @Test
    fun `favorit ab dem dritten mal in 14 tagen`() {
        val two = listOf(entry("Skyr", today.minusDays(3)), entry("skyr ", createdAt = 5))
        assertNull(BehaviorTips.favoriteCandidate(two, today, emptySet()))
        val three = two + entry("Skyr", today.minusDays(10))
        assertEquals("skyr ", BehaviorTips.favoriteCandidate(three, today, emptySet())?.name)
        // schon Favorit → nichts
        assertNull(BehaviorTips.favoriteCandidate(three, today, setOf("skyr")))
        // zu alt zählt nicht
        val old = two + entry("Skyr", today.minusDays(20))
        assertNull(BehaviorTips.favoriteCandidate(old, today, emptySet()))
    }

    @Test
    fun `favorit nur fuer heute eingetragenes`() {
        val past = List(3) { entry("Skyr", today.minusDays(it + 1L)) }
        assertNull(BehaviorTips.favoriteCandidate(past, today, emptySet()))
    }

    @Test
    fun `kombi wenn dieselbe zusammenstellung schon an einem frueheren tag in der mahlzeit war`() {
        val earlier = listOf(entry("Haferflocken", today.minusDays(2)), entry("Milch", today.minusDays(2)), entry("Banane", today.minusDays(2)))
        val now = listOf(entry("Haferflocken"), entry("Milch"))
        val result = BehaviorTips.comboCandidate(earlier + now, today, emptySet())
        assertEquals(MealType.BREAKFAST, result?.first)
        assertEquals(2, result?.second?.size)
        // andere Mahlzeit früher → keine Kombi
        val lunch = earlier.map { it.copy(mealType = MealType.LUNCH) }
        assertNull(BehaviorTips.comboCandidate(lunch + now, today, emptySet()))
        // schon gespeichert → nichts
        assertNull(BehaviorTips.comboCandidate(earlier + now, today, setOf(setOf("haferflocken", "milch"))))
        // nur ein Eintrag → keine Kombi
        assertNull(BehaviorTips.comboCandidate(earlier + now.take(1), today, emptySet()))
    }

    @Test
    fun `drei einzelne fotos fuer eine mahlzeit`() {
        val min = 60_000L
        fun photo(t: Long) = entry("x", createdAt = t, source = EntrySource.AI_PHOTO)
        // Ein Foto mit drei Dingen (ms auseinander) zählt als eine Aufnahme
        assertFalse(BehaviorTips.manySinglePhotos(listOf(photo(0), photo(1), photo(2))))
        assertTrue(BehaviorTips.manySinglePhotos(listOf(photo(0), photo(2 * min), photo(4 * min))))
        // über 10 Minuten verteilt → nicht
        assertFalse(BehaviorTips.manySinglePhotos(listOf(photo(0), photo(6 * min), photo(12 * min))))
        // andere Quelle zählt nicht
        assertFalse(BehaviorTips.manySinglePhotos(listOf(photo(0), photo(2 * min), entry("x", createdAt = 4 * min))))
    }

    @Test
    fun `geloescht und in anderer mahlzeit neu eingetragen`() {
        val deleted = DeletedEntry("Apfel", today, MealType.BREAKFAST, deletedAt = 1_000)
        assertTrue(BehaviorTips.reEnteredElsewhere(deleted, listOf(entry("apfel", meal = MealType.SNACK, createdAt = 60_000))))
        assertFalse(BehaviorTips.reEnteredElsewhere(deleted, listOf(entry("Apfel", meal = MealType.BREAKFAST, createdAt = 60_000))))
        assertFalse(BehaviorTips.reEnteredElsewhere(deleted, listOf(entry("Apfel", meal = MealType.SNACK, createdAt = 500))))
        assertFalse(BehaviorTips.reEnteredElsewhere(null, listOf(entry("Apfel", meal = MealType.SNACK))))
    }

    @Test
    fun `wasser vorschlagen ab dreimal an zwei tagen`() {
        assertTrue(BehaviorTips.isWater("Mineralwasser"))
        assertTrue(BehaviorTips.isWater("Wasser still"))
        assertTrue(BehaviorTips.isWater("Sprudel"))
        assertFalse(BehaviorTips.isWater("Wassermelone"))
        val sameDay = listOf(entry("Wasser"), entry("Wasser"), entry("Wasser"))
        assertFalse(BehaviorTips.waterCandidate(sameDay, today)) // nur ein Tag
        val spread = listOf(entry("Wasser"), entry("Mineralwasser", today.minusDays(2)), entry("Sprudel", today.minusDays(5)))
        assertTrue(BehaviorTips.waterCandidate(spread, today))
        val old = listOf(entry("Wasser"), entry("Wasser", today.minusDays(20)), entry("Wasser", today.minusDays(30)))
        assertFalse(BehaviorTips.waterCandidate(old, today))
    }

    @Test
    fun `ernaehrungsplan wenn zwei mahlzeiten sich wiederholen`() {
        fun day(d: Long) = listOf(
            entry("Haferflocken", today.minusDays(d), MealType.BREAKFAST),
            entry("Milch", today.minusDays(d), MealType.BREAKFAST),
            entry("Hähnchen mit Reis", today.minusDays(d), MealType.LUNCH),
        )
        // nur zwei Tage gleich → noch nicht
        assertNull(BehaviorTips.planCandidate(day(1) + day(2), today))
        // drei Tage gleich in Frühstück und Mittag → Plan
        assertEquals(MealType.BREAKFAST, BehaviorTips.planCandidate(day(1) + day(2) + day(3), today))
        // nur eine Mahlzeit wiederholt sich → kein Plan (dafür gibt es Kombis)
        val breakfastOnly = (1L..4L).flatMap { d -> listOf(entry("Toast", today.minusDays(d)), entry("Pizza $d", today.minusDays(d), MealType.DINNER)) }
        assertNull(BehaviorTips.planCandidate(breakfastOnly, today))
    }
}
