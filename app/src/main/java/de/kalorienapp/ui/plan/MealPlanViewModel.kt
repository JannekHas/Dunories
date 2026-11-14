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

    // Länge/Start ändern beginnt einen neuen ersten Durchlauf (Ende = Start + Länge).
    fun setDays(days: Int) =
        repository.updateConfig { it.copy(days = days.coerceIn(1, MealPlanSettings.MAX_DAYS), endEpochDay = null) }

    fun setStart(date: LocalDate) = repository.updateConfig { it.copy(startEpochDay = date.toEpochDay(), endEpochDay = null) }

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
