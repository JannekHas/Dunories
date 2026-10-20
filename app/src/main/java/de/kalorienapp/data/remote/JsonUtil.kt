package de.kalorienapp.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Tolerantes JSON für externe APIs: unbekannte Felder ignorieren, Zahlen auch als Strings akzeptieren. */
internal val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** Zahl aus einem JSON-Feld lesen – akzeptiert Zahl oder String ("12,5" / "12.5"); sonst null. */
internal fun JsonObject.number(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
