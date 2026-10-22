package de.kalorienapp.domain

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Recipe
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object RecipeCalculator {

    /** Faktor, um Zutatenmengen auf [servings] Portionen umzurechnen. */
    fun scaleFactor(recipe: Recipe, servings: Int): Double = servings.coerceAtLeast(1).toDouble() / recipe.safeServings

    /**
     * Tagebucheintrag für [portions] Portionen (auch halbe). Menge = anteiliges Gesamtgewicht,
     * damit der Eintrag später wie jedes Lebensmittel bearbeitet werden kann.
     */
    fun toFoodEntry(recipe: Recipe, portions: Double, date: LocalDate, mealType: MealType): FoodEntry {
        val factor = portions / recipe.safeServings
        val amount = recipe.totalAmount.takeIf { it > 0 }?.let { it * factor }
        return FoodEntry(
            date = date,
            mealType = mealType,
            name = recipe.name,
            amount = amount?.let { (it * 10).roundToInt() / 10.0 },
            unit = AmountUnit.GRAM,
            nutrients = recipe.totalNutrients * factor,
            source = EntrySource.RECIPE,
        )
    }

    /**
     * Ausschnitt fürs Rezeptbild: die von der KI gefundene Box (normiert 0–1000, [ymin, xmin, ymax, xmax])
     * mit etwas Rand, auf das Seitenverhältnis [aspect] (Breite/Höhe) erweitert und ins Bild geschoben.
     * Ergebnis in Pixeln: [left, top, right, bottom]; null bei unbrauchbarer Box.
     */
    fun cropRect(box: List<Int>, width: Int, height: Int, aspect: Double = 4.0 / 3.0, margin: Double = 0.06): IntArray? {
        if (box.size != 4 || width <= 0 || height <= 0) return null
        val (yMin, xMin, yMax, xMax) = box.map { it.coerceIn(0, 1000) / 1000.0 }
        if (yMax - yMin < 0.05 || xMax - xMin < 0.05) return null
        var left = xMin * width
        var right = xMax * width
        var top = yMin * height
        var bottom = yMax * height
        val padX = (right - left) * margin
        val padY = (bottom - top) * margin
        left -= padX; right += padX; top -= padY; bottom += padY

        // Auf Seitenverhältnis erweitern (nie verkleinern), höchstens auf Bildgröße.
        var w = right - left
        var h = bottom - top
        if (w / h < aspect) w = min(h * aspect, width.toDouble()) else h = min(w / aspect, height.toDouble())
        // Passt das Verhältnis nach dem Begrenzen nicht mehr, die andere Seite kürzen.
        if (w / h > aspect) w = h * aspect else if (w / h < aspect) h = w / aspect
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        val l = (cx - w / 2).coerceIn(0.0, max(0.0, width - w))
        val t = (cy - h / 2).coerceIn(0.0, max(0.0, height - h))
        return intArrayOf(l.roundToInt(), t.roundToInt(), (l + w).roundToInt().coerceAtMost(width), (t + h).roundToInt().coerceAtMost(height))
    }
}
