package de.kalorienapp.ui.onboarding

import kotlinx.coroutines.launch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.Feedback
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.LimitFontScale
import de.kalorienapp.ui.components.ValuePicker
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.goal.GoalFormState
import de.kalorienapp.ui.goal.GoalInputs
import de.kalorienapp.ui.goal.GoalPreviewCard
import de.kalorienapp.ui.profile.ChoiceCard
import de.kalorienapp.ui.profile.LifestyleCards
import de.kalorienapp.ui.profile.ProfileViewModel
import de.kalorienapp.ui.theme.Neon

/** Auswahl auf der Ziel-Seite; „Erstmal ohne Ziel“ macht das Gewichtsziel optional (Nutzer, Nov. 2026). */
private enum class GoalChoice { LOSE, GAIN, MAINTAIN, NONE }

/** Seiten der Einrichtung – je eine Frage. Wunschgewicht/Datum nur bei Abnehmen oder Zunehmen. */
private enum class Page { WELCOME, NAME, SEX, AGE, HEIGHT, WEIGHT, LIFESTYLE, GOAL, GOAL_DETAILS }

/**
 * Einrichtung neu (Wunsch des Nutzers Nov. 2026, inspiriert vom Aufbau großer Tracker-Apps – nur die Idee, nicht das
 * Aussehen): **eine Frage pro Seite**, groß und luftig, Fortschrittsbalken oben, überall „Weiter“ (kein automatisches
 * Weiterspringen – einheitlich, Entscheidung des Nutzers). Zahlen per Ziehleiste, „−/+“ oder Antippen und Tippen.
 * Danach wie bisher „Dein Plan steht!“.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onYazio: () -> Unit = {},
    initialName: String = "",
    onSaveName: (String) -> Unit = {},
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var index by rememberSaveable { mutableIntStateOf(0) }
    var forward by rememberSaveable { mutableStateOf(true) }
    var choice by rememberSaveable { mutableStateOf<GoalChoice?>(null) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    // Nach dem Speichern: „Dein Plan steht“ als Erfolgsmoment, erst dann zu „Heute“.
    var saved by rememberSaveable { mutableStateOf(false) }
    val targets = state.targets
    if (saved && targets != null) {
        BackHandler { onFinished() }
        PlanReveal(targets, state.goalState, onStart = onFinished)
        return
    }
    if (state.isLoading) return

    val pages = Page.entries.filter { it != Page.GOAL_DETAILS || choice == GoalChoice.LOSE || choice == GoalChoice.GAIN }
    val page = pages[index.coerceAtMost(pages.lastIndex)]
    val isLast = index >= pages.lastIndex
    val form = state.form
    val goal = state.goalState?.goal

    val valid = when (page) {
        Page.WELCOME, Page.NAME -> true
        Page.SEX -> form.sex != null
        Page.AGE -> form.ageValue != null
        Page.HEIGHT -> form.heightValue != null
        Page.WEIGHT -> form.weightValue != null
        Page.LIFESTYLE -> form.lifestyle != null
        Page.GOAL -> when (choice) {
            null -> false
            GoalChoice.NONE -> form.isValid
            GoalChoice.MAINTAIN -> form.isValid && goal?.direction == Goal.MAINTAIN
            GoalChoice.LOSE, GoalChoice.GAIN -> true
        }
        Page.GOAL_DETAILS -> form.isValid && goal?.direction == if (choice == GoalChoice.GAIN) Goal.GAIN else Goal.LOSE
    }

    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0

    fun back() {
        forward = false
        index--
    }

    fun next() {
        if (page == Page.NAME) onSaveName(name.trim().take(Feedback.MAX_NAME))
        if (isLast) {
            viewModel.save(withGoal = choice != GoalChoice.NONE) { saved = true }
        } else if (imeVisible) {
            // Tastatur erst schließen, dann weiter – sonst springt die nächste Seite mit dem Tastatur-Rand kurz
            // runter und wieder hoch (Handy-Test 26.11.).
            keyboard?.hide()
            focusManager.clearFocus()
            scope.launch {
                kotlinx.coroutines.delay(KEYBOARD_CLOSE_MS)
                forward = true
                index++
            }
        } else {
            forward = true
            index++
        }
    }

    fun choose(value: GoalChoice) {
        val current = form.weightValue
        when (value) {
            GoalChoice.MAINTAIN -> viewModel.updateGoal(GoalFormState(targetWeight = current?.toInputString().orEmpty()))
            GoalChoice.NONE -> viewModel.updateGoal(GoalFormState())
            // Startwert fürs Wunschgewicht: 5 kg in die gewählte Richtung (lässt sich auf der nächsten Seite ziehen).
            GoalChoice.LOSE, GoalChoice.GAIN -> if (choice != value && current != null) {
                val start = if (value == GoalChoice.LOSE) (current - 5).coerceAtLeast(MIN_WEIGHT) else (current + 5).coerceAtMost(MAX_WEIGHT)
                viewModel.updateGoal(GoalFormState(targetWeight = start.toInputString()))
            }
        }
        choice = value
    }

    // Zahlen-Seiten starten mit einem typischen Wert, den man nur noch anpasst (wie ein Regler, nie ein leeres Feld).
    LaunchedEffect(page) {
        val male = form.sex != Sex.FEMALE
        when (page) {
            Page.AGE -> if (form.age.isBlank()) viewModel.update { it.copy(age = DEFAULT_AGE.toString()) }
            Page.HEIGHT -> if (form.heightCm.isBlank()) viewModel.update { it.copy(heightCm = (if (male) 180 else 167).toString()) }
            Page.WEIGHT -> if (form.weightKg.isBlank()) viewModel.update { it.copy(weightKg = (if (male) 85 else 70).toString()) }
            else -> Unit
        }
    }

    BackHandler(enabled = index > 0) { back() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // ---------------- Oben: Zurück + Fortschritt (ab Seite 2) ----------------
        LimitFontScale {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0) {
                    IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    LinearProgressIndicator(
                        progress = { index.toFloat() / (pages.size - 1) },
                        color = Neon.Cyan,
                        trackColor = Neon.Track,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.weight(1f).height(8.dp).padding(end = 16.dp),
                    )
                }
            }
        }

        // ---------------- Seite ----------------
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(260)) { it / 5 * dir } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 5 * dir } + fadeOut(tween(160)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { p ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (p) {
                    Page.WELCOME -> WelcomePage(onYazio)
                    Page.NAME -> {
                        PageTitle(stringResource(R.string.onb_name_title))
                        PageText(stringResource(R.string.onb_name_text))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(Feedback.MAX_NAME) },
                            label = { Text(stringResource(R.string.social_name_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { next() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Page.SEX -> {
                        PageTitle(stringResource(R.string.onb_sex_title))
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(Sex.MALE, Sex.FEMALE).forEach { sex ->
                                ChoiceCard(
                                    title = stringResource(sex.labelRes),
                                    text = null,
                                    selected = form.sex == sex,
                                    accent = Neon.Cyan,
                                    onClick = { viewModel.update { it.copy(sex = sex) } },
                                )
                            }
                        }
                        PageHint(stringResource(R.string.profile_sex_info))
                    }
                    Page.AGE -> {
                        PageTitle(stringResource(R.string.onb_age_title))
                        Spacer(Modifier.height(24.dp))
                        ValuePicker(
                            value = form.ageValue?.toDouble() ?: DEFAULT_AGE.toDouble(),
                            onValueChange = { v -> viewModel.update { it.copy(age = v.toInt().toString()) } },
                            range = 14.0..100.0,
                            sliderRange = 14.0..90.0,
                            unit = stringResource(R.string.unit_years),
                            label = stringResource(R.string.profile_age),
                        )
                    }
                    Page.HEIGHT -> {
                        PageTitle(stringResource(R.string.onb_height_title))
                        Spacer(Modifier.height(24.dp))
                        ValuePicker(
                            value = form.heightValue ?: 175.0,
                            onValueChange = { v -> viewModel.update { it.copy(heightCm = v.toInputString(0)) } },
                            range = 100.0..250.0,
                            sliderRange = 140.0..210.0,
                            unit = stringResource(R.string.unit_cm),
                            label = stringResource(R.string.profile_height),
                        )
                    }
                    Page.WEIGHT -> {
                        PageTitle(stringResource(R.string.onb_weight_title))
                        PageText(stringResource(R.string.onb_weight_text))
                        Spacer(Modifier.height(8.dp))
                        ValuePicker(
                            value = form.weightValue ?: 80.0,
                            onValueChange = { v -> viewModel.update { it.copy(weightKg = v.toInputString(1)) } },
                            range = MIN_WEIGHT..MAX_WEIGHT,
                            sliderRange = 40.0..180.0,
                            unit = stringResource(R.string.unit_kg),
                            label = stringResource(R.string.profile_weight),
                            step = 0.1,
                            slideStep = 0.5,
                            decimals = 1,
                        )
                    }
                    Page.LIFESTYLE -> {
                        PageTitle(stringResource(R.string.onboarding_lifestyle_title))
                        PageText(stringResource(R.string.onboarding_lifestyle_text))
                        LifestyleCards(form.lifestyle) { lifestyle -> viewModel.update { it.copy(lifestyle = lifestyle) } }
                        PageHint(stringResource(R.string.lifestyle_hint))
                    }
                    Page.GOAL -> {
                        PageTitle(stringResource(R.string.onboarding_goal_title))
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GoalCard(GoalChoice.LOSE, choice, Neon.Green, R.string.onboarding_goal_lose, R.string.onboarding_goal_lose_text, Icons.Filled.KeyboardArrowDown, ::choose)
                            GoalCard(GoalChoice.GAIN, choice, Neon.Orange, R.string.onboarding_goal_gain, R.string.onboarding_goal_gain_text, Icons.Filled.KeyboardArrowUp, ::choose)
                            GoalCard(GoalChoice.MAINTAIN, choice, Neon.Cyan, R.string.onboarding_goal_maintain, R.string.onboarding_goal_maintain_text, Icons.Filled.Check, ::choose)
                            GoalCard(GoalChoice.NONE, choice, Neon.Violet, R.string.onboarding_goal_none, R.string.onboarding_goal_none_text, Icons.Filled.Close, ::choose)
                        }
                    }
                    Page.GOAL_DETAILS -> {
                        PageTitle(stringResource(R.string.onb_target_title))
                        form.weightValue?.let { PageText(stringResource(R.string.goal_current_weight, it.toInputString())) }
                        ValuePicker(
                            value = state.goalForm.targetWeight.parseDecimal()?.coerceIn(MIN_WEIGHT, MAX_WEIGHT) ?: form.weightValue ?: 80.0,
                            onValueChange = { v -> viewModel.updateGoal(state.goalForm.copy(targetWeight = v.toInputString(1))) },
                            range = MIN_WEIGHT..MAX_WEIGHT,
                            sliderRange = 40.0..180.0,
                            unit = stringResource(R.string.unit_kg),
                            label = stringResource(R.string.goal_target_weight),
                            step = 0.1,
                            slideStep = 0.5,
                            decimals = 1,
                        )
                        val recommendation = state.goalForm.recommendation(state.goalState?.profile)
                        GoalInputs(state.goalForm, null, viewModel::updateGoal, recommendation, showMaintainChip = false, showWeightField = false)
                        GoalPreviewCard(state.goalState?.takeIf { it.goal != null }, recommendation = recommendation)
                    }
                }
            }
        }

        // ---------------- Unten: ein breiter Knopf ----------------
        Button(
            onClick = ::next,
            enabled = valid,
            colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(56.dp),
        ) {
            Text(
                stringResource(
                    when {
                        page == Page.WELCOME -> R.string.onb_start
                        page == Page.NAME && name.isBlank() -> R.string.onb_skip
                        isLast -> R.string.finish
                        else -> R.string.next
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WelcomePage(onYazio: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Image(
        painterResource(R.drawable.wordmark_dunories),
        contentDescription = stringResource(R.string.app_name),
        modifier = Modifier.fillMaxWidth(0.7f),
    )
    Spacer(Modifier.height(24.dp))
    PageTitle(stringResource(R.string.onb_welcome_title))
    PageText(stringResource(R.string.onb_welcome_text))
    Spacer(Modifier.height(24.dp))
    // Wechsel von YAZIO: Serie mitnehmen – freiwillig, geht auch später unter Einstellungen.
    TextButton(onClick = onYazio) { Text(stringResource(R.string.yazio_onboarding_entry)) }
}

@Composable
private fun GoalCard(
    value: GoalChoice,
    selected: GoalChoice?,
    accent: Color,
    titleRes: Int,
    textRes: Int,
    icon: ImageVector,
    onChoose: (GoalChoice) -> Unit,
) {
    val isSelected = value == selected
    ChoiceCard(
        title = stringResource(titleRes),
        text = stringResource(textRes),
        selected = isSelected,
        accent = accent,
        onClick = { onChoose(value) },
    ) { Icon(icon, contentDescription = null, tint = if (isSelected) accent else Neon.TextSecondary, modifier = Modifier.size(28.dp)) }
}

@Composable
private fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PageText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Neon.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PageHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

private const val DEFAULT_AGE = 30
private const val MIN_WEIGHT = 30.0
private const val MAX_WEIGHT = 300.0

/** So lange schließt sich die Tastatur, bevor die nächste Seite kommt. */
private const val KEYBOARD_CLOSE_MS = 250L
