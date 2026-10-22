package de.kalorienapp.data.remote.social

enum class SocialPlatform { TIKTOK, INSTAGRAM, OTHER }

/** Öffentlich lesbare Daten eines geteilten Beitrags – nur Text und Vorschaubilder, nie das Video. */
data class SocialPost(
    val platform: SocialPlatform,
    val url: String,
    /** Beschreibung/Caption des Beitrags. */
    val caption: String?,
    /** Gesprochener Text (automatische Untertitel), falls vorhanden. */
    val transcript: String? = null,
    /** Vorschaubild (Video-Cover bzw. og:image). */
    val coverUrl: String? = null,
    /** Bilder eines Foto-Beitrags (TikTok-Fotos). */
    val imageUrls: List<String> = emptyList(),
    /** Untertitel-Adresse (WebVTT) – wird separat geladen. */
    val subtitleUrl: String? = null,
) {
    val hasText: Boolean get() = !caption.isNullOrBlank() || !transcript.isNullOrBlank()
}
