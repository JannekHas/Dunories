package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.MealPlanSchedule
import de.kalorienapp.domain.model.MealPlanSettings
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MealPlanScheduleTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val week = MealPlanSettings(enabled = true, startEpochDay = monday.toEpochDay(), days = 7)

    @Test
    fun `plan tag zaehlt ab dem start`() {
        assertEquals(0, MealPlanSchedule.dayIndex(week, monday))
        assertEquals(6, MealPlanSchedule.dayIndex(week, monday.plusDays(6)))
        assertNull(MealPlanSchedule.dayIndex(week, monday.minusDays(1)))
        assertNull(MealPlanSchedule.dayIndex(week, monday.plusDays(7)))
        assertNull(MealPlanSchedule.dayIndex(week.copy(enabled = false), monday))
    }

    @Test
    fun `am letzten tag und danach wird gefragt`() {
        assertFalse(MealPlanSchedule.askToExtend(week, monday.plusDays(5)))
        assertTrue(MealPlanSchedule.askToExtend(week, monday.plusDays(6)))
        assertTrue(MealPlanSchedule.askToExtend(week, monday.plusDays(20)))
        assertTrue(MealPlanSchedule.isExpired(week, monday.plusDays(7)))
    }

    @Test
    fun `verlaengern am letzten tag behaelt heute und haengt einen durchlauf an`() {
        val lastDay = monday.plusDays(6)
        val next = MealPlanSchedule.extend(week, lastDay)
        assertEquals(6, MealPlanSchedule.dayIndex(next, lastDay))
        assertEquals(0, MealPlanSchedule.dayIndex(next, lastDay.plusDays(1)))
        assertEquals(monday.plusDays(13), next.end)
        assertFalse(MealPlanSchedule.askToExtend(next, lastDay))
    }

    @Test
    fun `verlaengern nach laengerer pause behaelt den rhythmus und enthaelt heute`() {
        val today = monday.plusDays(17) // Donnerstag der dritten Woche
        val next = MealPlanSchedule.extend(week, today)
        assertEquals(3, MealPlanSchedule.dayIndex(next, today))
        assertEquals(monday.plusDays(14), MealPlanSchedule.cycleStart(next, today))
    }

    @Test
    fun `beenden laesst den letzten tag laufen und schaltet danach aus`() {
        val lastDay = monday.plusDays(6)
        val ending = week.copy(stopAtEnd = true)
        assertEquals(6, MealPlanSchedule.dayIndex(ending, lastDay))
        assertFalse(MealPlanSchedule.askToExtend(ending, lastDay))
        assertEquals(ending, MealPlanSchedule.finishIfEnded(ending, lastDay))
        val off = MealPlanSchedule.finishIfEnded(ending, lastDay.plusDays(1))
        assertFalse(off.enabled)
        assertFalse(off.stopAtEnd)
        // ohne „Beenden“ bleibt er an (und fragt weiter)
        assertTrue(MealPlanSchedule.finishIfEnded(week, lastDay.plusDays(1)).enabled)
    }

    @Test
    fun `eintrag und planpunkt rechnen hin und zurueck`() {
        val entry = FoodEntry(
            date = monday, mealType = MealType.BREAKFAST, name = "Haferflocken", amount = 60.0,
            unit = AmountUnit.GRAM, nutrients = Nutrients(kcal = 222.0, proteinG = 8.1),
        )
        val item = MealPlanItem.fromEntry(entry, dayIndex = 2, position = 0)
        assertEquals(370.0, item.per100.kcal, 0.001)
        val back = item.toEntry(monday.plusDays(2), createdAt = 5)
        assertEquals(222.0, back.nutrients.kcal, 0.001)
        assertEquals(MealType.BREAKFAST, back.mealType)
    }
}
