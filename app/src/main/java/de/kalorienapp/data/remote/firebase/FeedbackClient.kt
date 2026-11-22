package de.kalorienapp.data.remote.firebase

import de.kalorienapp.data.remote.Http
import de.kalorienapp.domain.Feedback

/**
 * Feedback an Firestore schicken und die Fernschalter aus `config/app` lesen.
 * [projectId]/[apiKey] kommen aus `google-services.json` (BuildConfig); fehlen sie, ist alles aus.
 * [identity] = Paketname + Zertifikat, weil der Firebase-Schlüssel auf die App beschränkt ist.
 */
class FeedbackClient(
    private val projectId: String,
    private val apiKey: String,
    private val identity: Map<String, String>,
) {
    val available: Boolean get() = projectId.isNotBlank() && apiKey.isNotBlank()

    suspend fun send(feedback: Feedback) {
        check(available) { "Firebase nicht eingerichtet" }
        Http.postJson(
            "${FirestoreRest.documentsUrl(projectId)}/feedback?key=$apiKey",
            FirestoreRest.feedbackDocument(feedback),
            identity,
            timeoutMs = 30_000,
        )
    }

    /** Feedback-Symbol auf „Heute“ zeigen? null = unbekannt (offline, nicht eingerichtet) → letzter Stand bleibt. */
    suspend fun feedbackOnToday(): Boolean? {
        if (!available) return null
        return runCatching {
            FirestoreRest.booleanField(Http.get("${FirestoreRest.documentsUrl(projectId)}/config/app?key=$apiKey", identity), "feedbackOnToday")
        }.getOrNull()
    }
}
