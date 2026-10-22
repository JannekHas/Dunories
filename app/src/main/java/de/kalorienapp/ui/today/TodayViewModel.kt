package de.kalorienapp.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.ActivityEntryRepository
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.FoodLibraryRepository
import de.kalorienapp.data.repository.ProfileRepository
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.sum
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MealGroup(
    val mealType: MealType,
    val entries: List<FoodEntry>,
    val total: Nutrients,
)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val summary: DaySummary? = null,
    val meals: List<MealGroup> = emptyList(),
    /** Selbst eingetragene Aktivitäten (verbrannte Kalorien) des Tages. */
    val activities: List<ActivityEntry> = emptyList(),
    /** Namen der Favoriten (klein geschrieben) – für „Als Favorit speichern / entfernen“. */
    val favoriteNames: Set<String> = emptySet(),
) {
    fun isFavorite(entry: FoodEntry) = entry.name.lowercase() in favoriteNames

    val isToday: Boolean get() = date == LocalDate.now()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    savedStateHandle: SavedStateHandle,
    profileRepository: ProfileRepository,
    private val entryRepository: FoodEntryRepository,
    private val library: FoodLibraryRepository,
    private val activityRepository: ActivityEntryRepository,
) : ViewModel() {

    /** Aus dem Kalender geöffnet → dieses Datum, sonst heute. */
    private val date = MutableStateFlow(
        savedStateHandle.get<Long>("epochDay")?.let(LocalDate::ofEpochDay) ?: LocalDate.now(),
    )

    val uiState: StateFlow<TodayUiState> = date
        .flatMapLatest { day ->
            combine(
                profileRepository.profile,
                entryRepository.entriesFor(day),
                library.favorites,
                activityRepository.entriesFor(day),
            ) { profile, entries, favorites, activities ->
                val summary = profile?.let {
                    GoalCalculator.daySummary(
                        targets = GoalCalculator.targets(it, day.year),
                        eaten = entries.map(FoodEntry::nutrients).sum(),
                        activityKcal = activities.sumOf(ActivityEntry::kcal),
                    )
                }
                val byMeal = entries.groupBy(FoodEntry::mealType)
                TodayUiState(
                    date = day,
                    summary = summary,
                    meals = MealType.entries.map { meal ->
                        val list = byMeal[meal].orEmpty()
                        MealGroup(meal, list, list.map(FoodEntry::nutrients).sum())
                    },
                    activities = activities,
                    favoriteNames = favorites.map { it.name.lowercase() }.toSet(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun previousDay() = date.update { it.minusDays(1) }

    fun nextDay() = date.update { it.plusDays(1) }

    fun goToToday() {
        date.value = LocalDate.now()
    }

    fun delete(entry: FoodEntry) {
        viewModelScope.launch { entryRepository.delete(entry.id) }
    }

    /** @return true wenn der Eintrag jetzt Favorit ist. */
    fun toggleFavorite(entry: FoodEntry): Boolean {
        val nowFavorite = !uiState.value.isFavorite(entry)
        viewModelScope.launch {
            if (nowFavorite) library.addFavorite(entry) else library.removeFavoriteByName(entry.name)
        }
        return nowFavorite
    }

    fun restore(entry: FoodEntry) {
        viewModelScope.launch { entryRepository.save(entry) }
    }

    // --- Aktivitäten (selbst eingetragene verbrannte Kalorien) ---

    /** Neue Aktivität für den angezeigten Tag ([existing] == null) oder vorhandene ändern. */
    fun saveActivity(existing: ActivityEntry?, name: String, kcal: Int) {
        val entry = existing?.copy(name = name.trim(), kcal = kcal)
            ?: ActivityEntry(date = date.value, name = name.trim(), kcal = kcal)
        viewModelScope.launch { activityRepository.save(entry) }
    }

    fun deleteActivity(entry: ActivityEntry) {
        viewModelScope.launch { activityRepository.delete(entry.id) }
    }

    fun restoreActivity(entry: ActivityEntry) {
        viewModelScope.launch { activityRepository.save(entry) }
    }
}
