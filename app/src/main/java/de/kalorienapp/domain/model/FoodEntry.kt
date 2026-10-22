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

        fun fromRouteArg(name: String?): MealType? = name?.let { n -> entries.find { it.name == n } }
    }
}

/** Herkunft eines Eintrags (wird als Text gespeichert – neue Werte brauchen keine Migration). */
enum class EntrySource { MANUAL, BARCODE, AI_PHOTO, AI_TEXT, RECIPE }

enum class AmountUnit { GRAM, MILLILITER }

/**
 * Ein gegessener Eintrag. Nährwerte sind Absolutwerte für die angegebene Menge
 * (der Editor rechnet von/zu „pro 100 g/ml“ um). Alt-Einträge können ohne Menge sein.
 */
data class FoodEntry(
    val id: Long = 0,
    val date: LocalDate,
    val mealType: MealType,
    val name: String,
    val amount: Double?,
    val unit: AmountUnit = AmountUnit.GRAM,
    val nutrients: Nutrients,
    val source: EntrySource = EntrySource.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
)
