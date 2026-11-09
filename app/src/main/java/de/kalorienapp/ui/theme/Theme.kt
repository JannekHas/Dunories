package de.kalorienapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em

/**
 * Neon-Palette im Stil von „Revolution Idle“: kräftige Regenbogenfarben auf Schwarz.
 * Auf farbigen Flächen steht immer schwarze Schrift ([OnNeon]).
 */
object Neon {
    val Red = Color(0xFFFF3B5C)
    val Orange = Color(0xFFFF8A1F)
    val Yellow = Color(0xFFFFD60A)
    val Green = Color(0xFF3DDC84)
    val Cyan = Color(0xFF22D3EE)
    val Blue = Color(0xFF3B82F6)
    val Violet = Color(0xFFA855F7)
    val Magenta = Color(0xFFF43FBF)

    /** Reihenfolge des Regenbogens – für Verläufe und Ringe. */
    val Rainbow = listOf(Red, Orange, Yellow, Green, Cyan, Blue, Violet, Magenta)

    /** Schrift auf Neon-Flächen. */
    val OnNeon = Color(0xFF0A0A0A)

    // Dunkle Grundflächen
    val Background = Color(0xFF050507)
    val Surface = Color(0xFF111116)
    val SurfaceHigh = Color(0xFF1A1A22)
    val SurfaceHighest = Color(0xFF24242E)
    val Track = Color(0xFF2A2A34)
    val TextPrimary = Color(0xFFF2F2F7)
    val TextSecondary = Color(0xFF9A9AAE)
}

private val NeonColors = darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Neon.OnNeon,
    primaryContainer = Neon.Cyan,
    onPrimaryContainer = Neon.OnNeon,
    secondary = Neon.Violet,
    onSecondary = Neon.OnNeon,
    secondaryContainer = Neon.SurfaceHighest,
    onSecondaryContainer = Neon.TextPrimary,
    tertiary = Neon.Yellow,
    onTertiary = Neon.OnNeon,
    tertiaryContainer = Neon.Yellow,
    onTertiaryContainer = Neon.OnNeon,
    error = Neon.Red,
    onError = Neon.OnNeon,
    background = Neon.Background,
    onBackground = Neon.TextPrimary,
    surface = Neon.Background,
    onSurface = Neon.TextPrimary,
    surfaceVariant = Neon.SurfaceHigh,
    onSurfaceVariant = Neon.TextSecondary,
    surfaceContainerLowest = Neon.Background,
    surfaceContainerLow = Neon.Surface,
    surfaceContainer = Neon.Surface,
    surfaceContainerHigh = Neon.SurfaceHigh,
    surfaceContainerHighest = Neon.SurfaceHigh,
    outline = Color(0xFF3A3A48),
    outlineVariant = Neon.Track,
)

/**
 * Laufweite je Größe (wie Apple): große Zahlen und Überschriften enger, kleine Texte unverändert,
 * große Schrift mit knappem Zeilenabstand.
 */
private val NeonTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(letterSpacing = (-0.03).em, lineHeight = 1.05.em),
        displayMedium = displayMedium.copy(letterSpacing = (-0.025).em, lineHeight = 1.08.em),
        displaySmall = displaySmall.copy(letterSpacing = (-0.02).em, lineHeight = 1.1.em),
        headlineLarge = headlineLarge.copy(letterSpacing = (-0.015).em),
        headlineMedium = headlineMedium.copy(letterSpacing = (-0.01).em),
        headlineSmall = headlineSmall.copy(letterSpacing = (-0.01).em),
        titleLarge = titleLarge.copy(letterSpacing = (-0.005).em),
    )
}

/** Immer dunkel – das Neon-Design lebt vom schwarzen Hintergrund. */
@Composable
fun KalorienTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NeonColors, typography = NeonTypography, content = content)
}
