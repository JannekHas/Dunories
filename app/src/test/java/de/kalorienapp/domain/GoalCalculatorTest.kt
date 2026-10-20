package de.kalorienapp.domain

import de.kalorienapp.domain.model.ActivityLevel
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Pace
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
        goal: Goal = Goal.MAINTAIN,
        pace: Pace = Pace.NORMAL,
        useHealthConnect: Boolean = false,
        activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    ) = Profile(
        sex = sex,
        birthYear = year - age,
        heightCm = heightCm,
        weightKg = weightKg,
        goal = goal,
        pace = pace,
        useHealthConnect = useHealthConnect,
        activityLevel = activityLevel,
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
    fun `manuelles Aktivitaetslevel wird verwendet`() {
        val p = profile(activityLevel = ActivityLevel.MODERATE)
        assertEquals(1.55, GoalCalculator.activityFactor(p), 0.0001)
    }

    @Test
    fun `mit Health Connect wird immer sitzend gerechnet`() {
        val p = profile(useHealthConnect = true, activityLevel = ActivityLevel.VERY_ACTIVE)
        assertEquals(1.2, GoalCalculator.activityFactor(p), 0.0001)
    }

    @Test
    fun `ziel anpassung nach tempo`() {
        assertEquals(-300, GoalCalculator.goalAdjustment(Goal.LOSE, Pace.SLOW))
        assertEquals(-500, GoalCalculator.goalAdjustment(Goal.LOSE, Pace.NORMAL))
        assertEquals(0, GoalCalculator.goalAdjustment(Goal.MAINTAIN, Pace.NORMAL))
        assertEquals(250, GoalCalculator.goalAdjustment(Goal.GAIN, Pace.SLOW))
        assertEquals(400, GoalCalculator.goalAdjustment(Goal.GAIN, Pace.NORMAL))
    }

    @Test
    fun `targets halten - kcal und makroverteilung`() {
        // 1780 * 1.2 = 2136 kcal; Protein 1.6*80 = 128 g; Fett 2136*0.25/9 = 59.33 → 59 g
        // KH = (2136 - 128*4 - 59*9) / 4 = (2136 - 512 - 531) / 4 = 273.25 → 273 g
        val t = GoalCalculator.targets(profile(), year)
        assertEquals(DailyTargets(kcal = 2136, proteinG = 128, carbsG = 273, fatG = 59), t)
    }

    @Test
    fun `targets abnehmen - defizit und hoeheres protein`() {
        // 2136 - 500 = 1636 kcal; Protein 1.8*80 = 144 g
        val t = GoalCalculator.targets(profile(goal = Goal.LOSE, pace = Pace.NORMAL), year)
        assertEquals(1636, t.kcal)
        assertEquals(144, t.proteinG)
    }

    @Test
    fun `kcal ziel faellt nicht unter minimum`() {
        val p = profile(sex = Sex.FEMALE, age = 80, heightCm = 150.0, weightKg = 40.0, goal = Goal.LOSE)
        assertEquals(GoalCalculator.MIN_KCAL_TARGET, GoalCalculator.targets(p, year).kcal)
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
    fun `gutschrift aktiver kalorien`() {
        assertEquals(300, GoalCalculator.creditedActiveKcal(500, 60))
        assertEquals(0, GoalCalculator.creditedActiveKcal(500, 0))
        assertEquals(500, GoalCalculator.creditedActiveKcal(500, 150))
    }

    @Test
    fun `verbleibend gleich ziel plus gutschrift minus gegessen`() {
        val targets = DailyTargets(kcal = 2000, proteinG = 150, carbsG = 200, fatG = 60)
        val summary = GoalCalculator.daySummary(
            targets = targets,
            eaten = Nutrients(kcal = 1200.0),
            activeKcal = 400,
            creditPercent = 60,
        )
        assertEquals(240, summary.creditedActiveKcal)
        assertEquals(2000 + 240 - 1200, summary.remainingKcal)
    }
}
