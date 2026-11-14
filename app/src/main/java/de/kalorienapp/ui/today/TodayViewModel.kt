package de.kalorienapp.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.ActivityEntryRepository
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.MealPlanRepository
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.MealPlanSchedule
import de.kalorienapp.data.repository.MergeNotice
import de.kalorienapp.data.repository.FoodLibraryRepository
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.StatsRepository
import de.kalorienapp.domain.Streak
import de.kalorienapp.data.repository.WaterRepository
import de.kalorienapp.domain.FastingConfig
import de.kalorienapp.domain.Water
import de.kalorienapp.domain.EntryOrder
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.domain.model.WeightGoal
import de.kalorienapp.domain.model.sum
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    /** Für die Vitamin-Tageswerte (DGE je nach Geschlecht). */
    val sex: Sex? = null,
    /** false → Hinweis „Kein aktives Gewichtsziel“. */
    val hasGoal: Boolean = true,
    /** false, solange die Datenbank noch lädt → Platzhalter statt leerem Bildschirm. */
    val loaded: Boolean = false,
) {
    fun isFavorite(entry: FoodEntry) = entry.name.lowercase() in favoriteNames

    val isToday: Boolean get() = date == LocalDate.now()
}

/** Ernährungsplan für den angezeigten Tag (null = Plan aus). */
data class TodayPlan(
    /** Plan-Tag (0-basiert) oder null, wenn der Tag außerhalb des Durchlaufs liegt. */
    val dayIndex: Int?,
    val days: Int,
    val byMeal: Map<MealType, List<MealPlanItem>>,
    /** „Stimmt dein Plan noch?“ (letzter Tag oder abgelaufen, nur für heute). */
    val askToExtend: Boolean,
    val expired: Boolean,
    val end: LocalDate,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    savedStateHandle: SavedStateHandle,
    private val goalRepository: GoalRepository,
    private val waterRepository: WaterRepository,
    private val settings: AppSettings,
    private val entryRepository: FoodEntryRepository,
    private val library: FoodLibraryRepository,
    private val activityRepository: ActivityEntryRepository,
    statsRepository: StatsRepository,
    private val mealPlan: MealPlanRepository,
) : ViewModel() {

    /** Tage in Folge im Ziel bis gestern – Erfolgsmoment auf „Heute“ (0 = nichts zeigen). */
    val streak: StateFlow<Int> = LocalDate.now().let { today ->
        statsRepository.days(today.minusDays(STREAK_LOOKBACK_DAYS), today.minusDays(1)).map { Streak.current(it, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Aus dem Kalender geöffnet → dieses Datum, sonst heute. */
    private val date = MutableStateFlow(
        savedStateHandle.get<Long>("epochDay")?.let(LocalDate::ofEpochDay) ?: LocalDate.now(),
    )

    val uiState: StateFlow<TodayUiState> = date
        .flatMapLatest { day ->
            combine(
                goalRepository.current,
                entryRepository.entriesFor(day),
                library.favorites,
                activityRepository.entriesFor(day),
            ) { goalState, entries, favorites, activities ->
                val summary = goalState?.let {
                    GoalCalculator.daySummary(
                        targets = it.targetsFor(day.year),
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
                    hasGoal = goalState == null || goalState.goal != null,
                    sex = goalState?.profile?.sex,
                    loaded = true,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** Gerade beendetes Ziel (erreicht oder Datum vorbei) – wird einmal als Ergebnis gezeigt. */
    private val _endedGoal = MutableStateFlow<WeightGoal?>(null)
    val endedGoal: StateFlow<WeightGoal?> = _endedGoal.asStateFlow()

    init {
        // Bei jedem neuen Gewicht (und beim Start) prüfen, ob das aktive Ziel erreicht oder abgelaufen ist.
        viewModelScope.launch {
            goalRepository.current
                .filterNotNull()
                .map { it.profile.weightKg }
                .distinctUntilChanged()
                .collect { weight -> goalRepository.checkEnded(weight)?.let { _endedGoal.value = it } }
        }
    }

    fun dismissEndedGoal() {
        _endedGoal.value = null
    }

    /** Intervallfasten (null = aus); nur für heute sinnvoll. */
    val fasting: StateFlow<FastingConfig?> = combine(settings.fastingEnabled, settings.fastingConfig) { on, config ->
        config.takeIf { on }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Wasser des angezeigten Tages mit Ziel (eigenes oder aus dem Gewicht). */
    val water: StateFlow<WaterState> = date
        .flatMapLatest { day ->
            combine(
                waterRepository.totalFor(day),
                settings.waterGoalMl,
                goalRepository.current,
                entryRepository.entriesFor(day),
            ) { ml, custom, state, entries ->
                WaterState(
                    ml = ml,
                    goalMl = Water.goalMl(custom, state?.profile?.weightKg ?: 70.0),
                    customGoal = custom != null,
                    drinksMl = Water.drinksMl(entries),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterState())

    /** Wasser-Karte an (Profil → Ziele → Wasser). */
    val waterEnabled: StateFlow<Boolean> = settings.waterEnabled

    fun addWater(ml: Int) {
        viewModelScope.launch { waterRepository.add(date.value, ml) }
    }

    fun undoWater() {
        viewModelScope.launch { waterRepository.undo(date.value) }
    }

    fun setWaterGoal(ml: Int?) = settings.setWaterGoalMl(ml)

    fun saveCombo(name: String, entries: List<FoodEntry>) {
        viewModelScope.launch { library.saveCombo(name, entries) }
    }

    fun previousDay() = date.update { it.minusDays(1) }

    fun nextDay() = date.update { it.plusDays(1) }

    fun showDate(day: LocalDate) {
        date.value = day
    }

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

    /**
     * Eintrag in [meal] an Position [index] verschieben (Ziehen auf „Heute“).
     * @return die vorherigen Stände aller geänderten Einträge – für „Rückgängig“.
     */
    fun move(entry: FoodEntry, meal: MealType, index: Int): List<FoodEntry> {
        val target = uiState.value.meals.firstOrNull { it.mealType == meal }?.entries.orEmpty().filter { it.id != entry.id }
        val updated = EntryOrder.place(target, entry, meal, index)
        viewModelScope.launch { entryRepository.saveAll(updated) }
        return target + entry
    }

    fun restoreAll(entries: List<FoodEntry>) {
        viewModelScope.launch { entryRepository.saveAll(entries) }
    }

    fun restore(entry: FoodEntry) {
        viewModelScope.launch { entryRepository.save(entry) }
    }

    // --- Ernährungsplan ---

    init {
        mealPlan.finishIfEnded()
    }

    val plan: StateFlow<TodayPlan?> = date
        .flatMapLatest { day ->
            combine(mealPlan.config, mealPlan.items) { config, items ->
                if (!config.enabled) return@combine null
                val index = MealPlanSchedule.dayIndex(config, day)
                val today = LocalDate.now()
                TodayPlan(
                    dayIndex = index,
                    days = config.days,
                    byMeal = items.filter { index != null && it.dayIndex == index }.groupBy { it.mealType },
                    askToExtend = day == today && MealPlanSchedule.askToExtend(config, today),
                    expired = MealPlanSchedule.isExpired(config, today),
                    end = config.end,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Mahlzeiten „wie geplant“ eintragen (sofort, ohne Zusammenfassen).
     * [onSaved] bekommt die neuen ids – für „Rückgängig“.
     */
    fun applyPlan(meals: List<MealType>, onSaved: (List<Long>) -> Unit) {
        val plan = plan.value ?: return
        val now = System.currentTimeMillis()
        val entries = meals.flatMap { plan.byMeal[it].orEmpty() }
            .mapIndexed { i, item -> item.toEntry(date.value, now + i) }
        if (entries.isEmpty()) return
        viewModelScope.launch { onSaved(entryRepository.insertAll(entries)) }
    }

    fun undoPlan(ids: List<Long>) {
        viewModelScope.launch { entryRepository.deleteAll(ids) }
    }

    fun extendPlan() = mealPlan.updateConfig { MealPlanSchedule.extend(it, LocalDate.now()) }

    /** „Beenden“: heute läuft normal zu Ende, ab morgen ist der Plan aus (Inhalt bleibt). */
    fun endPlan() = mealPlan.updateConfig { it.copy(stopAtEnd = true) }

    fun undoEndPlan() = mealPlan.updateConfig { it.copy(stopAtEnd = false) }

    /** Gerade zusammengefasster Eintrag (z. B. zweiter Salami-Stick) – Snackbar mit „Rückgängig“. */
    val lastMerge: StateFlow<MergeNotice?> = entryRepository.lastMerge

    fun mergeShown() = entryRepository.mergeShown()

    fun undoMerge(notice: MergeNotice) {
        viewModelScope.launch { entryRepository.undoMerge(notice) }
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

private const val STREAK_LOOKBACK_DAYS = 90L
