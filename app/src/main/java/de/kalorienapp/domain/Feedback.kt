package de.kalorienapp.domain

import java.time.Instant

/** Art des Feedbacks (Einstellungen → „Feedback geben“). */
enum class FeedbackType(val key: String) { BUG("bug"), IDEA("idea"), PRAISE("praise") }

/**
 * Feedback aus der App (Phase 1 Backend, Nov. 2026): geht ohne Konto an Firestore (`feedback`).
 * Enthält bewusst **keine** Ernährungs- oder Körperdaten – nur, was der Nutzer schreibt/anhängt, und Gerätedaten.
 */
data class Feedback(
    val type: FeedbackType,
    val text: String,
    /** Freiwillig, damit der Entwickler weiß, von wem es kommt. */
    val name: String = "",
    /** Verkleinerter Screenshot als JPEG in Base64 (null = keiner). */
    val screenshotJpegBase64: String? = null,
    val appVersion: String,
    val versionCode: Int,
    val device: String,
    val androidSdk: Int,
    val createdAt: Instant,
) {
    companion object {
        const val MAX_TEXT = 5_000
        const val MAX_NAME = 60

        /** Firestore-Dokumente dürfen höchstens 1 MiB groß sein → Bild in Base64 deutlich darunter. */
        const val MAX_SCREENSHOT_BASE64 = 800_000

        /** Senden möglich, sobald Text oder Screenshot da ist. */
        fun canSend(text: String, hasScreenshot: Boolean) = text.isNotBlank() || hasScreenshot
    }
}
