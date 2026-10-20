package de.kalorienapp.ui.components

import java.util.Locale
import kotlin.math.roundToLong

/** Parst Benutzereingaben mit Komma oder Punkt als Dezimaltrenner. Leer/ungültig → null. */
fun String.parseDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** Zahl für Eingabefelder: ohne unnötige Nachkommastellen, deutsches Komma. */
fun Double.toInputString(decimals: Int = 1): String {
    val factor = Math.pow(10.0, decimals.toDouble())
    val rounded = (this * factor).roundToLong() / factor
    return if (rounded % 1.0 == 0.0) {
        rounded.roundToLong().toString()
    } else {
        String.format(Locale.GERMANY, "%.${decimals}f", rounded).trimEnd('0')
    }
}

/** Zahl für die Anzeige, gerundet. */
fun Double.format(decimals: Int = 0): String = String.format(Locale.GERMANY, "%,.${decimals}f", this)

fun Int.format(): String = String.format(Locale.GERMANY, "%,d", this)
