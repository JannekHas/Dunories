package de.kalorienapp.ui.today

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
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
import de.kalorienapp.R
import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.ConcentricRings
import de.kalorienapp.ui.components.NeonPill
import de.kalorienapp.ui.components.RingSpec
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.goal.GoalResultDialog
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
    onAddEntry: (date: LocalDate, mealType: MealType?) -> Unit,
    onEditEntry: (FoodEntry) -> Unit,
    /** Gesetzt, wenn der Tag aus dem Kalender geöffnet wurde. */
    onBack: (() -> Unit)? = null,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val endedGoal by viewModel.endedGoal.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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

    endedGoal?.let { goal ->
        GoalResultDialog(
            goal = goal,
            onNewGoal = { viewModel.dismissEndedGoal(); onSetGoal() },
            onDismiss = viewModel::dismissEndedGoal,
        )
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddEntry(state.date, null) },
                containerColor = Neon.Yellow,
                contentColor = Neon.OnNeon,
            ) {
                Icon(painterResource(R.drawable.ic_camera), stringResource(R.string.add_entry))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.hasGoal && state.isToday) {
                item(key = "goal_banner") { GoalBanner(onSetGoal) }
            }
            state.summary?.let { summary ->
                item(key = "summary") {
                    SummaryCard(
                        summary = summary,
                        onActivityClick = { showActivityList = true },
                        onAddActivity = { showActivityList = true },
                    )
                }
            }
            items(state.meals, key = { it.mealType.name }) { group ->
                MealCard(
                    group = group,
                    isFavorite = state::isFavorite,
                    onAdd = { onAddEntry(state.date, group.mealType) },
                    onEdit = onEditEntry,
                    onDelete = ::deleteWithUndo,
                    onToggleFavorite = ::toggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun GoalBanner(onSetGoal: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Neon.SurfaceHigh)) {
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
    onActivityClick: () -> Unit,
    onAddActivity: () -> Unit,
) {
    val over = summary.remainingKcal < 0
    val budget = (summary.targets.kcal + summary.activityKcal).coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Außen Kalorien (Regenbogen), innen Protein, Kohlenhydrate, Fett.
        ConcentricRings(
            rings = listOf(
                RingSpec((summary.eaten.kcal / budget).toFloat(), Neon.Rainbow),
                RingSpec(ratio(summary.eaten.proteinG, summary.targets.proteinG), listOf(MacroColors.protein)),
                RingSpec(ratio(summary.eaten.carbsG, summary.targets.carbsG), listOf(MacroColors.carbs)),
                RingSpec(ratio(summary.eaten.fatG, summary.targets.fatG), listOf(MacroColors.fat)),
            ),
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    (if (over) -summary.remainingKcal else summary.remainingKcal).format(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (over) Neon.Red else Neon.TextPrimary,
                )
                Text(
                    stringResource(if (over) R.string.kcal_over else R.string.kcal_remaining),
                    style = MaterialTheme.typography.labelLarge,
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
            // Aktivität: Tippen oder „+“ öffnen die Übersicht der Aktivitäten.
            NeonPill(
                value = "+" + summary.activityKcal.format(),
                label = stringResource(R.string.summary_activity),
                color = Neon.Violet,
                modifier = Modifier.weight(1f),
                onClick = onActivityClick,
            )
            FilledIconButton(
                onClick = onAddActivity,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Neon.Violet,
                    contentColor = Neon.OnNeon,
                ),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.activity_add_title))
            }
        }

        NutrientLegend(summary)
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
private fun NutrientLegend(summary: DaySummary) {
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
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Surface(color = group.mealType.neonColor, contentColor = Neon.OnNeon, shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
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
                }
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_short))
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
        if (group.entries.isEmpty()) {
            Box(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 12.dp)) {
                Text(
                    stringResource(R.string.meal_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
