package de.kalorienapp.data.remote.gemini

import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.gemini.GeminiResponseParser.nonNegative
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.number
import de.kalorienapp.data.remote.string
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.DishImageHint
import de.kalorienapp.domain.model.Effort
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Recipe
import de.kalorienapp.domain.model.RecipeDraft
import de.kalorienapp.domain.model.RecipeIngredient
import de.kalorienapp.domain.model.RecipeSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64
import kotlin.math.roundToInt

/** Eingaben für die Rezept-Analyse – Text, Bilder (nummeriert) und optional ein hochgeladenes Video. */
data class RecipeAnalysisInput(
    val caption: String? = null,
    val transcript: String? = null,
    /** JPEGs; die KI wählt per Index das Bild mit dem fertigen Gericht. */
    val images: List<ByteArray> = emptyList(),
    val video: GeminiFile? = null,
    val source: RecipeSource,
    val sourceUrl: String? = null,
)

/**
 * Rezept aus Social-Media-Inhalten per Gemini: Zutaten mit Nährwerten pro 100 g/ml, Schritte,
 * Zeit, Aufwand, Kategorien und welches Bild das fertige Gericht zeigt.
 */
class GeminiRecipeAnalyzer(private val client: GeminiClient) {

    /** @throws GeminiException (NO_FOOD = kein Rezept erkannt) */
    suspend fun analyze(input: RecipeAnalysisInput, categories: List<String>): RecipeDraft {
        val text = client.generateJson(RESPONSE_SCHEMA, timeoutMs = 240_000) {
            addJsonObject { put("text", prompt(categories)) }
            input.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                addJsonObject { put("text", "Beschreibung des Beitrags:\n${caption.take(MAX_TEXT)}") }
            }
            input.transcript?.takeIf { it.isNotBlank() }?.let { transcript ->
                addJsonObject { put("text", "Gesprochener Text im Video:\n${transcript.take(MAX_TEXT)}") }
            }
            input.video?.let { video ->
                addJsonObject { put("text", "Video des Beitrags:") }
                addJsonObject {
                    putJsonObject("file_data") {
                        put("mime_type", video.mimeType)
                        put("file_uri", video.uri)
                    }
                }
            }
            input.images.forEachIndexed { index, jpeg ->
                addJsonObject { put("text", "Bild $index:") }
                addJsonObject {
                    putJsonObject("inline_data") {
                        put("mime_type", "image/jpeg")
                        put("data", Base64.getEncoder().encodeToString(jpeg))
                    }
                }
            }
        }
        val draft = GeminiRecipeParser.parse(text, input.source, input.sourceUrl)
            ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
        return draft.takeIf { it.recipe.ingredients.isNotEmpty() || it.recipe.steps.isNotEmpty() }
            ?: throw GeminiException(GeminiError.NO_FOOD)
    }

    private fun prompt(categories: List<String>) = """
Du bekommst Inhalte eines Social-Media-Beitrags (Beschreibung, gesprochener Text, Bilder und/oder Video).
Extrahiere daraus genau EIN Kochrezept auf Deutsch (übersetze bei Bedarf). Ist kein Rezept erkennbar: found = false.

Regeln:
- name: kurzer, appetitlicher deutscher Rezeptname, ohne Emojis und Hashtags.
- description: 1–2 Sätze, worum es geht.
- servings: Portionen laut Rezept, sonst sinnvoll schätzen.
- total_minutes: Gesamtzeit inkl. Back-/Garzeit (schätzen, falls nicht genannt).
- effort: easy, medium oder hard.
- categories: 1–3 passende aus dieser Liste: ${categories.joinToString(", ")}. Nur wenn nichts passt, einen neuen kurzen deutschen Namen.
- ingredients: ALLE Zutaten. quantity + quantity_unit wie im Rezept, deutsche Einheiten
  (g, ml, Stück, EL, TL, Prise, Dose, Packung, Bund, Zehe, Scheibe); Cups/oz/lb/tbsp/tsp umrechnen.
  amount = Menge in g (fest) bzw. ml (flüssig) für die Nährwertberechnung – bei Stück/EL/TL schätzen,
  bei „nach Geschmack“ null. Nährwerte PRO 100 g bzw. PRO 100 ml (Produkt wie gekauft/roh) mit typischen
  Werten aus deutschen Nährwerttabellen. Unbekannte optionale Werte null. note: z. B. „gewürfelt“, sonst null.
- Im Beitrag genannte Kalorien/Makros nur zur Plausibilitätsprüfung; maßgeblich sind die Zutaten.
- steps: alle Zubereitungsschritte in sinnvoller Reihenfolge, kurz und klar, ohne Nummerierung.
- image: Wähle aus den nummerierten Bildern (Index ab 0) das, auf dem das FERTIGE Gericht am größten und
  appetitlichsten zu sehen ist – nicht Personen, Hände oder Text. box_2d = [ymin, xmin, ymax, xmax] (0–1000)
  eng um das fertige Essen. dish_visible = false, wenn auf keinem Bild fertiges Essen zu sehen ist; ohne Bilder index = -1.
Gib ausschließlich JSON gemäß Schema zurück.
""".trimIndent()

    private companion object {
        const val MAX_TEXT = 12_000

        private val NUTRIENTS = listOf("kcal", "fat", "saturated_fat", "carbs", "sugar", "fiber", "protein", "salt")
        private val REQUIRED_NUTRIENTS = setOf("kcal", "fat", "carbs", "protein")

        private fun JsonObjectBuilder.type(type: String, nullable: Boolean = false) {
            put("type", type)
            if (nullable) put("nullable", true)
        }

        val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("found") { type("BOOLEAN") }
                putJsonObject("name") { type("STRING") }
                putJsonObject("description") { type("STRING") }
                putJsonObject("servings") { type("INTEGER") }
                putJsonObject("total_minutes") { type("INTEGER", nullable = true) }
                putJsonObject("effort") {
                    type("STRING")
                    putJsonArray("enum") { add("easy"); add("medium"); add("hard") }
                }
                putJsonObject("categories") {
                    type("ARRAY")
                    putJsonObject("items") { type("STRING") }
                }
                putJsonObject("ingredients") {
                    type("ARRAY")
                    putJsonObject("items") {
                        type("OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("name") { type("STRING") }
                            putJsonObject("quantity") { type("NUMBER", nullable = true) }
                            putJsonObject("quantity_unit") { type("STRING") }
                            putJsonObject("amount") { type("NUMBER", nullable = true) }
                            putJsonObject("unit") {
                                type("STRING")
                                putJsonArray("enum") { add("g"); add("ml") }
                            }
                            NUTRIENTS.forEach { n -> putJsonObject(n) { type("NUMBER", nullable = n !in REQUIRED_NUTRIENTS) } }
                            putJsonObject("note") { type("STRING", nullable = true) }
                        }
                        putJsonArray("required") {
                            listOf("name", "quantity_unit", "unit").forEach(::add)
                            REQUIRED_NUTRIENTS.forEach(::add)
                        }
                        putJsonArray("propertyOrdering") {
                            listOf("name", "quantity", "quantity_unit", "amount", "unit").forEach(::add)
                            NUTRIENTS.forEach(::add)
                            add("note")
                        }
                    }
                }
                putJsonObject("steps") {
                    type("ARRAY")
                    putJsonObject("items") { type("STRING") }
                }
                putJsonObject("image") {
                    type("OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("index") { type("INTEGER") }
                        putJsonObject("dish_visible") { type("BOOLEAN") }
                        putJsonObject("box_2d") {
                            type("ARRAY")
                            putJsonObject("items") { type("INTEGER") }
                        }
                    }
                    putJsonArray("required") { add("index"); add("dish_visible"); add("box_2d") }
                }
            }
            putJsonArray("required") {
                listOf("found", "name", "description", "servings", "effort", "categories", "ingredients", "steps", "image")
                    .forEach(::add)
            }
            putJsonArray("propertyOrdering") {
                listOf(
                    "found", "name", "description", "servings", "total_minutes", "effort",
                    "categories", "ingredients", "steps", "image",
                ).forEach(::add)
            }
        }
    }
}

