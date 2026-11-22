package de.kalorienapp.data.remote.firebase

import de.kalorienapp.domain.Feedback
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

    /** Feedback als Firestore-Dokument (`{"fields": {...}}`). Text/Name werden auf die erlaubte Länge gekürzt. */
    fun feedbackDocument(feedback: Feedback): String = buildJsonObject {
        putJsonObject("fields") {
            string("type", feedback.type.key)
            string("text", feedback.text.trim().take(Feedback.MAX_TEXT))
            string("name", feedback.name.trim().take(Feedback.MAX_NAME))
            feedback.screenshotJpegBase64?.let { string("screenshot", it) }
            string("appVersion", feedback.appVersion)
            integer("versionCode", feedback.versionCode.toLong())
            string("device", feedback.device)
            integer("android", feedback.androidSdk.toLong())
            putJsonObject("createdAt") { put("timestampValue", feedback.createdAt.toString()) }
        }
    }.toString()

    /** Schalter aus `config/app`; null = Feld fehlt oder Antwort unlesbar. */
    fun booleanField(documentJson: String, field: String): Boolean? = runCatching {
        val fields = json.parseToJsonElement(documentJson).jsonObject["fields"]?.jsonObject ?: return null
        fields[field]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.booleanOrNull
    }.getOrNull()

    private fun kotlinx.serialization.json.JsonObjectBuilder.string(name: String, value: String) =
        putJsonObject(name) { put("stringValue", value) }

    // Firestore erwartet Ganzzahlen als String.
    private fun kotlinx.serialization.json.JsonObjectBuilder.integer(name: String, value: Long) =
        putJsonObject(name) { put("integerValue", value.toString()) }
}
