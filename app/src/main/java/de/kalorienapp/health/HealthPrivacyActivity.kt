package de.kalorienapp.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.theme.KalorienTheme

/** Erklärt, wofür die App Health-Connect-Daten nutzt (wird aus dem Berechtigungsdialog verlinkt). */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KalorienTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .safeDrawingPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.health_privacy_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.health_privacy_text), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = ::finish) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
    }
}
