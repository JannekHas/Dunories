package de.kalorienapp.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.StatsRepository
import de.kalorienapp.data.repository.WeightRepository
import de.kalorienapp.domain.StatsCalculator
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.PeriodStats
import de.kalorienapp.domain.model.WeightEntry
import de.kalorienapp.domain.model.WeightGoal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    /** Aktuelles Profilgewicht – Vorbelegung, solange noch kein Eintrag existiert. */
    val profileWeightKg: Double? = null,
    /** Erreichte Gewichtsziele, neueste zuerst. */
    val reachedGoals: List<WeightGoal> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    statsRepository: StatsRepository,
    private val weightRepository: WeightRepository,
    private val goalRepository: GoalRepository,
) : ViewModel() {

    private val period = MutableStateFlow(StatsPeriod.WEEK)

    val uiState: StateFlow<StatsUiState> = period
        .flatMapLatest { p ->
            val today = LocalDate.now()
            combine(
                statsRepository.days(today.minusDays(p.days - 1L), today),
                weightRepository.entries,
                goalRepository.current,
                goalRepository.reachedGoals,
            ) { days, weights, goalState, reached ->
                StatsUiState(
                    period = p,
                    stats = StatsCalculator.period(days),
                    targets = goalState?.targets,
                    weights = weights,
                    profileWeightKg = goalState?.profile?.weightKg,
                    reachedGoals = reached,
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

    fun deleteGoal(goal: WeightGoal) {
        viewModelScope.launch { goalRepository.deleteEnded(goal) }
    }

    fun deleteWeight(entry: WeightEntry) {
        viewModelScope.launch { weightRepository.delete(entry) }
    }
}
