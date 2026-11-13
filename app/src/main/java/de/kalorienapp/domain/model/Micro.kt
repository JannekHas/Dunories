package de.kalorienapp.domain.model

/** Gruppe für die Anzeige: Vitamine stehen beieinander, dann Mineralstoffe, dann Weitere. */
enum class MicroGroup { VITAMIN, MINERAL, OTHER }

/**
 * Vitamine, Mineralstoffe und weitere Werte (Koffein, Cholesterin, Omega-3).
 * Reihenfolge = Anzeige: je Gruppe nach Wichtigkeit. [unit] = Einheit der gespeicherten Werte,
 * Referenzwerte für Erwachsene nach DGE (Männer/Frauen). [isLimit] = Obergrenze statt Empfehlung.
 * [alwaysShown] = auf „Heute“ immer sichtbar (auch mit 0 %), die übrigen nur, wenn eingetragen.
 */
enum class Micro(
    val unit: MicroUnit,
    val group: MicroGroup,
    private val referenceMale: Double,
    private val referenceFemale: Double,
    val alwaysShown: Boolean = false,
    val isLimit: Boolean = false,
) {
    VITAMIN_D(MicroUnit.MICROGRAM, MicroGroup.VITAMIN, 20.0, 20.0, alwaysShown = true),
    VITAMIN_B12(MicroUnit.MICROGRAM, MicroGroup.VITAMIN, 4.0, 4.0, alwaysShown = true),
    VITAMIN_C(MicroUnit.MILLIGRAM, MicroGroup.VITAMIN, 110.0, 95.0, alwaysShown = true),
    FOLATE(MicroUnit.MICROGRAM, MicroGroup.VITAMIN, 300.0, 300.0, alwaysShown = true),
    VITAMIN_A(MicroUnit.MICROGRAM, MicroGroup.VITAMIN, 850.0, 700.0),
    VITAMIN_E(MicroUnit.MILLIGRAM, MicroGroup.VITAMIN, 14.0, 12.0),
    VITAMIN_K(MicroUnit.MICROGRAM, MicroGroup.VITAMIN, 70.0, 60.0),
    VITAMIN_B6(MicroUnit.MILLIGRAM, MicroGroup.VITAMIN, 1.6, 1.4),
    VITAMIN_B1(MicroUnit.MILLIGRAM, MicroGroup.VITAMIN, 1.2, 1.0),
    VITAMIN_B2(MicroUnit.MILLIGRAM, MicroGroup.VITAMIN, 1.4, 1.1),

    IRON(MicroUnit.MILLIGRAM, MicroGroup.MINERAL, 11.0, 15.0, alwaysShown = true),
    CALCIUM(MicroUnit.MILLIGRAM, MicroGroup.MINERAL, 1000.0, 1000.0, alwaysShown = true),
    MAGNESIUM(MicroUnit.MILLIGRAM, MicroGroup.MINERAL, 350.0, 300.0, alwaysShown = true),
    POTASSIUM(MicroUnit.MILLIGRAM, MicroGroup.MINERAL, 4000.0, 4000.0),
    ZINC(MicroUnit.MILLIGRAM, MicroGroup.MINERAL, 14.0, 8.0),
    IODINE(MicroUnit.MICROGRAM, MicroGroup.MINERAL, 200.0, 200.0),
    SELENIUM(MicroUnit.MICROGRAM, MicroGroup.MINERAL, 70.0, 60.0),
    PHOSPHORUS(MicroUnit.MILLIGRAM, MicroGroup.MINERAL, 550.0, 550.0),

    /** EFSA: bis 400 mg/Tag für gesunde Erwachsene unbedenklich. */
    CAFFEINE(MicroUnit.MILLIGRAM, MicroGroup.OTHER, 400.0, 400.0, isLimit = true),
    /** Früherer Richtwert: höchstens 300 mg/Tag. */
    CHOLESTEROL(MicroUnit.MILLIGRAM, MicroGroup.OTHER, 300.0, 300.0, isLimit = true),
    /** Omega-3 (v. a. α-Linolensäure): DGE 0,5 % der Energie ≈ 1,5 g bzw. 1,2 g. */
    OMEGA_3(MicroUnit.GRAM, MicroGroup.OTHER, 1.5, 1.2),
    ;

    /** Empfohlene Tageszufuhr bzw. Obergrenze ([isLimit]). */
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

enum class MicroUnit { MILLIGRAM, MICROGRAM, GRAM }
