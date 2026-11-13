package de.kalorienapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.kalorienapp.domain.model.FoodSuggestion
import de.kalorienapp.domain.model.ReportTip
import de.kalorienapp.domain.model.WeeklyReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** KI-Wochenauswertung; der Inhalt liegt als JSON in [content] (Listen/Tipps ohne eigene Tabellen). */
@Entity(tableName = "weekly_report")
data class WeeklyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val createdAt: Long,
    val content: String,
) {
    fun toDomain(): WeeklyReport {
        val c = runCatching { json.decodeFromString<Content>(content) }.getOrElse { Content() }
        return WeeklyReport(
            id = id,
            from = LocalDate.ofEpochDay(fromEpochDay),
            to = LocalDate.ofEpochDay(toEpochDay),
            createdAt = createdAt,
            headline = c.headline,
            summary = c.summary,
            wins = c.wins,
            improvements = c.improvements,
            tips = c.tips.map { ReportTip(it.title, it.text) },
            goalNote = c.goalNote,
            foodsMore = c.foodsMore.map { FoodSuggestion(it.title, it.text) },
            foodsLess = c.foodsLess.map { FoodSuggestion(it.title, it.text) },
        )
    }

    @Serializable
    private data class Content(
        val headline: String = "",
        val summary: String = "",
        val wins: List<String> = emptyList(),
        val improvements: List<String> = emptyList(),
        val tips: List<Tip> = emptyList(),
        val goalNote: String? = null,
        val foodsMore: List<Tip> = emptyList(),
        val foodsLess: List<Tip> = emptyList(),
    )

    @Serializable
    private data class Tip(val title: String, val text: String)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromDomain(r: WeeklyReport) = WeeklyReportEntity(
            id = r.id,
            fromEpochDay = r.from.toEpochDay(),
            toEpochDay = r.to.toEpochDay(),
            createdAt = r.createdAt,
            content = json.encodeToString(
                Content.serializer(),
                Content(
                    r.headline, r.summary, r.wins, r.improvements, r.tips.map { Tip(it.title, it.text) }, r.goalNote,
                    r.foodsMore.map { Tip(it.name, it.reason) }, r.foodsLess.map { Tip(it.name, it.reason) },
                ),
            ),
        )
    }
}
