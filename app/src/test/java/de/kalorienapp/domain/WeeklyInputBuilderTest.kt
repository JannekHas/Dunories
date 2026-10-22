package de.kalorienapp.domain

import de.kalorienapp.data.repository.WeeklyReportRepository
import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.domain.model.WeightEntry
import de.kalorienapp.domain.model.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WeeklyInputBuilderTest {

    private val zone = ZoneOffset.UTC
    private val from = LocalDate.of(2026, 10, 12)
    private val to = from.plusDays(6)
    private val targets = DailyTargets(kcal = 1500, proteinG = 170, carbsG = 120, fatG = 42)

    private fun input(): WeeklyInput {
        val eaten = mapOf(
            from to Nutrients(kcal = 1400.0, proteinG = 150.0, carbsG = 110.0, fatG = 40.0, sugarG = 30.0),
            from.plusDays(1) to Nutrients(kcal = 1900.0, proteinG = 120.0, carbsG = 200.0, fatG = 60.0),
        )
        val activities = listOf(ActivityEntry(date = from.plusDays(1), name = "Fußball", kcal = 400))
        val days = StatsCalculator.days(from, to, eaten, mapOf(from.plusDays(1) to 400)) { targets }
        val entries = listOf(
            FoodEntry(date = from, mealType = MealType.BREAKFAST, name = "Skyr", amount = 500.0, nutrients = Nutrients(kcal = 300.0)),
            FoodEntry(date = from.plusDays(1), mealType = MealType.SNACK, name = "skyr ", amount = 250.0, nutrients = Nutrients(kcal = 150.0)),
            FoodEntry(
                date = from.plusDays(1), mealType = MealType.SNACK, name = "Chips", amount = 175.0,
                nutrients = Nutrients(kcal = 950.0),
                createdAt = from.plusDays(1).atTime(22, 30).toInstant(zone).toEpochMilli(),
            ),
        )
        val weights = listOf(
            WeightEntry(measuredAt = from.minusDays(3).atTime(7, 0).toInstant(zone), weightKg = 98.0),
            WeightEntry(measuredAt = from.plusDays(4).atTime(7, 0).toInstant(zone), weightKg = 97.2),
        )
        return WeeklyInput(
            from = from, to = to,
            profile = Profile(sex = Sex.MALE, birthYear = 1992, heightCm = 175.0, weightKg = 97.2),
            goal = WeightGoal(startDate = from.minusDays(10), startWeightKg = 98.5, targetWeightKg = 92.0, targetDate = from.plusWeeks(10)),
            plan = null,
            targets = targets,
            days = days,
            entries = entries,
            activities = activities,
            weights = weights,
        )
    }

    @Test
    fun `datenblock enthaelt tage lebensmittel und gewicht`() {
        val text = WeeklyInputBuilder.build(input(), zone)
        assertEquals(2, WeeklyInputBuilder.loggedDays(input()))
        assertTrue(text, text.contains("7 Tage, davon 2 mit Einträgen"))
        assertTrue(text, text.contains("1400 / 1500 kcal (im Budget)"))
        // Budget mit Aktivität 1900, gegessen 1900 → im Budget; Aktivität mit Namen
        assertTrue(text, text.contains("1900 / 1900 kcal (im Budget)"))
        assertTrue(text, text.contains("Aktivität: Fußball 400 kcal"))
        assertTrue(text, text.contains("Zucker 30 g"))
        assertTrue(text, text.contains("nichts eingetragen"))
        // Gleicher Name in anderer Schreibweise wird zusammengefasst; sortiert nach kcal
        assertTrue(text, text.contains("- Chips: 1× , zusammen 950 kcal\n- Skyr: 2× , zusammen 450 kcal"))
        assertTrue(text, text.contains("Nach 21 Uhr eingetragen: insgesamt 950 kcal"))
        assertTrue(text, text.contains("letzte Messung davor: 98,0 kg"))
        assertTrue(text, text.contains("2026-10-16: 97,2 kg"))
        assertTrue(text, text.contains("GEWICHTSZIEL: Abnehmen von 98,5 kg"))
    }

    @Test
    fun `letzte vollstaendige woche`() {
        // Donnerstag, 22.10.2026 → Mo 12.10. bis So 18.10.
        assertEquals(
            LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 18),
            WeeklyReportRepository.previousWeek(LocalDate.of(2026, 10, 22)),
        )
        // Montag → die Woche davor
        assertEquals(
            LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 18),
            WeeklyReportRepository.previousWeek(LocalDate.of(2026, 10, 19)),
        )
    }
}
