package de.kalorienapp.data.repository

import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.dao.StatsDao
import de.kalorienapp.data.db.dao.WaterDao
import de.kalorienapp.data.db.dao.WeeklyReportDao
import de.kalorienapp.domain.Gamification
import de.kalorienapp.domain.Water
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Alles für den Social-Reiter: Erfolge und Abzeichen. Wird live aus den vorhandenen Daten berechnet. */
data class GameState(
    val stats: Gamification.Stats,
    /** Sichtbare Erfolge in Anzeige-Reihenfolge ([Gamification.sorted]). */
    val progress: List<Gamification.Progress>,
    /** Verdiente Abzeichen (Schlüssel), seltenste zuerst: freigeschaltete Erfolge + „tester“. */
    val badges: List<String>,
    /** Erreichte Stufe je Erfolg (für Abzeichen-Rahmen). */
    val levels: Map<String, Int>,
    val newlyUnlocked: List<Gamification.Progress>,
)

class GameRepository(
    private val foodDao: FoodEntryDao,
    private val statsDao: StatsDao,
    private val waterDao: WaterDao,
    private val weights: WeightRepository,
    private val goals: GoalRepository,
    private val reports: WeeklyReportDao,
    private val settings: AppSettings,
    /** Mit Google angemeldet (Freunde nur mit Konto). */
    private val signedIn: Flow<Boolean>,
) {
    private data class Days(val logged: List<Long>, val imported: Gamification.ImportedStreak?)

    private data class Counts(val entries: Int, val barcode: Int, val photo: Int, val createdTimes: List<Long>, val reports: Int)

    private data class Social(val rated: Int, val friends: Int, val invites: Int)

    private val days = combine(foodDao.observeLoggedDays(), settings.importedStreak) { d, i -> Days(d, i) }

    private val counts = combine(
        foodDao.observeCount(),
        foodDao.observeSourceCount("BARCODE"),
        foodDao.observeSourceCount("AI_PHOTO"),
        foodDao.observeCreatedTimes(),
        reports.observeAll(),
    ) { entries, barcode, photo, times, r -> Counts(entries, barcode, photo, times, r.size) }

    /** Tage mit erreichtem Protein- bzw. Wasserziel (Ziele aus dem aktuellen Profil, wie im Kalender). */
    private val targetDays = combine(
        statsDao.observeDailyNutrition(0, Long.MAX_VALUE / 2),
        waterDao.observeDailyTotals(),
        foodDao.observeDrinks(),
        goals.current,
        settings.waterGoalMl,
    ) { nutrition, water, drinks, state, customWater ->
        if (state == null) return@combine 0 to 0
        val protein = nutrition.count { row ->
            val target = state.targetsFor(LocalDate.ofEpochDay(row.dateEpochDay).year).proteinG
            target > 0 && row.proteinG >= target
        }
        val waterGoal = Water.goalMl(customWater, state.profile.weightKg)
        val drinkMl = drinks.filterNot { Water.isAlcoholic(it.name) }
            .groupBy { it.dateEpochDay }.mapValues { (_, l) -> l.sumOf { it.amountG ?: 0.0 }.roundToInt() }
        val waterMl = water.associate { it.dateEpochDay to it.ml }
        val waterDays = (waterMl.keys + drinkMl.keys).count { (waterMl[it] ?: 0) + (drinkMl[it] ?: 0) >= waterGoal }
        protein to waterDays
    }

    private val social = combine(settings.ratedFeedback, settings.friendsCount, settings.invitesCount) { r, f, i -> Social(r, f, i) }

    private val stats: Flow<Gamification.Stats> = combine(
        days, counts, targetDays, combine(weights.entries, goals.reachedGoals) { w, g -> w.size to g.size }, social,
    ) { d, c, (proteinDays, waterDays), (weightCount, goalCount), s ->
        val today = LocalDate.now()
        val (longest, current) = Gamification.streaks(d.logged, today.toEpochDay(), d.imported)
        val loggedDates = d.logged.map(LocalDate::ofEpochDay).toSet()
        val hours = c.createdTimes.map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).hour }
        val events = Gamification.eventYears(loggedDates)
        Gamification.Stats(
            loggedDays = d.logged.size,
            entries = c.entries,
            longestStreak = longest,
            currentStreak = current,
            proteinDays = proteinDays,
            photoEntries = c.photo,
            barcodeEntries = c.barcode,
            weights = weightCount,
            waterDays = waterDays,
            reports = c.reports,
            ratedFeedback = s.rated,
            friends = s.friends,
            invites = s.invites,
            goalsReached = goalCount,
            nightEntries = hours.count { it < 4 },
            earlyEntries = hours.count { it in 4..5 },
            comeback = if (Gamification.comeback(d.logged)) 1 else 0,
            weekendHero = if (Gamification.weekendHero(loggedDates, today)) 1 else 0,
            christmasYears = events.christmas,
            newYearsEveYears = events.newYearsEve,
            resolutionYears = events.resolution,
            easterYears = events.easter,
            halloweenYears = events.halloween,
        )
    }

    private val visibility: Flow<Gamification.Visibility> = combine(
        settings.weightEnabled, settings.waterEnabled, settings.premiumSeen, signedIn,
    ) { weight, water, premium, account -> Gamification.Visibility(weight = weight, water = water, ai = premium, account = account) }

    val state: Flow<GameState> = combine(
        stats, visibility, settings.celebratedAchievements, settings.testerPoints,
    ) { s, v, celebrated, points ->
        // „Foto-Profi“ auch ohne Premium, sobald man schon ein KI-Foto gemacht hat (Entscheidung des Nutzers).
        val vis = v.copy(ai = v.ai || s.photoEntries > 0)
        val all = Gamification.progress(s)
        val shown = Gamification.sorted(all.filter { Gamification.visible(it, vis) })
        // Erstes Mal mit Stufen-Erfolgen: Erreichtes still übernehmen statt viele Karten zu zeigen.
        if (!settings.gamificationInitialized) settings.markCelebrated(Gamification.allReachedKeys(all))
        val unlocked = shown.filter { it.unlocked }
        GameState(
            stats = s,
            progress = shown,
            badges = (if (points > 0) listOf(TESTER_BADGE) else emptyList()) + unlocked.map { it.achievement.key },
            levels = unlocked.associate { it.achievement.key to it.level },
            newlyUnlocked = if (settings.gamificationInitialized) Gamification.newlyUnlocked(shown, celebrated) else emptyList(),
        )
    }

    fun markCelebrated(progress: List<Gamification.Progress>) =
        settings.markCelebrated(progress.map { Gamification.celebrationKey(it.achievement, it.level) })

    companion object {
        const val TESTER_BADGE = "tester"
    }
}
