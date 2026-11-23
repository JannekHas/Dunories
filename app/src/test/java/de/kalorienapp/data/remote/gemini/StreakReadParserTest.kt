package de.kalorienapp.data.remote.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreakReadParserTest {

    @Test
    fun `liest beide zahlen`() {
        assertEquals(ReadStreak(0, 81), StreakReadParser.parse("""{"current_streak": 0, "longest_streak": 81}"""))
    }

    @Test
    fun `fehlende oder unsinnige werte werden null`() {
        assertEquals(ReadStreak(null, 81), StreakReadParser.parse("""{"current_streak": null, "longest_streak": 81}"""))
        assertEquals(ReadStreak(null, null), StreakReadParser.parse("""{"current_streak": -3, "longest_streak": 2.5}"""))
        assertNull(StreakReadParser.parse("kein json"))
    }

    @Test
    fun `laengste ist nie kleiner als aktuelle`() {
        assertEquals(ReadStreak(12, 12), StreakReadParser.parse("""```json
{"current_streak": 12, "longest_streak": 5}
```"""))
    }
}
