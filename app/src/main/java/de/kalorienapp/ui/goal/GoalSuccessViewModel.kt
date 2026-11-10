package de.kalorienapp.ui.goal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.StatsRepository
import de.kalorienapp.data.repository.WeightRepository
import de.kalorienapp.domain.GoalSuccess
import de.kalorienapp.domain.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Was aufs teilbare Bild kommt (Baukasten). Absolute Gewichte standardmäßig aus – nicht jeder will sie zeigen. */
data class ShareOptions(
    val change: Boolean = true,
    val weights: Boolean = false,
    val chart: Boolean = true,
    val duration: Boolean = true,
    val onTarget: Boolean = true,
    val story: Boolean = false,
)

/** Daten für Feier-Bildschirm und „Erfolg teilen“ (beide nutzen die Ziel-ID aus der Route). */
class GoalSuccessViewModel(
    savedStateHandle: SavedStateHandle,
    private val goals: GoalRepository,
    private val weights: WeightRepository,
    private val stats: StatsRepository,
) : ViewModel() {
    private val goalId: Long = savedStateHandle["goalId"] ?: 0L

    private val _success = MutableStateFlow<GoalSuccess?>(null)
    val success: StateFlow<GoalSuccess?> = _success.asStateFlow()

    private val _options = MutableStateFlow(ShareOptions())
    val options: StateFlow<ShareOptions> = _options.asStateFlow()

    init {
        viewModelScope.launch {
            val goal = goals.get(goalId) ?: return@launch
            val zone = ZoneId.systemDefault()
            val measurements = weights.entries.first().map { it.measuredAt.atZone(zone).toLocalDate() to it.weightKg }
            val end = goal.endedDate ?: LocalDate.now()
            val onTarget = StatsCalculator.period(stats.days(goal.startDate, end).first()).onTargetRate
            _success.value = GoalSuccess.of(goal, measurements, onTarget)
        }
    }

    fun update(transform: (ShareOptions) -> ShareOptions) = _options.update(transform)
}
