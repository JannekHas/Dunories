package de.kalorienapp.ui.today

import de.kalorienapp.ui.components.tabularNums
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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
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
import de.kalorienapp.R
import de.kalorienapp.ui.components.BehaviorTipSlot
import de.kalorienapp.domain.BehaviorTip
import de.kalorienapp.ui.components.LocalWeightEnabled
import kotlin.math.roundToInt
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.ui.components.LocalProteinOnly
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.domain.model.AmountUnit
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.ui.changelog.ChangelogDialog
import de.kalorienapp.ui.changelog.ChangelogUi
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
import de.kalorienapp.domain.RankedNutrient
import de.kalorienapp.domain.NutrientLimit
import de.kalorienapp.domain.NutrientLimits
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMMM", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onSetGoal: () -> Unit,
    /** Feedback geben (Symbol unten rechts, nur in der Testphase per Fernschalter). */
    onFeedback: (() -> Unit)? = null,
    /** Erfolgs-Karte antippen → Social-Reiter. */
    onOpenSocial: () -> Unit = {},
    onGoalEnded: (Long) -> Unit = {},
    /** Im Kalender angetippter Tag → hier anzeigen (danach [onDayShown]). */
    requestedDay: LocalDate? = null,
    onDayShown: () -> Unit = {},
    onAddEntry: (date: LocalDate, mealType: MealType?) -> Unit,
    onEditEntry: (FoodEntry) -> Unit,
    /** Makro/Nährwert angetippt → Einträge des Tages danach sortiert. */
    onNutrientClick: (date: LocalDate, nutrient: RankedNutrient) -> Unit = { _, _ -> },
    /** Gesetzt, wenn der Tag aus dem Kalender geöffnet wurde. */
    onBack: (() -> Unit)? = null,
    /** Ernährungsplan bearbeiten. */
    onOpenPlan: () -> Unit = {},
    /** Plan-Mahlzeit als bearbeitbaren Vorschlag öffnen („Anpassen“). */
    onAdjustPlan: (date: LocalDate, mealType: MealType, dayIndex: Int) -> Unit = { _, _, _ -> },
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val endedGoal by viewModel.endedGoal.collectAsStateWithLifecycle()
    val water by viewModel.water.collectAsStateWithLifecycle()
    val fasting by viewModel.fasting.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val waterEnabled by viewModel.waterEnabled.collectAsStateWithLifecycle()
    val proteinOnly = LocalProteinOnly.current
    val behaviorTip by viewModel.behaviorTip.collectAsStateWithLifecycle()
    val weightEnabled = LocalWeightEnabled.current
    val goalSkipped by viewModel.goalSkipped.collectAsStateWithLifecycle()
    // Nach einem Update einmal „Neu in Dunories“ zeigen (nur auf dem echten „Heute“, nicht im Kalender-Tag)
    val unseenChangelog by viewModel.unseenChangelog.collectAsStateWithLifecycle()
    val reward by viewModel.reward.collectAsStateWithLifecycle()
    val newAchievement by viewModel.newAchievement.collectAsStateWithLifecycle()
    if (onBack == null && unseenChangelog.isNotEmpty()) {
        ChangelogDialog(ChangelogUi.byCodes(unseenChangelog), onClose = viewModel::changelogRead)
    } else if (onBack == null) {
        reward?.let { RewardDialog(it, onClose = viewModel::rewardSeen) }
    }
    LaunchedEffect(requestedDay) {
        requestedDay?.let { viewModel.showDate(it); onDayShown() }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dragState = rememberEntryDragState()
    val listState = rememberLazyListState()
    // Neue Erfolgs-Karte erscheint oberhalb der Ringe → wer oben ist, sieht sie auch (Liste hält sonst die Position).
    LaunchedEffect(newAchievement) {
        if (newAchievement != null && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    val currentMeals by rememberUpdatedState(state.meals)
    dragState.mealEntries = { currentMeals.associate { it.mealType to it.entries } }
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
            // Ohne „Gewicht tracken“ endet das unsichtbare Ziel still (keine Feier mit kg).
            if (weightEnabled) onGoalEnded(goal.id)
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

    val movedMessage = stringResource(R.string.entry_moved)
    val mealLabels = MealType.entries.associateWith { stringResource(it.labelRes) }

    fun moveWithUndo(entry: FoodEntry, target: MealType, index: Int) {
        val previous = viewModel.move(entry, target, index)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = movedMessage.format(entry.name, mealLabels[target]),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreAll(previous)
        }
    }

    // Gleiches von eben wurde zusammengefasst → kurz sagen, mit „Rückgängig“ (dann doch einzeln).
    val mergedMessage = stringResource(R.string.entry_merged)
    val mergedUnits = AmountUnit.entries.associateWith { stringResource(it.labelRes) }
    LaunchedEffect(Unit) {
        viewModel.lastMerge.filterNotNull().collect { notice ->
            viewModel.mergeShown()
            // Eigener Scope: das Zurücksetzen der Meldung darf die Snackbar nicht abbrechen
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val amount = notice.merged.amount?.format(1)?.removeSuffix(",0").orEmpty()
                val result = snackbarHostState.showSnackbar(
                    message = mergedMessage.format(notice.merged.name, amount, mergedUnits[notice.merged.unit]),
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoMerge(notice)
            }
        }
    }

    // Ernährungsplan: „Wie geplant“ trägt sofort ein, mit „Rückgängig“.
    val planApplied = stringResource(R.string.plan_applied)
    val planDayApplied = stringResource(R.string.plan_day_applied)
    fun applyPlan(meals: List<MealType>) {
        viewModel.applyPlan(meals) { ids ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val message = if (meals.size == 1) planApplied.format(mealLabels[meals.first()]) else planDayApplied.format(meals.size)
                val result = snackbarHostState.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) viewModel.undoPlan(ids)
            }
        }
    }

    val planEnding = stringResource(R.string.plan_ending)
    fun endPlanWithUndo() {
        viewModel.endPlan()
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(planEnding, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoEndPlan()
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

    val showFeedback by viewModel.feedbackOnToday.collectAsStateWithLifecycle()
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
        val bottomPadding = if (onBack != null) 88.dp else 16.dp + LocalBottomBarPadding.current
        // Einträge per Gedrückt-Halten in eine andere Mahlzeit ziehen (Geste liegt über der ganzen Liste).
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .entryDragArea(state = dragState, onDrop = ::moveWithUndo),
        ) {
        EntryDragAutoScroll(dragState, listState, with(LocalDensity.current) { bottomPadding.toPx() })
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.loaded) {
                item(key = "skeleton") { TodaySkeleton() }
                return@LazyColumn
            }
            if (!state.hasGoal && state.isToday && !proteinOnly && weightEnabled && !goalSkipped) {
                item(key = "goal_banner") { GoalBanner(onSetGoal) }
            }
            newAchievement?.takeIf { state.isToday && onBack == null }?.let { a ->
                item(key = "achievement") {
                    AchievementBanner(
                        a,
                        onOpen = {
                            viewModel.achievementSeen(a)
                            onOpenSocial()
                        },
                        onDismiss = { viewModel.achievementSeen(a) },
                    )
                }
            }
            if (streak > 0 && state.isToday) {
                item(key = "streak") { StreakBanner(streak) }
            }
            state.summary?.let { summary ->
                item(key = "summary") {
                    Box {
                        SummaryCard(
                            summary = summary,
                            sex = state.sex,
                            onActivityClick = { showActivityList = true },
                            onNutrientClick = { onNutrientClick(state.date, it) },
                        )
                        // Testphase: Feedback-Symbol in der Ecke neben den Ringen, unter dem Pfeil „nächster Tag“
                        // (Wunsch des Nutzers – schwebend lag es über „+ Eintragen“).
                        if (onBack == null && onFeedback != null && showFeedback) {
                            FeedbackBubble(onFeedback, Modifier.align(Alignment.TopEnd))
                        }
                    }
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
            if (waterEnabled) {
                item(key = "water") {
                    WaterCard(water, onAdd = viewModel::addWater, onUndo = viewModel::undoWater, onGoal = viewModel::setWaterGoal)
                }
            }
            plan?.let { p ->
                val open = state.meals.filter { it.entries.isEmpty() && !p.byMeal[it.mealType].isNullOrEmpty() }.map { it.mealType }
                if (p.dayIndex != null || p.askToExtend) {
                    item(key = "plan") {
                        MealPlanCard(
                            plan = p,
                            openMeals = open.size,
                            onApplyDay = { applyPlan(open) },
                            onExtend = viewModel::extendPlan,
                            onEdit = onOpenPlan,
                            onEnd = ::endPlanWithUndo,
                        )
                    }
                }
            }
            item(key = "tip_meal") { TipSlot(Tip.MEAL, caret = TipCaret.DOWN_CENTER, after = Tip.ADD) }
            state.meals.forEach { group ->
            // Tipp nach Verhalten direkt über der betroffenen Mahlzeit (nur heute).
            val tip = behaviorTip?.takeIf { it.meal == group.mealType && state.isToday }
            if (tip != null) {
                item(key = "behavior_tip") {
                    BehaviorTipSlot(
                        tip = when (tip) {
                            is TodayBehaviorTip.Favorite -> BehaviorTip.FAVORITE
                            is TodayBehaviorTip.Combo -> BehaviorTip.COMBO
                            is TodayBehaviorTip.Move -> BehaviorTip.MOVE
                        },
                        text = when (tip) {
                            is TodayBehaviorTip.Favorite -> stringResource(R.string.btip_favorite, tip.entry.name.trim())
                            is TodayBehaviorTip.Combo -> stringResource(R.string.btip_combo)
                            is TodayBehaviorTip.Move -> stringResource(R.string.tip_move)
                        },
                        condition = true,
                        caret = TipCaret.DOWN_CENTER,
                        actionLabel = when (tip) {
                            is TodayBehaviorTip.Favorite -> stringResource(R.string.btip_favorite_action)
                            is TodayBehaviorTip.Combo -> stringResource(R.string.btip_combo_action)
                            is TodayBehaviorTip.Move -> null
                        },
                        onAction = {
                            when (tip) {
                                is TodayBehaviorTip.Favorite -> {
                                    viewModel.addFavorite(tip.entry)
                                    scope.launch { snackbarHostState.showSnackbar(favoriteAdded.format(tip.entry.name)) }
                                }
                                is TodayBehaviorTip.Combo -> comboToSave = group.copy(entries = tip.entries)
                                is TodayBehaviorTip.Move -> Unit
                            }
                        },
                    )
                }
            }
            item(key = group.mealType.name) {
                val dragged = dragState.entry
                MealCard(
                    modifier = Modifier
                        .trackMeal(dragState, group.mealType)
                        .dropHighlight(
                            active = dragged != null && dragState.targetMeal == group.mealType,
                            color = group.mealType.neonColor,
                        ),
                    dragState = dragState,
                    group = group,
                    isFavorite = state::isFavorite,
                    onAdd = { onAddEntry(state.date, group.mealType) },
                    onEdit = onEditEntry,
                    onDelete = ::deleteWithUndo,
                    onToggleFavorite = ::toggleFavorite,
                    onSaveCombo = { comboToSave = group },
                    planItems = plan?.byMeal?.get(group.mealType).orEmpty(),
                    onPlanApply = { applyPlan(listOf(group.mealType)) },
                    onPlanAdjust = { plan?.dayIndex?.let { onAdjustPlan(state.date, group.mealType, it) } },
                )
            }
            }
            // Verschieben-Tipp kommt jetzt nach Verhalten (gelöscht und woanders neu eingetragen), siehe oben.
        }
        EntryDragGhost(dragState) { it.neonColor }
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

/** Erfolg freigeschaltet (Social): kurz, ohne Vibration; antippen öffnet den Social-Reiter, ✕ schließt. */
@Composable
private fun AchievementBanner(
    achievement: de.kalorienapp.domain.Gamification.Achievement,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassCard(strong = true, onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            de.kalorienapp.ui.social.BadgeIcon(achievement.key, size = 40)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.achievement_unlocked), style = MaterialTheme.typography.labelMedium, color = Neon.Yellow)
                Text(
                    de.kalorienapp.ui.social.badgeTitle(achievement.key) + " · " +
                        stringResource(de.kalorienapp.ui.social.rarityName(achievement.rarity)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Clear, stringResource(R.string.goal_celebrate_close), tint = Neon.TextSecondary)
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
    onNutrientClick: (RankedNutrient) -> Unit,
) {
    if (LocalProteinOnly.current) {
        ProteinSummaryCard(summary, onNutrientClick)
        return
    }
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
                    style = MaterialTheme.typography.displaySmall.tabularNums(),
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
                onClick = { onNutrientClick(RankedNutrient.KCAL) },
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
        NutrientLegend(summary, sex, onNutrientClick)
    }
}

/**
 * „Nur Eiweiß“: ein einziger Eiweiß-Ring, in der Mitte „noch X g“ bzw. „geschafft“, darunter Ziel und
 * Gegessen. Keine Aktivitäten (die erhöhen nur das Kalorienbudget) und keine Makro-Legende.
 */
@Composable
private fun ProteinSummaryCard(summary: DaySummary, onNutrientClick: (RankedNutrient) -> Unit) {
    val target = summary.targets.proteinG
    val eaten = summary.eaten.proteinG
    val reached = target > 0 && eaten >= target
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConcentricRings(
            rings = listOf(RingSpec(ratio(eaten, target).coerceAtMost(1f), listOf(MacroColors.protein))),
            modifier = Modifier.fillMaxWidth(0.62f),
            strokeWidth = 22.dp,
        ) {
            val shown by animateIntAsState(
                if (reached) eaten.roundToInt() else (target - eaten).roundToInt().coerceAtLeast(0),
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                label = "protein",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.grams_value, shown.format()),
                    style = MaterialTheme.typography.displaySmall.tabularNums(),
                    fontWeight = FontWeight.Bold,
                    color = if (reached) Neon.Green else Neon.TextPrimary,
                )
                Text(
                    stringResource(if (reached) R.string.protein_reached else R.string.protein_remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = Neon.TextSecondary,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonPill(
                value = stringResource(R.string.grams_value, target.format()),
                label = stringResource(R.string.summary_target),
                color = Neon.Cyan,
                modifier = Modifier.weight(1f),
            )
            NeonPill(
                value = stringResource(R.string.grams_value, eaten.format()),
                label = stringResource(R.string.summary_eaten),
                color = MacroColors.protein,
                modifier = Modifier.weight(1f),
                onClick = { onNutrientClick(RankedNutrient.PROTEIN) },
            )
        }
        TipSlot(Tip.RINGS, condition = eaten > 0, after = Tip.MEAL, textRes = R.string.tip_rings_protein)
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
private fun NutrientLegend(summary: DaySummary, sex: Sex?, onClick: (RankedNutrient) -> Unit) {
    var detailed by rememberSaveable { mutableStateOf(false) }
    val eaten = summary.eaten
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MacroLegend(stringResource(R.string.protein), eaten.proteinG, summary.targets.proteinG, MacroColors.protein) { onClick(RankedNutrient.PROTEIN) }
        MacroLegend(stringResource(R.string.carbs), eaten.carbsG, summary.targets.carbsG, MacroColors.carbs) { onClick(RankedNutrient.CARBS) }
        AnimatedVisibility(detailed) {
            SubNutrientRow(stringResource(R.string.nutrient_sugar), eaten.sugarG, MacroColors.carbs, NutrientLimits.SUGAR) { onClick(RankedNutrient.SUGAR) }
        }
        MacroLegend(stringResource(R.string.fat), eaten.fatG, summary.targets.fatG, MacroColors.fat) { onClick(RankedNutrient.FAT) }
        AnimatedVisibility(detailed) {
            SubNutrientRow(stringResource(R.string.nutrient_saturated_fat), eaten.saturatedFatG, MacroColors.fat, NutrientLimits.saturatedFat(summary.targets.kcal)) { onClick(RankedNutrient.SATURATED_FAT) }
        }
        AnimatedVisibility(detailed) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OtherNutrientRow(stringResource(R.string.nutrient_fiber), eaten.fiberG, NutrientLimits.FIBER) { onClick(RankedNutrient.FIBER) }
                OtherNutrientRow(stringResource(R.string.nutrient_salt), eaten.saltG, NutrientLimits.SALT) { onClick(RankedNutrient.SALT) }
                // Gruppen-Überschriften (Vitamine, Mineralstoffe, Weitere) kommen aus der Liste selbst
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
private fun SubNutrientRow(label: String, grams: Double?, parentColor: Color, limit: NutrientLimit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(parentColor.copy(alpha = 0.7f)))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Neon.TextSecondary,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
        )
        Text(limitText(grams, limit), style = MaterialTheme.typography.bodySmall, color = limitColor(grams, limit, Neon.TextSecondary))
    }
}

/** „32,1 / 50 g“ – mit Richtwert, damit man sieht, wie viel noch geht. */
@Composable
private fun limitText(grams: Double?, limit: NutrientLimit): String =
    stringResource(R.string.limit_value, grams?.format(1) ?: stringResource(R.string.value_missing), limit.grams.toString())

/** Rot über einer Obergrenze, grün bei erreichtem Mindestziel. */
private fun limitColor(grams: Double?, limit: NutrientLimit, normal: Color): Color = when {
    limit.exceeded(grams) -> Neon.Red
    limit.reached(grams) -> Neon.Green
    else -> normal
}

/** Eigenständiger Nährwert ohne Ziel (Ballaststoffe, Salz) mit grauem Punkt. */
@Composable
private fun OtherNutrientRow(label: String, grams: Double?, limit: NutrientLimit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Neon.TextSecondary))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text(
            limitText(grams, limit),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = limitColor(grams, limit, Neon.TextPrimary),
        )
    }
}


