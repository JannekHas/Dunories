package de.kalorienapp.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.pluralStringResource
import de.kalorienapp.ui.components.pressable
import de.kalorienapp.ui.components.Tip
import de.kalorienapp.ui.components.TipCaret
import de.kalorienapp.ui.components.TipSlot
import de.kalorienapp.ui.components.LocalBottomBarPadding
import de.kalorienapp.ui.components.SkeletonBlock
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.BuildConfig
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.ui.components.ConcentricRings
import de.kalorienapp.ui.components.MicroList
import de.kalorienapp.ui.components.NeonPill
import de.kalorienapp.ui.components.RingSpec
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.domain.Fasting
import de.kalorienapp.ui.fasting.FastingCard
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMMM", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onSetGoal: () -> Unit,
    onGoalEnded: (Long) -> Unit = {},
    /** Im Kalender angetippter Tag → hier anzeigen (danach [onDayShown]). */
    requestedDay: LocalDate? = null,
    onDayShown: () -> Unit = {},
    onAddEntry: (date: LocalDate, mealType: MealType?) -> Unit,
    onEditEntry: (FoodEntry) -> Unit,
    /** Gesetzt, wenn der Tag aus dem Kalender geöffnet wurde. */
    onBack: (() -> Unit)? = null,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val endedGoal by viewModel.endedGoal.collectAsStateWithLifecycle()
    val water by viewModel.water.collectAsStateWithLifecycle()
    val fasting by viewModel.fasting.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    LaunchedEffect(requestedDay) {
        requestedDay?.let { viewModel.showDate(it); onDayShown() }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var comboToSave by remember { mutableStateOf<MealGroup?>(null) }
    val comboSaved = stringResource(R.string.combo_saved)
    comboToSave?.let { group ->
        ComboNameDialog(
            initial = stringResource(R.string.combo_default_name, stringResource(group.mealType.labelRes)),
            onSave = { name ->
                viewModel.saveCombo(name, group.entries)
                comboToSave = null
                scope.launch { snackbarHostState.showSnackbar(comboSaved.format(name)) }
            },
            onDismiss = { comboToSave = null },
        )
    }
    val deletedMessage = stringResource(R.string.entry_deleted)
    val undoLabel = stringResource(R.string.undo)
    val favoriteAdded = stringResource(R.string.favorite_added)
    val favoriteRemoved = stringResource(R.string.favorite_removed)

    val activityDeleted = stringResource(R.string.activity_deleted)
    val defaultActivityName = stringResource(R.string.activity_default_name)

    // Aktivitäten: Übersicht (Bottom-Sheet) und Dialog zum Eintragen/Bearbeiten
    var showActivityList by remember { mutableStateOf(false) }
    val activitySnackbar = remember { SnackbarHostState() }
    var activityDialog by remember { mutableStateOf<ActivityDialogState?>(null) }

    // Ziel beendet → großer Feier-Bildschirm (statt eines schlichten Dialogs)
    LaunchedEffect(endedGoal) {
        endedGoal?.let { goal ->
            viewModel.dismissEndedGoal()
            onGoalEnded(goal.id)
        }
    }

    activityDialog?.let { dialog ->
        ActivityEditDialog(
            existing = dialog.existing,
            onSave = { name, kcal ->
                viewModel.saveActivity(dialog.existing, name, kcal)
                activityDialog = null
            },
            onDismiss = { activityDialog = null },
        )
    }
    if (showActivityList) {
        ActivityListSheet(
            activities = state.activities,
            onAdd = { activityDialog = ActivityDialogState(null) },
            onEdit = { activityDialog = ActivityDialogState(it) },
            onDelete = { activity ->
                viewModel.deleteActivity(activity)
                scope.launch {
                    activitySnackbar.currentSnackbarData?.dismiss()
                    val result = activitySnackbar.showSnackbar(
                        message = activityDeleted.format(activity.name.ifBlank { defaultActivityName }),
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreActivity(activity)
                }
            },
            onDismiss = { showActivityList = false },
            snackbarHostState = activitySnackbar,
        )
    }

    fun toggleFavorite(entry: FoodEntry) {
        val nowFavorite = viewModel.toggleFavorite(entry)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar((if (nowFavorite) favoriteAdded else favoriteRemoved).format(entry.name))
        }
    }

    fun deleteWithUndo(entry: FoodEntry) {
        viewModel.delete(entry)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage.format(entry.name),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { DateTitle(state.date, onClick = viewModel::goToToday) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    } else {
                        IconButton(onClick = viewModel::previousDay) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_day))
                        }
                    }
                },
                actions = {
                    if (onBack != null) {
                        IconButton(onClick = viewModel::previousDay) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_day))
                        }
                    }
                    IconButton(onClick = viewModel::nextDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
                    }
                },
            )
        },
        // Aus dem Kalender geöffnet: keine untere Leiste → eigener „+“-Knopf.
        floatingActionButton = {
            if (onBack != null) {
                FloatingActionButton(
                    onClick = { onAddEntry(state.date, null) },
                    containerColor = Neon.Yellow,
                    contentColor = Neon.OnNeon,
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_entry))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = LocalBottomBarPadding.current)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = if (onBack != null) 88.dp else 16.dp + LocalBottomBarPadding.current),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.loaded) {
                item(key = "skeleton") { TodaySkeleton() }
                return@LazyColumn
            }
            if (!state.hasGoal && state.isToday) {
                item(key = "goal_banner") { GoalBanner(onSetGoal) }
            }
            if (streak > 0 && state.isToday) {
                item(key = "streak") { StreakBanner(streak) }
            }
            state.summary?.let { summary ->
                item(key = "summary") {
                    SummaryCard(
                        summary = summary,
                        sex = state.sex,
                        onActivityClick = { showActivityList = true },
                    )
                }
            }
            fasting?.takeIf { state.isToday }?.let { config ->
                item(key = "fasting") {
                    val zone = java.time.ZoneId.systemDefault()
                    // Nur am selben Tag erfasste Einträge zählen als „gegessen um“.
                    val outside = if (!config.isDaily) 0 else state.meals.flatMap { it.entries }.count { entry ->
                        val created = java.time.Instant.ofEpochMilli(entry.createdAt).atZone(zone)
                        created.toLocalDate() == state.date && !Fasting.isInEatingWindow(config, created.toLocalTime())
                    }
                    FastingCard(config, state.sex, state.summary?.eaten?.kcal ?: 0.0, outside)
                }
            }
            if (BuildConfig.FEATURE_WATER) {
                item(key = "water") {
                    WaterCard(water, onAdd = viewModel::addWater, onUndo = viewModel::undoWater, onGoal = viewModel::setWaterGoal)
                }
            }
            item(key = "tip_meal") { TipSlot(Tip.MEAL, caret = TipCaret.DOWN_CENTER, after = Tip.ADD) }
            items(state.meals, key = { it.mealType.name }) { group ->
                MealCard(
                    group = group,
                    isFavorite = state::isFavorite,
                    onAdd = { onAddEntry(state.date, group.mealType) },
                    onEdit = onEditEntry,
                    onDelete = ::deleteWithUndo,
                    onToggleFavorite = ::toggleFavorite,
                    onSaveCombo = { comboToSave = group },
                )
            }
        }
    }
}

