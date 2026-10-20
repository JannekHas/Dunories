package de.kalorienapp.data.remote.gemini

import java.util.Base64
import de.kalorienapp.data.remote.Http
import de.kalorienapp.data.remote.HttpStatusException
import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.number
import de.kalorienapp.data.remote.string
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.Nutrients
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException

/** Warum eine KI-Analyse fehlgeschlagen ist – die UI bietet dann immer die manuelle Eingabe an. */
enum class GeminiError { NO_API_KEY, REJECTED, NETWORK, QUOTA, INVALID_RESPONSE, NO_FOOD }

class GeminiException(val error: GeminiError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * Gemini-REST-Client. Antworten werden immer als JSON mit festem Schema angefordert
 * (Nährwerte pro 100 g/ml in Etikett-Reihenfolge) und tolerant geparst.
 */
class GeminiClient(private val apiKey: String) {

    suspend fun analyzePhoto(jpeg: ByteArray): List<FoodDraft> {
        val image = Base64.getEncoder().encodeToString(jpeg)
        return generate(EntrySource.AI_PHOTO) {
            addJsonObject { put("text", PHOTO_PROMPT) }
            addJsonObject {
                putJsonObject("inline_data") {
                    put("mime_type", "image/jpeg")
                    put("data", image)
                }
            }
        }
    }

    suspend fun analyzeText(description: String): List<FoodDraft> =
        generate(EntrySource.AI_TEXT) {
            addJsonObject { put("text", TEXT_PROMPT + description.trim()) }
        }

    private suspend fun generate(
        source: EntrySource,
        parts: JsonArrayBuilder.() -> Unit,
    ): List<FoodDraft> {
        if (apiKey.isBlank()) throw GeminiException(GeminiError.NO_API_KEY)
        val request = buildJsonObject {
            putJsonArray("contents") { addJsonObject { putJsonArray("parts", parts) } }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", RESPONSE_SCHEMA)
                put("temperature", 0.2)
            }
        }
        val body = try {
            Http.postJson(
                url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent",
                body = request.toString(),
                headers = mapOf("x-goog-api-key" to apiKey),
                timeoutMs = 120_000,
            )
        } catch (e: HttpStatusException) {
            throw GeminiException(
                when (e.code) {
                    429 -> GeminiError.QUOTA
                    // Ungültiger Key liefert je nach Fall 400, 401 oder 403.
                    400, 401, 403 -> GeminiError.REJECTED
                    else -> GeminiError.NETWORK
                },
                e,
            )
        } catch (e: IOException) {
            throw GeminiException(GeminiError.NETWORK, e)
        }
        val drafts = GeminiResponseParser.parse(body, source)
            ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
        if (drafts.isEmpty()) throw GeminiException(GeminiError.NO_FOOD)
        return drafts
    }

    companion object {
        /**
         * Flash-Lite: im kostenlosen Kontingent schnell (wenige Sekunden). Das große Flash-Modell
         * brauchte im Test 90–160 s pro Anfrage und ist für die Erfassung zu langsam.
         */
        const val MODEL = "gemini-flash-lite-latest"

        private const val COMMON_RULES = """
Für jede Komponente: kurzer deutscher Name, Menge (g bei festen, ml bei flüssigen Lebensmitteln)
und typische Nährwerte PRO 100 g bzw. PRO 100 ml – NICHT für die Gesamtmenge.
Nutze typische Werte aus deutschen Nährwerttabellen. Unbekannte optionale Werte als null.
Gib ausschließlich JSON gemäß Schema zurück."""

        private const val PHOTO_PROMPT = """Analysiere dieses Foto einer Mahlzeit.
Erkenne alle sichtbaren Lebensmittel und Getränke als einzelne Komponenten und schätze die Menge auf dem Foto.
Wenn kein Essen oder Getränk zu sehen ist, gib eine leere Liste zurück.$COMMON_RULES"""

        private const val TEXT_PROMPT = """Zerlege die beschriebene Mahlzeit in einzelne Lebensmittel-Komponenten.
Fehlt eine Mengenangabe, schätze eine übliche Portion.$COMMON_RULES
Beschreibung: """

        // Muss vor RESPONSE_SCHEMA stehen (Initialisierungsreihenfolge).
        private val FIELD_ORDER = listOf(
            "name", "amount", "unit",
            "kcal", "fat", "saturated_fat", "carbs", "sugar", "fiber", "protein", "salt",
        )
        private val REQUIRED = listOf("name", "amount", "unit", "kcal", "fat", "carbs", "protein")

        private val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("items") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("name") { put("type", "STRING") }
                            putJsonObject("amount") { put("type", "NUMBER") }
                            putJsonObject("unit") {
                                put("type", "STRING")
                                putJsonArray("enum") { add("g"); add("ml") }
                            }
                            FIELD_ORDER.drop(3).forEach { field ->
                                putJsonObject(field) {
                                    put("type", "NUMBER")
                                    if (field !in REQUIRED) put("nullable", true)
                                }
                            }
                        }
                        putJsonArray("required") { REQUIRED.forEach { add(it) } }
                        putJsonArray("propertyOrdering") { FIELD_ORDER.forEach { add(it) } }
                    }
                }
            }
            putJsonArray("required") { add("items") }
        }

    }
}

/** Parst die Gemini-Antwort; null bei unbrauchbarem Format (→ Fehlerfall, manuelle Eingabe). */
internal object GeminiResponseParser {

    fun parse(body: String, source: EntrySource): List<FoodDraft>? {
        val root = runCatching { lenientJson.parseToJsonElement(body).asObject() }.getOrNull() ?: return null
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull().asObject() ?: return null
        val parts = candidate["content"].asObject()?.get("parts") as? JsonArray ?: return null
        // „Gedanken“-Teile überspringen, nur den eigentlichen Antworttext verwenden.
        val text = parts.mapNotNull { it.asObject() }
            .filterNot { (it["thought"] as? JsonPrimitive)?.boolean == true }
            .mapNotNull { it.string("text") }
            .joinToString("")
        return parseItems(text, source)
    }

    fun parseItems(text: String, source: EntrySource): List<FoodDraft>? {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = runCatching { lenientJson.parseToJsonElement(cleaned).asObject() }.getOrNull() ?: return null
        val items = obj["items"] as? JsonArray ?: return null
        return items.mapNotNull { element ->
            val item = element.asObject() ?: return@mapNotNull null
            val name = item.string("name") ?: return@mapNotNull null
            FoodDraft(
                name = name,
                amount = item.number("amount")?.takeIf { it > 0 },
                unit = if (item.string("unit")?.lowercase() == "ml") AmountUnit.MILLILITER else AmountUnit.GRAM,
                per100 = Nutrients(
                    kcal = item.nonNegative("kcal") ?: 0.0,
                    fatG = item.nonNegative("fat") ?: 0.0,
                    saturatedFatG = item.nonNegative("saturated_fat"),
                    carbsG = item.nonNegative("carbs") ?: 0.0,
                    sugarG = item.nonNegative("sugar"),
                    fiberG = item.nonNegative("fiber"),
                    proteinG = item.nonNegative("protein") ?: 0.0,
                    saltG = item.nonNegative("salt"),
                ),
                source = source,
            )
        }
    }

    private fun JsonObject.nonNegative(key: String) = number(key)?.coerceAtLeast(0.0)
}
