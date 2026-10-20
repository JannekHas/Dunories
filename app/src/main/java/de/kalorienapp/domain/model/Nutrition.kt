package de.kalorienapp.domain.model

data class Nutrients(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        kcal = kcal + other.kcal,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
    )

    operator fun times(factor: Double) = Nutrients(
        kcal = kcal * factor,
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
    )

    companion object {
        val ZERO = Nutrients()
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
