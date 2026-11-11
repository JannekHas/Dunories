package de.kalorienapp.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.data.AppSettings
import de.kalorienapp.ui.theme.Neon

/** Einmalige Tipps für neue Nutzer – erscheinen dort, wo man sie braucht, und kommen nach „Verstanden“ nie wieder. */
enum class Tip(@param:StringRes val textRes: Int) {
    ADD(R.string.tip_add),
    MEAL(R.string.tip_meal),
    ACTIVITY(R.string.tip_activity),
    RINGS(R.string.tip_rings),
    CAPTURE(R.string.tip_capture),
    STATS(R.string.tip_stats),
    WEIGHT(R.string.tip_weight),
    MOVE(R.string.tip_move),
}

/** Wohin die Sprechblase zeigt. */
enum class TipCaret { NONE, UP_START, UP_END, UP_CENTER, DOWN_CENTER }

/** Einstellungen mit den schon gesehenen Tipps; in [de.kalorienapp.ui.navigation.AppNavHost] gesetzt. */
val LocalTips = staticCompositionLocalOf<AppSettings?> { null }

/**
 * Zeigt [tip], solange er nicht weggeklickt wurde und [condition] gilt; mit [after] erst, wenn jener
 * Tipp gesehen ist – so erscheinen auf einem Screen nicht alle Tipps auf einmal.
 */
@Composable
fun TipSlot(
    tip: Tip,
    modifier: Modifier = Modifier,
    caret: TipCaret = TipCaret.NONE,
    condition: Boolean = true,
    after: Tip? = null,
) {
    val settings = LocalTips.current ?: return
    val seen by settings.seenTips.collectAsState()
    val visible = condition && tip.name !in seen && (after == null || after.name in seen)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)),
        exit = fadeOut() + shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)),
    ) {
        TipBubble(stringResource(tip.textRes), caret, onDismiss = { settings.markTipSeen(tip.name) })
    }
}

/** Cyanfarbene Sprechblase mit schwarzer Schrift (wie die Neon-Kacheln) und kleinem Zeiger. */
@Composable
private fun TipBubble(text: String, caret: TipCaret, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (caret == TipCaret.UP_START || caret == TipCaret.UP_END || caret == TipCaret.UP_CENTER) {
            Caret(up = true, modifier = Modifier.align(caret.alignment()))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Neon.Cyan, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    stringResource(R.string.tip_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Neon.OnNeon,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Neon.OnNeon, modifier = Modifier.weight(1f).padding(end = 8.dp))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.tip_dismiss), color = Neon.OnNeon, fontWeight = FontWeight.Bold)
            }
        }
        if (caret == TipCaret.DOWN_CENTER) Caret(up = false, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

private fun TipCaret.alignment(): Alignment.Horizontal = when (this) {
    TipCaret.UP_START -> Alignment.Start
    TipCaret.UP_END -> Alignment.End
    else -> Alignment.CenterHorizontally
}

@Composable
private fun Caret(up: Boolean, modifier: Modifier) {
    Canvas(modifier.padding(horizontal = 36.dp).size(width = 18.dp, height = 9.dp)) {
        val path = Path().apply {
            if (up) {
                moveTo(0f, size.height); lineTo(size.width / 2, 0f); lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f)
            }
            close()
        }
        drawPath(path, Neon.Cyan)
    }
}
