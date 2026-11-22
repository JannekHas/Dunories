package de.kalorienapp.data.remote.firebase

import de.kalorienapp.domain.CloudBackup
import de.kalorienapp.domain.Feedback
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Firestore über die REST-Schnittstelle statt über das Firebase-SDK (klein, kein Hintergrund-Verkehr,
 * gleiche Art wie Gemini/Open Food Facts). Reine Umwandlung, unit-getestet.
 */
object FirestoreRest {
    private val json = Json { ignoreUnknownKeys = true }

    fun documentsUrl(projectId: String) =
        "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    /** Schalter aus `config/app`; null = Feld fehlt oder Antwort unlesbar. */
    fun booleanField(documentJson: String, field: String): Boolean? = runCatching {
        val fields = json.parseToJsonElement(documentJson).jsonObject["fields"]?.jsonObject ?: return null
        fields[field]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.booleanOrNull
    }.getOrNull()

    // ------------------------------------------------------------ Cloud-Sicherung (Phase 2)

    /** Eine Änderung für `documents:commit` – mehrere werden gemeinsam (atomar) geschrieben. */
    sealed interface Write {
        data class Update(val path: String, val fields: JsonObject) : Write
        data class Delete(val path: String) : Write
    }

    fun commitUrl(projectId: String) = "${documentsUrl(projectId)}:commit"

    private fun docName(projectId: String, path: String) = "projects/$projectId/databases/(default)/documents/$path"

    fun commitBody(projectId: String, writes: List<Write>): String = buildJsonObject {
        put("writes", buildJsonArray {
            writes.forEach { w ->
                add(
                    when (w) {
                        is Write.Update -> buildJsonObject {
                            putJsonObject("update") {
                                put("name", docName(projectId, w.path))
                                put("fields", w.fields)
                            }
                        }
                        is Write.Delete -> buildJsonObject { put("delete", docName(projectId, w.path)) }
                    },
                )
            }
        })
    }.toString()

    fun backupPath(uid: String, slot: String) = "users/$uid/backups/$slot"
    fun chunkPath(uid: String, slot: String, index: Int) = "${backupPath(uid, slot)}/chunks/$index"
    fun userPath(uid: String) = "users/$uid"

    fun metaFields(meta: CloudBackup.Meta): JsonObject = buildJsonObject {
        integer("createdAt", meta.createdAt)
        integer("chunks", meta.chunks.toLong())
        putJsonObject("complete") { put("booleanValue", meta.complete) }
        string("appVersion", meta.appVersion)
        integer("foodEntries", meta.foodEntries)
        integer("weights", meta.weights)
        integer("measurements", meta.measurements)
        integer("reports", meta.reports)
        integer("recipes", meta.recipes)
        meta.firstDay?.let { integer("firstDay", it) }
        meta.lastDay?.let { integer("lastDay", it) }
    }

    fun parseMeta(slot: String, documentJson: String): CloudBackup.Meta? = runCatching {
        val f = json.parseToJsonElement(documentJson).jsonObject["fields"]!!.jsonObject
        fun long(name: String) = f[name]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull()
        CloudBackup.Meta(
            slot = slot,
            createdAt = long("createdAt") ?: return null,
            chunks = long("chunks")?.toInt() ?: return null,
            complete = f["complete"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.booleanOrNull ?: false,
            appVersion = f["appVersion"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content.orEmpty(),
            foodEntries = long("foodEntries") ?: 0,
            weights = long("weights") ?: 0,
            measurements = long("measurements") ?: 0,
            reports = long("reports") ?: 0,
            recipes = long("recipes") ?: 0,
            firstDay = long("firstDay"),
            lastDay = long("lastDay"),
        )
    }.getOrNull()

    fun chunkFields(data: String): JsonObject = buildJsonObject { string("data", data) }

    fun parseChunk(documentJson: String): String? = runCatching {
        json.parseToJsonElement(documentJson).jsonObject["fields"]!!.jsonObject["data"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content
    }.getOrNull()

    /** Einwilligung (DSGVO Art. 9) mit Zeitpunkt und Textversion. */
    fun userFields(consentAtIso: String, consentVersion: Int): JsonObject = buildJsonObject {
        putJsonObject("consentAt") { put("timestampValue", consentAtIso) }
        integer("consentVersion", consentVersion.toLong())
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.string(name: String, value: String) =
        putJsonObject(name) { put("stringValue", value) }

    // Firestore erwartet Ganzzahlen als String.
    private fun kotlinx.serialization.json.JsonObjectBuilder.integer(name: String, value: Long) =
        putJsonObject(name) { put("integerValue", value.toString()) }
}
