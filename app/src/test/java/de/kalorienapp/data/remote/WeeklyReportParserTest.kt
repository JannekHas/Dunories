package de.kalorienapp.data.remote

import de.kalorienapp.data.remote.gemini.WeeklyReportParser
import de.kalorienapp.domain.model.ReportTip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeeklyReportParserTest {

    private val from = LocalDate.of(2026, 10, 12)
    private val to = LocalDate.of(2026, 10, 18)

    @Test
    fun `vollstaendige auswertung`() {
        val report = WeeklyReportParser.parse(
            """
            {"headline":"Starke Proteinwoche!","summary":"Du warst an 5 von 7 Tagen im Budget.",
             "wins":["Protein fast immer erreicht"," "],"improvements":["Abends viele Snacks"],
             "tips":[{"title":"Snack tauschen","text":"Skyr statt Chips."},{"text":"ohne Titel"}],
             "goal_note":null}
            """.trimIndent(),
            from, to,
        )!!
        assertEquals("Starke Proteinwoche!", report.headline)
        assertEquals(listOf("Protein fast immer erreicht"), report.wins)
        assertEquals(listOf(ReportTip("Snack tauschen", "Skyr statt Chips.")), report.tips)
        assertNull(report.goalNote)
        assertEquals(from, report.from)
    }

    @Test
    fun `ohne zusammenfassung unbrauchbar`() {
        assertNull(WeeklyReportParser.parse("""{"headline":"x"}""", from, to))
        assertNull(WeeklyReportParser.parse("kaputt", from, to))
    }
}
