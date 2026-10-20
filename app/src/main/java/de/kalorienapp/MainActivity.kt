package de.kalorienapp

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        val profileRepository = (application as KalorienApplication).container.profileRepository

        setContent {
            KalorienTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Startziel einmalig festlegen: ohne Profil zuerst das Onboarding.
                    var startDestination by remember { mutableStateOf<Any?>(null) }
                    LaunchedEffect(Unit) {
                        startDestination = if (profileRepository.get() != null) TodayRoute else OnboardingRoute
                    }
                    startDestination?.let { AppNavHost(startDestination = it) }
                }
            }
        }
    }
}
