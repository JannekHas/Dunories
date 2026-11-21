package de.kalorienapp.domain.model

import java.time.Instant

/** Einheit einer Messung (Anzeige und Plausibilität). */
enum class MeasureUnit { CM, PERCENT, KG, LEVEL, KCAL }

/**
 * Messart. [analysis] = gehört zur Körperanalyse (Bioimpedanz-Waage, Schalter „Körperanalyse“, Nov. 2026);
 * Körperfett ist beides – ohne Körperanalyse steht es bei den Körpermaßen. [validRange] = plausible Eingaben.
 * [goodDirection]: −1 = weniger ist gut (grün), +1 = mehr ist gut, 0 = neutral.
 * Gespeichert per Name → neue Arten brauchen keine Migration.
 */
enum class MeasureType(
    val unit: MeasureUnit,
    val validRange: ClosedFloatingPointRange<Double>,
    val analysis: Boolean = false,
    val goodDirection: Int = -1,
) {
    WAIST(MeasureUnit.CM, 10.0..250.0),
    HIP(MeasureUnit.CM, 10.0..250.0),
    CHEST(MeasureUnit.CM, 10.0..250.0),
    UPPER_ARM(MeasureUnit.CM, 10.0..250.0, goodDirection = 0),
    THIGH(MeasureUnit.CM, 10.0..250.0),
    BODY_FAT(MeasureUnit.PERCENT, 2.0..70.0, analysis = true),
    MUSCLE_MASS(MeasureUnit.KG, 5.0..150.0, analysis = true, goodDirection = 1),
    BODY_WATER(MeasureUnit.PERCENT, 20.0..80.0, analysis = true, goodDirection = 0),
    VISCERAL_FAT(MeasureUnit.LEVEL, 1.0..60.0, analysis = true),
    BONE_MASS(MeasureUnit.KG, 0.5..10.0, analysis = true, goodDirection = 0),

    /** Grundumsatz laut Waage – nur Anzeige, das Tagesziel rechnet weiter mit Mifflin-St-Jeor (Nutzer, Nov. 2026). */
    BMR(MeasureUnit.KCAL, 500.0..5000.0, analysis = true, goodDirection = 0),
    ;

    val isPercent: Boolean get() = unit == MeasureUnit.PERCENT

    fun isValid(value: Double) = value in validRange

    companion object {
        /** Werte der Körperanalyse in Eingabe-Reihenfolge (wie auf den meisten Waagen-Ausdrucken). */
        val ANALYSIS: List<MeasureType> = entries.filter { it.analysis }

        /** Körpermaße (Umfänge). Körperfett gehört seit Nov. 2026 zum Gewicht bzw. zur Körperanalyse. */
        val MEASURES: List<MeasureType> = entries.filter { !it.analysis }
    }
}

/** Eine Messung – beliebig viele, nichts wird überschrieben. */
data class BodyMeasurement(
    val id: Long = 0,
    val type: MeasureType,
    val value: Double,
    val measuredAt: Instant,
)
