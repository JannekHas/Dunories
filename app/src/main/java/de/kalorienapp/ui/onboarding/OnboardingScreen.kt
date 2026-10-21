package de.kalorienapp.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.profile.ActivitySection
import de.kalorienapp.ui.profile.BodyDataSection
import de.kalorienapp.ui.profile.GoalSection
import de.kalorienapp.ui.profile.ProfileFormState
import de.kalorienapp.ui.profile.ProfileViewModel
import de.kalorienapp.ui.profile.TargetAdjustmentsSection
import de.kalorienapp.ui.profile.TargetsCard

private const val STEP_COUNT = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = step > 0) { step-- }

    val stepValid = when (step) {
        0 -> state.form.bodyDataValid
        1 -> true
        2 -> true
        else -> state.form.isValid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.onboarding_title))
                        Text(
                            stringResource(R.string.onboarding_step, step + 1, STEP_COUNT),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = stepValid,
                    onClick = {
                        if (step < STEP_COUNT - 1) step++ else viewModel.save(onFinished)
                    },
                ) {
                    Text(stringResource(if (step < STEP_COUNT - 1) R.string.next else R.string.finish))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1f) / STEP_COUNT },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isLoading) return@Column
            val form: ProfileFormState = state.form
            when (step) {
                0 -> {
                    StepIntro(stringResource(R.string.onboarding_body_intro))
                    BodyDataSection(form, viewModel::update)
                }
                1 -> {
                    StepIntro(stringResource(R.string.onboarding_goal_intro))
                    GoalSection(form, viewModel::update)
                }
                2 -> {
                    StepIntro(stringResource(R.string.onboarding_activity_intro))
                    ActivitySection(form, viewModel::update)
                }
                else -> {
                    StepIntro(stringResource(R.string.onboarding_targets_intro))
                    TargetsCard(state.targets)
                    TargetAdjustmentsSection(state, viewModel::update)
                }
            }
        }
    }
}

@Composable
private fun StepIntro(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}
