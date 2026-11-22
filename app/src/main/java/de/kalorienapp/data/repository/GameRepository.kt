package de.kalorienapp.data.repository

import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.dao.WeeklyReportDao
import de.kalorienapp.domain.Gamification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Alles für den Social-Reiter: Level, Erfolge, Abzeichen. Wird live aus den vorhandenen Daten berechnet. */
data class GameState(
    val stats: Gamification.Stats,
    val level: Gamification.Level,
    val progress: List<Gamification.Progress>,
    /** Verdiente Abzeichen (Schlüssel): freigeschaltete Erfolge + „tester“. */
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
        combine(reports.observeAll(), settings.feedbackSentCount) { r, f -> r.size to f },
    ) { days, entries, weightList, reached, (reportCount, feedback) ->
        val (longest, current) = Gamification.streaks(days, LocalDate.now().toEpochDay())
        Gamification.Stats(
            loggedDays = days.size,
            entries = entries,
            longestStreak = longest,
            currentStreak = current,
            weights = weightList.size,
            goalsReached = reached.size,
            reports = reportCount,
            feedbackSent = feedback,
        )
    }

    val state: Flow<GameState> = combine(stats, settings.celebratedAchievements, settings.testerPoints) { s, celebrated, points ->
        val progress = Gamification.progress(s)
        val newly = Gamification.newlyUnlocked(s, celebrated)
        // Erstes Mal (Update auf diese Version): Erreichtes still übernehmen statt viele Karten zu zeigen.
        if (!settings.gamificationInitialized) settings.markCelebrated(newly.map { it.key })
        GameState(
            stats = s,
            level = Gamification.level(Gamification.xp(s)),
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
