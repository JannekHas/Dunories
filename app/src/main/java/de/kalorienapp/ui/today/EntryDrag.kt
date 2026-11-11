package de.kalorienapp.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.ui.components.GlassShape
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Einträge per Gedrückt-Halten verschieben – in eine andere Mahlzeit und an eine bestimmte Stelle
 * (Tester-Wunsch). Während des Ziehens zeigt eine Lücke in der Farbe der Mahlzeit live, wo der Eintrag
 * beim Loslassen landet; die Original-Zeile zieht sich zusammen. Die Geste hängt an der ganzen Liste
 * (nicht an der Zeile), damit sie beim Mitscrollen nicht abreißt. Keine Vibration (Wunsch des Nutzers).
 */
@Stable
class EntryDragState {
    /** Gerade gezogener Eintrag; null = kein Ziehen. */
    var entry by mutableStateOf<FoodEntry?>(null)
        private set

    /** Fingerposition (y) im Listenbereich. */
    var pointerY by mutableFloatStateOf(0f)
        private set

    /** Wo der Eintrag beim Loslassen landet: Mahlzeit und Position darin. */
    var targetMeal by mutableStateOf<MealType?>(null)
        private set
    var targetIndex by mutableIntStateOf(0)
        private set

    /** Höhe der gezogenen Zeile – so groß wird die Lücke. */
    var rowHeight by mutableFloatStateOf(0f)
        private set

    private var grabOffsetY = 0f
    var ghostLeft by mutableFloatStateOf(0f)
        private set
    var ghostWidth by mutableFloatStateOf(0f)
        private set

    /** Ursprung des Listenbereichs im Fenster; Zeilen/Mahlzeiten melden Fenster-Koordinaten. */
    var areaOrigin by mutableStateOf(Offset.Zero)
    var areaHeight by mutableFloatStateOf(0f)

    /** Einträge je Mahlzeit in Anzeige-Reihenfolge (von „Heute“ gesetzt). */
    internal var mealEntries: () -> Map<MealType, List<FoodEntry>> = { emptyMap() }

    internal val entryBounds = mutableStateMapOf<Long, Rect>()
    internal val mealBounds = mutableStateMapOf<MealType, Rect>()

    /** Lage der Lücke (Fenster-Koordinaten), um ihre Höhe beim Zuordnen herauszurechnen. */
    internal var slotBounds: Rect? = null

    val ghostTop: Float get() = pointerY - grabOffsetY

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
        rowHeight = bounds.height
        targetMeal = entry.mealType
        targetIndex = mealEntries()[entry.mealType].orEmpty().indexOfFirst { it.id == entry.id }.coerceAtLeast(0)
    }

    internal fun move(local: Offset) {
        pointerY = local.y
        refreshTarget()
    }

    /**
     * Ziel aus der Fingerposition bestimmen. Zwischen den Karten bleibt das letzte Ziel stehen (kein Flackern);
     * die Lücke selbst wird herausgerechnet, damit die Position nicht hin und her springt.
     */
    internal fun refreshTarget() {
        val dragged = entry ?: return
        val y = pointerY + areaOrigin.y
        val meal = mealBounds.entries.firstOrNull { y >= it.value.top && y <= it.value.bottom }?.key ?: targetMeal ?: return
        val slot = slotBounds?.takeIf { meal == targetMeal }
        val others = mealEntries()[meal].orEmpty().filter { it.id != dragged.id }
        val index = others.count { other ->
            val b = entryBounds[other.id] ?: return@count false
            val center = if (slot != null && b.top >= slot.top) b.center.y - slot.height else b.center.y
            center < y
        }
        targetMeal = meal
        targetIndex = index
    }

    internal fun end() {
        entry = null
        targetMeal = null
        slotBounds = null
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
    if (active) border(2.dp, color, GlassShape) else this

/** Lücke in der Farbe der Mahlzeit: hier landet der Eintrag beim Loslassen. */
@Composable
fun DropSlot(state: EntryDragState, color: Color) {
    val height = with(LocalDensity.current) { state.rowHeight.toDp() }
    DisposableEffect(Unit) { onDispose { state.slotBounds = null } }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .onGloballyPositioned { state.slotBounds = it.boundsInRoot() }
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .border(2.dp, color, RoundedCornerShape(14.dp)),
    )
}

