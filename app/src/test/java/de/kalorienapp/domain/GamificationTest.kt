package de.kalorienapp.domain

import de.kalorienapp.domain.Gamification.Achievement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationTest {

    @Test
    fun `level kostet 100 mal level xp`() {
        assertEquals(1, Gamification.level(0).level)
        assertEquals(1, Gamification.level(99).level)
        assertEquals(2, Gamification.level(100).level)
        assertEquals(3, Gamification.level(300).level)
        val l = Gamification.level(350)
        assertEquals(3, l.level)
        assertEquals(50, l.xpInLevel)
        assertEquals(300, l.xpForNext)
    }

    @Test
    fun `serie zaehlt tage mit eintraegen, heute darf noch fehlen`() {
        val today = 100L
        assertEquals(3 to 3, Gamification.streaks(listOf(97L, 98L, 99L), today))
        assertEquals(4 to 4, Gamification.streaks(listOf(97L, 98L, 99L, 100L), today))
        assertEquals(3 to 0, Gamification.streaks(listOf(90L, 91L, 92L, 98L), today))
        assertEquals(0 to 0, Gamification.streaks(emptyList(), today))
    }

    @Test
    fun `xp aus tagen und erfolgen`() {
        val stats = Gamification.Stats(loggedDays = 7, entries = 20, longestStreak = 7)
        // 7 Tage × 10 + erster Eintrag 20 + 7 Tage 50 + 7er-Serie 75
        assertEquals(70 + 20 + 50 + 75, Gamification.xp(stats))
    }

    @Test
    fun `neu freigeschaltet nur was noch nicht gefeiert ist`() {
        val stats = Gamification.Stats(entries = 1, feedbackSent = 1)
        assertEquals(listOf(Achievement.FIRST_ENTRY, Achievement.FIRST_FEEDBACK), Gamification.newlyUnlocked(stats, emptySet()))
        assertEquals(listOf(Achievement.FIRST_FEEDBACK), Gamification.newlyUnlocked(stats, setOf("first_entry")))
    }

    @Test
    fun `schluessel sind eindeutig`() {
        val keys = Achievement.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(Achievement.entries.all { it.target > 0 && it.xp > 0 })
    }
}
