package de.kalorienapp.domain

import de.kalorienapp.data.repository.WeeklyReportRepository
import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.Budget
import de.kalorienapp.domain.model.CookingTime
import de.kalorienapp.domain.model.DietStyle
import de.kalorienapp.domain.model.ExcludedFood
import de.kalorienapp.domain.model.Intolerance
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.FoodPreferences
import de.kalorienapp.domain.model.FoodSuggestion
import de.kalorienapp.domain.model.WeeklyReport
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
            FoodEntry(date = from, mealType = MealType.BREAKFAST, name = "Skyr", amount = 500.0, nutrients = Nutrients(kcal = 300.0),
                createdAt = from.atTime(8, 0).toInstant(zone).toEpochMilli()),
            FoodEntry(date = from.plusDays(1), mealType = MealType.SNACK, name = "skyr ", amount = 250.0, nutrients = Nutrients(kcal = 150.0),
                createdAt = from.plusDays(1).atTime(15, 0).toInstant(zone).toEpochMilli()),
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
    fun `vorlieben stehen verbindlich im datenblock`() {
        val prefs = FoodPreferences(
            style = DietStyle.PESCETARIAN,
            // Schwein ist bei pescetarisch ohnehin ausgeschlossen → taucht nicht doppelt auf
            excluded = setOf(ExcludedFood.PORK, ExcludedFood.SEAFOOD),
            intolerances = setOf(Intolerance.LACTOSE, Intolerance.PEANUTS),
            cooking = CookingTime.LITTLE,
            budget = Budget.SMALL,
            dislikes = listOf("Tofu", "Magerquark"),
        )
        val text = WeeklyInputBuilder.build(input().copy(preferences = prefs), zone)
        assertTrue(text, text.contains("VORLIEBEN (verbindlich für Vorschläge):"))
        assertTrue(text, text.contains("- Ernährungsweise: pescetarisch (kein Fleisch, aber Fisch)"))
        assertTrue(text, text.contains("- Isst kein: Meeresfrüchte\n"))
        assertTrue(text, text.contains("- Unverträglichkeiten (meiden): Laktose"))
        assertTrue(text, text.contains("- ALLERGIEN (strikt ausschließen, auch Spuren): Erdnüsse"))
        assertTrue(text, text.contains("- Alltag: wenig Zeit zum Kochen, kleines Budget"))
        assertTrue(text, text.contains("- Mag nicht: Tofu, Magerquark"))
        assertTrue(WeeklyInputBuilder.build(input(), zone).contains("VORLIEBEN").not())
    }

    @Test
    fun `export enthaelt alle eintraege je tag und mahlzeit und die auswertung`() {
        val report = WeeklyReport(
            from = from, to = to, headline = "Gute Woche", summary = "Passt.", wins = listOf("Protein"),
            improvements = emptyList(), tips = emptyList(), goalNote = null,
            foodsMore = listOf(FoodSuggestion("Linsen", "Ballaststoffe")),
        )
        val text = WeeklyExport.build(input(), report, zone)
        assertTrue(text, text.startsWith("# Meine Ernährungsdaten aus der App Dunories (2026-10-12 bis 2026-10-18)"))
        assertTrue(text, text.contains("### Montag, 2026-10-12\nFrühstück:\n- Skyr, 500 g: 300 kcal · Fett 0 g · KH 0 g · Eiweiß 0 g"))
        assertTrue(text, text.contains("### Dienstag, 2026-10-13\nSnacks:\n- skyr, 250 g: 150 kcal"))
        assertTrue(text, text.contains("### Mittwoch, 2026-10-14\n(nichts eingetragen)"))
        assertTrue(text, text.contains("Öfter essen: Linsen (Ballaststoffe)"))
    }

    @Test
    fun `exportzeile mit zusatzwerten`() {
        val entry = FoodEntry(
            date = from, mealType = MealType.BREAKFAST, name = "Haferflocken", amount = 60.0,
            nutrients = Nutrients(kcal = 225.4, proteinG = 8.1, carbsG = 35.0, fatG = 4.2, saturatedFatG = 0.7, sugarG = 0.6, fiberG = 6.0, saltG = 0.01),
        )
        assertEquals(
            "Haferflocken, 60 g: 225 kcal · Fett 4,2 g (ges. 0,7) · KH 35 g (Zucker 0,6) · Ballaststoffe 6 g · Eiweiß 8,1 g · Salz 0,01 g",
            WeeklyExport.line(entry),
        )
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
