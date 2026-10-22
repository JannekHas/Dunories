package de.kalorienapp

import android.content.Intent
import android.os.Bundle
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
import de.kalorienapp.ui.navigation.AppNavHost
import de.kalorienapp.ui.navigation.OnboardingRoute
import de.kalorienapp.ui.navigation.TodayRoute
import de.kalorienapp.ui.theme.KalorienTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immer dunkles Design → helle Symbole in Status- und Navigationsleiste.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as KalorienApplication).container
        val profileRepository = container.profileRepository
        // Per „Teilen“ geöffnet (TikTok, Instagram, Galerie …) → Inhalt für den Rezept-Import merken.
        if (savedInstanceState == null) container.shareInbox.accept(intent)

        setContent {
            KalorienTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Startziel einmalig festlegen: ohne Profil zuerst das Onboarding.
                    var startDestination by remember { mutableStateOf<Any?>(null) }
                    LaunchedEffect(Unit) {
                        startDestination = if (profileRepository.get() != null) TodayRoute else OnboardingRoute
                    }
                    startDestination?.let { AppNavHost(startDestination = it, shareInbox = container.shareInbox) }
                }
            }
        }
    }

    /** singleTask: weiteres Teilen, während die App offen ist, kommt hier an. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (application as KalorienApplication).container.shareInbox.accept(intent)
    }
}
