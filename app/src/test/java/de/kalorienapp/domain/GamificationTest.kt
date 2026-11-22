package de.kalorienapp.domain

import de.kalorienapp.domain.Gamification.Achievement
import de.kalorienapp.domain.Gamification.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationTest {

    @Test
    fun `serie zaehlt tage mit eintraegen, heute darf noch fehlen`() {
        val today = 100L
        assertEquals(3 to 3, Gamification.streaks(listOf(97L, 98L, 99L), today))
        assertEquals(4 to 4, Gamification.streaks(listOf(97L, 98L, 99L, 100L), today))
        assertEquals(3 to 0, Gamification.streaks(listOf(90L, 91L, 92L, 98L), today))
        assertEquals(0 to 0, Gamification.streaks(emptyList(), today))
    }

    @Test
    fun `neu freigeschaltet nur was noch nicht gefeiert ist`() {
        val stats = Gamification.Stats(entries = 1, feedbackSent = 1)
        assertEquals(listOf(Achievement.FIRST_ENTRY, Achievement.FIRST_FEEDBACK), Gamification.newlyUnlocked(stats, emptySet()))
        assertEquals(listOf(Achievement.FIRST_FEEDBACK), Gamification.newlyUnlocked(stats, setOf("first_entry")))
    }

    @Test
    fun `geheime erfolge kommen ueber eigene kennzahlen`() {
        val stats = Gamification.Stats(nightEntries = 1, barcodeEntries = 50)
        val unlocked = Gamification.newlyUnlocked(stats, emptySet())
        assertTrue(Achievement.NIGHT_SNACK in unlocked)
        assertTrue(Achievement.BARCODE_50 in unlocked)
        assertTrue(unlocked.all { it.secret })
    }

    @Test
    fun `sortierung freigeschaltete seltenste oben, geheime offene unten`() {
        val stats = Gamification.Stats(entries = 1, goalsReached = 1)
        val order = Gamification.sorted(Gamification.progress(stats)).map { it.achievement }
        assertEquals(Achievement.GOAL_REACHED, order[0])
        assertEquals(Achievement.FIRST_ENTRY, order[1])
        assertTrue(order.takeLast(5).all { it.secret })
    }

    @Test
    fun `schluessel eindeutig, galaxy nur geheim`() {
        val keys = Achievement.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(Achievement.entries.all { it.target > 0 })
        assertTrue(Achievement.entries.filter { it.rarity == Rarity.GALAXY }.all { it.secret })
    }
}
