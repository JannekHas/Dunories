package de.kalorienapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import de.kalorienapp.ui.theme.Neon

/**
 * Platzhalter in der Form des späteren Inhalts, solange Daten laden – zeigt, was gleich kommt, statt nur
 * „warte“ (Kreisel). Ein sanfter Glanz läuft von links nach rechts (Leserichtung); alle Blöcke laufen im
 * gleichen Takt.
 */
@Composable
fun SkeletonBlock(shape: Shape, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonShimmer",
    )
    Box(
        modifier
            .clip(shape)
            .background(Neon.SurfaceHigh)
            .drawWithContent {
                drawContent()
                val band = size.width.coerceAtLeast(1f) * 0.6f
                val start = -band + progress * (size.width + 2 * band)
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(start + band, 0f),
                    ),
                )
            },
    )
}

private const val SHIMMER_MS = 1300

/** Skelett eines Lebensmittel-Vorschlags (Name, Menge, Nährwert-Zeilen) – solange Barcode/KI arbeiten. */
@Composable
fun ProposalSkeleton(modifier: Modifier = Modifier) {
    val small = RoundedCornerShape(6.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(small, Modifier.fillMaxWidth(0.65f).height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(RoundedCornerShape(12.dp), Modifier.weight(1f).height(56.dp))
            SkeletonBlock(RoundedCornerShape(12.dp), Modifier.weight(1f).height(56.dp))
        }
        repeat(4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SkeletonBlock(small, Modifier.fillMaxWidth(0.4f).height(18.dp))
                SkeletonBlock(small, Modifier.fillMaxWidth(0.25f).height(18.dp))
            }
        }
    }
}