/** Platzhalter in der Form von Ringen, Kacheln und Mahlzeiten, solange „Heute“ lädt. */
@Composable
private fun TodaySkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBlock(CircleShape, Modifier.fillMaxWidth(0.62f).aspectRatio(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { SkeletonBlock(RoundedCornerShape(14.dp), Modifier.weight(1f).height(56.dp)) }
        }
        repeat(3) { SkeletonBlock(RoundedCornerShape(6.dp), Modifier.fillMaxWidth().height(18.dp)) }
        repeat(MealType.entries.size) {
            SkeletonBlock(RoundedCornerShape(12.dp), Modifier.fillMaxWidth().height(48.dp))
        }
    }
}

/** Erfolgsmoment: Serie der Tage im Ziel bis gestern. */
@Composable
private fun StreakBanner(days: Int) {
    Surface(color = Neon.Green, contentColor = Neon.OnNeon, shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_streak_check), contentDescription = null, modifier = Modifier.size(32.dp))
            Column {
                Text(
                    pluralStringResource(R.plurals.streak_title, days, days),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(if (days == 1) R.string.streak_text_first else R.string.streak_text_more),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun GoalBanner(onSetGoal: () -> Unit) {
    GlassCard(strong = true) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.goal_banner_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSetGoal) { Text(stringResource(R.string.goal_set), color = Neon.Cyan) }
        }
    }
}

