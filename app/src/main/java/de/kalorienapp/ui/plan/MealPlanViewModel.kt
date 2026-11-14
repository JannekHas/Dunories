package de.kalorienapp.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.MealPlanRepository
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.MealPlanSchedule
import de.kalorienapp.domain.model.MealPlanSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ernährungsplan bearbeiten: Schalter, Zeitraum, Tage mit ihren Lebensmitteln. */
class MealPlanViewModel(private val repository: MealPlanRepository) : ViewModel() {
    init {
        repository.finishIfEnded()
    }

    val config: StateFlow<MealPlanSettings> = repository.config

    val items: StateFlow<List<MealPlanItem>> =
        repository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(enabled: Boolean) = repository.updateConfig { current ->
        val today = LocalDate.now()
        // Beim Einschalten nach einer Pause mit dem heutigen Tag als Tag 1 starten.
        val restart = enabled && MealPlanSchedule.dayIndex(current.copy(enabled = true), today) == null
        if (restart) {
            current.copy(enabled = enabled, startEpochDay = today.toEpochDay(), endEpochDay = null, stopAtEnd = false)
        } else {
            current.copy(enabled = enabled, stopAtEnd = false)
        }
    }

    /** Rhythmus: so viele Tage trägt man ein, danach beginnt der Plan wieder bei Tag 1. Laufzeit bleibt. */
    fun setDays(days: Int) = repository.updateConfig { it.copy(days = days.coerceIn(1, MealPlanSettings.MAX_DAYS)) }

    /** Neuer Tag 1; ein festes Ende vor dem Start fällt weg. */
    fun setStart(date: LocalDate) = repository.updateConfig {
        val end = it.endEpochDay?.takeIf { e -> e >= date.toEpochDay() }
        it.copy(startEpochDay = date.toEpochDay(), endEpochDay = end)
    }

    /** Laufzeit: bis [date] (inklusive). */
    fun setEnd(date: LocalDate) = repository.updateConfig {
        it.copy(endEpochDay = maxOf(date.toEpochDay(), it.startEpochDay), openEnded = false, stopAtEnd = false)
    }

    fun setOpenEnded(open: Boolean) = repository.updateConfig { it.copy(openEnded = open, stopAtEnd = false) }

    fun delete(item: MealPlanItem) {
        viewModelScope.launch { repository.delete(item.id) }
    }

    fun copyDay(from: Int, to: Int) {
        viewModelScope.launch { repository.copyDay(from, to) }
    }

    fun clearDay(dayIndex: Int) {
        viewModelScope.launch { repository.clearDay(dayIndex) }
    }
}
