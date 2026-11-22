package de.kalorienapp.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.backup.BackupFormat
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.theme.Neon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val backupTime = DateTimeFormatter.ofPattern("d. MMM, HH:mm", Locale.GERMAN)

/**
 * Konto-Karte ganz oben in Profil → Einstellungen (Phase 2): freiwillig, ohne Konto bleibt alles lokal.
 * Unsichtbar, solange Firebase/Google-Anmeldung nicht eingerichtet ist.
 */
@Composable
fun AccountSection(
    onPrivacy: () -> Unit = {},
    viewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.available) return
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleMedium)
            val user = state.user
            if (user == null) {
                Text(stringResource(R.string.account_signed_out_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                Button(
                    onClick = { activity?.let(viewModel::startGoogle) },
                    enabled = busy == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.account_google)) }
            } else {
                Text(user.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Neon.Cyan)
                Text(
                    state.lastBackupAt?.let {
                        stringResource(R.string.account_last_backup, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(backupTime))
                    } ?: stringResource(R.string.account_no_backup_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.TextSecondary,
                )
                if (state.conflict) {
                    Text(stringResource(R.string.account_conflict), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
                    OutlinedButton(onClick = viewModel::compareNow, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.account_compare))
                    }
                }
                Button(
                    onClick = viewModel::backupNow,
                    enabled = busy == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.account_backup_now)) }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = viewModel::signOut, enabled = busy == null) { Text(stringResource(R.string.account_sign_out)) }
                    TextButton(onClick = viewModel::askDelete, enabled = busy == null) {
                        Text(stringResource(R.string.account_delete), color = Neon.Red)
                    }
                }
            }
            busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.Cyan)
                    Text(stringResource(it.textRes), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (busy == null) {
                message?.let {
                    val error = it == R.string.account_error || it == R.string.account_restore_failed || it == R.string.account_no_google
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = if (error) Neon.Yellow else Neon.Green)
                }
            }
        }
    }

    when (val d = dialog) {
        AccountDialog.Consent -> ConsentDialog(
            onAccept = { activity?.let(viewModel::acceptConsent) },
            onCancel = viewModel::dismissDialog,
            onPrivacy = {
                viewModel.dismissDialog()
                onPrivacy()
            },
        )
        is AccountDialog.OfferRestore -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.account_offer_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.account_offer_restore_text))
                    SummaryCard(stringResource(R.string.account_cloud), d.cloud, highlight = true)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::takeCloud) { Text(stringResource(R.string.account_restore)) } },
            dismissButton = { TextButton(onClick = viewModel::keepPhone) { Text(stringResource(R.string.account_start_fresh)) } },
        )
        is AccountDialog.Compare -> CompareDialog(d.local, d.cloud, viewModel::keepPhone, viewModel::takeCloud, viewModel::dismissDialog)
        is AccountDialog.DeleteCode -> DeleteDialog(d.code, onConfirm = { activity?.let(viewModel::confirmDelete) }, onCancel = viewModel::dismissDialog)
        null -> Unit
    }
}

@Composable
private fun ConsentDialog(onAccept: () -> Unit, onCancel: () -> Unit, onPrivacy: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.account_consent_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account_consent_text))
                listOf(
                    R.string.account_consent_where,
                    R.string.account_consent_why,
                    R.string.account_consent_login,
                    R.string.account_consent_never,
                    R.string.account_consent_delete,
                ).forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = onPrivacy) { Text(stringResource(R.string.privacy_open)) }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.account_consent_accept)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Zwei Stände nebeneinander (untereinander, damit es auch schmal passt); der mit dem jüngeren Tagebuch ist markiert. */
@Composable
private fun CompareDialog(
    local: BackupFormat.Summary,
    cloud: BackupFormat.Summary,
    onKeepPhone: () -> Unit,
    onTakeCloud: () -> Unit,
    onCancel: () -> Unit,
) {
    val localNewer = (local.lastDay ?: java.time.LocalDate.MIN) >= (cloud.lastDay ?: java.time.LocalDate.MIN)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.account_compare_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_compare_text))
                SummaryCard(stringResource(R.string.account_phone), local, highlight = localNewer)
                SummaryCard(stringResource(R.string.account_cloud), cloud, highlight = !localNewer)
                Text(stringResource(R.string.account_compare_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onKeepPhone) { Text(stringResource(R.string.account_keep_phone)) } },
        dismissButton = { TextButton(onClick = onTakeCloud) { Text(stringResource(R.string.account_take_cloud)) } },
    )
}

@Composable
private fun SummaryCard(title: String, summary: BackupFormat.Summary, highlight: Boolean) {
    GlassCard(strong = true, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                if (highlight) {
                    Text(stringResource(R.string.account_newer), style = MaterialTheme.typography.labelSmall, color = Neon.Green)
                }
            }
            BackupSummaryText(summary)
        }
    }
}

@Composable
private fun DeleteDialog(code: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.account_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_delete_text))
                Text(
                    code.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 4.sp),
                    color = Neon.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.account_delete_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed == code) {
                Text(stringResource(R.string.account_delete_confirm), color = if (typed == code) Neon.Red else Neon.TextSecondary)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
