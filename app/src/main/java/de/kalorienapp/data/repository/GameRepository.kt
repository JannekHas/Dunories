package de.kalorienapp.data.repository

import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.dao.WeeklyReportDao
import de.kalorienapp.domain.Gamification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Alles für den Social-Reiter: Erfolge und Abzeichen. Wird live aus den vorhandenen Daten berechnet. */
data class GameState(
    val stats: Gamification.Stats,
    /** In Anzeige-Reihenfolge ([Gamification.sorted]). */
    val progress: List<Gamification.Progress>,
    /** Verdiente Abzeichen (Schlüssel), seltenste zuerst: freigeschaltete Erfolge + „tester“. */
    val badges: List<String>,
    val newlyUnlocked: List<Gamification.Achievement>,
)

class GameRepository(
    private val foodDao: FoodEntryDao,
    private val weights: WeightRepository,
    private val goals: GoalRepository,
    private val reports: WeeklyReportDao,
    private val settings: AppSettings,
) {
    private val stats: Flow<Gamification.Stats> = combine(
        foodDao.observeLoggedDays(),
        foodDao.observeCount(),
        weights.entries,
        goals.reachedGoals,
        combine(
            reports.observeAll(),
            settings.feedbackSentCount,
            foodDao.observeSourceCount("BARCODE"),
            foodDao.observeSourceCount("AI_PHOTO"),
            foodDao.observeCreatedTimes(),
        ) { r, f, barcode, photo, times -> Extra(r.size, f, barcode, photo, times.count(::isNight)) },
    ) { days, entries, weightList, reached, extra ->
        val (longest, current) = Gamification.streaks(days, LocalDate.now().toEpochDay())
        Gamification.Stats(
            loggedDays = days.size,
            entries = entries,
            longestStreak = longest,
            currentStreak = current,
            weights = weightList.size,
            goalsReached = reached.size,
            reports = extra.reports,
            feedbackSent = extra.feedback,
            barcodeEntries = extra.barcode,
            photoEntries = extra.photo,
            nightEntries = extra.night,
        )
    }

    private data class Extra(val reports: Int, val feedback: Int, val barcode: Int, val photo: Int, val night: Int)

    /** Erfasst zwischen 0 und 4 Uhr (Handy-Zeitzone). */
    private fun isNight(createdAt: Long): Boolean =
        java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()).hour < 4

    val state: Flow<GameState> = combine(stats, settings.celebratedAchievements, settings.testerPoints) { s, celebrated, points ->
        val progress = Gamification.sorted(Gamification.progress(s))
        val newly = Gamification.newlyUnlocked(s, celebrated)
        // Erstes Mal (Update auf diese Version): Erreichtes still übernehmen statt viele Karten zu zeigen.
        if (!settings.gamificationInitialized) settings.markCelebrated(newly.map { it.key })
        GameState(
            stats = s,
            progress = progress,
            badges = (if (points > 0) listOf(TESTER_BADGE) else emptyList()) + progress.filter { it.unlocked }.map { it.achievement.key },
            newlyUnlocked = if (settings.gamificationInitialized) newly else emptyList(),
        )
    }

    fun markCelebrated(achievements: List<Gamification.Achievement>) = settings.markCelebrated(achievements.map { it.key })

    companion object {
        const val TESTER_BADGE = "tester"
    }
}
