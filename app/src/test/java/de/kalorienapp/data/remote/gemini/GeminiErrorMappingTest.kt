package de.kalorienapp.data.remote.gemini

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiErrorMappingTest {

    @Test
    fun `eigenes Tageslimit des Workers ist kein Gemini-Kontingent`() {
        assertEquals(GeminiError.DAILY_LIMIT, GeminiClient.errorFor(429, """{"error":"daily_limit","used":40,"limit":40}"""))
        assertEquals(GeminiError.DAILY_LIMIT, GeminiClient.errorFor(429, """{"error":"weekly_done"}"""))
        assertEquals(GeminiError.QUOTA, GeminiClient.errorFor(429, """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}"""))
    }

    @Test
    fun `abgelehnte Anmeldung und Ueberlastung`() {
        assertEquals(GeminiError.REJECTED, GeminiClient.errorFor(401))
        assertEquals(GeminiError.OVERLOADED, GeminiClient.errorFor(503))
    }
}
