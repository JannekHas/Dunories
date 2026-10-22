package de.kalorienapp.data.remote

import de.kalorienapp.data.remote.social.SocialPageParser
import de.kalorienapp.data.remote.social.SocialPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialPageParserTest {

    @Test
    fun `url aus geteiltem text`() {
        assertEquals(
            "https://vm.tiktok.com/ZNdAbC123/",
            SocialPageParser.extractUrl("Schau dir dieses Video an! https://vm.tiktok.com/ZNdAbC123/ #tiktok"),
        )
        assertNull(SocialPageParser.extractUrl("nur Text"))
    }

    @Test
    fun `plattform erkennen`() {
        assertEquals(SocialPlatform.TIKTOK, SocialPageParser.platformOf("https://vm.tiktok.com/abc"))
        assertEquals(SocialPlatform.TIKTOK, SocialPageParser.platformOf("https://www.tiktok.com/@x/video/1"))
        assertEquals(SocialPlatform.INSTAGRAM, SocialPageParser.platformOf("https://www.instagram.com/reel/xyz/"))
        assertEquals(SocialPlatform.OTHER, SocialPageParser.platformOf("https://example.com/tiktok.com"))
    }

    @Test
    fun `tiktok videoseite - beschreibung cover und untertitel`() {
        val html = """
            <html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">
            {"__DEFAULT_SCOPE__":{"webapp.reflow.video.detail":{"itemInfo":{"itemStruct":{
              "desc":"Protein Pasta 🍝 Zutaten: 250g Nudeln",
              "video":{"cover":"https://c/large.jpg","originCover":"https://c/small.jpg",
                "subtitleInfos":[
                  {"LanguageCodeName":"fra-FR","Url":"https://s/fr.vtt","Format":"webvtt","Source":"MT"},
                  {"LanguageCodeName":"eng-US","Url":"https://s/en.vtt","Format":"webvtt","Source":"ASR"}
                ]}
            }}}}}
            </script></html>
        """.trimIndent()
        val post = SocialPageParser.parseTikTok(html, "https://www.tiktok.com/@a/video/1")!!
        assertEquals(SocialPlatform.TIKTOK, post.platform)
        assertEquals("Protein Pasta 🍝 Zutaten: 250g Nudeln", post.caption)
        assertEquals("https://c/large.jpg", post.coverUrl) // großes Cover statt 540-px-originCover
        assertEquals("https://s/en.vtt", post.subtitleUrl) // gesprochener Originalton bevorzugt
    }

    @Test
    fun `tiktok fotobeitrag liefert bilder`() {
        val html = """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">
            {"x":{"itemStruct":{"desc":"Rezept","imagePost":{"images":[
              {"imageURL":{"urlList":["https://i/1.jpg","https://i/1b.jpg"]}},
              {"imageURL":{"urlList":["https://i/2.jpg"]}}]}}}}</script>"""
        val post = SocialPageParser.parseTikTok(html, "u")!!
        assertEquals(listOf("https://i/1.jpg", "https://i/2.jpg"), post.imageUrls)
    }

    @Test
    fun `tiktok seite ohne daten`() {
        assertNull(SocialPageParser.parseTikTok("<html>ratelimit</html>", "u"))
    }

    @Test
    fun `instagram open graph mit entities`() {
        val html = """
            <meta property="og:title" content="Koch &amp; Co on Instagram" />
            <meta property="og:description" content="1.234 likes - koch: &quot;Feta-Nudeln&quot; Zutaten: 200 g Feta &#x1F9C0;" />
            <meta property="og:image" content="https://cdn/ig.jpg?a=1&amp;b=2" />
        """.trimIndent()
        val post = SocialPageParser.parseOpenGraph(html, "https://www.instagram.com/p/x/", SocialPlatform.INSTAGRAM)!!
        assertTrue(post.caption!!.contains("\"Feta-Nudeln\" Zutaten: 200 g Feta 🧀"))
        assertTrue(post.caption!!.startsWith("Koch & Co"))
        assertEquals("https://cdn/ig.jpg?a=1&b=2", post.coverUrl)
    }

    @Test
    fun `webvtt zu text`() {
        val vtt = "WEBVTT\n\n1\n00:00:00.000 --> 00:00:01.940\nDas leckerste Rezept,\n\n00:00:02.000 --> 00:00:03.000\nheute mit Feta."
        assertEquals("Das leckerste Rezept, heute mit Feta.", SocialPageParser.vttToText(vtt))
    }
}
