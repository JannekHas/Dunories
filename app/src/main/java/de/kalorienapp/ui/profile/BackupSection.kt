package de.kalorienapp.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.backup.BackupFormat
import de.kalorienapp.backup.BackupManager
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState

    /** Sicherung erstellt → teilen bzw. Speicherort wählen. */
    data class Ready(val created: BackupManager.Created, val share: Boolean) : BackupState

    /** Geteilt oder gespeichert – Übersicht bleibt sichtbar. */
    data class Done(val summary: BackupFormat.Summary, val saved: Boolean) : BackupState

    /** Datei geprüft → Übersicht zeigen und Bestätigung abwarten. */
    data class Confirm(val prepared: BackupManager.Prepared) : BackupState
    data class Failed(val message: Int) : BackupState
}

class BackupViewModel(private val backup: BackupManager) : ViewModel() {
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun create(share: Boolean) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            _state.value = runCatching { BackupState.Ready(backup.create(), share) }
                .getOrElse { BackupState.Failed(R.string.backup_error_create) }
        }
    }

    fun shared(created: BackupManager.Created) {
        _state.value = BackupState.Done(created.summary, saved = false)
    }

    fun saveTo(created: BackupManager.Created, target: Uri?) {
        if (target == null) {
            _state.value = BackupState.Idle
            return
        }
        _state.value = BackupState.Working
        viewModelScope.launch {
            _state.value = runCatching { backup.saveTo(created.file, target); BackupState.Done(created.summary, saved = true) }
                .getOrElse { BackupState.Failed(R.string.backup_error_create) }
        }
    }

    fun prepareRestore(uri: Uri) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            _state.value = when (val result = backup.prepareRestore(uri)) {
                is BackupManager.PrepareResult.Ready -> BackupState.Confirm(result.prepared)
                is BackupManager.PrepareResult.Failed -> BackupState.Failed(
                    if (result.problem == BackupFormat.Problem.NEWER_APP) R.string.backup_error_newer else R.string.backup_error_invalid,
                )
            }
        }
    }

    fun confirmRestore(prepared: BackupManager.Prepared) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            runCatching { backup.applyRestore(prepared) }
                .onSuccess { backup.restartApp() }
                .onFailure { _state.value = BackupState.Failed(R.string.backup_error_invalid) }
        }
    }

    fun cancelRestore() {
        backup.discardRestore()
        _state.value = BackupState.Idle
    }
}

private val dayFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", Locale.GERMAN)

/** Inhalt einer Sicherung in Kurzform: Zeitraum und Anzahl. */
@Composable
fun BackupSummaryText(summary: BackupFormat.Summary) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        summary.createdAt?.let {
            Text(
                stringResource(R.string.backup_summary_created, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)),
                style = MaterialTheme.typography.bodySmall,
                color = Neon.TextSecondary,
            )
        }
        val first = summary.firstDay
        val last = summary.lastDay
        Text(
            if (first != null && last != null) {
                stringResource(R.string.backup_summary_range, first.format(dayFormatter), last.format(dayFormatter))
            } else {
                stringResource(R.string.backup_summary_no_diary)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Neon.Cyan,
        )
        val parts = listOfNotNull(
            pluralStringResource(R.plurals.backup_entries, summary.foodEntries.toInt(), summary.foodEntries.toInt()),
            pluralStringResource(R.plurals.backup_recipes, summary.recipes.toInt(), summary.recipes.toInt()),
            pluralStringResource(R.plurals.backup_weights, summary.weights.toInt(), summary.weights.toInt()),
            summary.measurements.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_measurements, it.toInt(), it.toInt()) },
            summary.shoppingItems.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_shopping, it.toInt(), it.toInt()) },
            summary.reports.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.backup_reports, it.toInt(), it.toInt()) },
        )
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    }
}

/** Sicherung erstellen (teilen oder auf dem Gerät speichern) und wieder einspielen. */
@Composable
fun BackupSection(viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        (state as? BackupState.Ready)?.let { viewModel.saveTo(it.created, uri) }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.prepareRestore(uri)
    }

    // Sicherung fertig → Teilen-Menü bzw. „Speichern unter“ öffnen.
    LaunchedEffect(state) {
        val ready = state as? BackupState.Ready ?: return@LaunchedEffect
        val file = ready.created.file
        if (ready.share) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.backup_share_title)))
            viewModel.shared(ready.created)
        } else {
            saveLauncher.launch(file.name)
        }
    }

    (state as? BackupState.Confirm)?.let { confirm ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassCard(strong = true) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.backup_restore_contains), style = MaterialTheme.typography.labelLarge)
                            BackupSummaryText(confirm.prepared.summary)
                        }
                    }
                    Text(stringResource(R.string.backup_restore_text))
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore(confirm.prepared) }) {
                    Text(stringResource(R.string.backup_restore_confirm), color = Neon.Red)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.cancel)) } },
        )
    }

    val busy = state == BackupState.Working
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.backup_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.create(share = true) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.backup_share)) }
            OutlinedButton(
                onClick = { viewModel.create(share = false) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.backup_save)) }
        }
        OutlinedButton(
            onClick = { openLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed", "*/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.backup_restore)) }
        when (val s = state) {
            BackupState.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.Cyan)
                Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodySmall)
            }
            is BackupState.Done -> GlassCard(strong = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(if (s.saved) R.string.backup_saved else R.string.backup_created),
                        style = MaterialTheme.typography.labelLarge,
                        color = Neon.Green,
                    )
                    BackupSummaryText(s.summary)
                }
            }
            is BackupState.Failed -> Text(stringResource(s.message), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
            else -> Unit
        }
    }
}
