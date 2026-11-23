package de.kalorienapp.data.remote.gemini

import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.number
import de.kalorienapp.domain.Gamification
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/** Abgelesene Serie aus einer anderen App (null = stand nicht auf dem Bild). */
data class ReadStreak(val current: Int?, val longest: Int?)

/**
 * Serie aus einem Screenshot einer anderen Kalorien-App (YAZIO „Tage in Folge“ / „Längster Streak“) ablesen.
 * Ergebnis ist nur ein Vorschlag – der Nutzer prüft die Zahlen vor dem Übernehmen.
 */
class GeminiStreakReader(private val client: GeminiClient) {

    suspend fun read(images: List<ByteArray>): ReadStreak {
        val answer = client.generateJson(RESPONSE_SCHEMA, timeoutMs = 45_000, kind = AiKind.STREAK) {
            addJsonObject { put("text", PROMPT) }
            images.forEach { bytes ->
                addJsonObject {
                    putJsonObject("inline_data") {
                        put("mime_type", GeminiClient.IMAGE_MIME)
                        put("data", Base64.getEncoder().encodeToString(bytes))
                    }
                }
            }
        }
        val result = StreakReadParser.parse(answer) ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
        if (result.current == null && result.longest == null) throw GeminiException(GeminiError.NO_FOOD)
        return result
    }

    private companion object {
        val PROMPT = """
Das Bild ist ein Screenshot aus einer Kalorien-/Ernährungs-App (meist YAZIO), der eine Serie („Streak“) zeigt.
Lies zwei Zahlen ab, nur wenn sie wirklich dastehen – nichts schätzen:
current_streak = aktuelle Serie („Tage in Folge“, „Streak“, große Zahl neben der Flamme),
longest_streak = längste Serie („Längster Streak“, „Rekord“, „Bestwert“).
Fehlt eine Zahl, null. Gib ausschließlich JSON gemäß Schema zurück.
""".trimIndent()

        val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                listOf(StreakReadParser.CURRENT, StreakReadParser.LONGEST).forEach { key ->
                    putJsonObject(key) {
                        put("type", "INTEGER")
                        put("nullable", true)
                    }
                }
            }
            putJsonArray("propertyOrdering") {
                add(StreakReadParser.CURRENT)
                add(StreakReadParser.LONGEST)
            }
        }
    }
}

internal object StreakReadParser {
    const val CURRENT = "current_streak"
    const val LONGEST = "longest_streak"

    /** Nur ganze, plausible Zahlen; kaputtes JSON → null. Die längste Serie ist nie kleiner als die aktuelle. */
    fun parse(text: String): ReadStreak? {
        val obj = runCatching { lenientJson.parseToJsonElement(GeminiResponseParser.cleanJson(text)).asObject() }.getOrNull()
            ?: return null
        fun valid(key: String) = obj.number(key)?.takeIf { it >= 0 && it <= Gamification.ImportedStreak.MAX_IMPORT && it % 1.0 == 0.0 }?.toInt()
        val current = valid(CURRENT)
        val longest = valid(LONGEST)
        return ReadStreak(current, if (current != null && longest != null) maxOf(current, longest) else longest)
    }
}
