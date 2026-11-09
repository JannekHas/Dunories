package de.kalorienapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kalorienapp.ui.theme.Neon

/** Höhe der gläsernen unteren Leiste – Inhalte scrollen darunter durch und brauchen so viel Platz am Ende. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

val GlassShape = RoundedCornerShape(18.dp)

/** Lichtkante wie bei Glas: oben fängt sich das Licht, unten verschwindet die Kante. */
val GlassBorder = BorderStroke(1.dp, Brush.verticalGradient(listOf(Neon.GlassEdgeTop, Neon.GlassEdgeBottom)))

/**
 * Dunkelgrauer Hintergrund mit weichen Neon-Lichtflecken – erst darauf wirken die Glas-Karten.
 * Statisch (kein langsames Pulsieren, das wäre unruhig).
 */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Neon.Background)
            .drawBehind {
                fun glow(color: Color, x: Float, y: Float, radius: Float) = drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * x, size.height * y),
                        radius = size.width * radius,
                    ),
                    radius = size.width * radius,
                    center = Offset(size.width * x, size.height * y),
                )
                glow(Neon.Violet, 0.05f, 0.04f, 0.75f)
                glow(Neon.Cyan, 1.0f, 0.42f, 0.7f)
                glow(Neon.Orange, 0.0f, 0.88f, 0.7f)
            },
        content = content,
    )
}

/** Milchige, halbdurchsichtige Karte mit Lichtkante; [strong] für Hinweise, die mehr hervortreten. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = if (strong) Neon.GlassStrong else Neon.Glass,
        contentColor = Neon.TextPrimary,
    )
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = GlassShape, colors = colors, border = GlassBorder, content = content)
    } else {
        Card(modifier = modifier, shape = GlassShape, colors = colors, border = GlassBorder, content = content)
    }
}

/** Glas-Optik für beliebige Flächen (z. B. Fasten-Karte, Kopfzeilen). */
fun Modifier.glass(shape: Shape = GlassShape, strong: Boolean = false, borderWidth: Dp = 1.dp): Modifier =
    background(if (strong) Neon.GlassStrong else Neon.Glass, shape)
        .border(BorderStroke(borderWidth, GlassBorder.brush), shape)
