package de.kalorienapp.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.domain.NutrientRanking
import de.kalorienapp.domain.RankedEntry
import de.kalorienapp.domain.RankedNutrient
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.components.neonChipColors
import de.kalorienapp.ui.food.trimDecimalZero
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayNutrientsUiState(
    val date: LocalDate,
    val nutrient: RankedNutrient,
    val entries: List<RankedEntry> = emptyList(),
    val total: Double? = null,
    val loaded: Boolean = false,
)

/** Alle Einträge eines Tages, sortiert nach einem Nährwert (aus „Heute“ → Makro antippen). */
class DayNutrientsViewModel(
    savedStateHandle: SavedStateHandle,
    repository: FoodEntryRepository,
) : ViewModel() {
    private val date: LocalDate = LocalDate.ofEpochDay(savedStateHandle["epochDay"] ?: LocalDate.now().toEpochDay())
    private val nutrient = MutableStateFlow(RankedNutrient.fromRouteArg(savedStateHandle["nutrient"]))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DayNutrientsUiState> =
        combine(repository.entriesFor(date), nutrient) { entries, n ->
            DayNutrientsUiState(date, n, NutrientRanking.rank(entries, n), NutrientRanking.total(entries, n), loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayNutrientsUiState(date, nutrient.value))

    fun select(n: RankedNutrient) {
        nutrient.value = n
    }
}

private val titleDate = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayNutrientsScreen(
    onBack: () -> Unit,
    onEditEntry: (FoodEntry) -> Unit,
    viewModel: DayNutrientsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.day_nutrients_title))
                        Text(
                            if (state.date == LocalDate.now()) stringResource(R.string.today)
                            else state.date.format(titleDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = Neon.TextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "chips") {
                // Gewählter Nährwert ist beim Öffnen sichtbar (z. B. „Salz“ ganz rechts)
                val chips = rememberLazyListState(initialFirstVisibleItemIndex = (state.nutrient.ordinal - 1).coerceAtLeast(0))
                LaunchedEffect(state.nutrient) { chips.animateScrollToItem((state.nutrient.ordinal - 1).coerceAtLeast(0)) }
                LazyRow(
                    state = chips,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(RankedNutrient.entries) { n ->
                        FilterChip(
                            selected = n == state.nutrient,
                            onClick = { viewModel.select(n) },
                            label = { Text(stringResource(n.chipRes)) },
                            colors = neonChipColors(),
                        )
                    }
                }
            }
            item(key = "total") {
                Text(
                    stringResource(
                        R.string.day_nutrients_total,
                        stringResource(state.nutrient.chipRes),
                        state.total?.let { valueText(state.nutrient, it) } ?: stringResource(R.string.value_missing),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (state.loaded && state.entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.day_nutrients_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.entries, key = { it.entry.id }) { ranked ->
                RankedEntryRow(ranked, state.nutrient, onClick = { onEditEntry(ranked.entry) })
            }
        }
    }
}

@Composable
private fun RankedEntryRow(ranked: RankedEntry, nutrient: RankedNutrient, onClick: () -> Unit) {
    val entry = ranked.entry
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onClick) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val amount = entry.amount?.let { "${it.format(1).trimDecimalZero()} ${stringResource(entry.unit.labelRes)} · " }.orEmpty()
                    Text(
                        amount + stringResource(entry.mealType.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.TextSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        ranked.value?.let { valueText(nutrient, it) } ?: stringResource(R.string.value_missing),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (ranked.value != null) {
                        Text(
                            stringResource(R.string.percent_value, (ranked.share * 100).format()),
                            style = MaterialTheme.typography.bodySmall,
                            color = Neon.TextSecondary,
                        )
                    }
                }
            }
            // Anteil an der Tagessumme als Balken
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Neon.Track)) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(ranked.share.toFloat().coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp)).background(nutrient.color),
                )
            }
        }
    }
}

@Composable
private fun valueText(nutrient: RankedNutrient, value: Double): String = when (nutrient) {
    RankedNutrient.KCAL -> stringResource(R.string.kcal_value, value.format())
    RankedNutrient.SALT -> stringResource(R.string.grams_value, value.format(2).trimDecimalZero())
    else -> stringResource(R.string.grams_value, value.format(1).trimDecimalZero())
}

val RankedNutrient.chipRes: Int
    get() = when (this) {
        RankedNutrient.KCAL -> R.string.rank_kcal
        RankedNutrient.FAT -> R.string.fat
        RankedNutrient.SATURATED_FAT -> R.string.rank_saturated_fat
        RankedNutrient.CARBS -> R.string.carbs
        RankedNutrient.SUGAR -> R.string.rank_sugar
        RankedNutrient.FIBER -> R.string.nutrient_fiber
        RankedNutrient.PROTEIN -> R.string.protein
        RankedNutrient.SALT -> R.string.nutrient_salt
    }

private val RankedNutrient.color: Color
    get() = when (this) {
        RankedNutrient.KCAL -> Neon.Orange
        RankedNutrient.FAT, RankedNutrient.SATURATED_FAT -> Neon.Green
        RankedNutrient.CARBS, RankedNutrient.SUGAR -> Neon.Yellow
        RankedNutrient.PROTEIN -> Neon.Magenta
        RankedNutrient.FIBER, RankedNutrient.SALT -> Neon.Cyan
    }
