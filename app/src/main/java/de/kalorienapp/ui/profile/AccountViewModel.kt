package de.kalorienapp.ui.profile

import android.app.Activity
import androidx.annotation.StringRes
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.R
import de.kalorienapp.account.AccountManager
import de.kalorienapp.account.AccountUser
import de.kalorienapp.account.AiStatus
import de.kalorienapp.account.PremiumClient
import de.kalorienapp.account.RedeemResult
import de.kalorienapp.backup.BackupFormat
import de.kalorienapp.backup.BackupManager
import de.kalorienapp.backup.CloudBackupManager
import de.kalorienapp.data.AppSettings
import de.kalorienapp.domain.CloudBackup
import de.kalorienapp.notify.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountBusy(@param:StringRes val textRes: Int) {
    SIGNING_IN(R.string.account_busy_signin),
    CHECKING(R.string.account_busy_checking),
    UPLOADING(R.string.account_busy_upload),
    RESTORING(R.string.account_busy_restore),
    DELETING(R.string.account_busy_delete),
}

sealed interface AccountDialog {
    data object Consent : AccountDialog
    data class OfferRestore(val cloud: BackupFormat.Summary) : AccountDialog
    data class Compare(val local: BackupFormat.Summary, val cloud: BackupFormat.Summary) : AccountDialog
    data class DeleteCode(val code: String) : AccountDialog
    data object Redeem : AccountDialog

    /** Cloud-Sicherung ausschalten = Einwilligung widerrufen → Sicherungen in der Cloud werden gelöscht. */
    data object CloudOff : AccountDialog
}

data class AccountUiState(
    val available: Boolean = false,
    val user: AccountUser? = null,
    val lastBackupAt: Long? = null,
    val conflict: Boolean = false,
    val backupOn: Boolean = false,
)