/**
 * Lange drücken (ohne zu wischen) startet das Ziehen; danach gehören alle Bewegungen bis zum Loslassen
 * dem Ziehen – sie werden vor Liste und Zeilen abgefangen (Initial-Pass), sodass weder gescrollt noch
 * die Zeile „angetippt“ wird. Kurzes Tippen und normales Scrollen bleiben unverändert.
 */
fun Modifier.entryDragArea(
    state: EntryDragState,
    onDrop: (entry: FoodEntry, meal: MealType, index: Int) -> Unit,
): Modifier = this
    .onGloballyPositioned {
        state.areaOrigin = it.positionInRoot()
        state.areaHeight = it.size.height.toFloat()
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val id = state.entryAt(down.position) ?: return@awaitEachGesture
            val entry = state.mealEntries().values.flatten().firstOrNull { it.id == id } ?: return@awaitEachGesture
            // null = Zeit abgelaufen, ohne loszulassen oder zu wischen → langer Druck
            val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var ended = false
                while (!ended) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    ended = change == null || !change.pressed || change.isConsumed ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                }
                true
            }
            if (endedEarly != null) return@awaitEachGesture
            state.start(entry, down.position)
            val startMeal = state.targetMeal
            val startIndex = state.targetIndex
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) {
                        val meal = state.targetMeal
                        if (meal != null && (meal != startMeal || state.targetIndex != startIndex)) {
                            onDrop(entry, meal, state.targetIndex)
                        }
                        break
                    }
                    state.move(change.position)
                }
            } finally {
                state.end()
            }
        }
    }

/** Liste scrollt mit, solange der Finger nahe am oberen/unteren Rand ist; das Ziel wird dabei laufend neu bestimmt. */
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
            state.refreshTarget()
        }
    }
}

/**
 * Schwebende Karte am Finger: sieht aus wie die Zeile selbst (Name, Menge + Makros, kcal – nur ohne ⋮),
 * „ploppt“ beim Anheben leicht hervor und ist leicht durchsichtig, damit die Lücke darunter sichtbar bleibt.
 * Darüber ein Etikett mit der Ziel-Mahlzeit.
 */
@Composable
fun EntryDragGhost(state: EntryDragState, mealColor: (MealType) -> Color) {
    val entry = state.entry ?: return
    val density = LocalDensity.current
    val lift = remember(entry.id) { Animatable(1f) }
    LaunchedEffect(entry.id) { lift.animateTo(1.04f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) }
    val target = state.targetMeal
    Column(
        Modifier
            .offset { IntOffset(state.ghostLeft.roundToInt(), state.ghostTop.roundToInt() - with(density) { 34.dp.roundToPx() }) }
            .width(with(density) { state.ghostWidth.toDp() })
            .graphicsLayer {
                scaleX = lift.value
                scaleY = lift.value
            },
    ) {
        if (target != null) {
            Text(
                stringResource(R.string.entry_move_target, stringResource(target.labelRes)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Neon.OnNeon,
                modifier = Modifier
                    .padding(start = 12.dp, bottom = 4.dp)
                    .background(mealColor(target), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else {
            Spacer(Modifier.height(30.dp))
        }
        Surface(
            color = Neon.SurfaceHigh.copy(alpha = 0.92f),
            contentColor = Neon.TextPrimary,
            shape = GlassShape,
            shadowElevation = 12.dp,
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(entry.name) },
                supportingContent = { Text(entrySubtitle(entry)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.kcal_value, entry.nutrients.kcal.format()), style = MaterialTheme.typography.labelLarge)
                        // Platz des ⋮-Knopfs, damit die kcal genau an derselben Stelle stehen wie in der Zeile.
                        Spacer(Modifier.width(48.dp))
                    }
                },
            )
        }
    }
}
