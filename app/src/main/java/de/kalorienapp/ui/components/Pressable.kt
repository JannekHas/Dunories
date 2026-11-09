package de.kalorienapp.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Drück-Feedback wie bei Apple: schon beim Berühren (nicht erst beim Loslassen) gibt das Element
 * sichtbar nach und federt ohne Nachschwingen zurück. Keine Vibration (Wunsch des Nutzers).
 */
@Composable
fun Modifier.pressable(onClick: () -> Unit, shape: Shape): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
}

/** Farbige, antippbare Fläche mit [pressable]-Feedback. */
@Composable
fun PressableSurface(
    onClick: () -> Unit,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.pressable(onClick, shape).background(color, shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
