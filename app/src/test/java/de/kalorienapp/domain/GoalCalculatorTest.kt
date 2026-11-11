package de.kalorienapp.domain

import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.Lifestyle
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalCalculatorTest {

    private val year = 2026

    private fun profile(
        sex: Sex = Sex.MALE,
        age: Int = 30,
        heightCm: Double = 180.0,
        weightKg: Double = 80.0,
    ) = Profile(
        sex = sex,
        birthYear = year - age,
        heightCm = heightCm,
        weightKg = weightKg,
    )

    @Test
    fun `bmr male nach Mifflin-St-Jeor`() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780.0, GoalCalculator.bmr(Sex.MALE, 80.0, 180.0, 30), 0.001)
    }

    @Test
    fun `bmr female nach Mifflin-St-Jeor`() {
        // 10*60 + 6.25*165 - 5*25 - 161 = 1345.25
        assertEquals(1345.25, GoalCalculator.bmr(Sex.FEMALE, 60.0, 165.0, 25), 0.001)
    }

    @Test
    fun `basis ist grundumsatz mal alltagsfaktor ohne sport`() {
        // Standard „überwiegend sitzend“: 1780 * 1.4 = 2492
        assertEquals(2492.0, GoalCalculator.maintenanceKcal(profile(), year), 0.001)
        // „überwiegend stehend und gehend“: 1780 * 1.8 = 3204
        val standing = profile().copy(lifestyle = Lifestyle.STANDING)
        assertEquals(3204.0, GoalCalculator.maintenanceKcal(standing, year), 0.001)
    }

    @Test
    fun `targets halten - kcal und makroverteilung`() {
        // Bedarf 1780 * 1.4 = 2492 kcal; Protein 1.6*80 = 128 g; Fett 2492*0.25/9 = 69.2 → 69 g
        // KH = (2492 - 128*4 - 69*9) / 4 = (2492 - 512 - 621) / 4 = 339.75 → 340 g
        val t = GoalCalculator.targets(profile(), year)
        assertEquals(DailyTargets(kcal = 2492, proteinG = 128, carbsG = 340, fatG = 69), t)
    }

    @Test
    fun `abnehmen - anpassung aus dem zielplan und hoeheres protein`() {
        // 2492 - 550 = 1942 kcal; Protein 1.8*80 = 144 g
        val t = GoalCalculator.targets(profile(), year, direction = Goal.LOSE, adjustmentKcal = -550)
        assertEquals(1942, t.kcal)
        assertEquals(144, t.proteinG)
    }

    @Test
    fun `abnehmen - protein bezogen auf das wunschgewicht`() {
        // 98 kg, Wunsch 80 kg: 1.8*80 = 144 g statt 1.8*98 = 176 g
        val t = GoalCalculator.targets(profile(weightKg = 98.0), year, Goal.LOSE, -1100, targetWeightKg = 80.0)
        assertEquals(144, t.proteinG)
    }

    @Test
    fun `zunehmen - protein bleibt beim aktuellen gewicht`() {
        val t = GoalCalculator.targets(profile(weightKg = 70.0), year, Goal.GAIN, 300, targetWeightKg = 80.0)
        assertEquals(126, t.proteinG)
    }

    @Test
    fun `kcal ziel faellt nicht unter minimum`() {
        val p = profile(sex = Sex.FEMALE, age = 80, heightCm = 150.0, weightKg = 40.0)
        assertEquals(GoalCalculator.MIN_KCAL_TARGET, GoalCalculator.targets(p, year, Goal.LOSE, -1100).kcal)
    }

    @Test
    fun `overrides haben vorrang`() {
        val p = profile().copy(
            kcalTargetOverride = 2000,
            proteinOverrideG = 150,
            fatOverrideG = 70,
            carbsOverrideG = 200,
            proteinPerKg = 3.0,
        )
        assertEquals(DailyTargets(kcal = 2000, proteinG = 150, carbsG = 200, fatG = 70), GoalCalculator.targets(p, year))
    }

    @Test
    fun `eigenes protein pro kg und kh aus rest`() {
        // kcal 2000 fest; Protein 2.0*80 = 160; Fett 2000*0.25/9 = 55.56 → 56; KH = (2000-640-504)/4 = 214
        val p = profile().copy(kcalTargetOverride = 2000, proteinPerKg = 2.0)
        assertEquals(DailyTargets(kcal = 2000, proteinG = 160, carbsG = 214, fatG = 56), GoalCalculator.targets(p, year))
    }

    @Test
    fun `verbleibend gleich ziel plus aktivitaet minus gegessen`() {
        val targets = DailyTargets(kcal = 2000, proteinG = 150, carbsG = 200, fatG = 60)
        assertEquals(800, GoalCalculator.daySummary(targets = targets, eaten = Nutrients(kcal = 1200.0)).remainingKcal)
        val withActivity = GoalCalculator.daySummary(targets, Nutrients(kcal = 1200.0), activityKcal = 300)
        assertEquals(1100, withActivity.remainingKcal)
        assertEquals(300, withActivity.activityKcal)
    }
}