/** Legende zu einem Makro-Ring: farbiger Punkt, Name, gegessen / Ziel. */
@Composable
private fun MacroLegend(label: String, eatenG: Double, targetG: Int, color: Color, onClick: () -> Unit) {
    val over = targetG > 0 && eatenG > targetG * 1.05
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Text(
            stringResource(R.string.macro_progress_value, eatenG.format(), targetG.format()),
            style = MaterialTheme.typography.bodyMedium.tabularNums(),
            fontWeight = FontWeight.Medium,
            color = if (over) Neon.Red else Neon.TextPrimary,
        )
    }
}


@Composable
private fun MealCard(
    modifier: Modifier,
    dragState: EntryDragState,
    group: MealGroup,
    isFavorite: (FoodEntry) -> Boolean,
    onAdd: () -> Unit,
    onEdit: (FoodEntry) -> Unit,
    onDelete: (FoodEntry) -> Unit,
    onToggleFavorite: (FoodEntry) -> Unit,
    onSaveCombo: () -> Unit,
    planItems: List<MealPlanItem> = emptyList(),
    onPlanApply: () -> Unit = {},
    onPlanAdjust: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    // Karte wächst/schrumpft weich, wenn beim Ziehen die Lücke auf- oder zugeht.
    GlassCard(modifier = modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy))) {
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
                        entryValueText(group.total),
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
        // Ernährungsplan: leere Mahlzeit → „Laut Plan: …“ mit „Wie geplant“ / „Anpassen“.
        if (group.entries.isEmpty() && planItems.isNotEmpty() && dragState.entry == null) {
            PlanSuggestionRow(planItems, group.mealType.neonColor, onPlanApply, onPlanAdjust)
        }
        // Gezogene Zeile zieht sich zusammen; an der Landestelle geht eine Lücke in der Farbe der Mahlzeit auf.
        val dragged = dragState.entry
        val visible = group.entries.filter { it.id != dragged?.id }
        val slotIndex = if (dragged != null && dragState.targetMeal == group.mealType) dragState.targetIndex else -1
        visible.forEachIndexed { i, entry ->
            if (i == slotIndex) DropSlot(dragState, group.mealType.neonColor)
            EntryRow(
                modifier = Modifier.trackEntry(dragState, entry.id),
                entry = entry,
                isFavorite = isFavorite(entry),
                onClick = { onEdit(entry) },
                onDelete = { onDelete(entry) },
                onToggleFavorite = { onToggleFavorite(entry) },
            )
        }
        if (slotIndex >= visible.size) DropSlot(dragState, group.mealType.neonColor)
    }
}

