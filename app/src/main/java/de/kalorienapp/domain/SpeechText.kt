package de.kalorienapp.domain

/**
 * Setzt den Text der Spracheingabe zusammen: bisheriger Feldinhalt + fertig erkannte Abschnitte
 * + der gerade gesprochene (noch vorläufige) Teil. So steht der Text live im Feld, während man spricht.
 */
object SpeechText {

    fun combine(base: String, segments: List<String>, partial: String, separator: String): String {
        val spoken = (segments + partial).map(String::trim).filter(String::isNotEmpty).joinToString(" ")
        return listOf(base.trim(), spoken).filter(String::isNotEmpty).joinToString(separator)
    }
}
