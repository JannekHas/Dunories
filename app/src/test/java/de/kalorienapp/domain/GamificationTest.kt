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
    fun `uebernommene serie laeuft weiter und hebt den bestwert`() {
        val today = 100L
        // In YAZIO am Tag 95 abgelesen: 4 Tage laufend (91–94), längste 81.
        val yazio = Gamification.ImportedStreak(current = 4, longest = 81, epochDay = 95L)
        // In Dunories ab Tag 95 lückenlos weiter → 4 + 6 (95–100).
        assertEquals(81 to 10, Gamification.streaks((95L..100L).toList(), today, yazio))
        // Lücke an Tag 96 → laufende Serie nur 97–99, Bestwert bleibt 81.
        assertEquals(81 to 3, Gamification.streaks(listOf(95L, 97L, 98L, 99L), today, yazio))
        // Eigene Serie länger als der übernommene Bestwert.
        val small = Gamification.ImportedStreak(current = 0, longest = 2, epochDay = 95L)
        assertEquals(5 to 5, Gamification.streaks((95L..99L).toList(), today, small))
        // Direkt nach dem Übernehmen (noch nichts eingetragen) zählt die laufende Serie schon.
        assertEquals(81 to 4, Gamification.streaks(emptyList(), 95L, yazio))
    }

    @Test
    fun `uebernommene serie wird gespeichert und gelesen`() {
        val s = Gamification.ImportedStreak(3, 81, 20_000L)
        assertEquals(s, Gamification.ImportedStreak.decode(s.encode()))
        assertEquals(null, Gamification.ImportedStreak.decode("kaputt"))
        assertEquals(null, Gamification.ImportedStreak.decode(null))
    }

    @Test
    fun `yazio bestwert schaltet serien-erfolge bis dahin frei`() {
        val stats = Gamification.Stats(longestStreak = 81)
        val unlocked = Gamification.newlyUnlocked(stats, emptySet())
        assertTrue(Achievement.STREAK_50 in unlocked)
        assertTrue(Achievement.STREAK_100 !in unlocked)
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
