package de.kalorienapp.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import de.kalorienapp.ui.components.AiWaitProgress
import de.kalorienapp.data.repository.WeeklyReportRepository
import de.kalorienapp.domain.model.WeeklyReport
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rangeFormatter = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

@Composable
fun reportRange(from: LocalDate, to: LocalDate): String =
    stringResource(R.string.report_range, from.format(rangeFormatter), to.format(rangeFormatter))

/** Bereich „KI-Wochenauswertung“ in der Statistik: neueste Auswertung, Knopf, ältere Auswertungen. */
@Composable
fun WeeklyReportSection(
    reports: List<WeeklyReport>,
    generation: ReportGeneration,
    onGenerate: () -> Unit,
    onOpen: (WeeklyReport) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Neon.SurfaceHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = Neon.Violet, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(R.string.report_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            val latest = reports.firstOrNull()
            if (latest == null) {
                Text(stringResource(R.string.report_empty), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
            } else {
                // Eigene klickbare Fläche statt TextButton – dessen Form schnitt den Text am Rand ab.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Neon.SurfaceHighest)
                        .clickable { onOpen(latest) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(reportRange(latest.from, latest.to), style = MaterialTheme.typography.labelMedium, color = Neon.TextSecondary)
                    Text(latest.headline, style = MaterialTheme.typography.titleMedium, color = Neon.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(latest.summary, style = MaterialTheme.typography.bodyMedium, color = Neon.TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            when (generation) {
                ReportGeneration.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.report_running), style = MaterialTheme.typography.bodyMedium)
                    AiWaitProgress(expectedSeconds = 20, color = Neon.Violet)
                }
                else -> Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Violet, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.report_generate), fontWeight = FontWeight.Bold) }
            }
            if (generation is ReportGeneration.Failed) {
                Text(stringResource(generation.error.messageRes), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
            }
            Text(stringResource(R.string.report_auto_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            if (reports.size > 1) {
                HorizontalDivider()
                Text(stringResource(R.string.report_older), style = MaterialTheme.typography.labelLarge)
                reports.drop(1).take(MAX_OLDER).forEach { report ->
                    TextButton(onClick = { onOpen(report) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            reportRange(report.from, report.to) + " · " + report.headline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neon.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_OLDER = 8

private val ReportError.messageRes: Int
    get() = when (this) {
        ReportError.NOT_ENOUGH_DATA -> R.string.report_error_data
        ReportError.NO_API_KEY -> R.string.error_ai_no_key
        ReportError.QUOTA -> R.string.error_ai_quota
        ReportError.OVERLOADED -> R.string.error_ai_overloaded
        ReportError.NETWORK -> R.string.error_network
        ReportError.INVALID -> R.string.error_ai_invalid
    }

class WeeklyReportViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: WeeklyReportRepository,
) : ViewModel() {
    private val id: Long = savedStateHandle["id"] ?: 0L

    val report: StateFlow<WeeklyReport?> =
        repository.report(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDone: () -> Unit) {
        val report = report.value ?: return
        viewModelScope.launch {
            repository.delete(report)
            onDone()
        }
    }
}

/** Eine KI-Auswertung im Detail: Gesamteindruck, Erfolge, Verbesserungen, Tipps, Einschätzung zum Ziel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    onBack: () -> Unit,
    viewModel: WeeklyReportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.report_delete_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (report != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                },
            )
        },
    ) { padding ->
        val r = report ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(reportRange(r.from, r.to), style = MaterialTheme.typography.labelLarge, color = Neon.TextSecondary)
            Text(r.headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Neon.Violet)
            Text(r.summary, style = MaterialTheme.typography.bodyLarge)
            if (r.wins.isNotEmpty()) BulletSection(stringResource(R.string.report_wins), r.wins, Neon.Green)
            if (r.improvements.isNotEmpty()) BulletSection(stringResource(R.string.report_improvements), r.improvements, Neon.Yellow)
            if (r.tips.isNotEmpty()) {
                Text(stringResource(R.string.report_tips), style = MaterialTheme.typography.titleMedium)
                r.tips.forEachIndexed { index, tip ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(Neon.Rainbow[(index + 4) % Neon.Rainbow.size]),
                                contentAlignment = Alignment.Center,
                            ) { Text("${index + 1}", color = Neon.OnNeon, fontWeight = FontWeight.Bold) }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(tip.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(tip.text, style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
                            }
                        }
                    }
                }
            }
            r.goalNote?.let { note ->
                Card(colors = CardDefaults.cardColors(containerColor = Neon.SurfaceHigh), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.report_goal), style = MaterialTheme.typography.titleSmall, color = Neon.Cyan)
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(stringResource(R.string.report_disclaimer), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }
    }
}

@Composable
private fun BulletSection(title: String, items: List<String>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = color)
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(color))
                Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}
