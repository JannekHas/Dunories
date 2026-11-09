package de.kalorienapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.GoalState
import de.kalorienapp.data.repository.ProfileRepository
import de.kalorienapp.data.repository.WeightRepository
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.WeightEntry
import de.kalorienapp.domain.model.WeightGoal
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.goal.GoalFormState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.Year

data class ProfileUiState(
    val form: ProfileFormState = ProfileFormState(),
    val isLoading: Boolean = true,
    /** Effektive Ziele inkl. Overrides; null solange das Formular unvollständig ist. */
    val targets: DailyTargets? = null,
    /** Rein berechnete Ziele ohne Overrides – als Platzhalter in den Override-Feldern. */
    val autoTargets: DailyTargets? = null,
    /** Standard-Protein pro kg für das gewählte Ziel. */
    val defaultProteinPerKg: Double = 0.0,
    /** Onboarding: Eingabe des ersten Gewichtsziels. */
    val goalForm: GoalFormState = GoalFormState(),
    /** Aktives Ziel (Profil) bzw. eingegebenes Ziel (Onboarding) mit heutigem Plan. */
    val goalState: GoalState? = null,
)

/** Wird für Onboarding und Profil-Screen verwendet. */
class ProfileViewModel(
    private val repository: ProfileRepository,
    private val weightRepository: WeightRepository,
    private val goalRepository: GoalRepository,
) : ViewModel() {

    private val currentYear get() = Year.now().value

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * Automatisch speichern, sobald ein Profil existiert (Profil-Screen). Im Onboarding gibt es noch keins –
     * dort wird erst mit „Fertig“ gespeichert, damit kein halbes Profil entsteht.
     */
    private var autoSave = false
    private var saveJob: Job? = null
    private var activeGoal: WeightGoal? = null
    private var lastSavedWeightKg: Double? = null

    init {
        viewModelScope.launch {
            val existing = repository.get()
            autoSave = existing != null
            val form = existing?.let { ProfileFormState.from(it, currentYear) } ?: ProfileFormState()
            _uiState.value = withTargets(ProfileUiState(form = form, isLoading = false))
            if (autoSave) {
                lastSavedWeightKg = existing?.weightKg
                launch {
                    goalRepository.activeGoal.collect { goal ->
                        activeGoal = goal
                        _uiState.update(::withTargets)
                    }
                }
                // Gewicht kann sich auch von außen ändern (Messung in der Statistik eingetragen/gelöscht).
                repository.profile.collect { profile ->
                    val weight = profile?.weightKg ?: return@collect
                    if (weight != lastSavedWeightKg) {
                        lastSavedWeightKg = weight
                        _uiState.update { withTargets(it.copy(form = it.form.copy(weightKg = weight.toInputString()))) }
                    }
                }
            }
        }
    }

    fun updateGoal(goalForm: GoalFormState) {
        _uiState.update { withTargets(it.copy(goalForm = goalForm)) }
    }

    fun update(transform: (ProfileFormState) -> ProfileFormState) {
        _uiState.update { withTargets(it.copy(form = transform(it.form))) }
        if (autoSave) {
            // Ungültige Zwischenstände (z. B. beim Tippen) werden nicht gespeichert.
            val profile = _uiState.value.form.toProfile(currentYear) ?: return
            saveJob?.cancel()
            lastSavedWeightKg = profile.weightKg
            saveJob = viewModelScope.launch { repository.save(profile) }
        }
    }

    /** Schutz gegen Doppeltippen (sonst doppelte Gewichtsmessung und doppeltes Ziel). */
    private var saving = false

    /**
     * Onboarding-Abschluss: Profil und erstes Ziel speichern;
     * das Startgewicht wird die erste Messung im Gewichtsverlauf.
     */
    fun save(onSaved: () -> Unit) {
        if (saving) return
        val profile = _uiState.value.form.toProfile(currentYear) ?: return
        val goal = _uiState.value.goalState?.goal ?: return
        saving = true
        viewModelScope.launch {
            repository.save(profile)
            weightRepository.save(WeightEntry(measuredAt = Instant.now(), weightKg = profile.weightKg))
            goalRepository.startGoal(profile.weightKg, goal.targetWeightKg, goal.targetDate)
            onSaved()
        }
    }

    private fun withTargets(state: ProfileUiState): ProfileUiState {
        val form = state.form
        val profile = form.toProfile(currentYear)
        // Im Onboarding gibt es noch kein gespeichertes Ziel → das eingegebene zählt.
        val goal = if (autoSave) activeGoal else profile?.let { state.goalForm.toGoal(it.weightKg) }
        val goalState = profile?.let { GoalState.of(it, goal, LocalDate.now()) }
        return state.copy(
            goalState = goalState,
            targets = goalState?.targets,
            autoTargets = goalState?.let {
                it.copy(
                    profile = it.profile.copy(
                        kcalTargetOverride = null,
                        proteinOverrideG = null,
                        fatOverrideG = null,
                        carbsOverrideG = null,
                    ),
                ).targets
            },
            defaultProteinPerKg = GoalCalculator.defaultProteinPerKg(goalState?.direction ?: Goal.MAINTAIN),
        )
    }
}
