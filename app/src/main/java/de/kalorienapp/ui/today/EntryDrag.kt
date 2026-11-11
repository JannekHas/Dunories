package de.kalorienapp.ui.today

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Einträge per Gedrückt-Halten in eine andere Mahlzeit ziehen (Tester-Wunsch). Die Geste hängt an der
 * ganzen Liste (nicht an der Zeile), damit sie auch dann weiterläuft, wenn die Liste mitscrollt und die
 * Zeile dabei aus dem Bild verschwindet. Keine Vibration (Wunsch des Nutzers).
 */
@Stable
class EntryDragState {
    /** Gerade gezogener Eintrag; null = kein Ziehen. */
    var entry by mutableStateOf<FoodEntry?>(null)
        private set

    /** Fingerposition (y) im Listenbereich. */
    var pointerY by mutableFloatStateOf(0f)
        private set

    /** Abstand vom Finger zur Oberkante der Zeile – damit die Karte nicht springt. */
    private var grabOffsetY = 0f
    var ghostLeft by mutableFloatStateOf(0f)
        private set
    var ghostWidth by mutableFloatStateOf(0f)
        private set

    /** Ursprung des Listenbereichs im Fenster; Zeilen/Mahlzeiten melden Fenster-Koordinaten. */
    var areaOrigin by mutableStateOf(Offset.Zero)
    var areaHeight by mutableFloatStateOf(0f)

    internal val entryBounds = mutableStateMapOf<Long, Rect>()
    internal val mealBounds = mutableStateMapOf<MealType, Rect>()

    val ghostTop: Float get() = pointerY - grabOffsetY

    /** Mahlzeit unter dem Finger (null = keine). */
    val target: MealType?
        get() {
            if (entry == null) return null
            val y = pointerY + areaOrigin.y
            return mealBounds.entries.firstOrNull { y >= it.value.top && y <= it.value.bottom }?.key
        }

    internal fun entryAt(local: Offset): Long? {
        val p = local + areaOrigin
        return entryBounds.entries.firstOrNull { it.value.contains(p) }?.key
    }

    internal fun start(entry: FoodEntry, local: Offset) {
        val bounds = entryBounds[entry.id] ?: return
        this.entry = entry
        pointerY = local.y
        grabOffsetY = local.y - (bounds.top - areaOrigin.y)
        ghostLeft = bounds.left - areaOrigin.x
        ghostWidth = bounds.width
    }

    internal fun move(local: Offset) {
        pointerY = local.y
    }

    internal fun end() {
        entry = null
    }
}

@Composable
fun rememberEntryDragState() = remember { EntryDragState() }

/** Meldet die Lage einer Eintragszeile, damit ein langer Druck sie findet. */
@Composable
fun Modifier.trackEntry(state: EntryDragState, entryId: Long): Modifier {
    DisposableEffect(entryId) { onDispose { state.entryBounds.remove(entryId) } }
    return onGloballyPositioned { state.entryBounds[entryId] = it.boundsInRoot() }
}

/** Meldet die Lage einer Mahlzeit-Karte als mögliches Ziel. */
@Composable
fun Modifier.trackMeal(state: EntryDragState, mealType: MealType): Modifier {
    DisposableEffect(mealType) { onDispose { state.mealBounds.remove(mealType) } }
    return onGloballyPositioned { state.mealBounds[mealType] = it.boundsInRoot() }
}

/** Hervorhebung der Ziel-Mahlzeit während des Ziehens. */
fun Modifier.dropHighlight(active: Boolean, color: Color): Modifier =
    if (active) border(2.dp, color, RoundedCornerShape(20.dp)) else this

/**
 * Lange drücken (ohne zu wischen) startet das Ziehen; danach gehören alle Bewegungen bis zum Loslassen
 * dem Ziehen – sie werden vor Liste und Zeilen abgefangen (Initial-Pass), sodass weder gescrollt noch
 * die Zeile „angetippt“ wird. Kurzes Tippen und normales Scrollen bleiben unverändert.
 */
fun Modifier.entryDragArea(
    state: EntryDragState,
    findEntry: (Long) -> FoodEntry?,
    onDrop: (FoodEntry, MealType) -> Unit,
): Modifier = this
    .onGloballyPositioned {
        state.areaOrigin = it.positionInRoot()
        state.areaHeight = it.size.height.toFloat()
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val entry = state.entryAt(down.position)?.let(findEntry) ?: return@awaitEachGesture
            // null = Zeit abgelaufen, ohne loszulassen oder zu wischen → langer Druck
            val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull true
                    if (!change.pressed || change.isConsumed) return@withTimeoutOrNull true
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull true
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
            if (endedEarly != null) return@awaitEachGesture
            state.start(entry, down.position)
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) {
                        state.target?.let { target -> if (target != entry.mealType) onDrop(entry, target) }
                        break
                    }
                    state.move(change.position)
                }
            } finally {
                state.end()
            }
        }
    }

/** Liste scrollt mit, solange der Finger nahe am oberen/unteren Rand ist. */
@Composable
fun EntryDragAutoScroll(state: EntryDragState, listState: LazyListState, bottomInsetPx: Float) {
    val dragging = state.entry != null
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    val maxSpeed = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (isActive && state.entry != null) {
            val y = state.pointerY
            val bottom = state.areaHeight - bottomInsetPx
            val speed = when {
                y < edge -> -maxSpeed * ((edge - y) / edge).coerceIn(0f, 1f)
                y > bottom - edge -> maxSpeed * ((y - (bottom - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) listState.scrollBy(speed)
            withFrameNanos { }
        }
    }
}

/** Schwebende Karte am Finger: Name und kcal des gezogenen Eintrags. */
@Composable
fun EntryDragGhost(state: EntryDragState, targetColor: (MealType) -> Color) {
    val entry = state.entry ?: return
    val density = LocalDensity.current
    val color = state.target?.let(targetColor) ?: Neon.TextSecondary
    Surface(
        color = Neon.SurfaceHighest,
        contentColor = Neon.TextPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .offset { IntOffset(state.ghostLeft.roundToInt(), state.ghostTop.roundToInt()) }
            .width(with(density) { state.ghostWidth.toDp() })
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .border(2.dp, color, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.kcal_value, entry.nutrients.kcal.format()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
