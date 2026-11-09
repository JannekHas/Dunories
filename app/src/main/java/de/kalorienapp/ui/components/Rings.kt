package de.kalorienapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kalorienapp.ui.theme.Neon

/** Ein Ring: Fortschritt 0..∞ (über 1 = übervoll) und Farbe bzw. Verlauf. */
data class RingSpec(val progress: Float, val colors: List<Color>)

/**
 * Konzentrische Fortschrittsringe im Stil von „Revolution Idle“ – außen der erste Eintrag.
 * Überschreitet ein Ring 100 %, läuft er in Rot weiter.
 */
@Composable
fun ConcentricRings(
    rings: List<RingSpec>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    gap: Dp = 6.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    // Beim Öffnen von 0 hochfüllen, danach weich zum neuen Wert federn (ohne Nachschwingen, unterbrechbar).
    val animated = rings.map { ring ->
        val value = remember { Animatable(0f) }
        LaunchedEffect(ring.progress) {
            value.animateTo(
                ring.progress.coerceIn(0f, 2f),
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
            )
        }
        value.value
    }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val step = stroke + gap.toPx()
            rings.forEachIndexed { index, ring ->
                val inset = stroke / 2 + index * step
                val diameter = size.minDimension - 2 * inset
                if (diameter <= 0f) return@forEachIndexed
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                val arcSize = Size(diameter, diameter)
                // Hintergrund-Spur
                drawArc(Neon.Track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                val progress = animated[index]
                val main = progress.coerceAtMost(1f)
                if (main > 0f) {
                    val brush = if (ring.colors.size > 1) {
                        Brush.sweepGradient(ring.colors + ring.colors.first(), center = this.center)
                    } else {
                        Brush.linearGradient(listOf(ring.colors.first(), ring.colors.first()))
                    }
                    // Start oben (−90°); der Verlauf wird mitgedreht.
                    rotate(-90f) {
                        drawArc(brush, 0f, 360f * main, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                if (progress > 1f) {
                    rotate(-90f) {
                        drawArc(
                            Neon.Red, 0f, 360f * (progress - 1f).coerceAtMost(1f), false, topLeft, arcSize,
                            style = Stroke(stroke * 0.45f, cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
        center()
    }
}
