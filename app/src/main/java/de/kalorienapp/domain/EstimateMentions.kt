package de.kalorienapp.domain

import de.kalorienapp.domain.model.WeeklyReport

/**
 * KI-Wochenauswertung: Schätzungen (Foto/Beschreibung statt Etikett) dürfen höchstens in EINEM Tipp vorkommen
 * (Entscheidung des Nutzers Nov. 2026 – sonst wirkt es belehrend). Das schnelle Modell hält sich nicht
 * zuverlässig an die Regel im Prompt → hier im Code absichern.
 */
object EstimateMentions {
    /** „geschätzt“, „KI-geschätzt“, „schätzen lassen“, „Schätzung“ – nicht „unterschätzt“/„wertgeschätzt“. */
    private val pattern = Regex("""(?i)(?<!\p{L})(ki-?\s?)?(ge)?schätz\p{L}*""")

    /** Der eine erlaubte Tipp: bevorzugt der mit konkretem Rat (Barcode/Nährwerttabelle). */
    private val actionable = Regex("""(?i)barcode|nährwerttabelle|etikett""")

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
            tips = run {
                val estimateTips = report.tips.filter { mentions(it.title + " " + it.text) }
                val keep = estimateTips.firstOrNull { actionable.containsMatchIn(it.text) } ?: estimateTips.firstOrNull()
                report.tips.filter { it !in estimateTips || it == keep }
            },
        )
    }
}
