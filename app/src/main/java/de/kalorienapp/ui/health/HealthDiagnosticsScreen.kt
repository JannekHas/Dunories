package de.kalorienapp.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.health.HealthConnectManager
import de.kalorienapp.health.HealthDiagnosis
import de.kalorienapp.health.diagnose
import de.kalorienapp.ui.AppViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HealthDiagnosticsViewModel(private val healthConnect: HealthConnectManager) : ViewModel() {
    private val _diagnosis = MutableStateFlow<HealthDiagnosis?>(null)
    val diagnosis: StateFlow<HealthDiagnosis?> = _diagnosis.asStateFlow()

    val permissions: Set<String> get() = healthConnect.permissions
    fun permissionContract() = healthConnect.permissionContract()

    init {
        reload()
    }

    fun reload() {
        _diagnosis.value = null
        viewModelScope.launch { _diagnosis.value = healthConnect.diagnose() }
    }
}

/** Zeigt, welche Daten Health Connect wirklich liefert – zur Fehlersuche bei „Aktiv +0“. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: HealthDiagnosticsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val diagnosis by viewModel.diagnosis.collectAsStateWithLifecycle()
    @Suppress("DEPRECATION") // ClipboardManager reicht für reinen Text
    val clipboard = LocalClipboardManager.current
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.permissionContract()) { viewModel.reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.health_diag_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.health_diag_intro), style = MaterialTheme.typography.bodyMedium)
            val current = diagnosis
            if (current == null) {
                CircularProgressIndicator()
                return@Column
            }
            val report = current.toReport()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(report)) }) {
                    Text(stringResource(R.string.health_diag_copy))
                }
                OutlinedButton(onClick = viewModel::reload) { Text(stringResource(R.string.health_diag_reload)) }
            }
            if (!current.grantedPermissions.containsAll(viewModel.permissions)) {
                OutlinedButton(onClick = { permissionLauncher.launch(viewModel.permissions) }) {
                    Text(stringResource(R.string.health_diag_grant_all))
                }
            }
            SelectionContainer {
                Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
