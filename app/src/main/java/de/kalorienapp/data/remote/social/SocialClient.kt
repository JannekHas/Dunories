package de.kalorienapp.data.remote.social

import de.kalorienapp.data.remote.Http

/**
 * Liest öffentliche Beitragsseiten wie ein Browser beim Öffnen des Links:
 * Beschreibung, Untertitel-Text und Vorschaubilder. Videos werden nie heruntergeladen.
 */
class SocialClient {

    /** @return null, wenn die Seite keine verwertbaren Daten enthält. Netzwerkfehler werden geworfen. */
    suspend fun fetch(url: String): SocialPost? {
        val (finalUrl, html) = Http.getPage(url)
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
}
