package de.kalorienapp.domain

import de.kalorienapp.domain.model.WeeklyReport

/**
 * KI-Wochenauswertung: Schätzungen (Foto/Beschreibung statt Etikett) kommen gar nicht vor (Entscheidung des
 * Nutzers Nov. 2026 – Barcode-Tipps passten z. B. nicht zu Döner). Die KI bekommt keine Schätzungs-Infos mehr;
 * falls sie trotzdem davon spricht, wird es hier entfernt.
 */
object EstimateMentions {
    /** „geschätzt“, „KI-geschätzt“, „schätzen lassen“, „Schätzung“ – nicht „unterschätzt“/„wertgeschätzt“. */
    private val pattern = Regex("""(?i)(?<!\p{L})(ki-?\s?)?(ge)?schätz\p{L}*""")

    fun mentions(text: String): Boolean = pattern.containsMatchIn(text)

    /** Sätze mit Schätzungs-Hinweis entfernen; bliebe nichts übrig, den Text unverändert lassen. */
    fun stripSentences(text: String): String =
        text.split(Regex("""(?<=[.!?])\s+""")).filterNot(::mentions).joinToString(" ").ifBlank { text }

    fun clean(report: WeeklyReport): WeeklyReport {
        return report.copy(
            headline = report.headline.takeUnless(::mentions).orEmpty(),
            summary = stripSentences(report.summary),
            wins = report.wins.filterNot(::mentions),
            improvements = report.improvements.filterNot(::mentions),
            tips = report.tips.filterNot { mentions(it.title + " " + it.text) },
        )
    }
}
