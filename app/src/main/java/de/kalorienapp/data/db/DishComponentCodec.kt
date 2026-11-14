package de.kalorienapp.data.db

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.DishComponent
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Zutaten eines Gerichts als JSON-Text in `food_entry.components` (Werte pro 100 g/ml). */
object DishComponentCodec {
    fun encode(components: List<DishComponent>): String? = components.takeIf { it.isNotEmpty() }?.let { list ->
        buildJsonArray {
            list.forEach { c ->
                addJsonObject {
                    put("name", c.name)
                    put("amount", c.amount)
                    put("unit", c.unit.name)
                    put("kcal", c.per100.kcal)
                    put("protein", c.per100.proteinG)
                    put("carbs", c.per100.carbsG)
                    put("fat", c.per100.fatG)
                    c.per100.saturatedFatG?.let { put("saturated_fat", it) }
                    c.per100.sugarG?.let { put("sugar", it) }
                    c.per100.fiberG?.let { put("fiber", it) }
                    c.per100.saltG?.let { put("salt", it) }
                    Micro.encode(c.per100.micros)?.let { put("micros", it) }
                }
            }
        }.toString()
    }

    /** Defekter Text → keine Zutaten (der Eintrag selbst bleibt mit seinen Summen erhalten). */
    fun decode(text: String?): List<DishComponent> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element.jsonObject
                DishComponent(
                    name = o.string("name").orEmpty(),
                    amount = o.number("amount") ?: 0.0,
                    unit = AmountUnit.entries.find { it.name == o.string("unit") } ?: AmountUnit.GRAM,
                    per100 = Nutrients(
                        kcal = o.number("kcal") ?: 0.0,
                        proteinG = o.number("protein") ?: 0.0,
                        carbsG = o.number("carbs") ?: 0.0,
                        fatG = o.number("fat") ?: 0.0,
                        saturatedFatG = o.number("saturated_fat"),
                        sugarG = o.number("sugar"),
                        fiberG = o.number("fiber"),
                        saltG = o.number("salt"),
                        micros = Micro.decode(o.string("micros")),
                    ),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.number(key: String) = this[key]?.jsonPrimitive?.doubleOrNull
}