/** Konto-Karte unter Profil → Einstellungen: anmelden, sichern, vergleichen, abmelden, löschen. */
class AccountViewModel(
    private val account: AccountManager,
    private val cloud: CloudBackupManager,
    private val backup: BackupManager,
    private val settings: AppSettings,
    private val scheduler: WorkScheduler,
    private val premium: PremiumClient,
) : ViewModel() {

    /** KI-Stufe vom Worker (Gast/Konto/Premium); null = unbekannt (offline oder nicht eingerichtet). */
    private val _aiStatus = MutableStateFlow<AiStatus?>(null)
    val aiStatus: StateFlow<AiStatus?> = _aiStatus.asStateFlow()

    /** Ergebnis des letzten Einlöseversuchs (für den Dialog). */
    private val _redeem = MutableStateFlow<RedeemResult?>(null)
    val redeemResult: StateFlow<RedeemResult?> = _redeem.asStateFlow()
    private val _redeeming = MutableStateFlow(false)
    val redeeming: StateFlow<Boolean> = _redeeming.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        if (!account.available) return
        viewModelScope.launch { premium.status()?.let { _aiStatus.value = it } }
    }

    fun openRedeem() {
        _redeem.value = null
        _dialog.value = AccountDialog.Redeem
    }

    fun redeem(code: String) {
        if (_redeeming.value) return
        _redeeming.value = true
        viewModelScope.launch {
            try {
                val (result, status) = premium.redeem(code)
                _redeem.value = result
                status?.let { _aiStatus.value = it }
                if (result == RedeemResult.OK) _message.value = R.string.premium_redeemed
            } finally {
                _redeeming.value = false
            }
        }
    }

    val state: StateFlow<AccountUiState> = combine(
        account.user, settings.cloudBackupAt, settings.cloudConflict, settings.cloudBackupOn,
    ) { user, at, conflict, on ->
        AccountUiState(account.available, user, at, conflict, on)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState(account.available, account.user.value))

    /** Angemeldet, Cloud-Sicherung aus, aber in der Cloud liegt eine Sicherung (z. B. neues Handy) → Hinweis. */
    private val _cloudHasBackup = MutableStateFlow(false)
    val cloudHasBackup: StateFlow<Boolean> = _cloudHasBackup.asStateFlow()

    private val _busy = MutableStateFlow<AccountBusy?>(null)
    val busy: StateFlow<AccountBusy?> = _busy.asStateFlow()

    private val _dialog = MutableStateFlow<AccountDialog?>(null)
    val dialog: StateFlow<AccountDialog?> = _dialog.asStateFlow()

    /** Kurze Rückmeldung (Erfolg/Fehler) unter der Karte. */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun dismissDialog() = _dialog.update { null }

    /** „Mit Google weiter“: nur anmelden (Premium, Probezeit, Codes). Die Cloud-Sicherung ist ein eigener Schalter. */
    fun startGoogle(activity: Activity) = run(AccountBusy.SIGNING_IN) {
        try {
            account.signInWithGoogle(activity)
        } catch (_: GetCredentialCancellationException) {
            return@run
        } catch (_: NoCredentialException) {
            _message.value = R.string.account_no_google
            return@run
        }
        // Startet beim Worker die Probezeit (einmal pro Google-Konto) und zeigt sie gleich an.
        premium.status()?.let { _aiStatus.value = it }
        if (settings.cloudBackupOn.value) check() else _cloudHasBackup.value = runCatching { cloud.newest() != null }.getOrDefault(false)
    }

    /** Schalter „Cloud-Sicherung“: an → erst Einwilligung, aus → Widerruf (Sicherungen in der Cloud werden gelöscht). */
    fun setCloudBackup(on: Boolean) {
        _dialog.value = if (on) AccountDialog.Consent else AccountDialog.CloudOff
    }

    fun acceptConsent(@Suppress("UNUSED_PARAMETER") activity: Activity) {
        _dialog.value = null
        run(AccountBusy.CHECKING) {
            cloud.saveConsent(CONSENT_VERSION)
            settings.cloudConsentVersion = CONSENT_VERSION
            _cloudHasBackup.value = false
            check()
        }
    }

    fun confirmCloudOff() {
        _dialog.value = null
        run(AccountBusy.DELETING) {
            cloud.deleteAll()
            scheduler.cancelCloudBackup()
            settings.clearCloud()
            _message.value = R.string.account_cloud_off_done
        }
    }

    /** Nach dem Anmelden (oder bei Konflikt): hochladen, Wiederherstellen anbieten oder vergleichen. */
    fun compareNow() = run(AccountBusy.CHECKING) { check() }

    private suspend fun check() {
        _busy.value = AccountBusy.CHECKING
        val uid = account.user.value?.uid ?: return
        val local = backup.localSummary()
        val newest = cloud.newest()
        val lastUpload = settings.cloudLastUploadAt.takeIf { settings.cloudUid == uid }
        when (CloudBackup.decide(local.isEmpty, newest, lastUpload)) {
            CloudBackup.Decision.UPLOAD -> upload()
            CloudBackup.Decision.OFFER_RESTORE -> _dialog.value = AccountDialog.OfferRestore(newest!!.toSummary())
            CloudBackup.Decision.COMPARE -> _dialog.value = AccountDialog.Compare(local, newest!!.toSummary())
        }
        scheduler.scheduleCloudBackup()
    }

    fun backupNow() = run(AccountBusy.UPLOADING) {
        if (settings.cloudConflict.value) check() else upload()
    }

    /** Vergleich: „Handy behalten“ → Handy-Stand hochladen (der alte Cloud-Stand bleibt als zweiter Stand erhalten). */
    fun keepPhone() {
        _dialog.value = null
        run(AccountBusy.UPLOADING) { upload() }
    }

    private suspend fun upload() {
        _busy.value = AccountBusy.UPLOADING
        cloud.upload(auto = false)
        settings.setCloudConflict(false)
        _message.value = R.string.account_backed_up
    }

    /** Cloud-Stand übernehmen: ersetzt die Daten auf dem Handy, App startet neu. */
    fun takeCloud() {
        _dialog.value = null
        run(AccountBusy.RESTORING) {
            when (val result = cloud.prepareRestore()) {
                is BackupManager.PrepareResult.Ready -> {
                    cloud.markRestoring()
                    backup.applyRestore(result.prepared)
                    backup.restartApp()
                }
                is BackupManager.PrepareResult.Failed -> _message.value = R.string.account_restore_failed
            }
        }
    }

    fun signOut() = run(null) {
        account.signOut()
        scheduler.cancelCloudBackup()
        settings.clearCloud()
        _cloudHasBackup.value = false
        _message.value = R.string.account_signed_out
        _aiStatus.value = premium.status()
    }

    fun askDelete() {
        _dialog.value = AccountDialog.DeleteCode(CloudBackup.deleteCode())
    }

    fun confirmDelete(activity: Activity) {
        _dialog.value = null
        run(AccountBusy.DELETING) {
            cloud.deleteAll()
            account.deleteAccount(activity)
            scheduler.cancelCloudBackup()
            settings.clearCloud()
            _message.value = R.string.account_deleted
            _aiStatus.value = premium.status()
        }
    }

    private fun run(busy: AccountBusy?, block: suspend () -> Unit) {
        if (_busy.value != null) return
        _busy.value = busy
        _message.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = R.string.account_error
            } finally {
                _busy.value = null
            }
        }
    }

    companion object {
        /** Version des Einwilligungstexts – bei Änderung erhöhen, dann wird erneut gefragt. */
        const val CONSENT_VERSION = 1
    }
}

private fun CloudBackup.Meta.toSummary() = BackupFormat.Summary(
    firstDay = firstDay?.let(java.time.LocalDate::ofEpochDay),
    lastDay = lastDay?.let(java.time.LocalDate::ofEpochDay),
    foodEntries = foodEntries,
    recipes = recipes,
    weights = weights,
    measurements = measurements,
    shoppingItems = 0,
    reports = reports,
    createdAt = createdAt,
)