@Composable
private fun EntryRow(
    modifier: Modifier,
    entry: FoodEntry,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val n = entry.nutrients
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(entry.name) },
        supportingContent = { Text(entrySubtitle(entry)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // „≈“ = von der KI geschätzt (Foto/Beschreibung)
                Text(entryValueText(n, entry.isEstimate), style = MaterialTheme.typography.labelLarge)
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

/** Wert rechts an Einträgen und Mahlzeiten: „320 kcal“, im Modus „Nur Eiweiß“ „25 g Eiweiß“. */
@Composable
internal fun entryValueText(n: Nutrients, estimate: Boolean = false): String = when {
    LocalProteinOnly.current ->
        stringResource(if (estimate) R.string.protein_value_estimate else R.string.protein_value, n.proteinG.format())
    else -> stringResource(if (estimate) R.string.kcal_value_estimate else R.string.kcal_value, n.kcal.format())
}

/** „150 g · P 5 g · KH 4 g · F 1 g“ – Unterzeile eines Eintrags (Liste und Zieh-Karte); „Nur Eiweiß“: nur die Menge. */
@Composable
internal fun entrySubtitle(entry: FoodEntry): String {
    val n = entry.nutrients
    val unit = stringResource(entry.unit.labelRes)
    if (LocalProteinOnly.current) {
        val amount = entry.amount?.let { stringResource(R.string.amount_with_unit, it.format(), unit) }.orEmpty()
        return if (entry.isDish) stringResource(R.string.entry_dish_prefix, entry.components.size, amount).trimEnd(' ', '·') else amount
    }
    val macros = stringResource(R.string.entry_macros, n.proteinG.format(), n.carbsG.format(), n.fatG.format())
    val line = entry.amount?.let { stringResource(R.string.entry_amount_prefix, it.format(), unit, macros) } ?: macros
    // Gericht: „2 Zutaten · 450 g · P …“
    return if (entry.isDish) stringResource(R.string.entry_dish_prefix, entry.components.size, line) else line
}

/** Offener Aktivitäts-Dialog; existing == null → neue Aktivität. */
private data class ActivityDialogState(val existing: ActivityEntry?)

/** Neon-Farbe je Mahlzeit. */
val MealType.neonColor: Color
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

/** Kleiner runder Glas-Knopf „Feedback geben“ (nur Testphase, Fernschalter). */
@Composable
private fun FeedbackBubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Neon.SurfaceHighest.copy(alpha = 0.7f)),
    ) {
        Icon(painterResource(R.drawable.ic_feedback), stringResource(R.string.feedback_title), tint = Neon.Cyan, modifier = Modifier.size(22.dp))
    }
}

/** Tester-Belohnungsprogramm: Danke-Nachricht beim Start, wenn Feedback mit Punkten bewertet wurde. */
@Composable
private fun RewardDialog(status: de.kalorienapp.account.AiStatus, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(androidx.compose.ui.res.stringResource(R.string.reward_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    androidx.compose.ui.res.pluralStringResource(
                        R.plurals.reward_text, status.newRewards, status.newRewards, status.newPoints, status.testerPoints,
                    ),
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.reward_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = de.kalorienapp.ui.theme.Neon.TextSecondary,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onClose) { Text(androidx.compose.ui.res.stringResource(R.string.reward_ok)) }
        },
    )
}