@Composable
private fun DateTitle(date: LocalDate, onClick: () -> Unit) {
    val today = LocalDate.now()
    val text = when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> date.format(dateFormatter)
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SummaryCard(
    summary: DaySummary,
    sex: Sex?,
    onActivityClick: () -> Unit,
) {
    val over = summary.remainingKcal < 0
    val budget = (summary.targets.kcal + summary.activityKcal).coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Außen Kalorien (Regenbogen), innen Protein, Kohlenhydrate, Fett.
        ConcentricRings(
            rings = listOf(
                RingSpec((summary.eaten.kcal / budget).toFloat(), Neon.Rainbow),
                RingSpec(ratio(summary.eaten.proteinG, summary.targets.proteinG), listOf(MacroColors.protein)),
                RingSpec(ratio(summary.eaten.carbsG, summary.targets.carbsG), listOf(MacroColors.carbs)),
                RingSpec(ratio(summary.eaten.fatG, summary.targets.fatG), listOf(MacroColors.fat)),
            ),
            // Kleiner als früher (0,82), damit die Mahlzeiten ohne viel Scrollen sichtbar sind.
            modifier = Modifier.fillMaxWidth(0.62f),
            // Schlankere Ringe → mehr Platz in der Mitte für Zahl und „Verbleibend“.
            strokeWidth = 12.dp,
            gap = 5.dp,
        ) {
            // Zahl zählt beim Eintragen weich mit, statt zu springen.
            val shownKcal by animateIntAsState(
                if (over) -summary.remainingKcal else summary.remainingKcal,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                label = "kcal",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shownKcal.format(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (over) Neon.Red else Neon.TextPrimary,
                )
                Text(
                    stringResource(if (over) R.string.kcal_over else R.string.kcal_remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = Neon.TextSecondary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeonPill(
                value = summary.targets.kcal.format(),
                label = stringResource(R.string.summary_target),
                color = Neon.Cyan,
                modifier = Modifier.weight(1f),
            )
            NeonPill(
                value = summary.eaten.kcal.format(),
                label = stringResource(R.string.summary_eaten),
                color = Neon.Orange,
                modifier = Modifier.weight(1f),
            )
            // Aktivität: Tippen öffnet die Übersicht (dort auch „Hinzufügen“).
            NeonPill(
                value = "+" + summary.activityKcal.format(),
                label = stringResource(R.string.summary_activity),
                color = Neon.Violet,
                modifier = Modifier.weight(1f),
                onClick = onActivityClick,
            )
        }

        TipSlot(Tip.ACTIVITY, caret = TipCaret.UP_END, after = Tip.MEAL)
        TipSlot(Tip.RINGS, condition = summary.eaten.kcal > 0, after = Tip.ACTIVITY)
        NutrientLegend(summary, sex)
    }
}

private fun ratio(eaten: Double, target: Int): Float = if (target > 0) (eaten / target).toFloat() else 0f

/** Farben der Makro-Ringe. */
private object MacroColors {
    val protein = Neon.Magenta
    val carbs = Neon.Yellow
    val fat = Neon.Green
}

/**
 * Legende der Ringe. Kompakt: Protein, Kohlenhydrate, Fett.
 * Ausführlich: Untereinträge direkt unter ihrem Hauptwert (Zucker → Kohlenhydrate,
 * gesättigte Fettsäuren → Fett), danach Ballaststoffe und Salz.
 */
@Composable
private fun NutrientLegend(summary: DaySummary, sex: Sex?) {
    var detailed by rememberSaveable { mutableStateOf(false) }
    val eaten = summary.eaten
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MacroLegend(stringResource(R.string.protein), eaten.proteinG, summary.targets.proteinG, MacroColors.protein)
        MacroLegend(stringResource(R.string.carbs), eaten.carbsG, summary.targets.carbsG, MacroColors.carbs)
        AnimatedVisibility(detailed) {
            SubNutrientRow(stringResource(R.string.nutrient_sugar), eaten.sugarG, MacroColors.carbs)
        }
        MacroLegend(stringResource(R.string.fat), eaten.fatG, summary.targets.fatG, MacroColors.fat)
        AnimatedVisibility(detailed) {
            SubNutrientRow(stringResource(R.string.nutrient_saturated_fat), eaten.saturatedFatG, MacroColors.fat)
        }
        AnimatedVisibility(detailed) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OtherNutrientRow(stringResource(R.string.nutrient_fiber), eaten.fiberG)
                OtherNutrientRow(stringResource(R.string.nutrient_salt), eaten.saltG)
                Text(
                    stringResource(R.string.micros_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
                MicroList(eaten.micros, sex, showMissing = true)
                Text(
                    stringResource(R.string.extra_nutrients_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.TextSecondary,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.small)
                .clickable { detailed = !detailed }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (detailed) R.string.nutrient_details_hide else R.string.nutrient_details_show),
                style = MaterialTheme.typography.labelLarge,
                color = Neon.TextSecondary,
            )
            Icon(
                if (detailed) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Neon.TextSecondary,
            )
        }
    }
}

/** „davon …“-Zeile unter einem Hauptwert: eingerückt, mit Linie in dessen Farbe. */
@Composable
private fun SubNutrientRow(label: String, grams: Double?, parentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(parentColor.copy(alpha = 0.7f)))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Neon.TextSecondary,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
        )
        Text(gramsText(grams), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
    }
}

