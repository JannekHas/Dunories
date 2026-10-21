package de.kalorienapp.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.FoodLibraryRepository
import de.kalorienapp.data.repository.HealthSyncStatus
import de.kalorienapp.data.repository.ProfileRepository
import de.kalorienapp.data.repository.TrainingRepository
import de.kalorienapp.domain.GoalCalculator
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
    /** Namen der Favoriten (klein geschrieben) – für „Als Favorit speichern / entfernen“. */
    val favoriteNames: Set<String> = emptySet(),
    /** Trainings aus Health Connect werden übernommen (Schalter im Profil). */
    val importWorkouts: Boolean = false,
    /** Hinweis, falls der Import aktiv ist, Health Connect aber nicht gelesen werden kann. */
    val healthProblem: HealthSyncStatus? = null,
) {
    fun isFavorite(entry: FoodEntry) = entry.name.lowercase() in favoriteNames

    val isToday: Boolean get() = date == LocalDate.now()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val profileRepository: ProfileRepository,
    private val entryRepository: FoodEntryRepository,
    private val library: FoodLibraryRepository,
    private val trainingRepository: TrainingRepository,
) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())
    private val syncStatus = MutableStateFlow<HealthSyncStatus?>(null)

    val healthPermissions: Set<String> get() = trainingRepository.permissions
    fun healthPermissionContract() = trainingRepository.permissionContract()

    val uiState: StateFlow<TodayUiState> = date
        .flatMapLatest { day ->
            combine(
                profileRepository.profile,
                entryRepository.entriesFor(day),
                library.favorites,
                trainingRepository.training(day),
                syncStatus,
            ) { profile, entries, favorites, training, status ->
                val importWorkouts = profile?.importWorkouts == true
                val summary = profile?.let {
                    GoalCalculator.daySummary(
                        targets = GoalCalculator.targets(it, day.year),
                        eaten = entries.map(FoodEntry::nutrients).sum(),
                        trainingKcal = if (importWorkouts) training?.activeKcal ?: 0 else 0,
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
                    favoriteNames = favorites.map { it.name.lowercase() }.toSet(),
                    importWorkouts = importWorkouts,
                    healthProblem = status?.takeIf { importWorkouts && it != HealthSyncStatus.OK },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun previousDay() {
        date.update { it.minusDays(1) }
        refreshTraining()
    }

    fun nextDay() {
        date.update { it.plusDays(1) }
        refreshTraining()
    }

    fun goToToday() {
        date.value = LocalDate.now()
        refreshTraining()
    }

    /** Trainings des angezeigten Tages neu aus Health Connect lesen (beim Öffnen, Tageswechsel, nach Berechtigung). */
    fun refreshTraining() {
        viewModelScope.launch {
            val profile = profileRepository.get()
            syncStatus.value = if (profile?.importWorkouts == true) {
                trainingRepository.refresh(date.value, profile)
            } else {
                null
            }
        }
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
}
