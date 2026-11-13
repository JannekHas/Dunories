package de.kalorienapp.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.repository.WeeklyReportRepository
import de.kalorienapp.domain.model.WeeklyReport
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AllReportsViewModel(repository: WeeklyReportRepository) : ViewModel() {
    val reports: StateFlow<List<WeeklyReport>?> =
        repository.reports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
private val createdFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", Locale.GERMAN)

/** „erstellt am 13. Nov. 2026, 12:18 Uhr“ – damit man mehrere Auswertungen desselben Zeitraums unterscheidet. */
@Composable
fun createdText(report: WeeklyReport): String = stringResource(
    R.string.report_created,
    Instant.ofEpochMilli(report.createdAt).atZone(ZoneId.systemDefault()).format(createdFormatter),
)

/** Alle KI-Auswertungen, nach Monat (Ende des Zeitraums) gruppiert, neueste oben – jede einzeln antippbar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllReportsScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: AllReportsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_all_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val byMonth = reports.orEmpty().groupBy { YearMonth.from(it.to) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (reports?.isEmpty() == true) {
                item { Text(stringResource(R.string.report_empty), color = Neon.TextSecondary) }
            }
            byMonth.forEach { (month, list) ->
                item(key = "m$month") {
                    Text(
                        month.format(monthFormatter).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = Neon.Violet,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(list, key = { it.id }) { report ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(report.id) }) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(reportRange(report.from, report.to), style = MaterialTheme.typography.labelMedium, color = Neon.TextSecondary)
                            Text(
                                report.headline,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(createdText(report), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
