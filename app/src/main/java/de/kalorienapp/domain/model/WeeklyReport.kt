package de.kalorienapp.domain.model

import java.time.LocalDate

/** Ein konkreter Tipp der KI. */
data class ReportTip(val title: String, val text: String)

/** Konkretes Lebensmittel, das die KI öfter bzw. seltener empfiehlt, mit kurzer Begründung. */
data class FoodSuggestion(val name: String, val reason: String)

/** KI-Auswertung eines Zeitraums (meist eine Woche). Nur Hinweise – die App ändert dadurch nichts selbst. */
data class WeeklyReport(
    val id: Long = 0,
    val from: LocalDate,
    val to: LocalDate,
    val createdAt: Long = System.currentTimeMillis(),
    /** Kurze Überschrift, z. B. „Starke Proteinwoche!“ */
    val headline: String,
    /** 2–4 Sätze Gesamteindruck. */
    val summary: String,
    val wins: List<String>,
    val improvements: List<String>,
    val tips: List<ReportTip>,
    /** Einschätzung zum Gewichtsziel/Tempo (Vorschlag, z. B. Zieldatum anpassen), oder null. */
    val goalNote: String?,
    /** Lebensmittel, die öfter auf den Teller dürfen (passend zu den Vorlieben). */
    val foodsMore: List<FoodSuggestion> = emptyList(),
    /** Lebensmittel aus den Einträgen, die man reduzieren könnte. */
    val foodsLess: List<FoodSuggestion> = emptyList(),
)
