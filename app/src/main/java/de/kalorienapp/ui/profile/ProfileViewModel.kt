package de.kalorienapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.data.repository.ProfileRepository
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.domain.model.DailyTargets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
)

/** Wird für Onboarding und Profil-Screen verwendet. */
class ProfileViewModel(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val currentYear get() = Year.now().value

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = repository.get()
            val form = existing?.let { ProfileFormState.from(it, currentYear) } ?: ProfileFormState()
            _uiState.value = withTargets(ProfileUiState(form = form, isLoading = false))
        }
    }

    fun update(transform: (ProfileFormState) -> ProfileFormState) {
        _uiState.update { withTargets(it.copy(form = transform(it.form))) }
    }

    fun save(onSaved: () -> Unit) {
        val profile = _uiState.value.form.toProfile(currentYear) ?: return
        viewModelScope.launch {
            repository.save(profile)
            onSaved()
        }
    }

    private fun withTargets(state: ProfileUiState): ProfileUiState {
        val form = state.form
        // Für die Vorschau reichen Körperdaten; HC-Frage noch offen → wie „sitzend“ rechnen.
        val previewForm = if (form.useHealthConnect == null) form.copy(useHealthConnect = true) else form
        val profile = previewForm.toProfile(currentYear)
        return state.copy(
            targets = profile?.let { GoalCalculator.targets(it, currentYear) },
            autoTargets = profile?.let {
                GoalCalculator.targets(
                    it.copy(
                        kcalTargetOverride = null,
                        proteinOverrideG = null,
                        fatOverrideG = null,
                        carbsOverrideG = null,
                    ),
                    currentYear,
                )
            },
            defaultProteinPerKg = GoalCalculator.defaultProteinPerKg(form.goal),
        )
    }
}