/** Eigenständiger Nährwert ohne Ziel (Ballaststoffe, Salz) mit grauem Punkt. */
@Composable
private fun OtherNutrientRow(label: String, grams: Double?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Neon.TextSecondary))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text(gramsText(grams), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** „2,1 g“ oder „–“, wenn der Wert bei keinem Eintrag angegeben ist. */
@Composable
private fun gramsText(grams: Double?): String =
    grams?.let { stringResource(R.string.grams_value, it.format(1)) } ?: stringResource(R.string.value_missing)

/** Legende zu einem Makro-Ring: farbiger Punkt, Name, gegessen / Ziel. */
@Composable
private fun MacroLegend(label: String, eatenG: Double, targetG: Int, color: Color) {
    val over = targetG > 0 && eatenG > targetG * 1.05
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Text(
            stringResource(R.string.macro_progress_value, eatenG.format(), targetG.format()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (over) Neon.Red else Neon.TextPrimary,
        )
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MealCard(
    group: MealGroup,
    isFavorite: (FoodEntry) -> Boolean,
    onAdd: () -> Unit,
    onEdit: (FoodEntry) -> Unit,
    onDelete: (FoodEntry) -> Unit,
    onToggleFavorite: (FoodEntry) -> Unit,
    onSaveCombo: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        // Kopfzeile antippen = in diese Mahlzeit eintragen (statt eigenem „+“-Knopf).
        Surface(
            color = group.mealType.neonColor,
            contentColor = Neon.OnNeon,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.pressable(onAdd, RoundedCornerShape(12.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(group.mealType.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (group.entries.isNotEmpty()) {
                    Text(
                        stringResource(R.string.kcal_value, group.total.kcal.format()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.meal_menu_add),
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp).size(20.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.meal_add_hint),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                if (group.entries.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.meal_menu_add)) },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
                                onClick = { menu = false; onAdd() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.combo_save)) },
                                onClick = { menu = false; onSaveCombo() },
                            )
                        }
                    }
                }
            }
        }
        group.entries.forEach { entry ->
            EntryRow(
                entry = entry,
                isFavorite = isFavorite(entry),
                onClick = { onEdit(entry) },
                onDelete = { onDelete(entry) },
                onToggleFavorite = { onToggleFavorite(entry) },
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: FoodEntry,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val n = entry.nutrients
    val macros = stringResource(R.string.entry_macros, n.proteinG.format(), n.carbsG.format(), n.fatG.format())
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(entry.name) },
        supportingContent = {
            val unit = stringResource(entry.unit.labelRes)
            Text(entry.amount?.let { stringResource(R.string.entry_amount_prefix, it.format(), unit, macros) } ?: macros)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.kcal_value, n.kcal.format()), style = MaterialTheme.typography.labelLarge)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menuOpen = false; onClick() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add))
                            },
                            leadingIcon = {
                                Icon(if (isFavorite) Icons.Outlined.Star else Icons.Filled.Star, null)
                            },
                            onClick = { menuOpen = false; onToggleFavorite() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}

/** Offener Aktivitäts-Dialog; existing == null → neue Aktivität. */
private data class ActivityDialogState(val existing: ActivityEntry?)

/** Neon-Farbe je Mahlzeit. */
private val MealType.neonColor: Color
    get() = when (this) {
        MealType.BREAKFAST -> Neon.Orange
        MealType.LUNCH -> Neon.Green
        MealType.DINNER -> Neon.Blue
        MealType.SNACK -> Neon.Magenta
    }

@Composable
private fun ComboNameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.combo_save)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.combo_save_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.combo_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
