package de.kalorienapp.domain.model

/**
 * Nährwerte. Die Hauptwerte sind immer gesetzt; die Zusatzwerte (gesättigte Fettsäuren,
 * Zucker, Ballaststoffe, Salz) sind optional – `null` bedeutet „nicht angegeben“.
 */
data class Nutrients(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val saturatedFatG: Double? = null,
    val sugarG: Double? = null,
    val fiberG: Double? = null,
    val saltG: Double? = null,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        kcal = kcal + other.kcal,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
        saturatedFatG = saturatedFatG plusOrNull other.saturatedFatG,
        sugarG = sugarG plusOrNull other.sugarG,
        fiberG = fiberG plusOrNull other.fiberG,
        saltG = saltG plusOrNull other.saltG,
    )

    operator fun times(factor: Double) = Nutrients(
        kcal = kcal * factor,
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
        saturatedFatG = saturatedFatG?.times(factor),
        sugarG = sugarG?.times(factor),
        fiberG = fiberG?.times(factor),
        saltG = saltG?.times(factor),
    )

    /** Diese Werte gelten pro 100 g/ml → Werte für [amount] g/ml. */
    fun per100ToAmount(amount: Double): Nutrients = this * (amount / 100.0)

    /** Diese Werte gelten für [amount] g/ml → Werte pro 100 g/ml. */
    fun amountToPer100(amount: Double): Nutrients = this * (100.0 / amount)

    companion object {
        val ZERO = Nutrients()

        private infix fun Double?.plusOrNull(other: Double?): Double? =
            if (this == null && other == null) null else (this ?: 0.0) + (other ?: 0.0)
    }
}

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.ZERO) { acc, n -> acc + n }

data class DailyTargets(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/** Bilanz eines Tages. */
data class DaySummary(
    val targets: DailyTargets,
    val eaten: Nutrients,
    val activeKcal: Int,
    val creditedActiveKcal: Int,
    val remainingKcal: Int,
)
