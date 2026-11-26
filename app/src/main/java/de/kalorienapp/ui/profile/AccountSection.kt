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
import de.kalorienapp.account.AiStatus
import de.kalorienapp.account.AiTier
import de.kalorienapp.account.PremiumSource
import de.kalorienapp.account.RedeemResult
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.kalorienapp.backup.BackupFormat
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.SettingSwitchRow
import de.kalorienapp.ui.theme.Neon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val backupTime = DateTimeFormatter.ofPattern("d. MMM, HH:mm", Locale.GERMAN)
private val premiumDate = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)

internal fun Long.premiumDay(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(premiumDate)

/**
 * Konto-Karte ganz oben in Profil → Einstellungen (Phase 2): freiwillig, ohne Konto bleibt alles lokal.
 * Unsichtbar, solange Firebase/Google-Anmeldung nicht eingerichtet ist.
 */
/** Welche Teile der Konto-Karte gezeigt werden (Einstellungen in Kategorien, Neuordnung Nov. 2026). */
enum class AccountPart { ALL, ACCOUNT, PREMIUM }

@Composable
fun AccountSection(
    onPrivacy: () -> Unit = {},
    onPremium: () -> Unit = {},
    part: AccountPart = AccountPart.ALL,
    viewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.available) return
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    // Stand bei jedem Erscheinen und Zurückkehren neu holen (KI-Aufrufe zählen woanders hoch).
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshStatus()
        onPauseOrDispose { }
    }

    if (part != AccountPart.ACCOUNT) aiStatus?.let { status ->
        PremiumCard(
            status = status,
            signedIn = state.user != null,
            onPremium = onPremium,
            onRedeem = viewModel::openRedeem,
        )
    }

    if (part != AccountPart.PREMIUM) GlassCard(Modifier.fillMaxWidth()) {
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
                SettingSwitchRow(
                    title = stringResource(R.string.account_cloud_switch),
                    text = stringResource(R.string.account_cloud_switch_text),
                    checked = state.backupOn,
                    onChange = { if (busy == null) viewModel.setCloudBackup(it) },
                )
                val cloudHasBackup by viewModel.cloudHasBackup.collectAsStateWithLifecycle()
                if (!state.backupOn && cloudHasBackup) {
                    Text(stringResource(R.string.account_cloud_found_hint), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
                }
                if (state.backupOn) Text(
                    state.lastBackupAt?.let {
                        stringResource(R.string.account_last_backup, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(backupTime))
                    } ?: stringResource(R.string.account_no_backup_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.TextSecondary,
                )
                if (state.backupOn && state.conflict) {
                    Text(stringResource(R.string.account_conflict), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
                    OutlinedButton(onClick = viewModel::compareNow, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.account_compare))
                    }
                }
                if (state.backupOn) Button(
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

    AccountDialogs(viewModel, onPrivacy)
}

/** Alle Dialoge rund ums Konto (auch von der Paywall aus genutzt). */
@Composable
internal fun AccountDialogs(viewModel: AccountViewModel, onPrivacy: () -> Unit) {
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
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
        AccountDialog.Redeem -> RedeemDialog(viewModel)
        AccountDialog.CloudOff -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.account_cloud_off_title)) },
            text = { Text(stringResource(R.string.account_cloud_off_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCloudOff) { Text(stringResource(R.string.account_cloud_off_confirm), color = Neon.Red) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

/** KI-Stufe: Testphase, Probezeit, Premium bis …, Gast mit Gratis-Aufrufen oder ohne Premium. */
@Composable
private fun PremiumCard(
    status: AiStatus,
    signedIn: Boolean,
    onPremium: () -> Unit,
    onRedeem: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.premium_title), style = MaterialTheme.typography.titleMedium)
            Text(
                premiumStatusText(status),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (status.premium) Neon.Yellow else Neon.TextSecondary,
            )
            // Die Grenze erst kurz davor zeigen (Wunsch des Nutzers: sonst wirkt sie abschreckend).
            if (status.premium && status.dailyLimit > 0 && status.usedToday * 4 >= status.dailyLimit * 3) {
                Text(stringResource(R.string.premium_used_today, status.usedToday, status.dailyLimit), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                if (status.source != PremiumSource.LIFETIME && status.source != PremiumSource.TEST) {
                    TextButton(onClick = onPremium) { Text(stringResource(R.string.paywall_show)) }
                }
                if (signedIn) TextButton(onClick = onRedeem) { Text(stringResource(R.string.premium_redeem)) }
            }
            if (status.testerPoints > 0) {
                Text(stringResource(R.string.tester_points, status.testerPoints), style = MaterialTheme.typography.bodyMedium, color = Neon.Green)
            }
            if (status.id.isNotBlank()) {
                Text(stringResource(R.string.premium_id, status.id), style = MaterialTheme.typography.labelSmall, color = Neon.TextSecondary)
            }
        }
    }
}

@Composable
internal fun premiumStatusText(status: AiStatus): String = when (status.tier) {
    AiTier.PREMIUM -> when (status.source) {
        PremiumSource.TEST -> stringResource(R.string.premium_status_test)
        PremiumSource.LIFETIME -> stringResource(R.string.premium_status_lifetime)
        PremiumSource.TRIAL -> stringResource(R.string.premium_status_trial, status.until?.premiumDay().orEmpty())
        else -> stringResource(R.string.premium_status_until, status.until?.premiumDay().orEmpty())
    }
    AiTier.GUEST -> (status.freeLeft ?: 0).let { pluralStringResource(R.plurals.premium_status_guest, it, it) }
    AiTier.FREE -> stringResource(R.string.premium_status_free)
}

@Composable
internal fun RedeemDialog(viewModel: AccountViewModel) {
    var code by remember { mutableStateOf("") }
    val result by viewModel.redeemResult.collectAsStateWithLifecycle()
    val running by viewModel.redeeming.collectAsStateWithLifecycle()
    if (result == RedeemResult.OK) {
        viewModel.dismissDialog()
        return
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(stringResource(R.string.premium_redeem)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.premium_redeem_text))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(20) },
                    label = { Text(stringResource(R.string.premium_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                result?.let { Text(stringResource(it.messageRes), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow) }
                if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.Cyan)
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.redeem(code) }, enabled = !running && code.count(Char::isLetterOrDigit) >= 8) {
                Text(stringResource(R.string.premium_redeem_confirm))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.cancel)) } },
    )
}

private val RedeemResult.messageRes: Int
    get() = when (this) {
        RedeemResult.OK -> R.string.premium_redeemed
        RedeemResult.INVALID -> R.string.premium_code_invalid
        RedeemResult.EXPIRED -> R.string.premium_code_expired
        RedeemResult.USED_UP -> R.string.premium_code_used_up
        RedeemResult.ALREADY -> R.string.premium_code_already
        RedeemResult.TOO_MANY -> R.string.premium_code_too_many
        RedeemResult.NEEDS_ACCOUNT -> R.string.premium_code_needs_account
        RedeemResult.NETWORK -> R.string.account_error
    }

@Composable
internal fun ConsentDialog(onAccept: () -> Unit, onCancel: () -> Unit, onPrivacy: () -> Unit) {
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

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
