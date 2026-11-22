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

    private fun field(body: String, name: String) = Json.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.content

    @Test
    fun `feedback geht als json an den worker`() {
        val body = FeedbackClient.body(feedback())
        assertEquals("bug", field(body, "type"))
        assertEquals("Knopf hängt", field(body, "text"))
        assertEquals("8", field(body, "versionCode"))
        assertEquals("36", field(body, "android"))
        assertNull(field(body, "screenshot"))
    }

    @Test
    fun `zu langer text und name werden gekuerzt, screenshot kommt mit, danke-liste nur mit name`() {
        val body = FeedbackClient.body(feedback(text = "x".repeat(6_000), name = "n".repeat(100), shot = "QUJD").copy(credits = true))
        assertEquals(Feedback.MAX_TEXT, field(body, "text")!!.length)
        assertEquals(Feedback.MAX_NAME, field(body, "name")!!.length)
        assertEquals("QUJD", field(body, "screenshot"))
        assertEquals("true", field(body, "credits"))
        assertEquals("false", field(FeedbackClient.body(feedback(name = " ").copy(credits = true)), "credits"))
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
