package de.kalorienapp.data.remote.gemini

import android.util.Log
import de.kalorienapp.BuildConfig
import java.util.Base64
import de.kalorienapp.data.remote.Http
import de.kalorienapp.data.remote.HttpStatusException
import de.kalorienapp.data.remote.MicroFields
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.SocketTimeoutException

/** Warum eine KI-Analyse fehlgeschlagen ist – die UI bietet dann immer die manuelle Eingabe an. */
enum class GeminiError { NO_API_KEY, REJECTED, NETWORK, QUOTA, OVERLOADED, INVALID_RESPONSE, NO_FOOD }

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

    /** Foto einer Nährwerttabelle exakt ablesen (keine Schätzung). */
    suspend fun analyzeLabel(jpeg: ByteArray): List<FoodDraft> {
        val image = Base64.getEncoder().encodeToString(jpeg)
        return generate(EntrySource.LABEL) {
            addJsonObject { put("text", LABEL_PROMPT) }
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
        val text = generateJson(RESPONSE_SCHEMA, parts = parts)
        val drafts = GeminiResponseParser.parseItems(text, source)
            ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
        if (drafts.isEmpty()) throw GeminiException(GeminiError.NO_FOOD)
        return drafts
    }

    /**
     * Ein Aufruf mit festem JSON-Schema. @return Antworttext (JSON) ohne „Gedanken“-Teile.
     * Fehler werden als [GeminiException] geworfen.
     *
     * Die Antwortzeit im kostenlosen Kontingent schwankt stark (1 s bis über eine Minute, ab und
     * zu „überlastet“). Deshalb zwei Spuren im Rennen, die erste gültige Antwort gewinnt:
     * - Spur 1 sofort: wechselt bei Fehlern zwischen den schnellen Modellen ([FAST_MODELS]).
     * - Spur 2 nach [HEDGE_DELAY_MS] (oder wenn Spur 1 aufgibt): neue schnelle Anfrage – eine
     *   hängende Anfrage läuft beim zweiten Versuch meist sofort durch –, zuletzt [FALLBACK_MODEL].
     * Die unterlegene Anfrage wird abgebrochen.
     */
    internal suspend fun generateJson(
        schema: JsonObject,
        timeoutMs: Int = 120_000,
        parts: JsonArrayBuilder.() -> Unit,
    ): String {
        if (apiKey.isBlank()) throw GeminiException(GeminiError.NO_API_KEY)
        val request = buildJsonObject {
            putJsonArray("contents") { addJsonObject { putJsonArray("parts", parts) } }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
                put("temperature", 0.2)
                // Kein langes „Nachdenken“ – Schätzen und Ablesen brauchen das nicht.
                putJsonObject("thinkingConfig") { put("thinkingLevel", "minimal") }
            }
        }.toString()
        return coroutineScope {
            val first = async { runCatching { callModels(FAST_MODELS + FAST_MODELS, request, timeoutMs) } }
            withTimeoutOrNull(HEDGE_DELAY_MS) { first.join() }
            if (first.isCompleted) {
                val result = first.await()
                result.getOrNull()?.let { return@coroutineScope it }
                // Ungültiger Key gilt für alle Modelle.
                (result.exceptionOrNull() as? GeminiException)?.takeIf { !it.retryable }?.let { throw it }
            }
            val second = async { runCatching { callModels(FAST_MODELS.reversed() + FALLBACK_MODEL, request, timeoutMs) } }
            val pending = listOf(first, second).filter { !it.isCompleted }.toMutableList()
            var lastError = first.takeIf { it.isCompleted }?.await()?.exceptionOrNull()
            while (pending.isNotEmpty()) {
                val (done, result) = select<Pair<Deferred<Result<String>>, Result<String>>> {
                    pending.forEach { lane -> lane.onAwait { lane to it } }
                }
                pending.remove(done)
                result.getOrNull()?.let { text ->
                    pending.forEach { it.cancel() }
                    return@coroutineScope text
                }
                lastError = result.exceptionOrNull()
            }
            throw lastError ?: GeminiException(GeminiError.NETWORK)
        }
    }

    /** Lohnt ein anderes Modell? Kontingente gelten je Modell, also auch bei 429. */
    private val GeminiException.retryable
        get() = error == GeminiError.OVERLOADED || error == GeminiError.NETWORK || error == GeminiError.QUOTA

    /**
     * Modelle der Reihe nach versuchen; weiter nur bei Überlastung (5xx), unbekanntem Modell (404)
     * oder Netzwerkfehler. Kurze Pause dazwischen, damit ein kurzer Engpass vorbeigeht.
     */
    private suspend fun callModels(models: List<String>, request: String, timeoutMs: Int): String {
        var lastError: GeminiException? = null
        for ((index, model) in models.withIndex()) {
            if (index > 0) delay(RETRY_DELAY_MS)
            val started = System.currentTimeMillis()
            fun log(result: String) {
                if (BuildConfig.DEBUG) Log.d("Gemini", "$model: $result nach ${System.currentTimeMillis() - started} ms")
            }
            val error = try {
                val body = Http.postJson(
                    url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    body = request,
                    headers = mapOf("x-goog-api-key" to apiKey),
                    timeoutMs = timeoutMs,
                )
                log("ok")
                return GeminiResponseParser.answerText(body) ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
            } catch (e: HttpStatusException) {
                log("HTTP ${e.code}")
                GeminiException(if (e.code == 404) GeminiError.OVERLOADED else errorFor(e.code), e)
            } catch (e: SocketTimeoutException) {
                // Nach langem Warten nicht noch einmal minutenlang warten lassen.
                throw GeminiException(GeminiError.NETWORK, e)
            } catch (e: IOException) {
                log("abgebrochen/Netzwerk")
                GeminiException(GeminiError.NETWORK, e)
            }
            lastError = error
            if (!error.retryable) throw error
        }
        throw lastError ?: GeminiException(GeminiError.NETWORK)
    }

    internal val key: String get() = apiKey

    companion object {
        internal fun errorFor(code: Int): GeminiError = when (code) {
            429 -> GeminiError.QUOTA
            // Ungültiger Key liefert je nach Fall 400, 401 oder 403.
            400, 401, 403 -> GeminiError.REJECTED
            in 500..599 -> GeminiError.OVERLOADED
            else -> GeminiError.NETWORK
        }

        private const val RETRY_DELAY_MS = 600L

        /**
         * Im kostenlosen Kontingent gemessen (Okt. 2026): 3.1-Flash-Lite meist 2–13 s,
         * „flash-lite-latest“ (3.5) 30–60 s, große Flash-Modelle oft überlastet oder 90–160 s.
         */
        const val MODEL = "gemini-3.1-flash-lite"
        /** Gleich schnell, eigenes Kontingent – springt ein, wenn [MODEL] überlastet ist. */
        private val FAST_MODELS = listOf(MODEL, "gemini-3.1-flash-lite-preview")
        /** Langsam (30–60 s), aber verlässlich – nur als letzter Versuch. */
        const val FALLBACK_MODEL = "gemini-flash-lite-latest"
        private const val HEDGE_DELAY_MS = 8_000L

        private const val COMMON_RULES = """
Für jede Komponente: kurzer deutscher Name, Menge (g bei festen, ml bei flüssigen Lebensmitteln)
und typische Nährwerte PRO 100 g bzw. PRO 100 ml – NICHT für die Gesamtmenge.
Nutze typische Werte aus deutschen Nährwerttabellen. Unbekannte optionale Werte als null.
${MicroFields.PROMPT_RULE}
Gib ausschließlich JSON gemäß Schema zurück."""

        private const val PHOTO_PROMPT = """Analysiere dieses Foto einer Mahlzeit.
Erkenne alle sichtbaren Lebensmittel und Getränke als einzelne Komponenten und schätze die Menge auf dem Foto.
Wenn kein Essen oder Getränk zu sehen ist, gib eine leere Liste zurück.$COMMON_RULES"""

        private const val LABEL_PROMPT = """Auf dem Foto ist eine Nährwerttabelle (Etikett einer Verpackung).
Lies die Werte GENAU so ab, wie sie gedruckt sind – nichts schätzen. Gib genau EIN Element zurück:
name = Produktname, falls auf dem Foto lesbar, sonst „Produkt (Etikett)“.
Werte immer PRO 100 g bzw. PRO 100 ml (Spalte „pro 100 g/ml“; gibt es nur „pro Portion“, rechne mit der angegebenen
Portionsgröße auf 100 um). unit = ml bei Getränken („100 ml“), sonst g. amount = angegebene Portionsgröße, sonst 100.
Energie in kcal (nicht kJ). Salz in g. Steht ein Wert nicht auf dem Etikett: null (Hauptwerte dann 0).
Vitamine und Mineralstoffe nur, wenn sie auf dem Etikett stehen (umgerechnet in die Einheit aus dem Feldnamen), sonst null.
Ist keine Nährwerttabelle zu sehen, gib eine leere Liste zurück.
Gib ausschließlich JSON gemäß Schema zurück."""

        private const val TEXT_PROMPT = """Zerlege die beschriebene Mahlzeit in einzelne Lebensmittel-Komponenten.
Fehlt eine Mengenangabe, schätze eine übliche Portion.$COMMON_RULES
Beschreibung: """

        // Muss vor RESPONSE_SCHEMA stehen (Initialisierungsreihenfolge).
        private val FIELD_ORDER = listOf(
            "name", "amount", "unit",
            "kcal", "fat", "saturated_fat", "carbs", "sugar", "fiber", "protein", "salt",
        ) + MicroFields.KEYS.values
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

    fun parse(body: String, source: EntrySource): List<FoodDraft>? =
        answerText(body)?.let { parseItems(it, source) }

    /** Antworttext aus der API-Antwort; „Gedanken“-Teile werden übersprungen. */
    fun answerText(body: String): String? {
        val root = runCatching { lenientJson.parseToJsonElement(body).asObject() }.getOrNull() ?: return null
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull().asObject() ?: return null
        val parts = candidate["content"].asObject()?.get("parts") as? JsonArray ?: return null
        return parts.mapNotNull { it.asObject() }
            .filterNot { (it["thought"] as? JsonPrimitive)?.boolean == true }
            .mapNotNull { it.string("text") }
            .joinToString("")
            .takeIf { it.isNotBlank() }
    }

    /** Entfernt ```json-Zäune, die Modelle manchmal trotz JSON-Modus liefern. */
    fun cleanJson(text: String): String =
        text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    fun parseItems(text: String, source: EntrySource): List<FoodDraft>? {
        val obj = runCatching { lenientJson.parseToJsonElement(cleanJson(text)).asObject() }.getOrNull() ?: return null
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
                    micros = MicroFields.read(item),
                ),
                source = source,
            )
        }
    }

    internal fun JsonObject.nonNegative(key: String) = number(key)?.coerceAtLeast(0.0)
}
