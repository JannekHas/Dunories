package de.kalorienapp.domain

import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.GoalEndReason
import de.kalorienapp.domain.model.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalPlannerTest {

    private val today = LocalDate.of(2026, 10, 22)

    private fun goal(start: Double, target: Double, weeks: Long?) = WeightGoal(
        startDate = today,
        startWeightKg = start,
        targetWeightKg = target,
        targetDate = weeks?.let { today.plusWeeks(it) },
    )

    @Test
    fun `richtung aus wunschgewicht`() {
        assertEquals(Goal.LOSE, goal(98.0, 92.0, 12).direction)
        assertEquals(Goal.GAIN, goal(70.0, 74.0, 12).direction)
        assertEquals(Goal.MAINTAIN, goal(80.0, 80.05, null).direction)
    }

    @Test
    fun `abnehmen im gesunden tempo`() {
        // 6 kg in 84 Tagen: −6 × 7700 / 84 = −550 kcal/Tag ≈ −0,5 kg/Woche
        val plan = GoalPlanner.plan(goal(98.0, 92.0, 12), 98.0, today)
        assertEquals(-550, plan.dailyAdjustmentKcal)
        assertEquals(-0.5, plan.kgPerWeek, 0.001)
        assertFalse(plan.limited)
        assertNull(plan.realisticDate)
    }

    @Test
    fun `zu schnelles abnehmen wird auf 1 kg pro woche begrenzt`() {
        // 10 kg in 4 Wochen wären 2,5 kg/Woche → begrenzt auf 1 kg/Woche = −1100 kcal/Tag
        val plan = GoalPlanner.plan(goal(100.0, 90.0, 4), 100.0, today)
        assertEquals(-1100, plan.dailyAdjustmentKcal)
        assertTrue(plan.limited)
        assertEquals(today.plusDays(70), plan.realisticDate) // 10 Wochen
    }

    @Test
    fun `zu schnelles zunehmen wird auf 0,5 kg pro woche begrenzt`() {
        val plan = GoalPlanner.plan(goal(70.0, 75.0, 2), 70.0, today)
        assertEquals(550, plan.dailyAdjustmentKcal)
        assertTrue(plan.limited)
    }

    @Test
    fun `wer vorne liegt behaelt das geplante tempo`() {
        val g = goal(98.0, 92.0, 12)
        // Nach 6 Wochen schon 4 kg runter: rechnerisch nur noch −367 kcal/Tag nötig – es bleibt bei −550
        val plan = GoalPlanner.plan(g, 94.0, today.plusWeeks(6))
        assertEquals(-550, plan.dailyAdjustmentKcal)
        assertFalse(plan.limited)
    }

    @Test
    fun `wer hinten liegt bekommt ein groesseres defizit`() {
        val g = goal(98.0, 92.0, 12)
        // Nach 6 Wochen erst 1 kg runter: 5 kg in 42 Tagen = −917 kcal/Tag
        val plan = GoalPlanner.plan(g, 97.0, today.plusWeeks(6))
        assertEquals(-917, plan.dailyAdjustmentKcal)
    }

    @Test
    fun `zunehmen - wer vorne liegt behaelt das geplante tempo`() {
        val g = goal(70.0, 73.0, 12)
        // geplant 3 kg in 84 Tagen = +275 kcal/Tag
        assertEquals(275, GoalPlanner.plan(g, 72.5, today.plusWeeks(6)).dailyAdjustmentKcal)
    }

    @Test
    fun `halten hat keine anpassung`() {
        val plan = GoalPlanner.plan(goal(80.0, 80.0, null), 81.0, today)
        assertEquals(0, plan.dailyAdjustmentKcal)
    }

    @Test
    fun `ziel endet bei erreichtem gewicht oder datum`() {
        val g = goal(98.0, 92.0, 12)
        assertNull(GoalPlanner.endReason(g, 95.0, today.plusWeeks(4)))
        assertEquals(GoalEndReason.REACHED, GoalPlanner.endReason(g, 91.8, today.plusWeeks(8)))
        assertEquals(GoalEndReason.DATE_PASSED, GoalPlanner.endReason(g, 93.0, today.plusWeeks(12)))
        assertNull(GoalPlanner.endReason(goal(80.0, 80.0, null), 85.0, today.plusYears(1)))
    }
}
