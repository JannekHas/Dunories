package de.kalorienapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.delay
import kotlin.math.exp

/**
 * Warteanzeige für KI-Aufrufe: Gemini meldet keinen Fortschritt, deshalb ein geschätzter Balken
 * (nach [expectedSeconds] ca. 70 %, wird nie ganz voll) + verstrichene Sekunden + Hinweis, wenn
 * es länger dauert – damit niemand glaubt, die App hänge.
 * [key] wechseln (z. B. neuer Schritt) setzt die Anzeige zurück.
 */
@Composable
fun AiWaitProgress(
    expectedSeconds: Int,
    modifier: Modifier = Modifier,
    color: Color = Neon.Cyan,
    key: Any? = Unit,
) {
    val start = remember(key) { System.currentTimeMillis() }
    var now by remember(key) { mutableLongStateOf(start) }
    LaunchedEffect(key) {
        while (true) {
            delay(200)
            now = System.currentTimeMillis()
        }
    }
    val seconds = (now - start) / 1000.0
    val progress = (0.95 * (1 - exp(-1.2 * seconds / expectedSeconds))).toFloat()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = color,
            trackColor = Neon.Track,
            drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.ai_wait_percent, (progress * 100).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = Neon.TextSecondary,
            )
            Text(
                stringResource(R.string.ai_wait_seconds, seconds.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = Neon.TextSecondary,
            )
        }
        val hint = when {
            seconds >= expectedSeconds * 4 -> R.string.ai_wait_very_slow
            seconds >= expectedSeconds * 1.5 -> R.string.ai_wait_slow
            else -> null
        }
        hint?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = Neon.Yellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
