package de.kalorienapp.domain

import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.BodyMeasurement
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.DayStats
import de.kalorienapp.domain.model.DayStatus
import de.kalorienapp.domain.model.Budget
import de.kalorienapp.domain.model.CookingTime
import de.kalorienapp.domain.model.DietStyle
import de.kalorienapp.domain.model.ExcludedFood
import de.kalorienapp.domain.model.Intolerance
import de.kalorienapp.domain.model.FoodPreferences
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.GoalPlan
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.MeasureType
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.MicroUnit
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
    val measurements: List<BodyMeasurement> = emptyList(),
    /** Getrunkenes Wasser je Tag (ml) und Tagesziel. */
    val waterByDay: Map<LocalDate, Int> = emptyMap(),
    val waterGoalMl: Int? = null,
    /** Intervallfasten (null = aus) und Auswertung des Zeitraums. */
    val fasting: FastingConfig? = null,
    val fastingStats: FastingStats? = null,
    /** Ernährungsweise und „mag ich nicht“ – für passende Lebensmittel-Vorschläge. */
    val preferences: FoodPreferences = FoodPreferences(),
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
        appendLine("Bedarf ohne Sport (Grundumsatz × Alltagsfaktor ${p.lifestyle.pal}, Tagesziel-Basis): ${GoalCalculator.maintenanceKcal(p, year).roundToInt()} kcal. Sport wird als Aktivität extra eingetragen und erhöht das Budget.")
        input.waterGoalMl?.let { appendLine("WASSERZIEL: $it ml pro Tag") }
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
        val prefs = input.preferences
        if (!prefs.isEmpty) {
            appendLine()
            appendLine("VORLIEBEN (verbindlich für Vorschläge):")
            if (prefs.style != DietStyle.ALL) appendLine("- Ernährungsweise: ${prefs.style.german()}")
            if (prefs.relevantExcluded.isNotEmpty()) {
                appendLine("- Isst kein: " + prefs.relevantExcluded.sortedBy { it.ordinal }.joinToString { it.german() })
            }
            val (allergies, intolerances) = prefs.intolerances.sortedBy { it.ordinal }.partition { it.allergen }
            if (intolerances.isNotEmpty()) appendLine("- Unverträglichkeiten (meiden): " + intolerances.joinToString { it.german() })
            if (allergies.isNotEmpty()) appendLine("- ALLERGIEN (strikt ausschließen, auch Spuren): " + allergies.joinToString { it.german() })
            val everyday = listOfNotNull(
                when (prefs.cooking) {
                    CookingTime.LITTLE -> "wenig Zeit zum Kochen"
                    CookingTime.LIKES -> "kocht gern und aufwendig"
                    CookingTime.NORMAL -> null
                },
                "kleines Budget".takeIf { prefs.budget == Budget.SMALL },
                "kocht gern vor (Meal Prep)".takeIf { prefs.mealPrep },
            )
            if (everyday.isNotEmpty()) appendLine("- Alltag: " + everyday.joinToString())
            if (prefs.dislikes.isNotEmpty()) appendLine("- Mag nicht: " + prefs.dislikes.joinToString())
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
            input.waterByDay[d.date]?.let { append(", Wasser ${it} ml") }
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
        input.fasting?.let { f ->
            val plan = if (f.isDaily) "${f.fastHours}:${f.windowHours} (Essensfenster ${f.windowStart}–${f.windowEnd})" else "5:2 (Fastentage: ${f.fastDays.joinToString { it.name.lowercase() }})"
            append("INTERVALLFASTEN: $plan")
            input.fastingStats?.let { s ->
                append(" – eingehalten an ${s.keptDays} von ${s.evaluatedDays} Tagen")
                s.averageFastHours?.let { append(", Ø Fastenpause ${"%.1f".format(Locale.GERMANY, it)} h") }
            }
            appendLine(" (Eintragszeit ≈ Essenszeit)")
            appendLine()
        }
        val withMicros = input.days.filter { it.hasEntries && it.eaten.micros.isNotEmpty() }
        if (withMicros.isNotEmpty()) {
            appendLine("VITAMINE/MINERALSTOFFE/WEITERE (Ø pro Tag an ${withMicros.size} Tagen mit Angaben; nur Lebensmittel mit Angaben zählen, echte Zufuhr meist höher):")
            Micro.entries.forEach { micro ->
                val values = withMicros.mapNotNull { it.eaten.micros[micro] }
                if (values.isEmpty()) return@forEach
                val unit = when (micro.unit) {
                    MicroUnit.MICROGRAM -> "µg"
                    MicroUnit.MILLIGRAM -> "mg"
                    MicroUnit.GRAM -> "g"
                }
                val avg = values.average()
                val shown = if (avg < 10) "%.1f".format(Locale.GERMANY, avg) else avg.r()
                val ref = micro.reference(p.sex).let { if (it < 10) "%.1f".format(Locale.GERMANY, it) else it.r() }
                appendLine("- ${micro.german()}: $shown $unit (${if (micro.isLimit) "Obergrenze" else "Referenz"} $ref $unit)")
            }
            appendLine()
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
        if (input.measurements.isNotEmpty()) {
            appendLine("KÖRPERMASSE (erste und letzte Messung):")
            input.measurements.groupBy { it.type }.forEach { (type, list) ->
                val sorted = list.sortedBy { it.measuredAt }
                val unit = if (type.isPercent) "%" else "cm"
                val first = sorted.first()
                val last = sorted.last()
                append("- ${type.german()}: ${last.value.r1()} $unit am ${last.measuredAt.atZone(zone).toLocalDate()}")
                if (sorted.size > 1) append(" (vorher ${first.value.r1()} $unit am ${first.measuredAt.atZone(zone).toLocalDate()})")
                appendLine()
            }
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

    private fun Micro.german() = when (this) {
        Micro.VITAMIN_A -> "Vitamin A"
        Micro.VITAMIN_C -> "Vitamin C"
        Micro.VITAMIN_D -> "Vitamin D"
        Micro.VITAMIN_B12 -> "Vitamin B12"
        Micro.FOLATE -> "Folsäure"
        Micro.CALCIUM -> "Calcium"
        Micro.IRON -> "Eisen"
        Micro.MAGNESIUM -> "Magnesium"
        Micro.ZINC -> "Zink"
        Micro.POTASSIUM -> "Kalium"
        Micro.VITAMIN_E -> "Vitamin E"
        Micro.VITAMIN_K -> "Vitamin K"
        Micro.VITAMIN_B6 -> "Vitamin B6"
        Micro.VITAMIN_B1 -> "Vitamin B1"
        Micro.VITAMIN_B2 -> "Vitamin B2"
        Micro.IODINE -> "Jod"
        Micro.SELENIUM -> "Selen"
        Micro.PHOSPHORUS -> "Phosphor"
        Micro.CAFFEINE -> "Koffein"
        Micro.CHOLESTEROL -> "Cholesterin"
        Micro.OMEGA_3 -> "Omega-3"
    }

    private fun DietStyle.german() = when (this) {
        DietStyle.ALL -> "isst alles"
        DietStyle.FLEXITARIAN -> "flexitarisch (wenig Fleisch)"
        DietStyle.PESCETARIAN -> "pescetarisch (kein Fleisch, aber Fisch)"
        DietStyle.VEGETARIAN -> "vegetarisch"
        DietStyle.VEGAN -> "vegan"
    }

    private fun ExcludedFood.german() = when (this) {
        ExcludedFood.PORK -> "Schweinefleisch"
        ExcludedFood.BEEF -> "Rindfleisch"
        ExcludedFood.POULTRY -> "Geflügel"
        ExcludedFood.FISH -> "Fisch"
        ExcludedFood.SEAFOOD -> "Meeresfrüchte"
    }

    private fun Intolerance.german() = when (this) {
        Intolerance.LACTOSE -> "Laktose"
        Intolerance.GLUTEN -> "Gluten"
        Intolerance.FRUCTOSE -> "Fruktose"
        Intolerance.HISTAMINE -> "Histamin"
        Intolerance.NUTS -> "Nüsse"
        Intolerance.PEANUTS -> "Erdnüsse"
        Intolerance.EGG -> "Ei"
        Intolerance.SOY -> "Soja"
        Intolerance.FISH -> "Fisch"
        Intolerance.SHELLFISH -> "Krebstiere/Schalentiere"
        Intolerance.CELERY -> "Sellerie"
        Intolerance.MUSTARD -> "Senf"
        Intolerance.SESAME -> "Sesam"
    }

    private fun MeasureType.german() = when (this) {
        MeasureType.WAIST -> "Taille"
        MeasureType.HIP -> "Hüfte"
        MeasureType.CHEST -> "Brust"
        MeasureType.UPPER_ARM -> "Oberarm"
        MeasureType.THIGH -> "Oberschenkel"
        MeasureType.BODY_FAT -> "Körperfett"
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
