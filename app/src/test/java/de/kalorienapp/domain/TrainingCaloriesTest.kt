package de.kalorienapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingCaloriesTest {

    private val bmr = 1800.0 // 1,25 kcal pro Minute

    @Test
    fun `grundumsatz der trainingsdauer wird von gesamt abgezogen`() {
        // 60 min Joggen, 600 kcal gesamt − 60 × 1,25 = 75 → 525
        assertEquals(525.0, TrainingCalories.activeKcal(Workout(60.0, totalKcal = 600.0, activeKcal = null), bmr), 0.001)
    }

    @Test
    fun `aktive kalorien der quelle haben vorrang ohne abzug`() {
        assertEquals(480.0, TrainingCalories.activeKcal(Workout(60.0, totalKcal = 600.0, activeKcal = 480.0), bmr), 0.001)
    }

    @Test
    fun `nie negativ und keine daten ergeben 0`() {
        assertEquals(0.0, TrainingCalories.activeKcal(Workout(60.0, totalKcal = 50.0, activeKcal = null), bmr), 0.001)
        assertEquals(0.0, TrainingCalories.activeKcal(Workout(30.0, totalKcal = null, activeKcal = null), bmr), 0.001)
    }

    @Test
    fun `tagessumme ueber mehrere trainings`() {
        val workouts = listOf(
            Workout(60.0, totalKcal = 600.0, activeKcal = null), // 525
            Workout(10.0, totalKcal = 62.5, activeKcal = null), // 62,5 − 12,5 = 50
        )
        assertEquals(575, TrainingCalories.dayTotal(workouts, bmr))
    }
}
