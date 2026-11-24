package de.kalorienapp.capture

import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.Nutrients
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.LocalDate

/** Was im Hintergrund erkannt werden soll. */
enum class QueuedKind { PHOTO, LABEL, TEXT }

/** WAITING = KI wird noch gefragt; DONE = Vorschlag liegt bereit; FAILED = aufgegeben (z. B. kein Essen erkannt). */
enum class QueuedStatus { WAITING, DONE, FAILED }

/**
 * Ein Foto bzw. eine Beschreibung, die die KI gerade nicht erkennen konnte (Google überlastet, kein Netz) und die
 * im Hintergrund weiter versucht wird. Das Ergebnis ist immer nur ein Vorschlag – gespeichert wird erst nach Prüfen.
 */
data class QueuedJob(
    val id: String,
    val kind: QueuedKind,
    val date: LocalDate,
    val mealType: MealType,
    val createdAt: Long,
    val text: String? = null,
    val hint: String? = null,
    val status: QueuedStatus = QueuedStatus.WAITING,
    val drafts: List<FoodDraft> = emptyList(),
    /** Name eines [de.kalorienapp.data.remote.gemini.GeminiError] bei [QueuedStatus.FAILED]. */
    val error: String? = null,
)

/** Warteschlange als JSON-Datei (rein, ohne Android – getestet). Defekte Einträge werden übersprungen. */
object AiQueueCodec {
    fun encode(jobs: List<QueuedJob>): String = buildJsonArray {
        jobs.forEach { j ->
            add(
                buildJsonObject {
                    put("id", j.id)
                    put("kind", j.kind.name)
                    put("date", j.date.toString())
                    put("meal", j.mealType.name)
                    put("createdAt", j.createdAt)
                    j.text?.let { put("text", it) }
                    j.hint?.let { put("hint", it) }
                    put("status", j.status.name)
                    put("drafts", JsonArray(j.drafts.map(::draft)))
                    j.error?.let { put("error", it) }
                },
            )
        }
    }.toString()

    fun decode(text: String?): List<QueuedJob> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching {
                val o = element.jsonObject
                QueuedJob(
                    id = o.string("id")!!,
                    kind = QueuedKind.valueOf(o.string("kind")!!),
                    date = LocalDate.parse(o.string("date")!!),
                    mealType = MealType.valueOf(o.string("meal")!!),
                    createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                    text = o.string("text"),
                    hint = o.string("hint"),
                    status = QueuedStatus.entries.find { it.name == o.string("status") } ?: QueuedStatus.WAITING,
                    drafts = o["drafts"]?.jsonArray?.mapNotNull { runCatching { draft(it.jsonObject) }.getOrNull() }.orEmpty(),
                    error = o.string("error"),
                )
            }.getOrNull()
        }
    }

    private fun draft(d: FoodDraft): JsonObject = buildJsonObject {
        put("name", d.name)
        d.amount?.let { put("amount", it) }
        put("unit", d.unit.name)
        put("source", d.source.name)
        put("kcal", d.per100.kcal)
        put("protein", d.per100.proteinG)
        put("carbs", d.per100.carbsG)
        put("fat", d.per100.fatG)
        d.per100.saturatedFatG?.let { put("saturated_fat", it) }
        d.per100.sugarG?.let { put("sugar", it) }
        d.per100.fiberG?.let { put("fiber", it) }
        d.per100.saltG?.let { put("salt", it) }
        Micro.encode(d.per100.micros)?.let { put("micros", it) }
        if (d.components.isNotEmpty()) put("components", JsonArray(d.components.map(::draft)))
    }

    private fun draft(o: JsonObject): FoodDraft = FoodDraft(
        name = o.string("name").orEmpty(),
        amount = o.number("amount"),
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
        source = EntrySource.entries.find { it.name == o.string("source") } ?: EntrySource.AI_PHOTO,
        components = o["components"]?.jsonArray?.map { draft(it.jsonObject) }.orEmpty(),
    )

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull
}
