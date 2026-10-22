package de.kalorienapp.ui.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.GoalState
import de.kalorienapp.data.repository.ProfileRepository
import de.kalorienapp.domain.model.Profile
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoalUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val hasActiveGoal: Boolean = false,
    val form: GoalFormState = GoalFormState(),
) {
    val preview: GoalState? get() = form.preview(profile)
}

class GoalViewModel(
    private val profileRepository: ProfileRepository,
    private val goalRepository: GoalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = GoalUiState(
                isLoading = false,
                profile = profileRepository.get(),
                hasActiveGoal = goalRepository.active() != null,
            )
        }
    }

    fun update(form: GoalFormState) = _uiState.update { it.copy(form = form) }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val goal = state.preview?.goal ?: return
        viewModelScope.launch {
            goalRepository.startGoal(goal.startWeightKg, goal.targetWeightKg, goal.targetDate)
            onSaved()
        }
    }
}

/** Neues Gewichtsziel setzen – jederzeit möglich, ein laufendes Ziel wird dabei beendet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(
    onDone: () -> Unit,
    viewModel: GoalViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goal_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.goal_screen_intro), style = MaterialTheme.typography.bodyMedium)
            GoalInputs(state.form, state.profile?.weightKg, viewModel::update)
            GoalPreviewCard(state.preview)
            if (state.hasActiveGoal) {
                Text(
                    stringResource(R.string.goal_replace_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.TextSecondary,
                )
            }
            Button(
                onClick = { viewModel.save(onDone) },
                enabled = state.preview != null,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.goal_set))
            }
        }
    }
}
