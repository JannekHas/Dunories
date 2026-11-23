package de.kalorienapp.domain

/**
 * Erfolge & Abzeichen (Social-Reiter, Nov. 2026). Alles auf dem Handy berechnet.
 *
 * Entscheidungen des Nutzers: **keine Level/XP** (zu viel), Erfolge **gemischt** – sichtbare mit Fortschritt und
 * **geheime** („???“ bis freigeschaltet) –, Abzeichen mit **Seltenheit** (grau → galaxy).
 * Grundsatz (fest): Erfolge gibt es fürs **Dranbleiben** – **nie** dafür, weniger zu essen oder schneller abzunehmen.
 * Serien zählen deshalb Tage **mit Einträgen**, nicht Tage „im Budget“.
 */
object Gamification {

    /** Seltenheit = Farbe des Abzeichens (Reihenfolge aufsteigend). */
    enum class Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, GALAXY }

    /** Kennzahlen, aus denen Erfolge berechnet werden. */
    data class Stats(
        val loggedDays: Int = 0,
        val entries: Int = 0,
        val longestStreak: Int = 0,
        val currentStreak: Int = 0,
        val weights: Int = 0,
        val goalsReached: Int = 0,
        val reports: Int = 0,
        val feedbackSent: Int = 0,
        val barcodeEntries: Int = 0,
        val photoEntries: Int = 0,
        /** Einträge, die zwischen 0 und 4 Uhr erfasst wurden. */
        val nightEntries: Int = 0,
    )

    enum class Metric { ENTRIES, LOGGED_DAYS, LONGEST_STREAK, WEIGHTS, GOALS_REACHED, REPORTS, FEEDBACK, BARCODE, PHOTO, NIGHT }

    /** Reihenfolge = Anzeige. [key] ist dauerhaft (Sicherung, „schon gefeiert“) – nie umbenennen. */
    enum class Achievement(val key: String, val metric: Metric, val target: Int, val rarity: Rarity, val secret: Boolean = false) {
        FIRST_ENTRY("first_entry", Metric.ENTRIES, 1, Rarity.COMMON),
        FIRST_WEIGHT("first_weight", Metric.WEIGHTS, 1, Rarity.COMMON),
        STREAK_3("streak_3", Metric.LONGEST_STREAK, 3, Rarity.COMMON),
        DAYS_7("days_7", Metric.LOGGED_DAYS, 7, Rarity.UNCOMMON),
        FIRST_REPORT("first_report", Metric.REPORTS, 1, Rarity.UNCOMMON),
        FIRST_FEEDBACK("first_feedback", Metric.FEEDBACK, 1, Rarity.UNCOMMON),
        STREAK_7("streak_7", Metric.LONGEST_STREAK, 7, Rarity.RARE),
        DAYS_30("days_30", Metric.LOGGED_DAYS, 30, Rarity.RARE),
        ENTRIES_100("entries_100", Metric.ENTRIES, 100, Rarity.RARE),
        WEIGHTS_30("weights_30", Metric.WEIGHTS, 30, Rarity.RARE),
        STREAK_30("streak_30", Metric.LONGEST_STREAK, 30, Rarity.EPIC),
        DAYS_100("days_100", Metric.LOGGED_DAYS, 100, Rarity.EPIC),
        STREAK_50("streak_50", Metric.LONGEST_STREAK, 50, Rarity.LEGENDARY),
        GOAL_REACHED("goal_reached", Metric.GOALS_REACHED, 1, Rarity.LEGENDARY),

        // Geheim: erscheinen als „???“, bis man sie schafft.
        NIGHT_SNACK("night_snack", Metric.NIGHT, 1, Rarity.RARE, secret = true),
        BARCODE_50("barcode_50", Metric.BARCODE, 50, Rarity.EPIC, secret = true),
        PHOTO_50("photo_50", Metric.PHOTO, 50, Rarity.EPIC, secret = true),
        STREAK_100("streak_100", Metric.LONGEST_STREAK, 100, Rarity.GALAXY, secret = true),
        DAYS_365("days_365", Metric.LOGGED_DAYS, 365, Rarity.GALAXY, secret = true),
        ;

        companion object {
            fun byKey(key: String): Achievement? = entries.firstOrNull { it.key == key }
        }
    }

    fun value(stats: Stats, metric: Metric): Int = when (metric) {
        Metric.ENTRIES -> stats.entries
        Metric.LOGGED_DAYS -> stats.loggedDays
        Metric.LONGEST_STREAK -> stats.longestStreak
        Metric.WEIGHTS -> stats.weights
        Metric.GOALS_REACHED -> stats.goalsReached
        Metric.REPORTS -> stats.reports
        Metric.FEEDBACK -> stats.feedbackSent
        Metric.BARCODE -> stats.barcodeEntries
        Metric.PHOTO -> stats.photoEntries
        Metric.NIGHT -> stats.nightEntries
    }

    data class Progress(val achievement: Achievement, val current: Int) {
        val unlocked get() = current >= achievement.target
        val shown get() = current.coerceAtMost(achievement.target)
    }

    fun progress(stats: Stats): List<Progress> = Achievement.entries.map { Progress(it, value(stats, it.metric)) }

    /**
     * Anzeige-Reihenfolge der Erfolge: freigeschaltete zuerst (seltenste oben), dann offene sichtbare,
     * geheime offene ganz unten.
     */
    fun sorted(progress: List<Progress>): List<Progress> = progress.sortedWith(
        compareBy<Progress> { if (it.unlocked) 0 else if (it.achievement.secret) 2 else 1 }
            .thenByDescending { if (it.unlocked) it.achievement.rarity.ordinal else 0 }
            .thenBy { it.achievement.ordinal },
    )

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

    /** Neu freigeschaltet = erreicht, aber noch nicht gefeiert. */
    fun newlyUnlocked(stats: Stats, celebrated: Set<String>): List<Achievement> =
        progress(stats).filter { it.unlocked && it.achievement.key !in celebrated }.map { it.achievement }
}
