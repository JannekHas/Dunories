package de.kalorienapp.ui.profile

import de.kalorienapp.ui.plan.MealPlanSettingsSection
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import de.kalorienapp.ui.changelog.ChangelogDialog
import de.kalorienapp.ui.changelog.ChangelogUi
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.ui.components.LocalWeightEnabled
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.TabbedPages
import de.kalorienapp.ui.fasting.FastingSettingsSection
import de.kalorienapp.ui.goal.GoalCard
import de.kalorienapp.ui.components.ExpandableCard
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.theme.Neon

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
                stringResource(R.string.profile_tab_areas),
                stringResource(R.string.profile_tab_settings),
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
                    // Ohne „Gewicht tracken“ läuft ein Ziel unsichtbar weiter – keine kg, kein Fortschritt.
                    if (LocalWeightEnabled.current) {
                        SectionTitle(stringResource(R.string.section_goal))
                        GoalCard(state.goalState, onNewGoal)
                    }
                    // Körperdaten und Feinjustieren zugeklappt – die Seite passt so fast ohne Scrollen (Nutzer Nov. 2026).
                    val form = state.form
                    ExpandableCard(
                        title = stringResource(R.string.section_body),
                        summary = bodySummary(form),
                        initiallyExpanded = !form.bodyDataValid,
                    ) { BodyDataSection(form, viewModel::update) }
                    val custom = listOf(form.proteinPerKg, form.kcalOverride, form.proteinOverride, form.carbsOverride, form.fatOverride)
                        .any { it.isNotBlank() }
                    ExpandableCard(
                        title = stringResource(R.string.section_targets),
                        summary = stringResource(if (custom) R.string.fine_tune_custom else R.string.fine_tune_auto),
                        initiallyExpanded = !form.targetsValid,
                    ) { TargetAdjustmentsSection(state, viewModel::update) }
                }
                // Bereiche: alle Schalter an einem Ort, eingeschaltet klappen ihre Einstellungen auf (ersetzt Plan + Fasten).
                1 -> {
                    Text(stringResource(R.string.areas_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                    SectionTitle(stringResource(R.string.areas_food))
                    ProteinOnlySection()
                    WaterSettingsSection()
                    MealPlanSettingsSection(onOpenPlan)
                    FastingSettingsSection()
                    HorizontalDivider()
                    SectionTitle(stringResource(R.string.areas_body))
                    WeightTrackingSection()
                    BodyAnalysisSettingSection()
                    MeasuresSettingSection()
                }
                // Einstellungen: Hinweise und darunter Datensicherung (4 statt 5 Reiter – Wunsch des Nutzers)
                else -> {
                    SectionTitle(stringResource(R.string.profile_tab_notifications))
                    NotificationSettingsSection()
                    HorizontalDivider()
                    SectionTitle(stringResource(R.string.section_backup))
                    BackupSection()
                    HorizontalDivider()
                    var showChangelog by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { showChangelog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.changelog_open))
                    }
                    if (showChangelog) {
                        ChangelogDialog(ChangelogUi.VERSIONS.takeLast(1), onClose = { showChangelog = false })
                    }
                }
            }
        }
    }
}

/** Kurzfassung der Körperdaten für die zugeklappte Karte, z. B. „Mann · 30 Jahre · 180 cm · 80 kg“ + Alltag. */
@Composable
private fun bodySummary(form: ProfileFormState): String {
    val sex = form.sex
    val lifestyle = form.lifestyle
    if (!form.bodyDataValid || sex == null || lifestyle == null) return stringResource(R.string.body_summary_incomplete)
    val body = stringResource(R.string.body_summary, stringResource(sex.labelRes), form.age, form.heightCm, form.weightKg)
    return body + "\n" + stringResource(lifestyle.labelRes)
}
