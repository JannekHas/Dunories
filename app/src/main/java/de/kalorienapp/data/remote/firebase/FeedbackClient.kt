package de.kalorienapp.data.remote.firebase

import de.kalorienapp.data.remote.Http
import de.kalorienapp.domain.Feedback
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Feedback an den KI-Worker schicken ([aiBaseUrl], Postfach im Admin-Dashboard, Tester-Punkte) und die
 * Fernschalter aus Firestore `config/app` lesen.
 * [projectId]/[apiKey] kommen aus `google-services.json` (BuildConfig); fehlen sie, ist alles aus.
 * [identity] = Paketname + Zertifikat, weil der Firebase-Schlüssel auf die App beschränkt ist.
 */
class FeedbackClient(
    private val projectId: String,
    private val apiKey: String,
    private val identity: Map<String, String>,
    private val aiBaseUrl: String,
    /** Firebase-Token (Konto oder still angelegtes Gast-Konto). */
    private val token: suspend () -> String?,
) {
    val available: Boolean get() = projectId.isNotBlank() && apiKey.isNotBlank() && aiBaseUrl.isNotBlank()

    suspend fun send(feedback: Feedback) {
        check(available) { "Nicht eingerichtet" }
        val t = token() ?: error("Kein Token")
        Http.postJson("$aiBaseUrl/v1/feedback", body(feedback), mapOf("Authorization" to "Bearer $t"), timeoutMs = 30_000)
    }

    internal companion object {
        fun body(feedback: Feedback): String = buildJsonObject {
            put("type", feedback.type.key)
            put("text", feedback.text.trim().take(Feedback.MAX_TEXT))
            put("name", feedback.name.trim().take(Feedback.MAX_NAME))
            put("credits", feedback.credits && feedback.name.isNotBlank())
            feedback.screenshotJpegBase64?.let { put("screenshot", it) }
            put("appVersion", feedback.appVersion)
            put("versionCode", feedback.versionCode)
            put("device", feedback.device)
            put("android", feedback.androidSdk)
        }.toString()
    }

    /** Feedback-Symbol auf „Heute“ zeigen? null = unbekannt (offline, nicht eingerichtet) → letzter Stand bleibt. */
    suspend fun feedbackOnToday(): Boolean? {
        if (!available) return null
        return runCatching {
            FirestoreRest.booleanField(Http.get("${FirestoreRest.documentsUrl(projectId)}/config/app?key=$apiKey", identity), "feedbackOnToday")
        }.getOrNull()
    }
}
