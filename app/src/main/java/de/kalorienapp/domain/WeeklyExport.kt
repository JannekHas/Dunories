package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.WeeklyReport
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Text zum Kopieren/Teilen in eine eigene KI (ChatGPT, Gemini …): Überblick wie für die
 * KI-Wochenauswertung, **alle** Einträge des Zeitraums und die Auswertung der App.
 */
object WeeklyExport {

    fun build(input: WeeklyInput, report: WeeklyReport?, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine("# Meine Ernährungsdaten aus der App Dunories (${input.from} bis ${input.to})")
        appendLine()
        appendLine(
            "Hallo! Das sind meine Daten aus meiner Kalorien-App. Bitte analysiere meine Ernährung, " +
                "beantworte meine Fragen dazu und hilf mir, mein Ziel zu erreichen. " +
                "Nährwerte gelten jeweils für die eingetragene Menge; Tage ohne Einträge sind unbekannt, nicht „nichts gegessen“.",
        )
        appendLine()
        appendLine("## Überblick")
        appendLine(WeeklyInputBuilder.build(input, zone))
        appendLine()
        appendLine("## Alle Einträge")
        val byDay = input.entries.groupBy { it.date }
        input.days.map { it.date }.ifEmpty { byDay.keys.sorted() }.forEach { date ->
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
            appendLine()
            appendLine("### $dayName, $date")
            val entries = byDay[date].orEmpty()
            if (entries.isEmpty()) {
                appendLine("(nichts eingetragen)")
                return@forEach
            }
            MealType.entries.forEach { meal ->
                val inMeal = entries.filter { it.mealType == meal }.sortedBy { it.orderKey }
                if (inMeal.isEmpty()) return@forEach
                appendLine("${meal.german()}:")
                inMeal.forEach { appendLine("- " + line(it)) }
            }
        }
        if (report != null) {
            appendLine()
            appendLine("## Auswertung der App-KI")
            appendLine("**${report.headline}** – ${report.summary}")
            if (report.wins.isNotEmpty()) appendLine("Gut gelaufen: " + report.wins.joinToString("; "))
            if (report.improvements.isNotEmpty()) appendLine("Verbessern: " + report.improvements.joinToString("; "))
            report.tips.forEach { appendLine("Tipp: ${it.title} – ${it.text}") }
            if (report.foodsMore.isNotEmpty()) appendLine("Öfter essen: " + report.foodsMore.joinToString("; ") { "${it.name} (${it.reason})" })
            if (report.foodsLess.isNotEmpty()) appendLine("Weniger essen: " + report.foodsLess.joinToString("; ") { "${it.name} (${it.reason})" })
            report.goalNote?.let { appendLine("Zum Ziel: $it") }
        }
    }.trimEnd()

    /** „Haferflocken, 60 g: 225 kcal · Fett 4 g (ges. 0,7) · KH 35 g (Zucker 0,6) · Ballaststoffe 6 g · Eiweiß 8 g · Salz 0,01 g“ */
    internal fun line(entry: FoodEntry): String {
        val n = entry.nutrients
        val amount = entry.amount?.let { ", ${it.fmt(1)} ${if (entry.unit == AmountUnit.MILLILITER) "ml" else "g"}" }.orEmpty()
        val parts = mutableListOf("${n.kcal.roundToInt()} kcal")
        parts += "Fett ${n.fatG.fmt(1)} g" + (n.saturatedFatG?.let { " (ges. ${it.fmt(1)})" } ?: "")
        parts += "KH ${n.carbsG.fmt(1)} g" + (n.sugarG?.let { " (Zucker ${it.fmt(1)})" } ?: "")
        n.fiberG?.let { parts += "Ballaststoffe ${it.fmt(1)} g" }
        parts += "Eiweiß ${n.proteinG.fmt(1)} g"
        n.saltG?.let { parts += "Salz ${it.fmt(2)} g" }
        return "${entry.name.trim()}$amount: " + parts.joinToString(" · ")
    }

    private fun Double.fmt(decimals: Int): String =
        String.format(Locale.GERMANY, "%.${decimals}f", this).let { if (decimals > 0) it.trimEnd('0').trimEnd(',') else it }

    private fun MealType.german() = when (this) {
        MealType.BREAKFAST -> "Frühstück"
        MealType.LUNCH -> "Mittagessen"
        MealType.DINNER -> "Abendessen"
        MealType.SNACK -> "Snacks"
    }
}
