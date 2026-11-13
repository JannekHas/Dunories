package de.kalorienapp.domain

import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients

/** Nährwerte, nach denen die Einträge eines Tages sortiert werden können (Etikett-Reihenfolge). */
enum class RankedNutrient(val value: (Nutrients) -> Double?) {
    KCAL({ it.kcal }),
    FAT({ it.fatG }),
    SATURATED_FAT({ it.saturatedFatG }),
    CARBS({ it.carbsG }),
    SUGAR({ it.sugarG }),
    FIBER({ it.fiberG }),
    PROTEIN({ it.proteinG }),
    SALT({ it.saltG }),
    CAFFEINE({ it.micros[Micro.CAFFEINE] }),
    ;

    companion object {
        fun fromRouteArg(name: String?): RankedNutrient = entries.find { it.name == name } ?: KCAL
    }
}

/** Ein Eintrag mit seinem Wert für den gewählten Nährwert und dem Anteil an der Tagessumme (0..1). */
data class RankedEntry(val entry: FoodEntry, val value: Double?, val share: Double)

/**
 * „Was hat am meisten Fett gebracht?“: Einträge absteigend nach dem Nährwert sortiert.
 * Einträge ohne Angabe (Zusatzwerte `null`) stehen am Ende.
 */
object NutrientRanking {

    fun rank(entries: List<FoodEntry>, nutrient: RankedNutrient): List<RankedEntry> {
        val values = entries.map { it to nutrient.value(it.nutrients) }
        val total = values.sumOf { it.second ?: 0.0 }
        return values
            .sortedWith(compareBy<Pair<FoodEntry, Double?>> { it.second == null }.thenByDescending { it.second ?: 0.0 })
            .map { (entry, value) ->
                RankedEntry(entry, value, if (total > 0 && value != null) value / total else 0.0)
            }
    }

    /** Tagessumme; null, wenn kein Eintrag den Wert angibt. */
    fun total(entries: List<FoodEntry>, nutrient: RankedNutrient): Double? {
        val values = entries.mapNotNull { nutrient.value(it.nutrients) }
        return if (values.isEmpty()) null else values.sum()
    }
}
