package de.kalorienapp.ui.calendar

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.ui.components.LocalProteinOnly
import de.kalorienapp.data.repository.StatsRepository
import de.kalorienapp.domain.StatsCalculator
import de.kalorienapp.domain.model.DayStats
import de.kalorienapp.domain.model.DayStatus
import de.kalorienapp.domain.model.PeriodStats
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.NeonPill
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val days: List<DayStats> = emptyList(),
    val summary: PeriodStats? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(statsRepository: StatsRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<CalendarUiState> = month
        .flatMapLatest { m ->
            statsRepository.days(m.atDay(1), m.atEndOfMonth()).map { days ->
                // Zusammenfassung nur bis heute – zukünftige Tage zählen nicht.
                val past = days.filter { !it.date.isAfter(LocalDate.now()) }
                CalendarUiState(m, days, StatsCalculator.period(past))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }
    fun currentMonth() = month.update { YearMonth.now() }
}

private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)

/** Monatskalender (Tab in der Statistik): Tage farbig nach Zielerreichung, Tippen öffnet den Tag. */
@Composable
fun CalendarSection(
    onDayClick: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previousMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_month))
            }
            Text(
                state.month.format(monthFormatter).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                // Antippen springt zurück zum aktuellen Monat (wie der Titel auf „Heute“)
                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable(onClick = viewModel::currentMonth),
            )
            IconButton(onClick = viewModel::nextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month))
            }
        }
        MonthGrid(state.month, state.days, onDayClick)
        Legend()
        state.summary?.let { MonthSummary(it) }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, days: List<DayStats>, onDayClick: (LocalDate) -> Unit) {
    val byDate = days.associateBy { it.date }
    val firstOffset = month.atDay(1).dayOfWeek.value - 1 // Montag = 0
    val cells = List(firstOffset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2),
                    style = MaterialTheme.typography.labelMedium,
                    color = Neon.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, byDate[date], onClick = { onDayClick(date) })
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, stats: DayStats?, onClick: () -> Unit) {
    val today = LocalDate.now()
    val future = date.isAfter(today)
    val color = if (future) Color.Transparent else statusColor(stats?.status ?: DayStatus.NO_DATA)
    val onNeon = !future && stats?.status != null && stats.status != DayStatus.NO_DATA && stats.status != DayStatus.PROTEIN_LOW
    // TalkBack: Datum + Bewertung vorlesen („21. Oktober, im Budget“) – die Farbe allein sieht man nicht.
    val statusText = if (future) null else stringResource(statusLabel(stats?.status ?: DayStatus.NO_DATA, LocalProteinOnly.current))
    val description = listOfNotNull(date.format(cellDateFormatter), statusText).joinToString(", ")
    Box(
        modifier = Modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .fillMaxSize()
            .clip(CircleShape)
            .background(color)
            .then(if (date == today) Modifier.border(2.dp, Neon.Cyan, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (onNeon) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    onNeon -> Neon.OnNeon
                    future -> Neon.TextSecondary.copy(alpha = 0.5f)
                    else -> Neon.TextSecondary
                },
            )
            // Zeichen zusätzlich zur Farbe – bei Rot-Grün-Schwäche sind die Farben kaum zu unterscheiden.
            if (!future) statusIcon(stats?.status ?: DayStatus.NO_DATA)?.let { icon ->
                Icon(icon, contentDescription = null, tint = Neon.OnNeon, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** ✓ im Budget/Protein erreicht, + knapp drüber, ↑ drüber; sonst kein Zeichen. */
private fun statusIcon(status: DayStatus): ImageVector? = when (status) {
    DayStatus.ON_TARGET -> Icons.Filled.Check
    DayStatus.SLIGHTLY_OVER -> Icons.Filled.Add
    DayStatus.OVER -> Icons.Filled.KeyboardArrowUp
    else -> null
}

private val cellDateFormatter = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)

private fun statusLabel(status: DayStatus, proteinOnly: Boolean): Int = when (status) {
    DayStatus.ON_TARGET -> if (proteinOnly) R.string.calendar_protein_reached else R.string.calendar_on_target
    DayStatus.SLIGHTLY_OVER -> R.string.calendar_slightly_over
    DayStatus.OVER -> R.string.calendar_over
    DayStatus.PROTEIN_PARTIAL -> R.string.a11y_protein_partial
    DayStatus.PROTEIN_LOW -> R.string.a11y_protein_low
    DayStatus.NO_DATA -> R.string.calendar_no_data
}

fun statusColor(status: DayStatus): Color = when (status) {
    DayStatus.ON_TARGET -> Neon.Green
    DayStatus.SLIGHTLY_OVER -> Neon.Yellow
    DayStatus.OVER -> Neon.Red
    DayStatus.PROTEIN_PARTIAL -> Neon.Yellow
    DayStatus.PROTEIN_LOW, DayStatus.NO_DATA -> Neon.Track
}

@Composable
private fun Legend() {
    if (LocalProteinOnly.current) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendItem(Neon.Green, stringResource(R.string.calendar_protein_reached), statusIcon(DayStatus.ON_TARGET))
            LegendItem(Neon.Yellow, stringResource(R.string.calendar_protein_partial))
            LegendItem(Neon.Track, stringResource(R.string.calendar_protein_low))
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        LegendItem(Neon.Green, stringResource(R.string.calendar_on_target), statusIcon(DayStatus.ON_TARGET))
        LegendItem(Neon.Yellow, stringResource(R.string.calendar_slightly_over), statusIcon(DayStatus.SLIGHTLY_OVER))
        LegendItem(Neon.Red, stringResource(R.string.calendar_over), statusIcon(DayStatus.OVER))
        LegendItem(Neon.Track, stringResource(R.string.calendar_no_data))
    }
}

@Composable
private fun LegendItem(color: Color, label: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Neon.OnNeon, modifier = Modifier.size(11.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Neon.TextSecondary, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun MonthSummary(summary: PeriodStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonPill(
            value = summary.loggedDays.toString(),
            label = stringResource(R.string.stats_logged_days),
            color = Neon.Cyan,
            modifier = Modifier.weight(1f),
        )
        NeonPill(
            value = summary.onTargetRate?.let { "${(it * 100).roundToInt()} %" } ?: "–",
            label = stringResource(if (LocalProteinOnly.current) R.string.stats_protein_reached else R.string.stats_on_target),
            color = Neon.Green,
            modifier = Modifier.weight(1f),
        )
        if (LocalProteinOnly.current) {
            NeonPill(
                value = summary.averageEaten?.proteinG?.let { it.format() + " g" } ?: "–",
                label = stringResource(R.string.stats_avg_protein),
                color = Neon.Magenta,
                modifier = Modifier.weight(1f),
            )
        } else {
            NeonPill(
                value = summary.averageEaten?.kcal?.format() ?: "–",
                label = stringResource(R.string.stats_avg_kcal),
                color = Neon.Orange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
