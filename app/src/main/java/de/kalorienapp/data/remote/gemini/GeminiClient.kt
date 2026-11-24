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
import de.kalorienapp.domain.model.Dish
import de.kalorienapp.domain.model.DishComponent
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.Nutrients
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
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
enum class GeminiError { NO_API_KEY, REJECTED, NETWORK, QUOTA, OVERLOADED, INVALID_RESPONSE, NO_FOOD, DAILY_LIMIT,
    /** Gast-Gratis-Aufrufe aufgebraucht → Konto erstellen. */
    NEEDS_ACCOUNT,
    /** Konto ohne Premium (Probezeit vorbei) → Paywall. */
    NEEDS_PREMIUM,
}

/** Art des KI-Aufrufs – der Worker zählt danach (automatische Wochenauswertung zählt nicht mit). */
enum class AiKind(val header: String) { FOOD("food"), BODY("body"), PLAN("plan"), RECIPE("recipe"), WEEKLY("weekly"), WEEKLY_AUTO("weekly-auto"), STREAK("streak") }

class GeminiException(val error: GeminiError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * Gemini-Client. Antworten werden immer als JSON mit festem Schema angefordert
 * (Nährwerte pro 100 g/ml in Etikett-Reihenfolge) und tolerant geparst.
 * Alle Aufrufe laufen über den eigenen KI-Worker ([baseUrl], backend/ai-worker): Er hält den Gemini-Schlüssel,
 * prüft das Firebase-Token (Gast-Konto oder Google) und zählt die Aufrufe pro Person und Tag.
 */
class GeminiClient(
    private val baseUrl: String,
    /** Firebase-Token (legt bei Bedarf still ein Gast-Konto an); null = Konto-Dienst nicht verfügbar. */
    private val token: suspend () -> String?,
) {

    /** [hint] = Korrektur des Nutzers („Das ist Linsen-Dal, ca. 300 g“) – hat Vorrang vor dem Bild. */
    suspend fun analyzePhoto(imageBytes: ByteArray, hint: String? = null): List<FoodDraft> {
        val image = Base64.getEncoder().encodeToString(imageBytes)
        return generate(EntrySource.AI_PHOTO) {
            addJsonObject { put("text", PHOTO_PROMPT) }
            hint?.takeIf { it.isNotBlank() }?.let { addJsonObject { put("text", HINT_PROMPT + it.trim()) } }
            addJsonObject {
                putJsonObject("inline_data") {
                    put("mime_type", IMAGE_MIME)
                    put("data", image)
                }
            }
        }
    }

    /** Foto einer Nährwerttabelle exakt ablesen (keine Schätzung). */
    suspend fun analyzeLabel(imageBytes: ByteArray): List<FoodDraft> {
        val image = Base64.getEncoder().encodeToString(imageBytes)
        return generate(EntrySource.LABEL) {
            addJsonObject { put("text", LABEL_PROMPT) }
            addJsonObject {
                putJsonObject("inline_data") {
                    put("mime_type", IMAGE_MIME)
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
        val text = generateJson(RESPONSE_SCHEMA, kind = AiKind.FOOD, parts = parts)
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
     * - Spur 1 sofort: wechselt bei Fehlern zwischen den Modellen ([MODELS], je eigenes Kontingent).
     * - Spur 2 nach [HEDGE_DELAY_MS] (oder wenn Spur 1 aufgibt): neue Anfrage, beginnt beim anderen
     *   Modell – eine hängende Anfrage läuft beim zweiten Versuch meist sofort durch.
     * Zwischen den Versuchen wachsende Pausen ([RETRY_DELAYS_MS]): Bei „überlastet“ (Google, 24.11.2026)
     * hilft kurz warten, und so reißen wir nicht selbst das Minutenlimit (15/min je Modell).
     * Die unterlegene Anfrage wird abgebrochen.
     */
    internal suspend fun generateJson(
        schema: JsonObject,
        timeoutMs: Int = 120_000,
        kind: AiKind,
        parts: JsonArrayBuilder.() -> Unit,
    ): String {
        // Eine Kennung für alle Versuche/Spuren dieses Aufrufs → der Worker zählt ihn nur einmal.
        val headers = aiHeaders() + mapOf("X-Ai-Kind" to kind.header, "X-Ai-Request" to java.util.UUID.randomUUID().toString())
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
            val first = async { runCatching { callModels(MODELS + MODELS, request, headers, timeoutMs) } }
            withTimeoutOrNull(HEDGE_DELAY_MS) { first.join() }
            if (first.isCompleted) {
                val result = first.await()
                result.getOrNull()?.let { return@coroutineScope it }
                // Ungültiger Key gilt für alle Modelle.
                (result.exceptionOrNull() as? GeminiException)?.takeIf { !it.retryable }?.let { throw it }
            }
            val second = async { runCatching { callModels(MODELS.reversed() + MODELS.reversed().first(), request, headers, timeoutMs) } }
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
     * oder Netzwerkfehler. Wachsende Pause dazwischen, damit ein kurzer Engpass vorbeigeht.
     */
    private suspend fun callModels(models: List<String>, request: String, headers: Map<String, String>, timeoutMs: Int): String {
        var lastError: GeminiException? = null
        for ((index, model) in models.withIndex()) {
            if (index > 0) delay(RETRY_DELAYS_MS.getOrElse(index - 1) { RETRY_DELAYS_MS.last() })
            val started = System.currentTimeMillis()
            fun log(result: String) {
                if (BuildConfig.DEBUG) Log.d("Gemini", "$model: $result nach ${System.currentTimeMillis() - started} ms")
            }
            val error = try {
                val body = Http.postJson(
                    url = "$baseUrl/v1/models/$model:generateContent",
                    body = request,
                    headers = headers,
                    timeoutMs = timeoutMs,
                )
                log("ok")
                return GeminiResponseParser.answerText(body) ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
            } catch (e: HttpStatusException) {
                log("HTTP ${e.code}")
                GeminiException(if (e.code == 404) GeminiError.OVERLOADED else errorFor(e.code, e.body), e)
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

    internal val workerUrl: String get() = baseUrl

    /** Anmelde-Header für den Worker. Kein Worker/Konto-Dienst → [GeminiError.NO_API_KEY], kein Netz → NETWORK. */
    internal suspend fun aiHeaders(): Map<String, String> {
        if (baseUrl.isBlank()) throw GeminiException(GeminiError.NO_API_KEY)
        val t = try {
            token()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw GeminiException(GeminiError.NETWORK, e)
        } ?: throw GeminiException(GeminiError.NO_API_KEY)
        return mapOf("Authorization" to "Bearer $t")
    }

    companion object {
        /** Bilder gehen als WebP an die KI (kleiner als JPEG → schnellerer Upload). */
        const val IMAGE_MIME = "image/webp"

        internal fun errorFor(code: Int, body: String = ""): GeminiError = when (code) {
            // Eigenes Tageslimit des Workers (oder automatische Wochenauswertung schon gelaufen) – kein anderes Modell hilft.
            429 -> when {
                "needs_account" in body -> GeminiError.NEEDS_ACCOUNT
                "needs_premium" in body -> GeminiError.NEEDS_PREMIUM
                "daily_limit" in body || "weekly_done" in body -> GeminiError.DAILY_LIMIT
                else -> GeminiError.QUOTA
            }
            // Ungültiger Key liefert je nach Fall 400, 401 oder 403.
            400, 401, 403 -> GeminiError.REJECTED
            in 500..599 -> GeminiError.OVERLOADED
            else -> GeminiError.NETWORK
        }

        /** Pause vor dem 2., 3., 4. … Versuch einer Spur. */
        private val RETRY_DELAYS_MS = listOf(600L, 2_500L, 5_000L)

        /**
         * Im kostenlosen Kontingent gemessen (Okt. 2026): 3.1-Flash-Lite meist 2–13 s, 3.5 damals 30–60 s.
         * Nov. 2026: 3.5-Flash-Lite mit Foto ~1,5 s. „-preview“ und „flash-lite-latest“ teilen sich das
         * Kontingent mit 3.1 bzw. 3.5 (AI Studio zeigt nur diese zwei) → bringen beim Ausweichen nichts.
         */
        const val MODEL = "gemini-3.1-flash-lite"
        /** Zwei Modelle mit je eigenem Kontingent (15/min, 500/Tag) – springen füreinander ein. */
        private val MODELS = listOf(MODEL, "gemini-3.5-flash-lite")
        private const val HEDGE_DELAY_MS = 8_000L

        /** Gerichte als EIN Element mit Zutaten (Wunsch des Nutzers: „Heute“ soll nicht volllaufen). */
        private const val DISH_RULE = """Gerichte: Was zusammen ein Gericht bildet (z. B. Spaghetti Bolognese = Nudeln + Soße, Burger,
Bowl, Curry mit Reis, Müsli mit Milch, belegtes Brot), ist EIN Element: name = Name des Gerichts,
components = die einzelnen Zutaten (je mit Name, Menge und Nährwerten pro 100 g/ml), amount = Gesamtmenge,
Nährwerte des Elements = ganzes Gericht pro 100 g. Was eigenständig daneben steht, ist ein eigenes Element
ohne components: Getränke, Beilagensalat, Dessert, Obst, Snacks. Einzelne Lebensmittel haben keine components."""

        /** Regeln für einzelne Lebensmittel (auch für den Plan-Import). */
        internal const val FOOD_RULES = """
Für jede Komponente: kurzer deutscher Name, Menge (g bei festen, ml bei flüssigen Lebensmitteln)
und typische Nährwerte PRO 100 g bzw. PRO 100 ml – NICHT für die Gesamtmenge.
Nutze typische Werte aus deutschen Nährwerttabellen. Unbekannte optionale Werte als null.
$DISH_RULE
${MicroFields.PROMPT_RULE}"""

        private const val COMMON_RULES = """
Für jede Komponente: kurzer deutscher Name, Menge (g bei festen, ml bei flüssigen Lebensmitteln)
und typische Nährwerte PRO 100 g bzw. PRO 100 ml – NICHT für die Gesamtmenge.
Nutze typische Werte aus deutschen Nährwerttabellen. Unbekannte optionale Werte als null.
$DISH_RULE
${MicroFields.PROMPT_RULE}
Gib ausschließlich JSON gemäß Schema zurück."""

        private const val PHOTO_PROMPT = """Analysiere dieses Foto einer Mahlzeit.
Erkenne alle sichtbaren Gerichte, Lebensmittel und Getränke und schätze die Menge auf dem Foto.
Wenn kein Essen oder Getränk zu sehen ist, gib eine leere Liste zurück.$COMMON_RULES"""

        private const val HINT_PROMPT = """Hinweis des Nutzers zu diesem Foto – er hat Vorrang vor dem, was du auf dem Bild
zu erkennen glaubst (z. B. andere Lebensmittel, Mengen, Zubereitung oder Zutaten, die man nicht sieht).
Nutze das Foto nur noch für alles, was der Hinweis offenlässt: """

        private const val LABEL_PROMPT = """Auf dem Foto ist eine Nährwerttabelle (Etikett einer Verpackung).
Lies die Werte GENAU so ab, wie sie gedruckt sind – nichts schätzen. Gib genau EIN Element zurück:
name = Produktname, falls auf dem Foto lesbar, sonst „Produkt (Etikett)“.
Werte immer PRO 100 g bzw. PRO 100 ml (Spalte „pro 100 g/ml“; gibt es nur „pro Portion“, rechne mit der angegebenen
Portionsgröße auf 100 um). unit = ml bei Getränken („100 ml“), sonst g. amount = angegebene Portionsgröße, sonst 100.
Energie in kcal (nicht kJ). Salz in g. Steht ein Wert nicht auf dem Etikett: null (Hauptwerte dann 0).
Vitamine und Mineralstoffe nur, wenn sie auf dem Etikett stehen (umgerechnet in die Einheit aus dem Feldnamen), sonst null.
Ist keine Nährwerttabelle zu sehen, gib eine leere Liste zurück.
Gib ausschließlich JSON gemäß Schema zurück."""

        private const val TEXT_PROMPT = """Erfasse die beschriebene Mahlzeit als Gerichte und einzelne Lebensmittel.
Fehlt eine Mengenangabe, schätze eine übliche Portion.$COMMON_RULES
Beschreibung: """

        // Muss vor RESPONSE_SCHEMA stehen (Initialisierungsreihenfolge).
        private val FIELD_ORDER = listOf(
            "name", "amount", "unit",
            "kcal", "fat", "saturated_fat", "carbs", "sugar", "fiber", "protein", "salt",
        ) + MicroFields.KEYS.values
        private val REQUIRED = listOf("name", "amount", "unit", "kcal", "fat", "carbs", "protein")

        /** Ein Lebensmittel; Gerichte haben zusätzlich `components` (Zutaten, gleiche Felder, nicht verschachtelt). */
        internal fun JsonObjectBuilder.foodObject(withComponents: Boolean) {
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
                if (withComponents) {
                    putJsonObject("components") {
                        put("type", "ARRAY")
                        put("nullable", true)
                        putJsonObject("items") { foodObject(withComponents = false) }
                    }
                }
            }
            putJsonArray("required") { REQUIRED.forEach { add(it) } }
            putJsonArray("propertyOrdering") {
                FIELD_ORDER.forEach { add(it) }
                if (withComponents) add("components")
            }
        }

        private val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("items") {
                    put("type", "ARRAY")
                    putJsonObject("items") { foodObject(withComponents = true) }
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
        return items.mapNotNull { element -> parseItem(element.asObject() ?: return@mapNotNull null, source) }
    }

    /** Ein Element; Gerichte mit mind. 2 Zutaten werden aus den Zutaten berechnet (Menge, Nährwerte). */
    internal fun parseItem(item: JsonObject, source: EntrySource): FoodDraft? {
        val name = item.string("name") ?: return null
        val components = (item["components"] as? JsonArray).orEmpty()
            .mapNotNull { it.asObject()?.let { c -> parseFood(c, source) } }
            .filter { (it.amount ?: 0.0) > 0 }
        if (components.size >= 2) {
            val dish = components.map { DishComponent(it.name, it.amount!!, it.unit, it.per100) }
            val total = Dish.totalAmount(dish)
            return FoodDraft(
                name = name,
                amount = total,
                unit = Dish.unit(dish),
                per100 = Dish.total(dish).amountToPer100(total),
                source = source,
                components = components,
            )
        }
        return parseFood(item, source)
    }

    private fun parseFood(item: JsonObject, source: EntrySource): FoodDraft? {
        val name = item.string("name") ?: return null
        return FoodDraft(
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

    internal fun JsonObject.nonNegative(key: String) = number(key)?.coerceAtLeast(0.0)
}
