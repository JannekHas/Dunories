package de.kalorienapp.data.remote.social

import de.kalorienapp.data.remote.asObject
import de.kalorienapp.data.remote.lenientJson
import de.kalorienapp.data.remote.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Reine Parser für öffentliche Beitragsseiten (ohne Netzwerk, unit-getestet). */
object SocialPageParser {

    private val URL_REGEX = Regex("""https?://[^\s"'<>]+""")

    /** Erste URL aus geteiltem Text („Schau dir das an! https://vm.tiktok.com/…“). */
    fun extractUrl(text: String): String? = URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', '!')

    fun platformOf(url: String): SocialPlatform {
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> SocialPlatform.TIKTOK
            host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am" -> SocialPlatform.INSTAGRAM
            else -> SocialPlatform.OTHER
        }
    }

    /**
     * TikTok-Videoseite: Daten stehen als JSON in `__UNIVERSAL_DATA_FOR_REHYDRATION__`
     * (Pfad je nach Seitenvariante unterschiedlich → `itemStruct` wird gesucht).
     */
    fun parseTikTok(html: String, url: String): SocialPost? {
        val json = scriptContent(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__") ?: return null
        val root = runCatching { lenientJson.parseToJsonElement(json) }.getOrNull() ?: return null
        val item = findObject(root, "itemStruct") ?: return null
        val video = item["video"].asObject()
        val subtitles = (video?.get("subtitleInfos") as? JsonArray)?.mapNotNull { it.asObject() }.orEmpty()
        val subtitle = subtitles.sortedBy { sub ->
            val lang = sub.string("LanguageCodeName").orEmpty().lowercase()
            when {
                lang.startsWith("de") -> 0
                sub.string("Source") == "ASR" -> 1
                lang.startsWith("en") -> 2
                else -> 3
            }
        }.firstOrNull { it.string("Format").equals("webvtt", ignoreCase = true) || it.string("Format") == null }
        val images = (item["imagePost"].asObject()?.get("images") as? JsonArray)
            ?.mapNotNull { image ->
                val urls = image.asObject()?.get("imageURL").asObject()?.get("urlList") as? JsonArray
                (urls?.firstOrNull() as? JsonPrimitive)?.content
            }.orEmpty()
        return SocialPost(
            platform = SocialPlatform.TIKTOK,
            url = url,
            caption = item.string("desc"),
            coverUrl = video?.string("originCover") ?: video?.string("cover")
                ?: item["imagePost"].asObject()?.get("cover").asObject()?.get("imageURL").asObject()
                    ?.let { (it["urlList"] as? JsonArray)?.firstOrNull() as? JsonPrimitive }?.content,
            imageUrls = images,
            subtitleUrl = subtitle?.string("Url"),
        )
    }

    /** Instagram (und sonstige Seiten): Open-Graph-Angaben `og:description`/`og:title`/`og:image`. */
    fun parseOpenGraph(html: String, url: String, platform: SocialPlatform): SocialPost? {
        val description = metaContent(html, "og:description") ?: metaContent(html, "description")
        val title = metaContent(html, "og:title")
        val caption = listOfNotNull(title, description).distinct().joinToString("\n").takeIf { it.isNotBlank() }
        val image = metaContent(html, "og:image")
        if (caption == null && image == null) return null
        return SocialPost(platform = platform, url = url, caption = caption, coverUrl = image)
    }

    /** WebVTT → reiner Text ohne Zeitstempel. */
    fun vttToText(vtt: String): String = vtt.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && it != "WEBVTT" && "-->" !in it && !it.all(Char::isDigit) }
        .joinToString(" ")

    private fun scriptContent(html: String, id: String): String? =
        Regex("""<script[^>]*id="${Regex.escape(id)}"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)

    private fun metaContent(html: String, property: String): String? {
        val tags = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).map { it.value }
        val tag = tags.firstOrNull {
            Regex("""(property|name)\s*=\s*"${Regex.escape(property)}"""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        } ?: return null
        val content = Regex("""content\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
        return content?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() }
    }

    internal fun decodeEntities(text: String): String =
        Regex("""&(#x?[0-9a-fA-F]+|amp|quot|apos|lt|gt|#39);""").replace(text) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"
                "quot" -> "\""
                "apos", "#39" -> "'"
                "lt" -> "<"
                "gt" -> ">"
                else -> {
                    val code = if (e.startsWith("#x") || e.startsWith("#X")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                    code?.let { String(Character.toChars(it)) } ?: m.value
                }
            }
        }

    private fun findObject(element: JsonElement, key: String): JsonObject? = when (element) {
        is JsonObject -> element[key].asObject() ?: element.values.firstNotNullOfOrNull { findObject(it, key) }
        is JsonArray -> element.firstNotNullOfOrNull { findObject(it, key) }
        else -> null
    }
}
