package de.kalorienapp.data.remote.firebase

import de.kalorienapp.domain.Feedback
import de.kalorienapp.domain.FeedbackType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FirestoreRestTest {
    private fun feedback(text: String = " Knopf hängt ", name: String = "Tim", shot: String? = null) = Feedback(
        type = FeedbackType.BUG, text = text, name = name, screenshotJpegBase64 = shot,
        appVersion = "0.6.1", versionCode = 8, device = "Google Pixel 8", androidSdk = 36,
        createdAt = Instant.parse("2026-11-22T10:15:30Z"),
    )

    private fun fields(doc: String) = Json.parseToJsonElement(doc).jsonObject["fields"]!!.jsonObject

    private fun value(doc: String, field: String, type: String) =
        fields(doc)[field]!!.jsonObject[type]!!.jsonPrimitive.content

    @Test
    fun `feedback wird als firestore dokument geschrieben`() {
        val doc = FirestoreRest.feedbackDocument(feedback())
        assertEquals("bug", value(doc, "type", "stringValue"))
        assertEquals("Knopf hängt", value(doc, "text", "stringValue"))
        assertEquals("8", value(doc, "versionCode", "integerValue"))
        assertEquals("36", value(doc, "android", "integerValue"))
        assertEquals("2026-11-22T10:15:30Z", value(doc, "createdAt", "timestampValue"))
        assertFalse("screenshot" in fields(doc))
    }

    @Test
    fun `zu langer text und name werden gekuerzt, screenshot kommt mit`() {
        val doc = FirestoreRest.feedbackDocument(feedback(text = "x".repeat(6_000), name = "n".repeat(100), shot = "QUJD"))
        assertEquals(Feedback.MAX_TEXT, value(doc, "text", "stringValue").length)
        assertEquals(Feedback.MAX_NAME, value(doc, "name", "stringValue").length)
        assertEquals("QUJD", value(doc, "screenshot", "stringValue"))
    }

    @Test
    fun `fernschalter lesen`() {
        assertEquals(false, FirestoreRest.booleanField("""{"fields":{"feedbackOnToday":{"booleanValue":false}}}""", "feedbackOnToday"))
        assertNull(FirestoreRest.booleanField("""{"fields":{}}""", "feedbackOnToday"))
        assertNull(FirestoreRest.booleanField("kaputt", "feedbackOnToday"))
    }

    @Test
    fun `senden braucht text oder screenshot`() {
        assertFalse(Feedback.canSend("  ", hasScreenshot = false))
        assertTrue(Feedback.canSend("", hasScreenshot = true))
        assertTrue(Feedback.canSend("Idee", hasScreenshot = false))
    }
}
