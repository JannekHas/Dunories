package de.kalorienapp.ui.today

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.repository.HealthSyncStatus
import de.kalorienapp.domain.model.DaySummary
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.MacroProgress
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMMM", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onAddEntry: (date: LocalDate, mealType: MealType?) -> Unit,
    onEditEntry: (FoodEntry) -> Unit,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.entry_deleted)
    val undoLabel = stringResource(R.string.undo)
    val favoriteAdded = stringResource(R.string.favorite_added)
    val favoriteRemoved = stringResource(R.string.favorite_removed)

    val context = LocalContext.current
    val healthPermissionLauncher = rememberLauncherForActivityResult(viewModel.healthPermissionContract()) {
        viewModel.refreshActivity()
    }
    // Beim Öffnen/Zurückkehren aktive Kalorien neu lesen (z. B. nach einem Spaziergang).
    LifecycleResumeEffect(Unit) {
        viewModel.refreshActivity()
        onPauseOrDispose { }
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
                    IconButton(onClick = viewModel::previousDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_day))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::nextDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddEntry(state.date, null) }) {
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
            state.healthProblem?.let { problem ->
                item(key = "health") {
                    HealthConnectHint(
                        problem = problem,
                        onConnect = { healthPermissionLauncher.launch(viewModel.healthPermissions) },
                        onInstall = { context.openHealthConnectInStore() },
                        onRetry = viewModel::refreshActivity,
                    )
                }
            }
            state.summary?.let { summary ->
                item(key = "summary") { SummaryCard(summary, showActive = state.usesHealthConnect) }
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
private fun SummaryCard(summary: DaySummary, showActive: Boolean) {
    val over = summary.remainingKcal < 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (over) R.string.kcal_over else R.string.kcal_remaining),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    (if (over) -summary.remainingKcal else summary.remainingKcal).format(),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryValue(stringResource(R.string.summary_target), summary.targets.kcal.format())
                SummaryValue(stringResource(R.string.summary_eaten), summary.eaten.kcal.format())
                if (showActive) {
                    SummaryValue(stringResource(R.string.summary_active), "+" + summary.creditedActiveKcal.format())
                }
            }
            MacroProgress(stringResource(R.string.protein), summary.eaten.proteinG, summary.targets.proteinG)
            MacroProgress(stringResource(R.string.carbs), summary.eaten.carbsG, summary.targets.carbsG)
            MacroProgress(stringResource(R.string.fat), summary.eaten.fatG, summary.targets.fatG)
        }
    }
}

/** Hinweis, wenn Health Connect aktiviert ist, aber (noch) nicht gelesen werden kann. */
@Composable
private fun HealthConnectHint(
    problem: HealthSyncStatus,
    onConnect: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    when (problem) {
                        HealthSyncStatus.NO_PERMISSION -> R.string.health_hint_no_permission
                        HealthSyncStatus.NEEDS_INSTALL -> R.string.health_hint_needs_install
                        HealthSyncStatus.NOT_SUPPORTED -> R.string.health_hint_not_supported
                        HealthSyncStatus.ERROR, HealthSyncStatus.OK -> R.string.health_hint_error
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            when (problem) {
                HealthSyncStatus.NO_PERMISSION -> Button(onClick = onConnect) {
                    Text(stringResource(R.string.health_connect_button))
                }
                HealthSyncStatus.NEEDS_INSTALL -> Button(onClick = onInstall) {
                    Text(stringResource(R.string.health_install_button))
                }
                HealthSyncStatus.ERROR -> TextButton(onClick = onRetry) { Text(stringResource(R.string.capture_retry)) }
                HealthSyncStatus.NOT_SUPPORTED, HealthSyncStatus.OK -> Unit
            }
        }
    }
}

/** Öffnet Health Connect im Play Store (Android 13 und älter, falls nicht installiert/veraltet). */
private fun Context.openHealthConnectInStore() {
    val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(group.mealType.labelRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (group.entries.isNotEmpty()) {
                Text(
                    stringResource(R.string.kcal_value, group.total.kcal.format()),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(onClick = onAdd) { Text(stringResource(R.string.add_short)) }
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
            Box(Modifier.padding(start = 16.dp, bottom = 12.dp)) {
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
