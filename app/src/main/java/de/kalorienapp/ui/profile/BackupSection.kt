package de.kalorienapp.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.backup.BackupFormat
import de.kalorienapp.backup.BackupManager
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState

    /** Sicherung liegt bereit → teilen bzw. Speicherort wählen. */
    data class Ready(val file: File, val share: Boolean) : BackupState
    data object Saved : BackupState
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

    fun saveTo(file: File, target: Uri) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            _state.value = runCatching { backup.saveTo(file, target); BackupState.Saved }
                .getOrElse { BackupState.Failed(R.string.backup_error_create) }
        }
    }

    fun restore(uri: Uri) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            when (val result = backup.restore(uri)) {
                BackupManager.RestoreResult.Success -> backup.restartApp()
                is BackupManager.RestoreResult.Failed -> _state.value = BackupState.Failed(
                    if (result.problem == BackupFormat.Problem.NEWER_APP) R.string.backup_error_newer else R.string.backup_error_invalid,
                )
            }
        }
    }

    fun reset() {
        _state.value = BackupState.Idle
    }
}

/** Sicherung erstellen (teilen oder auf dem Gerät speichern) und wieder einspielen. */
@Composable
fun BackupSection(viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val ready = state as? BackupState.Ready
        if (uri != null && ready != null) viewModel.saveTo(ready.file, uri) else viewModel.reset()
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    // Sicherung fertig → Teilen-Menü bzw. „Speichern unter“ öffnen.
    LaunchedEffect(state) {
        val ready = state as? BackupState.Ready ?: return@LaunchedEffect
        if (ready.share) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready.file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, ready.file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.backup_share_title)))
            viewModel.reset()
        } else {
            saveLauncher.launch(ready.file.name)
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_text)) },
            confirmButton = {
                TextButton(onClick = { pendingRestore = null; viewModel.restore(uri) }) {
                    Text(stringResource(R.string.backup_restore_confirm), color = Neon.Red)
                }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.backup_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.create(share = true) },
                enabled = state != BackupState.Working,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.backup_share)) }
            OutlinedButton(
                onClick = { viewModel.create(share = false) },
                enabled = state != BackupState.Working,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.backup_save)) }
        }
        OutlinedButton(
            onClick = { openLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed", "*/*")) },
            enabled = state != BackupState.Working,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.backup_restore)) }
        when (val s = state) {
            BackupState.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.Cyan)
                Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodySmall)
            }
            BackupState.Saved -> Text(stringResource(R.string.backup_saved), style = MaterialTheme.typography.bodySmall, color = Neon.Green)
            is BackupState.Failed -> Text(stringResource(s.message), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
            else -> Unit
        }
    }
}
