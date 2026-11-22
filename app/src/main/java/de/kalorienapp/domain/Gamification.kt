package de.kalorienapp.domain

/**
 * Level & Erfolge (Social-Reiter, Nov. 2026). Alles auf dem Handy berechnet.
 *
 * Grundsatz (fest): Erfahrungspunkte gibt es fürs **Dranbleiben** (Eintragen, Serien, Erfolge) –
 * **nie** dafür, weniger zu essen oder schneller abzunehmen. Die Serie zählt deshalb Tage **mit Einträgen**,
 * nicht Tage „im Budget“.
 */
object Gamification {

    /** XP je Tag mit mindestens einem Eintrag. */
    const val XP_PER_LOGGED_DAY = 10

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
    )

    enum class Metric { ENTRIES, LOGGED_DAYS, LONGEST_STREAK, WEIGHTS, GOALS_REACHED, REPORTS, FEEDBACK }

    /** Reihenfolge = Anzeige. [key] ist dauerhaft (Sicherung, „schon gefeiert“) – nie umbenennen. */
    enum class Achievement(val key: String, val metric: Metric, val target: Int, val xp: Int) {
        FIRST_ENTRY("first_entry", Metric.ENTRIES, 1, 20),
        ENTRIES_100("entries_100", Metric.ENTRIES, 100, 100),
        DAYS_7("days_7", Metric.LOGGED_DAYS, 7, 50),
        DAYS_30("days_30", Metric.LOGGED_DAYS, 30, 150),
        DAYS_100("days_100", Metric.LOGGED_DAYS, 100, 400),
        STREAK_7("streak_7", Metric.LONGEST_STREAK, 7, 75),
        STREAK_30("streak_30", Metric.LONGEST_STREAK, 30, 250),
        FIRST_WEIGHT("first_weight", Metric.WEIGHTS, 1, 20),
        WEIGHTS_30("weights_30", Metric.WEIGHTS, 30, 120),
        GOAL_REACHED("goal_reached", Metric.GOALS_REACHED, 1, 300),
        FIRST_REPORT("first_report", Metric.REPORTS, 1, 40),
        FIRST_FEEDBACK("first_feedback", Metric.FEEDBACK, 1, 40),
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
    }

    data class Progress(val achievement: Achievement, val current: Int) {
        val unlocked get() = current >= achievement.target
        val shown get() = current.coerceAtMost(achievement.target)
    }

    fun progress(stats: Stats): List<Progress> = Achievement.entries.map { Progress(it, value(stats, it.metric)) }

    fun xp(stats: Stats): Int =
        stats.loggedDays * XP_PER_LOGGED_DAY + progress(stats).filter { it.unlocked }.sumOf { it.achievement.xp }

    /** Level ab 1; Level n → n+1 kostet 100·n XP (100, 200, 300 …), damit es anfangs schnell und später langsamer geht. */
    data class Level(val level: Int, val xpInLevel: Int, val xpForNext: Int, val totalXp: Int) {
        val fraction: Float get() = if (xpForNext == 0) 0f else xpInLevel.toFloat() / xpForNext
    }

    fun level(totalXp: Int): Level {
        var level = 1
        var rest = totalXp.coerceAtLeast(0)
        while (rest >= 100 * level) {
            rest -= 100 * level
            level++
        }
        return Level(level, rest, 100 * level, totalXp.coerceAtLeast(0))
    }

    /** Längste und aktuelle Serie von Tagen mit Einträgen (aktuell: bis heute oder gestern, heute läuft noch). */
    fun streaks(loggedEpochDays: Collection<Long>, todayEpochDay: Long): Pair<Int, Int> {
        val days = loggedEpochDays.toSortedSet()
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
        return longest to current
    }

    /** Neu freigeschaltet = erreicht, aber noch nicht gefeiert. */
    fun newlyUnlocked(stats: Stats, celebrated: Set<String>): List<Achievement> =
        progress(stats).filter { it.unlocked && it.achievement.key !in celebrated }.map { it.achievement }
}
