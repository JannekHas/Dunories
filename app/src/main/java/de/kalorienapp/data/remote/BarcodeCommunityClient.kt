package de.kalorienapp.data.remote

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.Nutrients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Gemeinsame Barcode-Sammlung über den eigenen Worker (Entscheidung des Nutzers, Nov. 2026, Variante A):
 * Produkte, die Open Food Facts nicht kennt, aber jemand per Etikett-Foto erfasst hat. Ohne Konto-Daten
 * (der Worker speichert nur eine pseudonyme Kennung, damit jede Person pro Barcode einmal zählt).
 */
class BarcodeCommunityClient(
    private val baseUrl: String,
    /** Token (auch Gast-Konto); null = Dienst nicht verfügbar. */
    private val token: suspend () -> String?,
    /** Melden läuft im Hintergrund weiter, auch wenn der Bildschirm schon zu ist. */
    private val scope: CoroutineScope,
) {
    private suspend fun headers(): Map<String, String>? =
        runCatching { token() }.getOrNull()?.let { mapOf("Authorization" to "Bearer $it") }

    /** Produkt aus der Sammlung; null = unbekannt oder nicht erreichbar (dann geht es normal weiter). */
    suspend fun lookup(barcode: String): FoodDraft? {
        val h = headers() ?: return null
        val body = runCatching { Http.get("$baseUrl/v1/barcode/$barcode", h, timeoutMs = 6_000) }.getOrNull() ?: return null
        return parse(body)
    }

    /** Etikett-Werte für einen unbekannten Barcode melden (fire and forget). */
    fun submit(barcode: String, name: String, unit: AmountUnit, per100: Nutrients) {
        scope.launch {
            val h = headers() ?: return@launch
            val json = buildJsonObject {
                put("barcode", barcode)
                put("name", name)
                put("unit", if (unit == AmountUnit.MILLILITER) "ml" else "g")
                putJsonObject("per100") {
                    put("kcal", per100.kcal)
                    put("proteinG", per100.proteinG)
                    put("carbsG", per100.carbsG)
                    put("fatG", per100.fatG)
                    per100.saturatedFatG?.let { put("saturatedFatG", it) }
                    per100.sugarG?.let { put("sugarG", it) }
                    per100.fiberG?.let { put("fiberG", it) }
                    per100.saltG?.let { put("saltG", it) }
                }
            }
            runCatching { Http.postJson("$baseUrl/v1/barcode", json.toString(), h, timeoutMs = 10_000) }
        }
    }

    internal companion object {
        /** Antwort des Workers → Vorschlag (Menge muss der Nutzer eintragen). */
        fun parse(body: String): FoodDraft? {
            val o = runCatching { lenientJson.parseToJsonElement(body).asObject() }.getOrNull() ?: return null
            if ((o["found"] as? JsonPrimitive)?.content != "true") return null
            val name = o.string("name")?.takeIf { it.isNotBlank() } ?: return null
            val p = o["per100"] as? JsonObject ?: return null
            fun n(key: String) = p.number(key)
            return FoodDraft(
                name = name,
                amount = null,
                unit = if (o.string("unit") == "ml") AmountUnit.MILLILITER else AmountUnit.GRAM,
                per100 = Nutrients(
                    kcal = n("kcal") ?: return null,
                    proteinG = n("proteinG") ?: 0.0,
                    carbsG = n("carbsG") ?: 0.0,
                    fatG = n("fatG") ?: 0.0,
                    saturatedFatG = n("saturatedFatG"),
                    sugarG = n("sugarG"),
                    fiberG = n("fiberG"),
                    saltG = n("saltG"),
                ),
                source = EntrySource.BARCODE,
            )
        }
    }
}
