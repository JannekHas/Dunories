package de.kalorienapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import de.kalorienapp.ui.theme.Neon

/**
 * Platzhalter in der Form des späteren Inhalts, solange Daten laden – wirkt ruhiger als ein leerer
 * Bildschirm, der plötzlich aufspringt. Pulsiert sanft (kein schnelles Blinken).
 */
@Composable
fun SkeletonBlock(shape: Shape, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.graphicsLayer { this.alpha = alpha }.background(Neon.SurfaceHigh, shape))
}
