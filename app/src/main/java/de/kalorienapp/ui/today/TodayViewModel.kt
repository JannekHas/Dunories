package de.kalorienapp.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.ActivityEntryRepository
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.MealPlanRepository
import de.kalorienapp.BuildConfig
import de.kalorienapp.domain.Changelog
import de.kalorienapp.ui.changelog.ChangelogUi
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.MealPlanSchedule
import de.kalorienapp.data.repository.MergeNotice
import de.kalorienapp.data.repository.FoodLibraryRepository
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.StatsRepository
import de.kalorienapp.domain.Streak
import de.kalorienapp.domain.BehaviorTip
import de.kalorienapp.domain.BehaviorTips
import de.kalorienapp.domain.DeletedEntry
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

/** Tipp nach Verhalten auf „Heute“ – erscheint über der betroffenen Mahlzeit. */
sealed interface TodayBehaviorTip {
    val meal: MealType

    /** [entry] zum 3. Mal in 14 Tagen → „Als Favorit“. */
    data class Favorite(val entry: FoodEntry) : TodayBehaviorTip {
        override val meal get() = entry.mealType
    }

    /** Diese Mahlzeit gab es so schon → „Als Kombi“. */
    data class Combo(override val meal: MealType, val entries: List<FoodEntry>) : TodayBehaviorTip

    /** Gelöscht und hier neu eingetragen → „Halte gedrückt und zieh“. */
    data class Move(override val meal: MealType) : TodayBehaviorTip
}

