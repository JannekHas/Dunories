package de.kalorienapp.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Erfolge & Abzeichen (Social-Reiter). Alles auf dem Handy berechnet.
 *
 * **Stufen-Erfolge (Entscheidung des Nutzers 23.11.2026):** statt vieler Einzel-Erfolge eine Reihe pro Thema, die
 * aufsteigt – Stufe 1–5 = Gewöhnlich (grau) → Legendär (gold), das Abzeichen wechselt mit. Dazu **geheime** Erfolge
 * (eine Stufe, Galaxy, als „???“ sichtbar) und **Events** (Weihnachten …), die **komplett unsichtbar** sind, bis man
 * sie zufällig schafft; ihre Stufe = Anzahl Jahre.
 * Grundsatz (fest): Erfolge gibt es fürs **Dranbleiben** – **nie** dafür, weniger zu essen oder schneller abzunehmen
 * (deshalb kein Fasten-Erfolg). Serien zählen Tage **mit Einträgen**, nicht Tage „im Budget“.
 */
object Gamification {

    /** Seltenheit = Rahmen/Farbe des Abzeichens. EVENT = eigener festlicher Rahmen. */
    enum class Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, GALAXY, EVENT }

    enum class Kind { TIERED, SECRET, EVENT }

    /** Wann ein Erfolg überhaupt auftaucht (sonst würde er Unerreichbares versprechen). */
    enum class Requirement { NONE, WEIGHT, WATER, AI, ACCOUNT }

    /** Kennzahlen, aus denen Erfolge berechnet werden. */
    data class Stats(
        val loggedDays: Int = 0,
        val entries: Int = 0,
        val longestStreak: Int = 0,
        val currentStreak: Int = 0,
        val proteinDays: Int = 0,
        val photoEntries: Int = 0,
        val barcodeEntries: Int = 0,
        val weights: Int = 0,
        val waterDays: Int = 0,
        val reports: Int = 0,
        /** Von dir (Admin) mit Punkten bewertetes Feedback – nicht einfach gesendetes. */
        val ratedFeedback: Int = 0,
        val friends: Int = 0,
        val goalsReached: Int = 0,
        /** Einträge, die zwischen 0 und 4 Uhr erfasst wurden. */
        val nightEntries: Int = 0,
        /** Einträge, die zwischen 4 und 6 Uhr erfasst wurden. */
        val earlyEntries: Int = 0,
        val comeback: Int = 0,
        val weekendHero: Int = 0,
        /** Events: Anzahl Jahre, in denen es geklappt hat. */
        val christmasYears: Int = 0,
        val newYearsEveYears: Int = 0,
        val resolutionYears: Int = 0,
        val easterYears: Int = 0,
        val halloweenYears: Int = 0,
    )

    /** Was gerade sichtbar sein darf (Bereiche an/aus, KI nutzbar, Konto). */
    data class Visibility(
        val weight: Boolean = true,
        val water: Boolean = false,
        val ai: Boolean = true,
        val account: Boolean = false,
    )

    /**
     * Reihenfolge = Anzeige. [key] ist dauerhaft (Sicherung, „schon gefeiert“, Freunde) – nie umbenennen,
     * und Schlüssel enden nie auf eine Ziffer (Freunde bekommen „schlüssel_stufe“).
     */
    enum class Achievement(
        val key: String,
        val tiers: List<Int>,
        val kind: Kind = Kind.TIERED,
        val requirement: Requirement = Requirement.NONE,
        /** Feste Seltenheit statt grau → gold (z. B. „Ziel erreicht“ startet bei gold). */
        val fixedRarity: Rarity? = null,
        val value: (Stats) -> Int,
    ) {
        ENTRIES("eintragen", listOf(1, 50, 250, 1000, 5000), value = { it.entries }),
        DAYS("dabei", listOf(7, 30, 100, 365, 1000), value = { it.loggedDays }),
        STREAK("serie", listOf(3, 7, 30, 100, 365), value = { it.longestStreak }),
        PROTEIN("proteinziel", listOf(1, 7, 30, 100, 365), value = { it.proteinDays }),
        PHOTO("foto_profi", listOf(1, 10, 50, 200, 500), requirement = Requirement.AI, value = { it.photoEntries }),
        BARCODE("barcode_jaeger", listOf(1, 10, 50, 200, 500), value = { it.barcodeEntries }),
        WEIGHT("waage", listOf(1, 10, 30, 100, 365), requirement = Requirement.WEIGHT, value = { it.weights }),
        WATER("wasser", listOf(1, 7, 30, 100, 365), requirement = Requirement.WATER, value = { it.waterDays }),
        REPORTS("rueckblick", listOf(1, 4, 12, 26, 52), value = { it.reports }),
        FEEDBACK("mitgestalter", listOf(1, 3, 10, 25, 50), value = { it.ratedFeedback }),
        FRIENDS("freunde", listOf(1, 3, 10), requirement = Requirement.ACCOUNT, value = { it.friends }),
        GOAL("ziel_erreicht", listOf(1, 2, 3), fixedRarity = Rarity.LEGENDARY, value = { it.goalsReached }),

        // Geheim: als „???“ sichtbar, bis man sie schafft.
        NIGHT("mitternachtssnack", listOf(1), Kind.SECRET, value = { it.nightEntries }),
        EARLY("fruehaufsteher", listOf(1), Kind.SECRET, value = { it.earlyEntries }),
        COMEBACK("comeback", listOf(1), Kind.SECRET, value = { it.comeback }),
        WEEKEND("wochenend_held", listOf(1), Kind.SECRET, value = { it.weekendHero }),

        // Events: unsichtbar, bis geschafft; Stufe = Jahre.
        CHRISTMAS("weihnachten", YEARS, Kind.EVENT, value = { it.christmasYears }),
        NEW_YEARS_EVE("silvester", YEARS, Kind.EVENT, value = { it.newYearsEveYears }),
        RESOLUTION("neujahrsvorsatz", YEARS, Kind.EVENT, value = { it.resolutionYears }),
        EASTER("ostern", YEARS, Kind.EVENT, value = { it.easterYears }),
        HALLOWEEN("halloween", YEARS, Kind.EVENT, value = { it.halloweenYears }),
        ;

        companion object {
            fun byKey(key: String): Achievement? = entries.firstOrNull { it.key == key }
        }
    }

    /** Stand eines Erfolgs: erreichte Stufe (0 = noch nicht), Wert, nächstes Ziel. */
    data class Progress(val achievement: Achievement, val value: Int) {
        val level: Int = achievement.tiers.count { value >= it }
        val unlocked get() = level > 0
        val maxed get() = level == achievement.tiers.size

        /** Ziel der nächsten Stufe (bei höchster Stufe das letzte). */
        val target get() = achievement.tiers.getOrElse(level) { achievement.tiers.last() }
        val shown get() = value.coerceAtMost(target)

        /** Seltenheit der erreichten Stufe (für Rahmen/Farbe); noch nicht erreicht = die der ersten Stufe. */
        val rarity get() = rarityOf(achievement, level.coerceAtLeast(1))
    }

    fun rarityOf(a: Achievement, level: Int): Rarity = when {
        a.kind == Kind.SECRET -> Rarity.GALAXY
        a.kind == Kind.EVENT -> Rarity.EVENT
        a.fixedRarity != null -> a.fixedRarity
        else -> Rarity.entries[(level - 1).coerceIn(0, Rarity.LEGENDARY.ordinal)]
    }

    fun progress(stats: Stats): List<Progress> = Achievement.entries.map { Progress(it, it.value(stats)) }

    /**
     * Was angezeigt wird: Events nur geschafft (man soll nie wissen, dass es sie gibt), Bereichs-Erfolge nur bei
     * eingeschaltetem Bereich. Schon Erreichtes bleibt sichtbar, auch wenn der Bereich später aus ist (außer Events).
     */
    fun visible(p: Progress, v: Visibility): Boolean = when {
        p.achievement.kind == Kind.EVENT -> p.unlocked
        p.unlocked -> true
        else -> when (p.achievement.requirement) {
            Requirement.NONE -> true
            Requirement.WEIGHT -> v.weight
            Requirement.WATER -> v.water
            Requirement.AI -> v.ai
            Requirement.ACCOUNT -> v.account
        }
    }

    /**
     * Anzeige-Reihenfolge: freigeschaltete zuerst (höchste Seltenheit oben), dann offene sichtbare,
     * geheime offene ganz unten.
     */
    fun sorted(progress: List<Progress>): List<Progress> = progress.sortedWith(
        compareBy<Progress> { if (it.unlocked) 0 else if (it.achievement.kind == Kind.SECRET) 2 else 1 }
            .thenByDescending { if (it.unlocked) it.rarity.ordinal else 0 }
            .thenByDescending { it.level }
            .thenBy { it.achievement.ordinal },
    )

    /** Schlüssel für „schon gefeiert“: jede Stufe wird einmal gefeiert. */
    fun celebrationKey(a: Achievement, level: Int) = "${a.key}@$level"

    /** Neu erreichte Stufen (je Erfolg nur die höchste), die noch nicht gefeiert wurden. */
    fun newlyUnlocked(progress: List<Progress>, celebrated: Set<String>): List<Progress> =
        progress.filter { it.unlocked && celebrationKey(it.achievement, it.level) !in celebrated }

    /** Alle bisher erreichten Stufen als „gefeiert“ (beim ersten Start mit Stufen-Erfolgen: keine Kartenflut). */
    fun allReachedKeys(progress: List<Progress>): List<String> =
        progress.flatMap { p -> (1..p.level).map { celebrationKey(p.achievement, it) } }

    // ---------------- Freunde & alte Schlüssel ----------------

    /** Für Freunde: „serie_3“ = Erfolg + Stufe (Server erlaubt nur a–z, 0–9, _). */
    fun shareKey(a: Achievement, level: Int) = "${a.key}_$level"

    /**
     * Schlüssel von Freunden (neu „serie_3“, alte App-Versionen „streak_30“) → Erfolg + Stufe.
     * Unbekanntes (z. B. „tester“) → null.
     */
    fun parseShared(key: String): Pair<Achievement, Int>? {
        LEGACY[key]?.let { return it }
        val cut = key.lastIndexOf('_')
        if (cut <= 0) return Achievement.byKey(key)?.let { it to 1 }
        val level = key.substring(cut + 1).toIntOrNull() ?: return Achievement.byKey(key)?.let { it to 1 }
        val a = Achievement.byKey(key.substring(0, cut)) ?: return null
        return a to level.coerceIn(1, a.tiers.size)
    }

    /** Erfolge vor den Stufen (bis 0.7.0-Entwicklung) → neue Reihe + Stufe; für Lieblings-Abzeichen und alte Freunde. */
    private val LEGACY: Map<String, Pair<Achievement, Int>> = mapOf(
        "first_entry" to (Achievement.ENTRIES to 1),
        "entries_100" to (Achievement.ENTRIES to 2),
        "days_7" to (Achievement.DAYS to 1),
        "days_30" to (Achievement.DAYS to 2),
        "days_100" to (Achievement.DAYS to 3),
        "days_365" to (Achievement.DAYS to 4),
        "streak_3" to (Achievement.STREAK to 1),
        "streak_7" to (Achievement.STREAK to 2),
        "streak_30" to (Achievement.STREAK to 3),
        "streak_50" to (Achievement.STREAK to 3),
        "streak_100" to (Achievement.STREAK to 4),
        "first_weight" to (Achievement.WEIGHT to 1),
        "weights_30" to (Achievement.WEIGHT to 3),
        "goal_reached" to (Achievement.GOAL to 1),
        "first_report" to (Achievement.REPORTS to 1),
        "first_feedback" to (Achievement.FEEDBACK to 1),
        "night_snack" to (Achievement.NIGHT to 1),
        "barcode_50" to (Achievement.BARCODE to 3),
        "photo_50" to (Achievement.PHOTO to 3),
    )

    /** Lieblings-Abzeichen aus alter Version („streak_7“) → neuer Schlüssel („serie“); Unbekanntes bleibt. */
    fun canonicalBadge(key: String?): String? = key?.let { LEGACY[it]?.first?.key ?: it }

    // ---------------- Berechnungen ----------------

    /**
     * Aus einer anderen App (YAZIO) übernommene Serie, abgelesen am Tag [epochDay].
     * Die laufende Serie zählt als [current] Tage direkt **vor** diesem Tag – wer in Dunories ab dann weiter einträgt,
     * führt sie fort. Die längste Serie gilt als Bestwert (Entscheidung des Nutzers: zählt auch für Erfolge).
     */
    data class ImportedStreak(val current: Int, val longest: Int, val epochDay: Long) {
        /** Tage, die durch die übernommene laufende Serie als „mit Einträgen“ gelten. */
        fun days(): List<Long> = (1..current.coerceIn(0, MAX_IMPORT)).map { epochDay - it }

        fun encode(): String = "$current;$longest;$epochDay"

        companion object {
            /** Obergrenze gegen Tippfehler/Unsinn beim Ablesen (rund 27 Jahre). */
            const val MAX_IMPORT = 10_000

            fun decode(raw: String?): ImportedStreak? {
                val p = raw?.split(';')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 3 } ?: return null
                return ImportedStreak(p[0].toInt(), p[1].toInt(), p[2]).takeIf { it.current >= 0 && it.longest >= 0 }
            }
        }
    }

    /**
     * Längste und aktuelle Serie von Tagen mit Einträgen (aktuell: bis heute oder gestern, heute läuft noch).
     * Eine übernommene Serie ([imported]) zählt mit: ihre laufenden Tage verlängern die eigene Serie, ihr Bestwert
     * hebt die längste Serie an.
     */
    fun streaks(loggedEpochDays: Collection<Long>, todayEpochDay: Long, imported: ImportedStreak? = null): Pair<Int, Int> {
        val days = (loggedEpochDays + imported?.days().orEmpty()).toSortedSet()
        var longest = 0
        var run = 0
        var previous: Long? = null
        for (day in days) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }
        var current = 0
        var day = if (todayEpochDay in days) todayEpochDay else todayEpochDay - 1
        while (day in days) {
            current++
            day--
        }
        val importedBest = imported?.longest?.coerceAtMost(ImportedStreak.MAX_IMPORT) ?: 0
        return maxOf(longest, importedBest) to current
    }

    /** Comeback: nach mindestens 7 Tagen ohne Eintrag wieder eingetragen. */
    fun comeback(loggedEpochDays: Collection<Long>): Boolean =
        loggedEpochDays.sorted().zipWithNext().any { (a, b) -> b - a > COMEBACK_PAUSE_DAYS }

    /** Wochenend-Held: in einem Monat an allen Samstagen und Sonntagen eingetragen (Monat schon vorbei). */
    fun weekendHero(logged: Set<LocalDate>, today: LocalDate): Boolean =
        logged.map { YearMonth.from(it) }.toSet().any { month ->
            val weekend = (1..month.lengthOfMonth()).map { month.atDay(it) }
                .filter { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
            weekend.isNotEmpty() && !weekend.last().isAfter(today) && weekend.all { it in logged }
        }

    /** Event-Jahre aus den Tagen mit Einträgen. */
    data class EventYears(val christmas: Int, val newYearsEve: Int, val resolution: Int, val easter: Int, val halloween: Int)

    fun eventYears(logged: Set<LocalDate>): EventYears {
        val years = logged.map { it.year }.toSet()
        val all = years + years.map { it - 1 } // 1. Januar zählt zum Silvester des Vorjahrs
        return EventYears(
            christmas = years.count { y -> (24..26).any { LocalDate.of(y, 12, it) in logged } },
            newYearsEve = all.count { y -> LocalDate.of(y, 12, 31) in logged || LocalDate.of(y + 1, 1, 1) in logged },
            resolution = years.count { y -> (1..7).all { LocalDate.of(y, 1, it) in logged } },
            easter = years.count { y -> easterSunday(y).let { it in logged || it.plusDays(1) in logged } },
            halloween = years.count { y -> LocalDate.of(y, 10, 31) in logged },
        )
    }

    /** Ostersonntag (Gregorianischer Kalender, anonymer Algorithmus nach Gauß/Meeus). */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    private const val COMEBACK_PAUSE_DAYS = 7
}

/** Event-Stufen = Jahre, in denen man dabei war. */
private val YEARS = listOf(1, 2, 3, 4, 5)
