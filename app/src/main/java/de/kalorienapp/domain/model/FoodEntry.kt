package de.kalorienapp.domain.model

import java.time.LocalDate

enum class MealType {
    BREAKFAST, LUNCH, DINNER, SNACK;

    companion object {
        /** Sinnvolle Vorauswahl anhand der Uhrzeit. */
        fun forHour(hour: Int): MealType = when (hour) {
            in 4..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACK
        }
    }
}

/** Herkunft eines Eintrags; weitere Quellen (Barcode, KI, Rezept) kommen in späteren Phasen. */
enum class EntrySource { MANUAL }

/** Ein gegessener Eintrag. Nährwerte sind Absolutwerte für die angegebene Menge. */
data class FoodEntry(
    val id: Long = 0,
    val date: LocalDate,
    val mealType: MealType,
    val name: String,
    val amountG: Double?,
    val nutrients: Nutrients,
    val source: EntrySource = EntrySource.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
)
