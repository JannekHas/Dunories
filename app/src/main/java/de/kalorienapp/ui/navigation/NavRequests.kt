package de.kalorienapp.ui.navigation

import android.content.Intent
import de.kalorienapp.notify.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wohin die App nach dem Antippen einer Benachrichtigung springen soll. */
sealed interface NavRequest {
    /** Statistik öffnen und den Wiege-Dialog zeigen. */
    data object Weigh : NavRequest

    data class Report(val id: Long) : NavRequest
}

class NavRequests {
    private val _pending = MutableStateFlow<NavRequest?>(null)
    val pending: StateFlow<NavRequest?> = _pending.asStateFlow()

    /** Für den Statistik-Screen: Wiege-Dialog beim nächsten Anzeigen öffnen. */
    private val _openWeighDialog = MutableStateFlow(false)
    val openWeighDialog: StateFlow<Boolean> = _openWeighDialog.asStateFlow()

    fun accept(intent: Intent?) {
        _pending.value = when (intent?.getStringExtra(Notifications.EXTRA_OPEN)) {
            Notifications.OPEN_WEIGH -> NavRequest.Weigh
            Notifications.OPEN_REPORT -> intent.getLongExtra(Notifications.EXTRA_REPORT_ID, 0L)
                .takeIf { it > 0 }?.let { NavRequest.Report(it) }
            else -> return
        }
        // Extra verbrauchen, damit eine Drehung/Neuerstellung nicht erneut springt.
        intent.removeExtra(Notifications.EXTRA_OPEN)
    }

    fun take(): NavRequest? = _pending.value.also {
        _pending.value = null
        if (it == NavRequest.Weigh) _openWeighDialog.value = true
    }

    fun weighDialogShown() {
        _openWeighDialog.value = false
    }
}
