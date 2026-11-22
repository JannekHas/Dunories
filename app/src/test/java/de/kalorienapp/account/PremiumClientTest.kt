package de.kalorienapp.account

import de.kalorienapp.data.remote.gemini.GeminiClient
import de.kalorienapp.data.remote.gemini.GeminiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumClientTest {

    @Test
    fun `Status vom Worker lesen`() {
        val trial = AiStatus.parse("""{"tier":"premium","source":"trial","until":1800000000000,"lifetime":false,"anonymous":false,"usedToday":3,"dailyLimit":40,"freeLeft":null,"trialDays":7,"id":"abcd1234"}""")!!
        assertTrue(trial.premium)
        assertEquals(PremiumSource.TRIAL, trial.source)
        assertEquals(1_800_000_000_000L, trial.until)
        assertEquals(3, trial.usedToday)
        val guest = AiStatus.parse("""{"tier":"guest","source":null,"until":null,"lifetime":false,"usedToday":0,"dailyLimit":40,"freeLeft":2,"trialDays":7,"id":"x"}""")!!
        assertEquals(AiTier.GUEST, guest.tier)
        assertEquals(2, guest.freeLeft)
        val lifetime = AiStatus.parse("""{"tier":"premium","source":"code","until":null,"lifetime":true}""")!!
        assertEquals(PremiumSource.LIFETIME, lifetime.source)
        assertNull(AiStatus.parse("kaputt"))
    }

    @Test
    fun `Fehler beim Einloesen`() {
        assertEquals(RedeemResult.INVALID, PremiumClient.redeemError("""{"error":"invalid_code"}"""))
        assertEquals(RedeemResult.TOO_MANY, PremiumClient.redeemError("""{"error":"too_many"}"""))
        assertEquals(RedeemResult.NETWORK, PremiumClient.redeemError(""))
    }

    @Test
    fun `Paywall-Fehler der KI`() {
        assertEquals(GeminiError.NEEDS_ACCOUNT, GeminiClient.errorFor(429, """{"error":"needs_account"}"""))
        assertEquals(GeminiError.NEEDS_PREMIUM, GeminiClient.errorFor(429, """{"error":"needs_premium"}"""))
    }
}
