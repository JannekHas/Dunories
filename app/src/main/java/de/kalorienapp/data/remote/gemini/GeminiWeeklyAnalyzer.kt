package de.kalorienapp.data.remote.gemini

import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.string
import de.kalorienapp.domain.model.ReportTip
import de.kalorienapp.domain.model.WeeklyReport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

/** KI-Wochenauswertung: analysiert Ernährung, Bewegung und Gewicht und gibt konkrete Tipps zum Ziel. */
class GeminiWeeklyAnalyzer(private val client: GeminiClient) {

    suspend fun analyze(data: String, from: LocalDate, to: LocalDate): WeeklyReport {
        val text = client.generateJson(RESPONSE_SCHEMA, timeoutMs = 180_000) {
            addJsonObject { put("text", PROMPT) }
            addJsonObject { put("text", data) }
        }
        return WeeklyReportParser.parse(text, from, to) ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
    }

    private companion object {
        val PROMPT = """
Du bist ein erfahrener, freundlicher Ernährungscoach. Analysiere die folgenden Daten aus einer Kalorien-App
und hilf der Person, ihr Ziel zu erreichen. Antworte auf Deutsch, per „du“, ehrlich aber motivierend.

Beachte:
- Beziehe dich konkret auf die Zahlen (Tage, Lebensmittel, Mahlzeiten, Gewichtsverlauf) – keine allgemeinen Floskeln.
- Tage ohne Einträge sind unbekannt (nicht „nichts gegessen“); erwähne Lücken beim Eintragen, wenn sie die Aussagekraft schmälern.
- Gewicht schwankt täglich (Wasser) – bewerte den Trend, nicht einzelne Messungen.
- Das Budget ist Grundumsatz ± Zielanpassung + selbst eingetragene Aktivitäten.
- Keine medizinischen Diagnosen; keine extremen Diäten (nie unter 1200 kcal raten).
- Die App ändert nichts automatisch: Formuliere Änderungen als Vorschläge.

Felder:
- headline: kurze, motivierende Überschrift (max. 6 Wörter).
- summary: 2–4 Sätze Gesamteindruck.
- wins: 2–4 Dinge, die gut liefen.
- improvements: 1–4 wichtigste Verbesserungspunkte.
- tips: 2–4 konkrete, umsetzbare Tipps für die nächste Woche (z. B. Lebensmittel tauschen, Protein-Quelle ergänzen,
  Mahlzeit verlagern), jeweils kurzer Titel + 1–2 Sätze.
- goal_note: Einschätzung zum Gewichtsziel und Tempo (liegt die Person im Plan? Ist das Zieldatum realistisch?
  ggf. Vorschlag zum Anpassen). null, wenn kein Gewichtsziel gesetzt ist und es nichts zu sagen gibt.
Gib ausschließlich JSON gemäß Schema zurück.
""".trimIndent()

        val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("headline") { put("type", "STRING") }
                putJsonObject("summary") { put("type", "STRING") }
                putJsonObject("wins") { put("type", "ARRAY"); putJsonObject("items") { put("type", "STRING") } }
                putJsonObject("improvements") { put("type", "ARRAY"); putJsonObject("items") { put("type", "STRING") } }
                putJsonObject("tips") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("title") { put("type", "STRING") }
                            putJsonObject("text") { put("type", "STRING") }
                        }
                        putJsonArray("required") { add("title"); add("text") }
                    }
                }
                putJsonObject("goal_note") { put("type", "STRING"); put("nullable", true) }
            }
            putJsonArray("required") { listOf("headline", "summary", "wins", "improvements", "tips").forEach(::add) }
            putJsonArray("propertyOrdering") {
                listOf("headline", "summary", "wins", "improvements", "tips", "goal_note").forEach(::add)
            }
        }
    }
}

internal object WeeklyReportParser {
    fun parse(text: String, from: LocalDate, to: LocalDate): WeeklyReport? {
        val obj = runCatching { lenientJson.parseToJsonElement(GeminiResponseParser.cleanJson(text)).asObject() }
            .getOrNull() ?: return null
        val summary = obj.string("summary") ?: return null
        return WeeklyReport(
            from = from,
            to = to,
            headline = obj.string("headline") ?: "",
            summary = summary,
            wins = obj.strings("wins"),
            improvements = obj.strings("improvements"),
            tips = (obj["tips"] as? JsonArray).orEmpty().mapNotNull { element ->
                val tip = element.asObject() ?: return@mapNotNull null
                val title = tip.string("title") ?: return@mapNotNull null
                ReportTip(title, tip.string("text").orEmpty())
            },
            goalNote = obj.string("goal_note"),
        )
    }

    private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
}
