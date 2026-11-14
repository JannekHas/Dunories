package de.kalorienapp.data.remote.gemini

import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.number
import de.kalorienapp.data.remote.string
import de.kalorienapp.domain.model.DishComponent
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.MealPlanSettings
import de.kalorienapp.domain.model.MealType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/** Ernährungsplan aus Fotos/Screenshots und/oder Text erkennen (z. B. Plan vom Ernährungsberater). */
class GeminiMealPlanAnalyzer(private val client: GeminiClient) {

    suspend fun analyze(images: List<ByteArray>, text: String?): List<MealPlanItem> {
        val answer = client.generateJson(RESPONSE_SCHEMA, timeoutMs = 180_000) {
            addJsonObject { put("text", PROMPT) }
            text?.takeIf { it.isNotBlank() }?.let { addJsonObject { put("text", "Plan als Text:\n" + it.trim()) } }
            images.forEach { bytes ->
                addJsonObject {
                    putJsonObject("inline_data") {
                        put("mime_type", GeminiClient.IMAGE_MIME)
                        put("data", Base64.getEncoder().encodeToString(bytes))
                    }
                }
            }
        }
        val items = MealPlanParser.parse(answer) ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
        if (items.isEmpty()) throw GeminiException(GeminiError.NO_FOOD)
        return items
    }

    private companion object {
        val PROMPT = """
Lies den Ernährungsplan aus den Bildern bzw. dem Text aus. Er besteht aus Tagen (z. B. „Tag 1“, „Montag“) mit
Mahlzeiten (Frühstück, Mittagessen, Abendessen, Snacks/Zwischenmahlzeiten). Gib jeden Tag mit day = 1, 2, 3 … in
der Reihenfolge des Plans zurück (Montag = 1, wenn Wochentage genannt sind), höchstens ${MealPlanSettings.MAX_DAYS} Tage.
Steht nur ein Tag ohne Wochentage da, ist es Tag 1. meal: BREAKFAST, LUNCH, DINNER oder SNACK
(Zwischenmahlzeiten, Nachtisch und Getränke zwischendurch = SNACK).
Übernimm Mengenangaben aus dem Plan (Stück, EL, Tasse … in g/ml umrechnen); fehlt eine, schätze eine übliche Portion.
${GeminiClient.FOOD_RULES}
Gib ausschließlich JSON gemäß Schema zurück.
""".trimIndent()

        val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("days") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("day") { put("type", "INTEGER") }
                            putJsonObject("meals") {
                                put("type", "ARRAY")
                                putJsonObject("items") {
                                    put("type", "OBJECT")
                                    putJsonObject("properties") {
                                        putJsonObject("meal") {
                                            put("type", "STRING")
                                            putJsonArray("enum") { MealType.entries.forEach { add(it.name) } }
                                        }
                                        putJsonObject("items") {
                                            put("type", "ARRAY")
                                            putJsonObject("items") { with(GeminiClient) { foodObject(withComponents = true) } }
                                        }
                                    }
                                    putJsonArray("required") { add("meal"); add("items") }
                                }
                            }
                        }
                        putJsonArray("required") { add("day"); add("meals") }
                    }
                }
            }
            putJsonArray("required") { add("days") }
        }
    }
}

/** Parst die Plan-Antwort; null bei unbrauchbarem Format. */
internal object MealPlanParser {
    fun parse(text: String): List<MealPlanItem>? {
        val root = runCatching { lenientJson.parseToJsonElement(GeminiResponseParser.cleanJson(text)).asObject() }.getOrNull()
            ?: return null
        val days = root["days"] as? JsonArray ?: return null
        val result = mutableListOf<MealPlanItem>()
        days.mapNotNull { it.asObject() }.forEachIndexed { fallback, day ->
            val dayIndex = ((day.number("day")?.toInt() ?: (fallback + 1)) - 1)
                .takeIf { it in 0 until MealPlanSettings.MAX_DAYS } ?: return@forEachIndexed
            (day["meals"] as? JsonArray).orEmpty().mapNotNull { it.asObject() }.forEach { meal ->
                val mealType = MealType.entries.find { it.name == meal.string("meal")?.uppercase() } ?: MealType.SNACK
                (meal["items"] as? JsonArray).orEmpty().mapNotNull { it.asObject() }.forEach { item ->
                    val draft = GeminiResponseParser.parseItem(item, EntrySource.PLAN) ?: return@forEach
                    val amount = draft.amount ?: return@forEach
                    val position = result.count { it.dayIndex == dayIndex && it.mealType == mealType }
                    result += MealPlanItem(
                        dayIndex = dayIndex,
                        mealType = mealType,
                        position = position,
                        name = draft.name,
                        amount = amount,
                        unit = draft.unit,
                        per100 = draft.per100,
                        components = draft.components.mapNotNull { c ->
                            c.amount?.let { DishComponent(c.name, it, c.unit, c.per100) }
                        }.takeIf { it.size >= 2 }.orEmpty(),
                    )
                }
            }
        }
        return result
    }
}