/** Offene Reste, die in der leeren Mahlzeit [meal] vorgeschlagen werden (nur heute). */
data class LeftoverSuggestion(val meal: MealType, val items: List<de.kalorienapp.domain.model.Leftover>)

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
    private val account: de.kalorienapp.account.AccountManager,
    private val premium: de.kalorienapp.account.PremiumClient,
    private val game: de.kalorienapp.data.repository.GameRepository,
    private val leftovers: de.kalorienapp.data.repository.LeftoverRepository,
) : ViewModel() {

    /** Neu freigeschalteter Erfolg (Level & Erfolge an) → kurze Karte oben auf „Heute“. */
    val newAchievement: StateFlow<de.kalorienapp.domain.Gamification.Progress?> =
        kotlinx.coroutines.flow.combine(game.state, settings.gamificationEnabled) { g, on -> if (on) g.newlyUnlocked.firstOrNull() else null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun achievementSeen(achievement: de.kalorienapp.domain.Gamification.Progress) = game.markCelebrated(listOf(achievement))

    /** Tester-Belohnungsprogramm: neu bewertetes Feedback → einmal „Danke, du hast x Punkte bekommen“. */
    private val _reward = MutableStateFlow<de.kalorienapp.account.AiStatus?>(null)
    val reward: StateFlow<de.kalorienapp.account.AiStatus?> = _reward.asStateFlow()

    init {
        // Nur fragen, wenn schon angemeldet (Gast oder Konto) – sonst entstünde nur dafür ein Gast-Konto.
        if (account.hasAnyUser) viewModelScope.launch {
            premium.status()?.takeIf { it.newRewards > 0 && it.newPoints > 0 }?.let { _reward.value = it }
        }
    }

    fun rewardSeen() {
        _reward.value = null
        viewModelScope.launch { premium.rewardsSeen() }
    }

    /** Tage in Folge im Ziel bis gestern – Erfolgsmoment auf „Heute“ (0 = nichts zeigen). */
    val streak: StateFlow<Int> = LocalDate.now().let { today ->
        statsRepository.days(today.minusDays(STREAK_LOOKBACK_DAYS), today.minusDays(1)).map { Streak.current(it, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Zuletzt gelöschter Eintrag – „gelöscht und woanders neu eingetragen“ → Verschieben-Tipp. */
    private val lastDeleted = MutableStateFlow<DeletedEntry?>(null)

    /**
     * Tipp nach Verhalten für heute (Priorität: Verschieben, Favorit, Kombi). Nebenbei: mehrere Einzelfotos
     * für eine Mahlzeit merken → Tipp „Ein Foto reicht“ beim nächsten Erfassen.
     */
    val behaviorTip: StateFlow<TodayBehaviorTip?> = LocalDate.now().let { today ->
        combine(
            entryRepository.entriesBetween(today.minusDays(BehaviorTips.LOOKBACK_DAYS), today),
            library.favorites,
            library.combos,
            lastDeleted,
        ) { entries, favorites, combos, deleted ->
            val todays = entries.filter { it.date == today }
            if (BehaviorTips.manySinglePhotos(todays)) settings.addPendingTip(BehaviorTip.ONE_PHOTO.name)
            val moved = deleted?.takeIf { BehaviorTips.reEnteredElsewhere(it, todays) }
            val favorite = BehaviorTips.favoriteCandidate(entries, today, favorites.map { BehaviorTips.key(it.name) }.toSet())
            val combo = BehaviorTips.comboCandidate(
                entries, today, combos.map { c -> c.items.map { BehaviorTips.key(it.name) }.toSet() }.toSet(),
            )
            val seen = settings.seenTips.value
            when {
                moved != null && BehaviorTip.MOVE.name !in seen ->
                    todays.firstOrNull { BehaviorTips.key(it.name) == BehaviorTips.key(moved.name) && it.mealType != moved.mealType }
                        ?.let { TodayBehaviorTip.Move(it.mealType) }
                favorite != null && BehaviorTip.FAVORITE.name !in seen -> TodayBehaviorTip.Favorite(favorite)
                combo != null && BehaviorTip.COMBO.name !in seen -> TodayBehaviorTip.Combo(combo.first, combo.second)
                else -> null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addFavorite(entry: FoodEntry) {
        viewModelScope.launch { library.addFavorite(entry) }
    }

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

    /** Feedback-Symbol (Testphase, Fernschalter) – nur wenn Firebase eingerichtet ist. */
    val feedbackOnToday: StateFlow<Boolean> = settings.feedbackOnToday
        .map { it && de.kalorienapp.BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Ungelesene Patchnotes (versionCodes, neueste zuerst) – „Neu in Dunories“ auf „Heute“. */
    val unseenChangelog: StateFlow<List<Int>> = settings.changelogSeen
        .map { seen -> Changelog.unseen(seen, BuildConfig.VERSION_CODE, ChangelogUi.VERSIONS.map { it.code }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun changelogRead() = settings.markChangelogSeen(BuildConfig.VERSION_CODE)

    /** Wasser-Karte an (Profil → Bereiche → Wasser). */
    val waterEnabled: StateFlow<Boolean> = settings.waterEnabled

    /** „Erstmal ohne Ziel“ in der Einrichtung → kein Ziel-Banner. */
    val goalSkipped: StateFlow<Boolean> = settings.goalSkipped

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
        lastDeleted.value = DeletedEntry(entry.name, entry.date, entry.mealType, System.currentTimeMillis())
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

    // --- Reste / offene Mengen ---

    /** Vorschlag in der nächsten leeren Mittag-/Abend-Mahlzeit von heute (höchstens zwei Reste). */
    val leftoverSuggestion: StateFlow<LeftoverSuggestion?> = combine(uiState, leftovers.open) { state, open ->
        if (!state.loaded || !state.isToday || open.isEmpty()) return@combine null
        val empty = state.meals.filter { it.entries.isEmpty() }.map { it.mealType }.toSet()
        val meal = de.kalorienapp.domain.Leftovers.suggestionMeal(java.time.LocalTime.now().hour, empty) ?: return@combine null
        open.filter { de.kalorienapp.domain.Leftovers.suggestOn(it, state.date, meal) }
            .take(MAX_LEFTOVER_SUGGESTIONS)
            .takeIf { it.isNotEmpty() }
            ?.let { LeftoverSuggestion(meal, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun groupDish(entries: List<FoodEntry>, name: String, eatenAmount: Double, onDone: (de.kalorienapp.data.repository.LeftoverUndo.Grouped) -> Unit) {
        viewModelScope.launch { leftovers.group(entries, name, eatenAmount)?.let(onDone) }
    }

    fun partial(entry: FoodEntry, eatenAmount: Double, onDone: (de.kalorienapp.data.repository.LeftoverUndo.Partial) -> Unit) {
        viewModelScope.launch { leftovers.partial(entry, eatenAmount)?.let(onDone) }
    }

    fun takeLeftover(
        leftover: de.kalorienapp.domain.model.Leftover,
        amount: Double,
        meal: MealType,
        onDone: (de.kalorienapp.data.repository.LeftoverUndo) -> Unit,
    ) {
        viewModelScope.launch { onDone(leftovers.take(leftover, amount, date.value, meal)) }
    }

    fun discardLeftover(leftover: de.kalorienapp.domain.model.Leftover, onDone: (de.kalorienapp.data.repository.LeftoverUndo) -> Unit) {
        viewModelScope.launch { onDone(leftovers.discard(leftover)) }
    }

    fun freezeLeftover(leftover: de.kalorienapp.domain.model.Leftover, frozen: Boolean) {
        viewModelScope.launch { leftovers.update(leftover.copy(frozen = frozen)) }
    }

    /** ✕: heute nicht mehr vorschlagen. */
    fun leftoverNotToday(leftover: de.kalorienapp.domain.model.Leftover) {
        viewModelScope.launch { leftovers.update(leftover.copy(dismissedDay = LocalDate.now())) }
    }

    /** „Noch gut“ → in ein paar Tagen wieder fragen. */
    fun leftoverStillGood(leftover: de.kalorienapp.domain.model.Leftover) {
        viewModelScope.launch { leftovers.update(leftover.copy(checkedDay = LocalDate.now())) }
    }

    fun undoLeftover(undo: de.kalorienapp.data.repository.LeftoverUndo) {
        viewModelScope.launch { leftovers.undo(undo) }
    }
}

private const val STREAK_LOOKBACK_DAYS = 90L
private const val MAX_LEFTOVER_SUGGESTIONS = 2
