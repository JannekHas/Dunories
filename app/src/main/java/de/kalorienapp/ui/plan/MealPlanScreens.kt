package de.kalorienapp.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.MealPlanSchedule
import de.kalorienapp.domain.model.MealPlanSettings
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.sum
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.LocalBottomBarPadding
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.components.neonChipColors
import de.kalorienapp.ui.food.trimDecimalZero
import de.kalorienapp.ui.today.neonColor
import de.kalorienapp.ui.theme.Neon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d.M.", Locale.GERMAN)
private val longFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val shortFormatter = DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)
private const val MILLIS_PER_DAY = 86_400_000L

/** Datum, an dem Plan-Tag [dayIndex] im aktuellen Durchlauf liegt. */
private fun MealPlanSettings.dateOf(dayIndex: Int): LocalDate =
    MealPlanSchedule.cycleStart(this, LocalDate.now()).plusDays(dayIndex.toLong())

// ---------------------------------------------------------------- Profil → Reiter „Plan“

/** Schalter und Kurzstatus im Profil; „Plan bearbeiten“ öffnet die Plan-Übersicht. */
@Composable
fun MealPlanSettingsSection(
    onOpenPlan: () -> Unit,
    viewModel: MealPlanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.plan_enable), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.plan_enable_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = viewModel::setEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.OnNeon),
            )
        }
        if (!config.enabled) return@Column
        val today = LocalDate.now()
        val index = MealPlanSchedule.dayIndex(config, today)
        val status = when {
            index != null && config.stopAtEnd ->
                stringResource(R.string.plan_status_ending, index + 1, config.days, config.end.format(dayFormatter))
            index != null && config.openEnded -> stringResource(R.string.plan_status_open, index + 1, config.days)
            index != null -> stringResource(R.string.plan_status_until, index + 1, config.days, config.end.format(dayFormatter))
            MealPlanSchedule.isExpired(config, today) -> stringResource(R.string.plan_status_expired, config.end.format(dayFormatter))
            else -> stringResource(R.string.plan_status_before, config.start.format(dayFormatter))
        }
        GlassCard(Modifier.fillMaxWidth(), onClick = onOpenPlan) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.plan_edit), style = MaterialTheme.typography.titleMedium)
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = Neon.Cyan)
                    val planned = items.filter { it.dayIndex < config.days }.map { it.dayIndex }.distinct().size
                    Text(
                        stringResource(R.string.plan_days_filled, planned, config.days),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.TextSecondary,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

// ---------------------------------------------------------------- Übersicht

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    onBack: () -> Unit,
    onOpenDay: (Int) -> Unit,
    onImport: () -> Unit,
    viewModel: MealPlanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    if (pickEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = config.end.toEpochDay() * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { pickEnd = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { viewModel.setEnd(LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) }
                        pickEnd = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickEnd = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (pickStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = config.startEpochDay * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { pickStart = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { viewModel.setStart(LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) }
                        pickStart = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickStart = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Rhythmus (so viele Tage trägt man ein) und Laufzeit (wie lange er sich wiederholt) – getrennt,
            // damit klar ist: 30 Tage mit gleichem Wochenessen = nur 7 Tage eintragen (Tester-Feedback).
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.plan_days), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StepButton("−", enabled = config.days > 1) { viewModel.setDays(config.days - 1) }
                        Text(
                            pluralStringResource(R.plurals.plan_days_count, config.days, config.days),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        StepButton("+", enabled = config.days < MealPlanSettings.MAX_DAYS) { viewModel.setDays(config.days + 1) }
                    }
                    Text(
                        pluralStringResource(R.plurals.plan_rhythm_hint, config.days, config.days),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.TextSecondary,
                    )
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.plan_start), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickStart = true }) { Text(config.start.format(longFormatter)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.plan_runs_until), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickEnd = true }, enabled = !config.openEnded) {
                            Text(if (config.openEnded) stringResource(R.string.plan_open_ended) else config.end.format(longFormatter))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = config.openEnded,
                            onClick = { viewModel.setOpenEnded(!config.openEnded) },
                            label = { Text(stringResource(R.string.plan_open_ended)) },
                            colors = neonChipColors(),
                        )
                    }
                    Text(
                        stringResource(if (config.openEnded) R.string.plan_open_hint else R.string.plan_length_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.TextSecondary,
                    )
                }
            }
            // Import per Foto/Text
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_sparkle), contentDescription = null)
                Text(stringResource(R.string.plan_import), modifier = Modifier.padding(start = 8.dp))
            }
            // Tage
            val byDay = items.groupBy { it.dayIndex }
            val today = LocalDate.now()
            repeat(config.days) { day ->
                val (later, more) = MealPlanSchedule.laterDates(config, day, today)
                DayCard(
                    title = stringResource(R.string.plan_day_title, day + 1, config.dateOf(day).format(dayFormatter)),
                    repeats = later.takeIf { it.isNotEmpty() }?.let { dates ->
                        stringResource(R.string.plan_day_repeats, dates.joinToString { it.format(shortFormatter) } + if (more) " …" else "")
                    },
                    items = byDay[day].orEmpty(),
                    onClick = { onOpenDay(day) },
                )
            }
            if (config.openEnded || config.end.isAfter(config.dateOf(config.days - 1))) {
                Text(
                    stringResource(R.string.plan_repeat_after),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neon.Cyan,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.padding(bottom = LocalBottomBarPadding.current))
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
        modifier = Modifier.size(36.dp),
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
}

