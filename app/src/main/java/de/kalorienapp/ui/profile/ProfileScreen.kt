package de.kalorienapp.ui.profile

import de.kalorienapp.ui.plan.MealPlanSettingsSection
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.TabbedPages
import de.kalorienapp.ui.fasting.FastingSettingsSection
import de.kalorienapp.ui.goal.GoalCard

/** Profil & Ziele – jede gültige Änderung wird sofort gespeichert (kein Speichern-Knopf). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNewGoal: () -> Unit,
    onOpenPlan: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_title)) }) },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        TabbedPages(
            tabs = listOf(
                stringResource(R.string.profile_tab_goals),
                stringResource(R.string.profile_tab_plan),
                stringResource(R.string.profile_tab_fasting),
                stringResource(R.string.profile_tab_notifications),
                stringResource(R.string.profile_tab_data),
            ),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
            when (page) {
                0 -> {
                    Text(
                        stringResource(R.string.profile_autosave_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TargetsCard(state.targets, atMinimum = state.form.kcalOverride.isBlank() && state.targets?.kcal == GoalCalculator.MIN_KCAL_TARGET)
                    SectionTitle(stringResource(R.string.section_goal))
                    GoalCard(state.goalState, onNewGoal)
                    HorizontalDivider()
                    SectionTitle(stringResource(R.string.section_body))
                    BodyDataSection(state.form, viewModel::update)
                    HorizontalDivider()
                    SectionTitle(stringResource(R.string.section_targets))
                    TargetAdjustmentsSection(state, viewModel::update)
                    HorizontalDivider()
                    SectionTitle(stringResource(R.string.water_title))
                    WaterSettingsSection()
                }
                1 -> MealPlanSettingsSection(onOpenPlan)
                2 -> FastingSettingsSection()
                3 -> NotificationSettingsSection()
                else -> BackupSection()
            }
        }
    }
}
