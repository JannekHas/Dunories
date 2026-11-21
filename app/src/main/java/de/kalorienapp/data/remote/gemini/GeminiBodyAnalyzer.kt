package de.kalorienapp.data.remote.gemini

import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.number
import de.kalorienapp.domain.model.MeasureType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * Körperanalyse (Bioimpedanz-Waage) aus Fotos vom Display/Ausdruck oder aus gesprochenem/getipptem Text
 * auslesen. Ergebnis ist immer nur ein Vorschlag – der Nutzer prüft die Felder vor dem Speichern.
 */
class GeminiBodyAnalyzer(private val client: GeminiClient) {

    suspend fun analyze(images: List<ByteArray>, text: String?): Map<MeasureType, Double> {
        val answer = client.generateJson(RESPONSE_SCHEMA, timeoutMs = 60_000) {
            addJsonObject { put("text", PROMPT) }
            text?.takeIf { it.isNotBlank() }?.let { addJsonObject { put("text", "Gesprochen/getippt:\n" + it.trim()) } }
            images.forEach { bytes ->
                addJsonObject {
                    putJsonObject("inline_data") {
                        put("mime_type", GeminiClient.IMAGE_MIME)
                        put("data", Base64.getEncoder().encodeToString(bytes))
                    }
                }
            }
        }
        val values = BodyAnalysisParser.parse(answer) ?: throw GeminiException(GeminiError.INVALID_RESPONSE)
        if (values.isEmpty()) throw GeminiException(GeminiError.NO_FOOD)
        return values
    }

    private companion object {
        val PROMPT = """
Lies die Werte einer Körperanalyse (Bioimpedanz-Waage, Display, App-Screenshot oder Ausdruck) bzw. aus dem Text aus.
Nur Werte, die wirklich dastehen – nichts schätzen oder ausrechnen; fehlt ein Wert, null.
body_fat_percent = Körperfett in %, muscle_mass_kg = Muskelmasse in kg (nicht „fettfreie Masse“, nicht in %),
body_water_percent = Körperwasser in % (steht es nur in kg, null), visceral_fat_level = viszerales Fett als Stufe/Index
(Zahl ohne Einheit), bone_mass_kg = Knochenmasse in kg, bmr_kcal = Grundumsatz in kcal (bei kJ in kcal umrechnen: ÷ 4,184).
Komma = Dezimaltrenner. Gib ausschließlich JSON gemäß Schema zurück.
""".trimIndent()

        val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                BodyAnalysisParser.KEYS.values.forEach { key ->
                    putJsonObject(key) {
                        put("type", "NUMBER")
                        put("nullable", true)
                    }
                }
            }
            putJsonArray("propertyOrdering") { BodyAnalysisParser.KEYS.values.forEach { add(it) } }
        }
    }
}

internal object BodyAnalysisParser {
    val KEYS: Map<MeasureType, String> = linkedMapOf(
        MeasureType.BODY_FAT to "body_fat_percent",
        MeasureType.MUSCLE_MASS to "muscle_mass_kg",
        MeasureType.BODY_WATER to "body_water_percent",
        MeasureType.VISCERAL_FAT to "visceral_fat_level",
        MeasureType.BONE_MASS to "bone_mass_kg",
        MeasureType.BMR to "bmr_kcal",
    )

    /** Nur plausible Werte ([MeasureType.isValid]); kaputtes JSON → null. */
    fun parse(text: String): Map<MeasureType, Double>? {
        val obj = runCatching { lenientJson.parseToJsonElement(GeminiResponseParser.cleanJson(text)).asObject() }.getOrNull()
            ?: return null
        return KEYS.mapNotNull { (type, key) -> obj.number(key)?.takeIf(type::isValid)?.let { type to it } }.toMap()
    }
}