@Composable
private fun DayCard(title: String, repeats: String?, items: List<MealPlanItem>, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    Text(
                        stringResource(R.string.kcal_value, items.map { it.nutrients }.sum().kcal.format()),
                        style = MaterialTheme.typography.labelLarge,
                        color = Neon.Cyan,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            repeats?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Neon.TextSecondary) }
            if (items.isEmpty()) {
                Text(stringResource(R.string.plan_day_empty), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
            MealType.entries.forEach { meal ->
                val list = items.filter { it.mealType == meal }
                if (list.isEmpty()) return@forEach
                Row {
                    Text(
                        stringResource(meal.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = meal.neonColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(list.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Ein Tag

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MealPlanDayScreen(
    dayIndex: Int,
    onBack: () -> Unit,
    onAdd: (MealType) -> Unit,
    onEdit: (MealPlanItem) -> Unit,
    viewModel: MealPlanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val allItems by viewModel.items.collectAsStateWithLifecycle()
    val items = allItems.filter { it.dayIndex == dayIndex }
    var menu by remember { mutableStateOf(false) }
    var copyDialog by remember { mutableStateOf(false) }

    if (copyDialog) {
        AlertDialog(
            onDismissRequest = { copyDialog = false },
            title = { Text(stringResource(R.string.plan_copy_to)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(config.days) { day ->
                        if (day == dayIndex) return@repeat
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.copyDay(dayIndex, day); copyDialog = false },
                            label = { Text(stringResource(R.string.plan_day_short, day + 1)) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { copyDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_day_title, dayIndex + 1, config.dateOf(dayIndex).format(dayFormatter))) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plan_copy_day)) },
                            enabled = config.days > 1,
                            onClick = { menu = false; copyDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plan_clear_day)) },
                            onClick = { menu = false; viewModel.clearDay(dayIndex) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val total = items.map { it.nutrients }.sum()
            Text(
                stringResource(
                    R.string.plan_day_total,
                    total.kcal.format(),
                    total.proteinG.format(),
                    total.carbsG.format(),
                    total.fatG.format(),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = Neon.Cyan,
            )
            MealType.entries.forEach { meal ->
                PlanMealCard(
                    meal = meal,
                    items = items.filter { it.mealType == meal },
                    onAdd = { onAdd(meal) },
                    onEdit = onEdit,
                    onDelete = viewModel::delete,
                )
            }
        }
    }
}

@Composable
private fun PlanMealCard(
    meal: MealType,
    items: List<MealPlanItem>,
    onAdd: () -> Unit,
    onEdit: (MealPlanItem) -> Unit,
    onDelete: (MealPlanItem) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(meal.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = meal.neonColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (items.isNotEmpty()) {
                    Text(
                        stringResource(R.string.kcal_value, items.map { it.nutrients }.sum().kcal.format()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onEdit(item) }, modifier = Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            val amount = stringResource(
                                R.string.proposal_item_summary,
                                item.amount.format(1).trimDecimalZero(),
                                stringResource(item.unit.labelRes),
                                item.nutrients.kcal.format(),
                            )
                            Text(
                                if (item.components.isNotEmpty()) stringResource(R.string.entry_dish_prefix, item.components.size, amount) else amount,
                                style = MaterialTheme.typography.bodySmall,
                                color = Neon.TextSecondary,
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(item) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                }
            }
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = meal.neonColor)
                Text(stringResource(R.string.plan_add), color = meal.neonColor, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Kurzbeschreibung eines Plan-Tags für eine Mahlzeit: „Haferflocken, Milch“. */
internal fun List<MealPlanItem>.names(): String = joinToString { it.name }
