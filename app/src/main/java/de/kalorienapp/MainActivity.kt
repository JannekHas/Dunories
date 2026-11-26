package de.kalorienapp

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import de.kalorienapp.ui.capture.HardwareShutter
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import de.kalorienapp.notify.Notifications
import de.kalorienapp.ui.navigation.AppNavHost
import de.kalorienapp.ui.navigation.OnboardingRoute
import de.kalorienapp.ui.navigation.TodayRoute
import de.kalorienapp.ui.theme.KalorienTheme

class MainActivity : ComponentActivity() {

    /** Kennung des zuletzt verarbeiteten Teilen-/Benachrichtigungs-Intents (überlebt Drehen und Neustart). */
    private var handledIntentKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immer dunkles Design → helle Symbole in Status- und Navigationsleiste.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as KalorienApplication).container
        val profileRepository = container.profileRepository
        // Per „Teilen“ oder Benachrichtigung geöffnet → Inhalt merken. Nicht nur beim ersten Start prüfen:
        // Wurde die App im Hintergrund beendet, stellt Android sie mit altem Zustand wieder her – der neue
        // geteilte Link steckt trotzdem im Intent. Bereits verarbeitete Intents (Drehen, Übersicht) nicht doppelt.
        handledIntentKey = savedInstanceState?.getString(KEY_HANDLED_INTENT)
        handleIntent(intent)

        setContent {
            KalorienTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Startziel einmalig festlegen: ohne Profil zuerst das Onboarding.
                    var startDestination by remember { mutableStateOf<Any?>(null) }
                    LaunchedEffect(Unit) {
                        startDestination = if (profileRepository.get() != null) TodayRoute else OnboardingRoute
                    }
                    startDestination?.let { AppNavHost(startDestination = it, container = container) }
                }
            }
        }
    }

    /** singleTask: weiteres Teilen bzw. Antippen einer Benachrichtigung, während die App offen ist. */
    /** Lauter/Leiser löst in der Kamera aus (wie in der Kamera-App), sonst normale Lautstärke. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val shutter = HardwareShutter.onPress
        if (volumeKey && shutter != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) shutter()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handledIntentKey = null
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_HANDLED_INTENT, handledIntentKey)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        // Aus der Übersicht „Zuletzt verwendet“ neu gestartet → der alte Intent ist schon erledigt.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val key = intentKey(intent) ?: return
        if (key == handledIntentKey) return
        handledIntentKey = key
        val container = (application as KalorienApplication).container
        // Teilen nur, wenn Rezepte sichtbar sind – sonst könnten fremde Apps den Import trotzdem auslösen.
        if (BuildConfig.FEATURE_RECIPES) container.shareInbox.accept(intent)
        container.navRequests.accept(intent)
    }

    /** Inhalt des Intents als Kennung; null = nichts zu verarbeiten (normaler App-Start). */
    @Suppress("DEPRECATION")
    private fun intentKey(intent: Intent): String? {
        val open = intent.getStringExtra(Notifications.EXTRA_OPEN)
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE && open == null) return null
        return listOf(
            intent.action,
            intent.type,
            intent.getStringExtra(Intent.EXTRA_TEXT),
            intent.extras?.get(Intent.EXTRA_STREAM)?.toString(),
            open,
            intent.getLongExtra(Notifications.EXTRA_REPORT_ID, 0L).toString(),
            intent.getLongExtra(Notifications.EXTRA_POSTED_AT, 0L).toString(),
            intent.getStringExtra(Notifications.EXTRA_MEAL),
            intent.getStringExtra(Notifications.EXTRA_QUEUE_ID),
        ).joinToString("|")
    }

    private companion object {
        const val KEY_HANDLED_INTENT = "handled_intent"
    }

    override fun onResume() {
        super.onResume()
        // Tageswechsel bei geöffneter App / nach langer Pause: Benachrichtigung auf heute bringen.
        val container = (application as KalorienApplication).container
        container.appScope.launch { container.calorieNotifier.refresh() }
        // Wartende KI-Erkennungen gleich versuchen (Netz ist evtl. wieder da).
        container.aiQueue.retryWaitingNow()
    }

    override fun onStop() {
        super.onStop()
        // App verlassen: angemeldet und letzte Cloud-Sicherung älter als ein paar Stunden → gleich sichern.
        val container = (application as KalorienApplication).container
        val last = container.settings.cloudLastUploadAt ?: 0L
        if (container.account.user.value != null && container.settings.cloudBackupOn.value &&
            System.currentTimeMillis() - last > de.kalorienapp.domain.CloudBackup.MIN_AUTO_INTERVAL_MS
        ) {
            container.workScheduler.cloudBackupSoon()
        }
    }
}
