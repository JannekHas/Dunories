package de.kalorienapp.data.repository

import de.kalorienapp.BuildConfig
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.db.dao.ActivityEntryDao
import de.kalorienapp.data.db.dao.FoodEntryDao
import de.kalorienapp.data.db.dao.WeeklyReportDao
import de.kalorienapp.data.db.entity.WeeklyReportEntity
import de.kalorienapp.data.remote.gemini.GeminiWeeklyAnalyzer
import de.kalorienapp.domain.Water
import de.kalorienapp.domain.WeeklyInput
import de.kalorienapp.domain.WeeklyInputBuilder
import de.kalorienapp.domain.model.WeeklyReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Zu wenige Tage mit Einträgen für eine sinnvolle Auswertung. */
class NotEnoughDataException(val loggedDays: Int) : Exception()

/** KI-Wochenauswertungen: Daten sammeln → Gemini → speichern. */
class WeeklyReportRepository(
    private val dao: WeeklyReportDao,
    private val foodDao: FoodEntryDao,
    private val activityDao: ActivityEntryDao,
    private val statsRepository: StatsRepository,
    private val goalRepository: GoalRepository,
    private val weightRepository: WeightRepository,
    private val measurementRepository: BodyMeasurementRepository,
    private val waterRepository: WaterRepository,
    private val settings: AppSettings,
    private val analyzer: GeminiWeeklyAnalyzer,
) {
    val reports: Flow<List<WeeklyReport>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun report(id: Long): Flow<WeeklyReport?> = dao.observe(id).map { it?.toDomain() }

    suspend fun exists(from: LocalDate, to: LocalDate) = dao.countFor(from.toEpochDay(), to.toEpochDay()) > 0

    suspend fun delete(report: WeeklyReport) = dao.delete(report.id)

    /**
     * Auswertung für [from]..[to] erstellen und speichern.
     * @throws NotEnoughDataException bei weniger als [WeeklyInputBuilder.MIN_LOGGED_DAYS] erfassten Tagen
     * @throws de.kalorienapp.data.remote.gemini.GeminiException bei KI-Fehlern
     */
    suspend fun generate(from: LocalDate, to: LocalDate, replaceId: Long = 0): WeeklyReport {
        val input = collectInput(from, to)
        val logged = WeeklyInputBuilder.loggedDays(input)
        if (logged < WeeklyInputBuilder.MIN_LOGGED_DAYS) throw NotEnoughDataException(logged)
        val report = analyzer.analyze(WeeklyInputBuilder.build(input), from, to).copy(id = replaceId)
        val id = dao.upsert(WeeklyReportEntity.fromDomain(report))
        return report.copy(id = if (replaceId != 0L) replaceId else id)
    }

    /** Text für die eigene KI: Überblick, alle Einträge des Zeitraums und die Auswertung. */
    suspend fun exportText(report: WeeklyReport): String =
        de.kalorienapp.domain.WeeklyExport.build(collectInput(report.from, report.to), report)

    private suspend fun collectInput(from: LocalDate, to: LocalDate): WeeklyInput {
        val state = goalRepository.current.first() ?: throw NotEnoughDataException(0)
        val baseInput = WeeklyInput(
            from = from,
            to = to,
            profile = state.profile,
            goal = state.goal,
            plan = state.plan,
            targets = state.targets,
            days = statsRepository.days(from, to).first(),
            entries = foodDao.between(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() },
            activities = activityDao.between(from.toEpochDay(), to.toEpochDay()).map { it.toDomain() },
            weights = weightRepository.entries.first(),
            measurements = measurementRepository.all.first(),
        ).let { input ->
            if (!BuildConfig.FEATURE_WATER) input else input.copy(
                waterByDay = waterRepository.dailyTotals(from, to),
                waterGoalMl = Water.goalMl(settings.waterGoalMl.value, state.profile.weightKg),
            )
        }
        val fasting = settings.fastingConfig.value.takeIf { settings.fastingEnabled.value }
        // Nur der Plan, keine „eingehalten“-Auswertung: nachträglich erfasste Einträge sähen wie
        // Essen in der Fastenzeit aus – das wäre falsch und demotivierend (Wunsch des Nutzers).
        val withPrefs = baseInput.copy(preferences = settings.foodPreferences.value)
        return if (fasting == null) withPrefs else withPrefs.copy(fasting = fasting)
    }

    companion object {
        /** Letzte vollständige Woche (Montag–Sonntag) vor [today]. */
        fun previousWeek(today: LocalDate): Pair<LocalDate, LocalDate> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
            return monday to monday.plusDays(6)
        }

        /** Manuell: die letzten 7 Tage bis einschließlich heute. */
        fun lastSevenDays(today: LocalDate): Pair<LocalDate, LocalDate> = today.minusDays(6) to today
    }
}
