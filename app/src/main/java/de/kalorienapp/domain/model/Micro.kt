package de.kalorienapp.domain.model

/**
 * Vitamine und Mineralstoffe (die 10 wichtigsten). [unit] = Einheit der gespeicherten Werte,
 * Referenzwerte für Erwachsene nach DGE (Männer/Frauen).
 */
enum class Micro(val unit: MicroUnit, private val referenceMale: Double, private val referenceFemale: Double) {
    VITAMIN_A(MicroUnit.MICROGRAM, 850.0, 700.0),
    VITAMIN_C(MicroUnit.MILLIGRAM, 110.0, 95.0),
    VITAMIN_D(MicroUnit.MICROGRAM, 20.0, 20.0),
    VITAMIN_B12(MicroUnit.MICROGRAM, 4.0, 4.0),
    FOLATE(MicroUnit.MICROGRAM, 300.0, 300.0),
    CALCIUM(MicroUnit.MILLIGRAM, 1000.0, 1000.0),
    IRON(MicroUnit.MILLIGRAM, 11.0, 15.0),
    MAGNESIUM(MicroUnit.MILLIGRAM, 350.0, 300.0),
    ZINC(MicroUnit.MILLIGRAM, 14.0, 8.0),
    POTASSIUM(MicroUnit.MILLIGRAM, 4000.0, 4000.0),
    ;

    /** Empfohlene Tageszufuhr. */
    fun reference(sex: Sex): Double = if (sex == Sex.MALE) referenceMale else referenceFemale

    companion object {
        /** Kompakte Textform für die Datenbank: „VITAMIN_C=12.5;IRON=3.1“ (robust gegen unbekannte Schlüssel). */
        fun encode(values: Map<Micro, Double>): String? =
            values.takeIf { it.isNotEmpty() }?.entries?.joinToString(";") { "${it.key.name}=${it.value}" }

        fun decode(text: String?): Map<Micro, Double> = text.orEmpty().split(';').mapNotNull { part ->
            val (key, value) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val micro = entries.find { it.name == key } ?: return@mapNotNull null
            value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { micro to it }
        }.toMap()
    }
}

enum class MicroUnit { MILLIGRAM, MICROGRAM }
