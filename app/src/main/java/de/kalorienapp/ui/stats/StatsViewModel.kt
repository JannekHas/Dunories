package de.kalorienapp.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.remote.gemini.GeminiError
import de.kalorienapp.data.remote.gemini.GeminiException
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.NotEnoughDataException
import de.kalorienapp.data.repository.WeeklyReportRepository
import de.kalorienapp.data.repository.StatsRepository
import de.kalorienapp.data.repository.WeightRepository
import de.kalorienapp.domain.StatsCalculator
import de.kalorienapp.domain.TrendPoint
import de.kalorienapp.domain.WeightTrend
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.PeriodStats
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.domain.model.WeightEntry
import de.kalorienapp.domain.model.WeeklyReport
import de.kalorienapp.domain.model.WeightGoal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** Zeitraum der Statistik (rollierend bis heute). */
enum class StatsPeriod(val days: Int) { WEEK(7), MONTH(30) }

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val stats: PeriodStats? = null,
    val targets: DailyTargets? = null,
    val weights: List<WeightEntry> = emptyList(),
    /** Geglätteter Gewichtsverlauf (siehe [WeightTrend]). */
    val weightTrend: List<TrendPoint> = emptyList(),
    /** Aktuelles Profilgewicht – Vorbelegung, solange noch kein Eintrag existiert. */
    val profileWeightKg: Double? = null,
    /** Erreichte Gewichtsziele, neueste zuerst. */
    val reachedGoals: List<WeightGoal> = emptyList(),
    val sex: Sex? = null,
    /** KI-Wochenauswertungen, neueste zuerst. */
    val reports: List<WeeklyReport> = emptyList(),
)

/** Zustand der manuell gestarteten KI-Auswertung. */
sealed interface ReportGeneration {
    data object Idle : ReportGeneration
    data object Running : ReportGeneration
    data class Failed(val error: ReportError) : ReportGeneration
}

enum class ReportError { NOT_ENOUGH_DATA, NO_API_KEY, QUOTA, OVERLOADED, NETWORK, INVALID }

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    statsRepository: StatsRepository,
    private val weightRepository: WeightRepository,
    private val goalRepository: GoalRepository,
    private val reportRepository: WeeklyReportRepository,
) : ViewModel() {

    private val _reportGeneration = MutableStateFlow<ReportGeneration>(ReportGeneration.Idle)
    val reportGeneration: StateFlow<ReportGeneration> = _reportGeneration.asStateFlow()

    private val period = MutableStateFlow(StatsPeriod.WEEK)

    val uiState: StateFlow<StatsUiState> = period
        .flatMapLatest { p ->
            val today = LocalDate.now()
            combine(
                statsRepository.days(today.minusDays(p.days - 1L), today),
                weightRepository.entries,
                goalRepository.current,
                goalRepository.reachedGoals,
                reportRepository.reports,
            ) { days, weights, goalState, reached, reports ->
                StatsUiState(
                    period = p,
                    stats = StatsCalculator.period(days),
                    targets = goalState?.targets,
                    weights = weights,
                    weightTrend = WeightTrend.compute(weights),
                    profileWeightKg = goalState?.profile?.weightKg,
                    reachedGoals = reached,
                    sex = goalState?.profile?.sex,
                    reports = reports,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun selectPeriod(value: StatsPeriod) {
        period.value = value
    }

    /** Neue Messung mit aktuellem Zeitpunkt – überschreibt nichts. */
    fun addWeight(weightKg: Double) {
        viewModelScope.launch { weightRepository.save(WeightEntry(measuredAt = Instant.now(), weightKg = weightKg)) }
    }

    /** KI-Auswertung der letzten 7 Tage; bei Erfolg wird sie geöffnet. */
    fun generateReport(onDone: (Long) -> Unit) {
        if (_reportGeneration.value == ReportGeneration.Running) return
        _reportGeneration.value = ReportGeneration.Running
        viewModelScope.launch {
            val (from, to) = WeeklyReportRepository.lastSevenDays(LocalDate.now())
            _reportGeneration.value = try {
                onDone(reportRepository.generate(from, to).id)
                ReportGeneration.Idle
            } catch (e: NotEnoughDataException) {
                ReportGeneration.Failed(ReportError.NOT_ENOUGH_DATA)
            } catch (e: GeminiException) {
                ReportGeneration.Failed(
                    when (e.error) {
                        GeminiError.NO_API_KEY -> ReportError.NO_API_KEY
                        GeminiError.QUOTA -> ReportError.QUOTA
                        GeminiError.OVERLOADED -> ReportError.OVERLOADED
                        GeminiError.NETWORK -> ReportError.NETWORK
                        else -> ReportError.INVALID
                    },
                )
            }
        }
    }

    fun deleteGoal(goal: WeightGoal) {
        viewModelScope.launch { goalRepository.deleteEnded(goal) }
    }

    fun deleteWeight(entry: WeightEntry) {
        viewModelScope.launch { weightRepository.delete(entry) }
    }
}