/** Parst die Rezept-Antwort tolerant; null bei unbrauchbarem Format. found = false → leeres Rezept. */
internal object GeminiRecipeParser {

    fun parse(text: String, source: RecipeSource, sourceUrl: String?): RecipeDraft? {
        val obj = runCatching { lenientJson.parseToJsonElement(GeminiResponseParser.cleanJson(text)).asObject() }
            .getOrNull() ?: return null
        val found = (obj["found"] as? JsonPrimitive)?.booleanOrNull ?: true
        if (!found) return RecipeDraft(Recipe(name = "", source = source, sourceUrl = sourceUrl))

        val ingredients = (obj["ingredients"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element.asObject() ?: return@mapNotNull null
            val name = item.string("name") ?: return@mapNotNull null
            RecipeIngredient(
                name = name,
                quantity = item.number("quantity")?.takeIf { it > 0 },
                quantityUnit = item.string("quantity_unit").orEmpty(),
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
                note = item.string("note"),
            )
        }
        val steps = (obj["steps"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
        val image = obj["image"].asObject()?.let { img ->
            val index = (img["index"] as? JsonPrimitive)?.intOrNull ?: -1
            val visible = (img["dish_visible"] as? JsonPrimitive)?.booleanOrNull ?: false
            val box = (img["box_2d"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.roundToInt() }
            if (visible && index >= 0 && box.size == 4) DishImageHint(index, box) else null
        }
        return RecipeDraft(
            recipe = Recipe(
                name = obj.string("name").orEmpty(),
                description = obj.string("description").orEmpty(),
                servings = obj.number("servings")?.roundToInt()?.coerceIn(1, 50) ?: 1,
                totalMinutes = obj.number("total_minutes")?.roundToInt()?.takeIf { it > 0 },
                effort = when (obj.string("effort")?.lowercase()) {
                    "easy" -> Effort.EASY
                    "medium" -> Effort.MEDIUM
                    "hard" -> Effort.HARD
                    else -> null
                },
                sourceUrl = sourceUrl,
                source = source,
                ingredients = ingredients,
                steps = steps,
            ),
            categoryNames = (obj["categories"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
                .distinct()
                .take(3),
            image = image,
        )
    }
}
