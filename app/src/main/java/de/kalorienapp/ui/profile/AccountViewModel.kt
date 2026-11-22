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
}

data class AccountUiState(
    val available: Boolean = false,
    val user: AccountUser? = null,
    val lastBackupAt: Long? = null,
    val conflict: Boolean = false,
)

/** Konto-Karte unter Profil → Einstellungen: anmelden, sichern, vergleichen, abmelden, löschen. */
class AccountViewModel(
    private val account: AccountManager,
    private val cloud: CloudBackupManager,
    private val backup: BackupManager,
    private val settings: AppSettings,
    private val scheduler: WorkScheduler,
) : ViewModel() {

    val state: StateFlow<AccountUiState> = combine(account.user, settings.cloudBackupAt, settings.cloudConflict) { user, at, conflict ->
        AccountUiState(account.available, user, at, conflict)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState(account.available, account.user.value))

    private val _busy = MutableStateFlow<AccountBusy?>(null)
    val busy: StateFlow<AccountBusy?> = _busy.asStateFlow()

    private val _dialog = MutableStateFlow<AccountDialog?>(null)
    val dialog: StateFlow<AccountDialog?> = _dialog.asStateFlow()

    /** Kurze Rückmeldung (Erfolg/Fehler) unter der Karte. */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun dismissDialog() = _dialog.update { null }

    /** „Mit Google weiter“: beim ersten Mal erst die Einwilligung. */
    fun startGoogle(activity: Activity) {
        if (settings.cloudConsentVersion < CONSENT_VERSION) _dialog.value = AccountDialog.Consent else signIn(activity)
    }

    fun acceptConsent(activity: Activity) {
        _dialog.value = null
        signIn(activity, consentJustGiven = true)
    }

    private fun signIn(activity: Activity, consentJustGiven: Boolean = false) = run(AccountBusy.SIGNING_IN) {
        try {
            account.signInWithGoogle(activity)
        } catch (_: GetCredentialCancellationException) {
            return@run
        } catch (_: NoCredentialException) {
            _message.value = R.string.account_no_google
            return@run
        }
        if (consentJustGiven) {
            cloud.saveConsent(CONSENT_VERSION)
            settings.cloudConsentVersion = CONSENT_VERSION
        }
        check()
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
        _message.value = R.string.account_signed_out
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
