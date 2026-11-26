package de.kalorienapp.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.repository.MealPlanRepository
import de.kalorienapp.domain.GoalCalculator
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.ExpandableCard
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.LocalBottomBarPadding
import de.kalorienapp.ui.components.LocalPageScroll
import de.kalorienapp.ui.components.LocalWeightEnabled
import de.kalorienapp.ui.components.PageScroll
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.components.neonChipColors
import de.kalorienapp.ui.goal.GoalCard
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Profil (Neuordnung Nov. 2026, Variante A des Nutzers): nur das Wichtigste – du, dein Tagesziel, dein Gewichtsziel und
 * deine Bereiche. Alle Einstellungen hinter dem Zahnrad oben rechts. Jede gültige Änderung wird sofort gespeichert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNewGoal: () -> Unit,
    onOpenSettings: () -> Unit = {},
    /** „Meine Bereiche“ → Einstellungen → Bereiche. */
    onOpenAreas: () -> Unit = {},
    onOpenSocial: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings_title), tint = Neon.TextPrimary)
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        val scrollState = rememberScrollState()
        val pageScroll = remember(scrollState) { PageScroll(scrollState) }
        CompositionLocalProvider(LocalPageScroll provides pageScroll) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .onGloballyPositioned { pageScroll.viewport = it }
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 24.dp + LocalBottomBarPadding.current),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileHeader(onOpenSocial)
                TargetsCard(state.targets, atMinimum = state.form.kcalOverride.isBlank() && state.targets?.kcal == GoalCalculator.MIN_KCAL_TARGET)
                // Ohne „Gewicht tracken“ läuft ein Ziel unsichtbar weiter – keine kg, kein Fortschritt.
                if (LocalWeightEnabled.current) GoalCard(state.goalState, onNewGoal)
                // Körperdaten und Feinjustieren zugeklappt – die Seite passt so fast ohne Scrollen.
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
                MyAreasCard(onOpenAreas)
                Text(
                    stringResource(R.string.profile_autosave_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.TextSecondary,
                )
            }
        }
    }
}

/** Avatar und Name (wie im Social-Reiter) – antippen öffnet Social. */
@Composable
private fun ProfileHeader(onOpenSocial: () -> Unit, viewModel: AreasViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val avatar by viewModel.avatar.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSocial)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        de.kalorienapp.ui.social.AvatarImage(avatar, name, size = 52)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                name.ifBlank { stringResource(R.string.profile_no_name) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.profile_header_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Neon.TextSecondary)
    }
}

/** Ein zuschaltbarer Bereich als Chip. [simple] = lässt sich direkt hier einschalten (ohne weitere Einstellungen). */
private data class Area(val labelRes: Int, val on: Boolean, val simple: Boolean, val enable: () -> Unit)

/**
 * „Meine Bereiche“: was eingeschaltet ist (grün) und was es noch gibt („+ Wasser“). Einfache Bereiche schalten sich
 * per Antippen ein, alles andere (Plan, Fasten, Ausschalten) führt in Einstellungen → Bereiche.
 * Hier setzen später auch die Vorschläge nach Verhalten an („Wasser tracken?“).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyAreasCard(onOpenAreas: () -> Unit, viewModel: AreasViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val areas = listOf(
        Area(R.string.area_water, s.water, true) { viewModel.settings.setWaterEnabled(true) },
        Area(R.string.area_plan, s.plan, false) {},
        Area(R.string.area_fasting, s.fasting, false) {},
        Area(R.string.area_weight, s.weight, true) { viewModel.settings.setWeightEnabled(true) },
        Area(R.string.area_body, s.bodyAnalysis, true) { viewModel.settings.setBodyAnalysisEnabled(true) },
        Area(R.string.area_measures, s.measures, true) { viewModel.settings.setMeasuresEnabled(true) },
        Area(R.string.area_game, s.gamification, true) { viewModel.settings.setGamificationEnabled(true) },
        Area(R.string.area_protein_only, s.proteinOnly, false) {},
    )
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.areas_mine), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenAreas) { Text(stringResource(R.string.areas_manage)) }
            }
            Text(stringResource(R.string.areas_mine_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                // Eingeschaltete zuerst
                areas.sortedByDescending { it.on }.forEach { area ->
                    FilterChip(
                        selected = area.on,
                        onClick = { if (!area.on && area.simple) area.enable() else onOpenAreas() },
                        label = { Text(stringResource(area.labelRes)) },
                        leadingIcon = { Icon(if (area.on) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null) },
                        colors = neonChipColors(),
                    )
                }
            }
        }
    }
}

data class AreasState(
    val water: Boolean = false,
    val plan: Boolean = false,
    val fasting: Boolean = false,
    val weight: Boolean = true,
    val bodyAnalysis: Boolean = false,
    val measures: Boolean = false,
    val gamification: Boolean = true,
    val proteinOnly: Boolean = false,
)

class AreasViewModel(val settings: AppSettings, mealPlan: MealPlanRepository) : ViewModel() {
    val name: StateFlow<String> = settings.testerName
    val avatar: StateFlow<String?> = settings.avatar

    val state: StateFlow<AreasState> = combine(
        combine(settings.waterEnabled, mealPlan.config, settings.fastingEnabled, settings.weightEnabled) { w, p, f, wt ->
            AreasState(water = w, plan = p.enabled, fasting = f, weight = wt)
        },
        combine(settings.bodyAnalysisEnabled, settings.measuresEnabled, settings.gamificationEnabled, settings.proteinOnly) { b, m, g, p ->
            listOf(b, m, g, p)
        },
    ) { a, rest -> a.copy(bodyAnalysis = rest[0], measures = rest[1], gamification = rest[2], proteinOnly = rest[3]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AreasState())
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
