package de.kalorienapp.domain

import de.kalorienapp.domain.Gamification.Achievement
import de.kalorienapp.domain.Gamification.Kind
import de.kalorienapp.domain.Gamification.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GamificationTest {

    private fun progressOf(a: Achievement, stats: Gamification.Stats) = Gamification.progress(stats).first { it.achievement == a }

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
        val yazio = Gamification.ImportedStreak(current = 4, longest = 81, epochDay = 95L)
        assertEquals(81 to 10, Gamification.streaks((95L..100L).toList(), today, yazio))
        assertEquals(81 to 3, Gamification.streaks(listOf(95L, 97L, 98L, 99L), today, yazio))
        val small = Gamification.ImportedStreak(current = 0, longest = 2, epochDay = 95L)
        assertEquals(5 to 5, Gamification.streaks((95L..99L).toList(), today, small))
        assertEquals(81 to 4, Gamification.streaks(emptyList(), 95L, yazio))
    }

    @Test
    fun `uebernommene serie wird gespeichert und gelesen`() {
        val s = Gamification.ImportedStreak(3, 81, 20_000L)
        assertEquals(s, Gamification.ImportedStreak.decode(s.encode()))
        assertNull(Gamification.ImportedStreak.decode("kaputt"))
        assertNull(Gamification.ImportedStreak.decode(null))
    }

    @Test
    fun `stufen steigen mit dem wert, seltenheit grau bis gold`() {
        val p = progressOf(Achievement.STREAK, Gamification.Stats(longestStreak = 81))
        assertEquals(3, p.level) // 3, 7, 30 geschafft – 100 nicht (Entscheidung des Nutzers)
        assertEquals(100, p.target)
        assertEquals(Rarity.RARE, p.rarity)
        val max = progressOf(Achievement.ENTRIES, Gamification.Stats(entries = 9000))
        assertTrue(max.maxed)
        assertEquals(Rarity.LEGENDARY, max.rarity)
        assertEquals(5000, max.target)
        val none = progressOf(Achievement.ENTRIES, Gamification.Stats())
        assertFalse(none.unlocked)
        assertEquals(1, none.target)
    }

    @Test
    fun `ziel erreicht startet bei gold, geheim ist galaxy, events eigener rahmen`() {
        assertEquals(Rarity.LEGENDARY, progressOf(Achievement.GOAL, Gamification.Stats(goalsReached = 1)).rarity)
        assertEquals(Rarity.GALAXY, progressOf(Achievement.NIGHT, Gamification.Stats(nightEntries = 1)).rarity)
        assertEquals(Rarity.EVENT, progressOf(Achievement.CHRISTMAS, Gamification.Stats(christmasYears = 2)).rarity)
    }

    @Test
    fun `jede stufe wird einmal gefeiert`() {
        val progress = Gamification.progress(Gamification.Stats(entries = 60))
        val newly = Gamification.newlyUnlocked(progress, emptySet())
        assertEquals(listOf(Achievement.ENTRIES), newly.map { it.achievement })
        assertEquals(2, newly.single().level)
        val celebrated = Gamification.allReachedKeys(progress).toSet()
        assertEquals(setOf("eintragen@1", "eintragen@2"), celebrated)
        assertTrue(Gamification.newlyUnlocked(progress, celebrated).isEmpty())
        // Nächste Stufe → wieder feiern
        val later = Gamification.progress(Gamification.Stats(entries = 250))
        assertEquals(3, Gamification.newlyUnlocked(later, celebrated).single().level)
    }

    @Test
    fun `sichtbarkeit nach bereichen, events nur wenn geschafft`() {
        val stats = Gamification.Stats()
        val off = Gamification.Visibility(weight = false, water = false, ai = false, account = false)
        val visible = Gamification.progress(stats).filter { Gamification.visible(it, off) }.map { it.achievement }
        assertFalse(Achievement.WEIGHT in visible)
        assertFalse(Achievement.WATER in visible)
        assertFalse(Achievement.PHOTO in visible)
        assertFalse(Achievement.FRIENDS in visible)
        assertTrue(visible.none { it.kind == Kind.EVENT })
        assertTrue(Achievement.NIGHT in visible) // geheim: als ??? sichtbar
        // Schon erreicht bleibt sichtbar, auch wenn der Bereich aus ist
        val reached = Gamification.progress(Gamification.Stats(weights = 3, christmasYears = 1))
        val shown = reached.filter { Gamification.visible(it, off) }.map { it.achievement }
        assertTrue(Achievement.WEIGHT in shown)
        assertTrue(Achievement.CHRISTMAS in shown)
    }

    @Test
    fun `sortierung freigeschaltete seltenste oben, geheime offene unten`() {
        val stats = Gamification.Stats(entries = 1, goalsReached = 1)
        val order = Gamification.sorted(Gamification.progress(stats)).map { it.achievement }
        assertEquals(Achievement.GOAL, order[0])
        assertEquals(Achievement.ENTRIES, order[1])
        assertTrue(order.takeLast(4).all { it.kind == Kind.SECRET })
    }

    @Test
    fun `freunde-schluessel neu und alt`() {
        assertEquals(Achievement.STREAK to 3, Gamification.parseShared("serie_3"))
        assertEquals(Achievement.PHOTO to 2, Gamification.parseShared("foto_profi_2"))
        assertEquals(Achievement.STREAK to 2, Gamification.parseShared("streak_7"))
        assertEquals(Achievement.NIGHT to 1, Gamification.parseShared("night_snack"))
        assertEquals(Achievement.ENTRIES to 5, Gamification.parseShared("eintragen_9"))
        assertNull(Gamification.parseShared("tester"))
        assertEquals("serie", Gamification.canonicalBadge("streak_30"))
        assertEquals("tester", Gamification.canonicalBadge("tester"))
    }

    @Test
    fun `comeback nach mindestens 7 tagen pause`() {
        assertFalse(Gamification.comeback(listOf(1L, 2L, 8L))) // 5 Tage Pause
        assertTrue(Gamification.comeback(listOf(1L, 9L))) // 7 Tage ohne Eintrag dazwischen
    }

    @Test
    fun `wochenend-held braucht alle wochenenden eines vergangenen monats`() {
        val month = java.time.YearMonth.of(2026, 11)
        val weekend = (1..30).map { month.atDay(it) }.filter { it.dayOfWeek.value >= 6 }.toSet()
        assertTrue(Gamification.weekendHero(weekend, LocalDate.of(2026, 12, 2)))
        assertFalse(Gamification.weekendHero(weekend - weekend.first(), LocalDate.of(2026, 12, 2)))
        // Monat noch nicht vorbei (letztes Wochenende kommt noch)
        assertFalse(Gamification.weekendHero(weekend.filter { it.dayOfMonth < 25 }.toSet(), LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun `events zaehlen jahre`() {
        val logged = setOf(
            LocalDate.of(2025, 12, 25), LocalDate.of(2026, 12, 24), // Weihnachten 2 Jahre
            LocalDate.of(2026, 1, 1), // Silvester 2025
            LocalDate.of(2026, 4, 6), // Ostermontag 2026 (Ostern 5. April)
            LocalDate.of(2026, 10, 31),
        ) + (1..7).map { LocalDate.of(2027, 1, it) }
        val e = Gamification.eventYears(logged)
        assertEquals(2, e.christmas)
        assertEquals(2, e.newYearsEve) // 2025 (über 1.1.2026) und 2026 (über 1.1.2027)
        assertEquals(1, e.resolution)
        assertEquals(1, e.easter)
        assertEquals(1, e.halloween)
    }

    @Test
    fun `ostersonntag stimmt`() {
        assertEquals(LocalDate.of(2026, 4, 5), Gamification.easterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), Gamification.easterSunday(2027))
        assertEquals(LocalDate.of(2025, 4, 20), Gamification.easterSunday(2025))
    }

    @Test
    fun `schluessel eindeutig und ohne ziffer am ende`() {
        val keys = Achievement.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.none { it.last().isDigit() })
        assertTrue(Achievement.entries.all { a -> a.tiers.zipWithNext().all { (x, y) -> y > x } && a.tiers.first() > 0 })
    }
}
