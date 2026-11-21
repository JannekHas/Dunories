package de.kalorienapp.domain

import de.kalorienapp.domain.model.ReportTip
import de.kalorienapp.domain.model.WeeklyReport
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EstimateMentionsTest {
    private val day = LocalDate.of(2026, 11, 9)

    @Test
    fun `schaetzungen kommen in der auswertung nicht vor`() {
        val report = WeeklyReport(
            from = day, to = day.plusDays(6),
            headline = "Starke Basis",
            summary = "Gute Woche. Da viele Abendessen KI-geschätzt sind, lohnt ein Blick. Weiter so!",
            wins = listOf("Konstant getrackt"),
            improvements = listOf("Mehr Protein", "Der hohe Anteil an geschätzten Abendessen macht es ungenau"),
            tips = listOf(
                ReportTip("Protein", "Mehr Skyr"),
                ReportTip("Abends", "Da deine Abendessen oft geschätzt sind, nimm Hähnchen dazu"),
                ReportTip("Präzision", "Scanne den Barcode, statt per Foto schätzen zu lassen"),
            ),
            goalNote = null,
        )
        val clean = EstimateMentions.clean(report)
        assertEquals("Gute Woche. Weiter so!", clean.summary)
        assertEquals(listOf("Mehr Protein"), clean.improvements)
        assertEquals(listOf("Protein"), clean.tips.map { it.title })
        assertEquals("Starke Basis", clean.headline)
    }

    @Test
    fun `wertgeschaetzt zaehlt nicht`() {
        assertEquals(false, EstimateMentions.mentions("Du hast dich wertgeschätzt gefühlt"))
        assertEquals(false, EstimateMentions.mentions("Mikros werden unterschätzt"))
        assertEquals(true, EstimateMentions.mentions("lass es nicht schätzen"))
        assertEquals(true, EstimateMentions.mentions("KI-geschätzt"))
    }
}
