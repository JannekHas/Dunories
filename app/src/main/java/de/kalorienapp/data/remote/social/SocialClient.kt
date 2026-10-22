package de.kalorienapp.data.remote.social

import de.kalorienapp.data.remote.Http
import de.kalorienapp.data.remote.HttpStatusException
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Liest öffentliche Beitragsseiten wie ein Browser beim Öffnen des Links:
 * Beschreibung, Untertitel-Text und Vorschaubilder. Videos werden nie heruntergeladen.
 */
class SocialClient {

    /**
     * TikTok liefert die Seite nicht immer gleich aus (Rate-Limit, Variante ohne Daten) →
     * mehrere Versuche mit Pause und wechselndem Browser-Kennzeichen.
     * @return null, wenn auch nach allen Versuchen keine verwertbaren Daten kamen.
     */
    suspend fun fetch(url: String): SocialPost? {
        var lastError: IOException? = null
        USER_AGENTS.forEachIndexed { attempt, userAgent ->
            if (attempt > 0) delay(RETRY_DELAY_MS * attempt)
            try {
                val post = fetchOnce(url, userAgent)
                if (post != null && (post.hasText || post.imageUrls.isNotEmpty())) return post
            } catch (e: IOException) {
                lastError = e
            }
        }
        lastError?.let { if (it !is HttpStatusException) throw it }
        return null
    }

    private suspend fun fetchOnce(url: String, userAgent: String): SocialPost? {
        val (finalUrl, html) = Http.getPage(url, userAgent)
        return when (val platform = SocialPageParser.platformOf(finalUrl)) {
            SocialPlatform.TIKTOK -> SocialPageParser.parseTikTok(html, finalUrl)?.let { post ->
                val transcript = post.subtitleUrl?.let { subtitleUrl ->
                    runCatching { SocialPageParser.vttToText(Http.get(subtitleUrl)) }.getOrNull()
                }
                post.copy(transcript = transcript?.takeIf { it.isNotBlank() })
            } ?: SocialPageParser.parseOpenGraph(html, finalUrl, platform)
            else -> SocialPageParser.parseOpenGraph(html, finalUrl, platform)
        }
    }

    suspend fun downloadImage(url: String): ByteArray = Http.getBytes(url)

    private companion object {
        const val RETRY_DELAY_MS = 1_500L
        val USER_AGENTS = listOf(
            Http.BROWSER_USER_AGENT,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
            Http.BROWSER_USER_AGENT,
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
        )
    }
}
