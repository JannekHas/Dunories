package de.kalorienapp.domain

import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.DayStats
import de.kalorienapp.domain.model.DayStatus
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.GoalPlan
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.domain.model.WeightEntry
import de.kalorienapp.domain.model.WeightGoal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Alles, was die KI für die Wochenauswertung bekommt. */
data class WeeklyInput(
    val from: LocalDate,
    val to: LocalDate,
    val profile: Profile,
    val goal: WeightGoal?,
    val plan: GoalPlan?,
    val targets: DailyTargets,
    val days: List<DayStats>,
    val entries: List<FoodEntry>,
    val activities: List<ActivityEntry>,
    /** Alle Messungen (die Auswertung nimmt Zeitraum + letzte davor). */
    val weights: List<WeightEntry>,
)

/**
 * Baut einen kompakten, gut lesbaren Datenblock für die KI (deutsch, ohne Namen/Personendaten außer
 * Körperdaten). Reine Logik, unit-getestet.
 */
object WeeklyInputBuilder {

    const val MIN_LOGGED_DAYS = 2

    fun loggedDays(input: WeeklyInput) = input.days.count { it.hasEntries }

    fun build(input: WeeklyInput, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        val p = input.profile
        val year = input.to.year
        appendLine("ZEITRAUM: ${input.from} bis ${input.to} (${input.days.size} Tage, davon ${loggedDays(input)} mit Einträgen)")
        appendLine()
        appendLine("PERSON: ${if (p.sex.name == "MALE") "männlich" else "weiblich"}, ${p.ageIn(year)} Jahre, ${p.heightCm.r()} cm, aktuell ${p.weightKg.r1()} kg")
        appendLine("Grundumsatz (Tagesziel-Basis, ohne Bewegung): ${GoalCalculator.maintenanceKcal(p, year).roundToInt()} kcal. Bewegung wird als Aktivität extra eingetragen und erhöht das Budget.")
        appendLine("TAGESZIEL: ${input.targets.kcal} kcal, Protein ${input.targets.proteinG} g, KH ${input.targets.carbsG} g, Fett ${input.targets.fatG} g")
        val goal = input.goal
        if (goal == null) {
            appendLine("GEWICHTSZIEL: keins gesetzt")
        } else {
            val date = goal.targetDate?.let { " bis $it" } ?: ""
            appendLine("GEWICHTSZIEL: ${goal.direction.german()} von ${goal.startWeightKg.r1()} kg (Start ${goal.startDate}) auf ${goal.targetWeightKg.r1()} kg$date")
            input.plan?.let { plan ->
                append("PLAN HEUTE: ${"%+d".format(plan.dailyAdjustmentKcal)} kcal/Tag gegenüber Grundumsatz, ${"%+.2f".format(Locale.GERMANY, plan.kgPerWeek)} kg/Woche")
                if (plan.limited) append(" (auf gesundes Maximum begrenzt; realistisches Datum ${plan.realisticDate})")
                appendLine()
            }
        }
        appendLine()
        appendLine("TAGE (gegessen / Budget = Ziel + Aktivität):")
        val activityByDay = input.activities.groupBy { it.date }
        input.days.forEach { d ->
            val day = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
            if (!d.hasEntries) {
                appendLine("- $day ${d.date}: nichts eingetragen")
                return@forEach
            }
            val e = d.eaten
            append("- $day ${d.date}: ${e.kcal.roundToInt()} / ${d.budgetKcal} kcal (${d.status.german()}), ")
            append("P ${e.proteinG.roundToInt()} g (Ziel ${d.proteinTargetG}), KH ${e.carbsG.roundToInt()} g, F ${e.fatG.roundToInt()} g")
            e.sugarG?.let { append(", Zucker ${it.roundToInt()} g") }
            e.fiberG?.let { append(", Ballaststoffe ${it.roundToInt()} g") }
            e.saltG?.let { append(", Salz ${it.r1()} g") }
            activityByDay[d.date]?.let { acts ->
                append(", Aktivität: " + acts.joinToString { a -> "${a.name.ifBlank { "Aktivität" }} ${a.kcal} kcal" })
            }
            appendLine()
        }
        appendLine()
        val entries = input.entries
        if (entries.isNotEmpty()) {
            appendLine("MAHLZEITEN (Ø kcal pro erfasstem Tag):")
            val logged = loggedDays(input).coerceAtLeast(1)
            MealType.entries.forEach { meal ->
                val kcal = entries.filter { it.mealType == meal }.sumOf { it.nutrients.kcal }
                appendLine("- ${meal.german()}: ${(kcal / logged).roundToInt()} kcal")
            }
            appendLine()
            appendLine("HÄUFIGSTE / KALORIENREICHSTE LEBENSMITTEL:")
            entries.groupBy { it.name.trim().lowercase() }
                .map { (_, list) -> Triple(list.first().name.trim(), list.size, list.sumOf { it.nutrients.kcal }) }
                .sortedByDescending { it.third }
                .take(TOP_FOODS)
                .forEach { (name, count, kcal) -> appendLine("- $name: ${count}× , zusammen ${kcal.roundToInt()} kcal") }
            appendLine()
            // Wann eingetragen wird (Hinweis auf Essenszeiten, z. B. spätes Snacken).
            val late = entries.filter { it.createdAt.localHour(zone) >= 21 }.sumOf { it.nutrients.kcal }
            if (late > 0) appendLine("Nach 21 Uhr eingetragen: insgesamt ${late.roundToInt()} kcal")
        }
        val weights = input.weights.sortedBy { it.measuredAt }
        val fromInstant = input.from.atStartOfDay(zone).toInstant()
        val before = weights.lastOrNull { it.measuredAt.isBefore(fromInstant) }
        val inPeriod = weights.filter { !it.measuredAt.isBefore(fromInstant) }
        appendLine("GEWICHT:")
        if (before == null && inPeriod.isEmpty()) {
            appendLine("- keine Messungen")
        } else {
            before?.let { appendLine("- letzte Messung davor: ${it.weightKg.r1()} kg am ${it.measuredAt.atZone(zone).toLocalDate()}") }
            inPeriod.forEach { appendLine("- ${it.measuredAt.atZone(zone).toLocalDate()}: ${it.weightKg.r1()} kg") }
        }
    }.trimEnd()

    private const val TOP_FOODS = 12

    private fun Long.localHour(zone: ZoneId) = Instant.ofEpochMilli(this).atZone(zone).hour

    private fun Double.r() = roundToInt().toString()
    private fun Double.r1() = "%.1f".format(Locale.GERMANY, this)

    private fun Goal.german() = when (this) {
        Goal.LOSE -> "Abnehmen"
        Goal.MAINTAIN -> "Gewicht halten"
        Goal.GAIN -> "Zunehmen"
    }

    private fun DayStatus.german() = when (this) {
        DayStatus.ON_TARGET -> "im Budget"
        DayStatus.SLIGHTLY_OVER -> "knapp drüber"
        DayStatus.OVER -> "deutlich drüber"
        DayStatus.NO_DATA -> "keine Daten"
    }

    private fun MealType.german() = when (this) {
        MealType.BREAKFAST -> "Frühstück"
        MealType.LUNCH -> "Mittagessen"
        MealType.DINNER -> "Abendessen"
        MealType.SNACK -> "Snacks"
    }
}
