package de.kalorienapp.ui.onboarding

import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import de.kalorienapp.ui.theme.Neon
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.ui.goal.GoalFormState
import de.kalorienapp.ui.profile.ChoiceCard
import de.kalorienapp.ui.profile.LifestyleCards
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.goal.GoalInputs
import de.kalorienapp.ui.goal.GoalPreviewCard
import de.kalorienapp.ui.profile.BodyDataSection
import de.kalorienapp.ui.profile.ProfileFormState
import de.kalorienapp.ui.profile.ProfileViewModel

private const val STEP_COUNT = 3

/** Auswahl in Schritt 3; „Erstmal ohne Ziel“ macht das Gewichtsziel optional (Nutzer, Nov. 2026). */
private enum class GoalChoice { LOSE, GAIN, MAINTAIN, NONE }

/**
 * Einrichtung in 3 Schritten (Nutzer-Feedback Nov. 2026: Seiten wirkten leer, Ziel sollte optional sein):
 * 1 Körperdaten · 2 Alltag als große Karten · 3 Ziel als große Karten (Abnehmen, Zunehmen, Halten, ohne Ziel).
 * Die frühere Seite „berechnete Ziele“ entfällt – das zeigt danach „Dein Plan steht!“.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var choice by rememberSaveable { mutableStateOf<GoalChoice?>(null) }
    // Nach dem Speichern: „Dein Plan steht“ als Erfolgsmoment, erst dann zu „Heute“.
    var saved by rememberSaveable { mutableStateOf(false) }
    val targets = state.targets
    if (saved && targets != null) {
        BackHandler { onFinished() }
        PlanReveal(targets, state.goalState, onStart = onFinished)
        return
    }

    BackHandler(enabled = step > 0) { step-- }

    val goal = state.goalState?.goal
    val stepValid = when (step) {
        0 -> state.form.basicsValid
        1 -> state.form.lifestyle != null
        else -> state.form.isValid && when (choice) {
            GoalChoice.NONE -> true
            GoalChoice.MAINTAIN -> goal?.direction == Goal.MAINTAIN
            GoalChoice.LOSE -> goal?.direction == Goal.LOSE
            GoalChoice.GAIN -> goal?.direction == Goal.GAIN
            null -> false
        }
    }

    fun choose(value: GoalChoice) {
        val current = state.form.weightValue
        when (value) {
            GoalChoice.MAINTAIN -> viewModel.updateGoal(GoalFormState(targetWeight = current?.toInputString().orEmpty()))
            GoalChoice.NONE -> viewModel.updateGoal(GoalFormState())
            // Von „halten“/„ohne Ziel“ kommend: Wunschgewicht leeren, sonst stünde das aktuelle Gewicht drin.
            else -> if (choice != GoalChoice.LOSE && choice != GoalChoice.GAIN) viewModel.updateGoal(GoalFormState())
        }
        choice = value
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
                        if (step < STEP_COUNT - 1) step++ else viewModel.save(withGoal = choice != GoalChoice.NONE) { saved = true }
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
                    Image(
                        painterResource(R.drawable.wordmark_dunories),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(0.62f).align(Alignment.CenterHorizontally),
                    )
                    StepTitle(stringResource(R.string.onboarding_body_title))
                    StepIntro(stringResource(R.string.onboarding_body_intro))
                    BodyDataSection(form, viewModel::update, showLifestyle = false)
                }
                1 -> {
                    StepTitle(stringResource(R.string.onboarding_lifestyle_title))
                    StepIntro(stringResource(R.string.onboarding_lifestyle_text))
                    LifestyleCards(form.lifestyle) { lifestyle -> viewModel.update { it.copy(lifestyle = lifestyle) } }
                    Text(stringResource(R.string.lifestyle_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                }
                else -> {
                    StepTitle(stringResource(R.string.onboarding_goal_title))
                    form.weightValue?.let {
                        StepIntro(stringResource(R.string.goal_current_weight, it.toInputString()))
                    }
                    GoalCard(GoalChoice.LOSE, choice, Neon.Green, R.string.onboarding_goal_lose, R.string.onboarding_goal_lose_text, Icons.Filled.KeyboardArrowDown, ::choose)
                    if (choice == GoalChoice.LOSE) GoalDetails(state, viewModel)
                    GoalCard(GoalChoice.GAIN, choice, Neon.Orange, R.string.onboarding_goal_gain, R.string.onboarding_goal_gain_text, Icons.Filled.KeyboardArrowUp, ::choose)
                    if (choice == GoalChoice.GAIN) GoalDetails(state, viewModel)
                    GoalCard(GoalChoice.MAINTAIN, choice, Neon.Cyan, R.string.onboarding_goal_maintain, R.string.onboarding_goal_maintain_text, Icons.Filled.Check, ::choose)
                    GoalCard(GoalChoice.NONE, choice, Neon.Violet, R.string.onboarding_goal_none, R.string.onboarding_goal_none_text, Icons.Filled.Close, ::choose)
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    value: GoalChoice,
    selected: GoalChoice?,
    accent: androidx.compose.ui.graphics.Color,
    titleRes: Int,
    textRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onChoose: (GoalChoice) -> Unit,
) {
    val isSelected = value == selected
    ChoiceCard(
        title = stringResource(titleRes),
        text = stringResource(textRes),
        selected = isSelected,
        accent = accent,
        onClick = { onChoose(value) },
    ) { Icon(icon, contentDescription = null, tint = if (isSelected) accent else Neon.TextSecondary) }
}

/** Wunschgewicht + Zieldatum mit Empfehlung, aufgeklappt unter „Abnehmen“/„Zunehmen“. */
@Composable
private fun GoalDetails(state: de.kalorienapp.ui.profile.ProfileUiState, viewModel: ProfileViewModel) {
    val recommendation = state.goalForm.recommendation(state.goalState?.profile)
    GoalInputs(state.goalForm, null, viewModel::updateGoal, recommendation, showMaintainChip = false)
    GoalPreviewCard(state.goalState?.takeIf { it.goal != null }, recommendation = recommendation)
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StepIntro(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Neon.TextSecondary)
}
