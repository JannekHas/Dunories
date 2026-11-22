package de.kalorienapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.theme.Neon

/**
 * Glas-Karte mit Titel und Kurzfassung, die erst beim Antippen ihren Inhalt zeigt
 * (Profil → Ziele: Körperdaten, Feinjustieren – damit die Seite kurz bleibt, Nutzer Nov. 2026).
 */
@Composable
fun ExpandableCard(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val page = LocalPageScroll.current
    val scope = rememberCoroutineScope()
    val gap = with(LocalDensity.current) { TOP_GAP.toPx() }
    val position = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val stateText = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    GlassCard(modifier.fillMaxWidth().onGloballyPositioned { position[0] = it }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    expanded = !expanded
                    if (expanded && page != null) scope.launch { scrollToTop(page, position[0], gap) }
                }
                .semantics { stateDescription = stateText }
                .padding(16.dp),
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!expanded) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
                }
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(
            expanded,
            enter = expandVertically(tween(EXPAND_MS, easing = FastOutSlowInEasing)) + fadeIn(tween(EXPAND_MS)),
            exit = shrinkVertically(tween(EXPAND_MS, easing = FastOutSlowInEasing)) + fadeOut(tween(EXPAND_MS)),
        ) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
        }
    }
}

private const val EXPAND_MS = 300

/** Kleine Lücke zwischen Reitern und hochgerutschter Karte. */
private val TOP_GAP = 8.dp

/**
 * Aufgeklappt rutscht die Karte nach oben, bis ihr Rand knapp unter den Reitern liegt (Nutzer, Nov. 2026) –
 * gleichzeitig mit dem Aufklappen. Reicht der Inhalt darunter nicht, so weit es geht (nie hinter die Reiter).
 */
private suspend fun scrollToTop(page: PageScroll, card: LayoutCoordinates?, gap: Float) {
    val viewport = page.viewport ?: return
    if (card == null || !card.isAttached || !viewport.isAttached) return
    val top = viewport.localPositionOf(card, Offset.Zero).y
    val target = (page.state.value + top - gap).roundToInt().coerceAtLeast(0)
    page.state.animateScrollTo(target, tween(EXPAND_MS, easing = FastOutSlowInEasing))
    // Wuchs die Seite langsamer als gescrollt wurde, den Rest nachholen.
    if (page.state.value < target) page.state.animateScrollTo(target)
}
