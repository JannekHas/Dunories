package de.kalorienapp.domain

import kotlin.math.roundToInt

/** Ob ein Tageswert eine Obergrenze (rot drüber) oder ein Mindestziel (grün ab) hat. */
enum class LimitKind { MAX, MIN }

data class NutrientLimit(val grams: Int, val kind: LimitKind) {
    /** Überschritten (bei MAX) – dann rot. */
    fun exceeded(value: Double?) = kind == LimitKind.MAX && value != null && value > grams
    /** Erreicht (bei MIN) – dann grün. */
    fun reached(value: Double?) = kind == LimitKind.MIN && value != null && value >= grams
}

/**
 * Richtwerte für die Zusatznährwerte auf „Heute“ (Entscheidung des Nutzers, Nov. 2026):
 * Zucker höchstens 50 g (WHO: < 10 % der Energie, bei ~2000 kcal), Salz höchstens 6 g (DGE),
 * gesättigte Fettsäuren höchstens 10 % der Tageskalorien (DGE/WHO), Ballaststoffe mindestens 30 g (DGE).
 */
object NutrientLimits {
    val SUGAR = NutrientLimit(50, LimitKind.MAX)
    val SALT = NutrientLimit(6, LimitKind.MAX)
    val FIBER = NutrientLimit(30, LimitKind.MIN)

    /** 10 % der Tageskalorien aus gesättigten Fettsäuren (9 kcal/g). */
    fun saturatedFat(kcalTarget: Int) = NutrientLimit((kcalTarget * 0.10 / 9).roundToInt().coerceAtLeast(1), LimitKind.MAX)
}
