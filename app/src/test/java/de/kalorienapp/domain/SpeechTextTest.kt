package de.kalorienapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextTest {

    @Test
    fun `leeres Feld zeigt nur Gesprochenes`() {
        assertEquals("zwei Brötchen", SpeechText.combine("", emptyList(), "zwei Brötchen", ", "))
    }

    @Test
    fun `Abschnitte nach Pausen werden mit Leerzeichen verbunden`() {
        assertEquals(
            "zwei Brötchen mit Butter und ein Kaffee",
            SpeechText.combine("", listOf("zwei Brötchen mit Butter"), "und ein Kaffee", ", "),
        )
    }

    @Test
    fun `bisheriger Text bleibt vorne mit Trenner`() {
        assertEquals("Müsli, mit Milch", SpeechText.combine("Müsli ", listOf("mit Milch"), "", ", "))
    }

    @Test
    fun `nichts gesprochen laesst Text unveraendert`() {
        assertEquals("Müsli", SpeechText.combine("Müsli", emptyList(), " ", ", "))
    }
}
