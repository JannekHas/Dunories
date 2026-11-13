package de.kalorienapp.ui.stats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import de.kalorienapp.data.AppSettings
import de.kalorienapp.domain.model.FoodPreferences
import de.kalorienapp.domain.model.FoodSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import de.kalorienapp.ui.components.GlassCard
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
    onShowAll: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
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
                // Kurz halten: nur die zwei vorherigen, alle weiteren auf der eigenen Seite (nach Monaten)
                reports.drop(1).take(MAX_OLDER).forEach { report ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpen(report) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        Text(
                            reportRange(report.from, report.to) + " · " + report.headline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neon.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(createdText(report), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                    }
                }
                TextButton(onClick = onShowAll, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.report_show_all, reports.size))
                }
            }
        }
    }
}

private const val MAX_OLDER = 2

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
    private val settings: AppSettings,
) : ViewModel() {
    private val id: Long = savedStateHandle["id"] ?: 0L

    val report: StateFlow<WeeklyReport?> =
        repository.report(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val preferences: StateFlow<FoodPreferences> = settings.foodPreferences

    private val _regeneration = MutableStateFlow<ReportGeneration>(ReportGeneration.Idle)
    val regeneration: StateFlow<ReportGeneration> = _regeneration.asStateFlow()

    fun dislike(name: String) = settings.addDislike(name)

    /** Gleicher Zeitraum, neu ausgewertet mit den aktuellen Vorlieben – ersetzt die Auswertung. */
    fun regenerate() {
        val r = report.value ?: return
        if (_regeneration.value == ReportGeneration.Running) return
        _regeneration.value = ReportGeneration.Running
        viewModelScope.launch {
            _regeneration.value = runReport { repository.generate(r.from, r.to, replaceId = r.id) }
        }
    }

    /** Export-Text für die eigene KI (Überblick + alle Einträge + Auswertung). */
    fun exportText(onReady: (String) -> Unit) {
        val r = report.value ?: return
        viewModelScope.launch { runCatching { repository.exportText(r) }.onSuccess(onReady) }
    }

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
    onEditPreferences: () -> Unit,
    viewModel: WeeklyReportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val regeneration by viewModel.regeneration.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.export_copied)
    val shareTitle = stringResource(R.string.export_share_title)

    if (showExport) {
        ExportDialog(
            onCopy = {
                showExport = false
                viewModel.exportText { text ->
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText(shareTitle, text))
                    scope.launch { snackbar.showSnackbar(copied) }
                }
            },
            onShare = {
                showExport = false
                viewModel.exportText { text ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }
            },
            onDismiss = { showExport = false },
        )
    }

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
                        IconButton(onClick = { showExport = true }) { Icon(Icons.Filled.Share, stringResource(R.string.export_title)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val r = report ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(reportRange(r.from, r.to), style = MaterialTheme.typography.labelLarge, color = Neon.TextSecondary)
                Text(createdText(r), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
            Text(r.headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Neon.Violet)
            Text(r.summary, style = MaterialTheme.typography.bodyLarge)
            if (r.wins.isNotEmpty()) BulletSection(stringResource(R.string.report_wins), r.wins, Neon.Green)
            if (r.improvements.isNotEmpty()) BulletSection(stringResource(R.string.report_improvements), r.improvements, Neon.Yellow)
            if (r.tips.isNotEmpty()) {
                Text(stringResource(R.string.report_tips), style = MaterialTheme.typography.titleMedium)
                r.tips.forEachIndexed { index, tip ->
                    GlassCard(Modifier.fillMaxWidth()) {
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
            if (r.foodsMore.isNotEmpty()) {
                FoodSection(stringResource(R.string.report_foods_more), r.foodsMore, Neon.Green) { food ->
                    // „Mag ich nicht“ → wird gemerkt und künftig nicht mehr vorgeschlagen
                    if (preferences.dislikes(food.name)) {
                        Text(stringResource(R.string.report_disliked), style = MaterialTheme.typography.labelMedium, color = Neon.TextSecondary)
                    } else {
                        TextButton(onClick = { viewModel.dislike(food.name) }) {
                            Text(stringResource(R.string.report_dislike), color = Neon.Orange)
                        }
                    }
                }
            }
            if (r.foodsLess.isNotEmpty()) {
                FoodSection(stringResource(R.string.report_foods_less), r.foodsLess, Neon.Orange) {}
            }
            PreferencesCard(
                preferences = preferences,
                regeneration = regeneration,
                onEdit = onEditPreferences,
                onRegenerate = viewModel::regenerate,
            )
            r.goalNote?.let { note ->
                GlassCard(strong = true, modifier = Modifier.fillMaxWidth()) {
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
private fun FoodSection(
    title: String,
    foods: List<FoodSuggestion>,
    color: Color,
    action: @Composable (FoodSuggestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = color)
        foods.forEach { food ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(food.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (food.reason.isNotBlank()) {
                            Text(food.reason, style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                        }
                    }
                    action(food)
                }
            }
        }
    }
}

/** Gezielte Antworten statt freiem Chat: Ernährungsweise wählen, „mag ich nicht“ verwalten, neu auswerten. */
/** Vorlieben kompakt: Zusammenfassung + „Bearbeiten“ (eigene Seite in Ebenen) + neu auswerten. */
@Composable
private fun PreferencesCard(
    preferences: FoodPreferences,
    regeneration: ReportGeneration,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
) {
    GlassCard(strong = true, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.prefs_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Neon.Violet,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) { Text(stringResource(R.string.prefs_edit)) }
            }
            Text(preferencesSummary(preferences), style = MaterialTheme.typography.bodyMedium)
            when (regeneration) {
                ReportGeneration.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.report_running), style = MaterialTheme.typography.bodyMedium)
                    AiWaitProgress(expectedSeconds = 20, color = Neon.Violet)
                }
                else -> Button(
                    onClick = onRegenerate,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Violet, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null)
                    Text(stringResource(R.string.prefs_regenerate), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (regeneration is ReportGeneration.Failed) {
                Text(stringResource(regeneration.error.messageRes), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
            }
        }
    }
}

@Composable
private fun ExportDialog(onCopy: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = { Text(stringResource(R.string.export_text)) },
        confirmButton = { TextButton(onClick = onCopy) { Text(stringResource(R.string.export_copy)) } },
        dismissButton = { TextButton(onClick = onShare) { Text(stringResource(R.string.export_share)) } },
    )
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
