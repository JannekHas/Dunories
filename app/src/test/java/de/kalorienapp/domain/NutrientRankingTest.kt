package de.kalorienapp.domain

import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NutrientRankingTest {

    private fun entry(name: String, fat: Double, sugar: Double? = null) = FoodEntry(
        date = LocalDate.of(2026, 11, 13),
        mealType = MealType.LUNCH,
        name = name,
        amount = 100.0,
        nutrients = Nutrients(kcal = 100.0, fatG = fat, sugarG = sugar),
    )

    @Test
    fun `sortiert absteigend mit Anteil an der Tagessumme`() {
        val ranked = NutrientRanking.rank(listOf(entry("Brot", 2.0), entry("Pizza", 30.0), entry("Nüsse", 18.0)), RankedNutrient.FAT)
        assertEquals(listOf("Pizza", "Nüsse", "Brot"), ranked.map { it.entry.name })
        assertEquals(0.6, ranked.first().share, 1e-9)
    }

    @Test
    fun `Eintraege ohne Angabe stehen am Ende`() {
        val ranked = NutrientRanking.rank(
            listOf(entry("A", 0.0, sugar = null), entry("B", 0.0, sugar = 0.0), entry("C", 0.0, sugar = 12.0)),
            RankedNutrient.SUGAR,
        )
        assertEquals(listOf("C", "B", "A"), ranked.map { it.entry.name })
        assertNull(ranked.last().value)
    }

    @Test
    fun `Summe ist null wenn nichts angegeben`() {
        assertNull(NutrientRanking.total(listOf(entry("A", 1.0)), RankedNutrient.SUGAR))
        assertEquals(1.0, NutrientRanking.total(listOf(entry("A", 1.0)), RankedNutrient.FAT)!!, 1e-9)
    }
}
