package de.kalorienapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import de.kalorienapp.R

/**
 * Neon-Palette im Stil von „Revolution Idle“: kräftige Regenbogenfarben auf Dunkelgrau mit
 * Glas-Karten. Auf farbigen Flächen steht immer schwarze Schrift ([OnNeon]).
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

    // Dunkelgraue Grundflächen (bewusst nicht tiefschwarz – Wunsch des Nutzers)
    val Background = Color(0xFF17171D)
    /** Deckende Flächen: Dialoge, Menüs, Bottom-Sheets (dort wäre Glas unlesbar). */
    val Surface = Color(0xFF20202A)
    val SurfaceHigh = Color(0xFF282833)
    val SurfaceHighest = Color(0xFF32323E)

    /** Glas: halbdurchsichtiges Weiß über den Lichtflecken des Hintergrunds. */
    val Glass = Color(0x14FFFFFF)
    val GlassStrong = Color(0x21FFFFFF)
    /** Lichtkante der Glas-Karten: oben heller, unten fast unsichtbar. */
    val GlassEdgeTop = Color(0x33FFFFFF)
    val GlassEdgeBottom = Color(0x0DFFFFFF)

    /** Leere Spur von Ringen und Balken – durchscheinend, damit der Hintergrund wirkt. */
    val Track = Color(0x1FFFFFFF)
    val TextPrimary = Color(0xFFF2F2F7)
    val TextSecondary = Color(0xFFA3A3B8)
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
    // Durchsichtig: Hintergrund samt Lichtflecken zeichnet GlowBackground hinter jeden Screen.
    background = Color.Transparent,
    onBackground = Neon.TextPrimary,
    surface = Color.Transparent,
    onSurface = Neon.TextPrimary,
    surfaceVariant = Neon.SurfaceHigh,
    onSurfaceVariant = Neon.TextSecondary,
    surfaceContainerLowest = Neon.Background,
    surfaceContainerLow = Neon.Surface,
    surfaceContainer = Neon.Surface,
    surfaceContainerHigh = Neon.SurfaceHigh,
    surfaceContainerHighest = Neon.SurfaceHigh,
    inverseSurface = Neon.TextPrimary,
    inverseOnSurface = Neon.OnNeon,
    outline = Color(0x40FFFFFF),
    outlineVariant = Neon.Track,
)

/** Bricolage Grotesque (SIL OFL, liegt in der App – kein Laden aus dem Netz). */
private fun bricolage(weight: Int, opticalSize: Float) = Font(
    R.font.bricolage_grotesque,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

/** Für Fließtext und Beschriftungen: optische Größe für kleine Schrift (offener, besser lesbar). */
private val BricolageText = FontFamily(
    bricolage(400, 14f), bricolage(500, 14f), bricolage(600, 14f), bricolage(700, 14f), bricolage(800, 14f),
)

/** Für große Zahlen und Überschriften: optische Größe für große Schrift (enger, mehr Charakter). */
private val BricolageDisplay = FontFamily(bricolage(600, 72f), bricolage(700, 72f), bricolage(800, 72f))

private fun TextStyle.display(weight: FontWeight, tracking: Double, leading: Double? = null) = copy(
    fontFamily = BricolageDisplay,
    fontWeight = weight,
    letterSpacing = tracking.em,
    lineHeight = leading?.em ?: lineHeight,
)

/**
 * Laufweite je Größe (wie Apple): große Zahlen und Überschriften enger und kräftig,
 * kleine Texte unverändert, große Schrift mit knappem Zeilenabstand.
 */
private val NeonTypography = Typography().run {
    val text = copy(
        bodyLarge = bodyLarge.copy(fontFamily = BricolageText),
        bodyMedium = bodyMedium.copy(fontFamily = BricolageText),
        bodySmall = bodySmall.copy(fontFamily = BricolageText),
        titleMedium = titleMedium.copy(fontFamily = BricolageText, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = BricolageText, fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontFamily = BricolageText, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = BricolageText, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = BricolageText, fontWeight = FontWeight.SemiBold),
    )
    text.copy(
        displayLarge = displayLarge.display(FontWeight.ExtraBold, -0.035, 1.0),
        displayMedium = displayMedium.display(FontWeight.ExtraBold, -0.03, 1.02),
        displaySmall = displaySmall.display(FontWeight.ExtraBold, -0.025, 1.05),
        headlineLarge = headlineLarge.display(FontWeight.ExtraBold, -0.02),
        headlineMedium = headlineMedium.display(FontWeight.ExtraBold, -0.015),
        headlineSmall = headlineSmall.display(FontWeight.Bold, -0.01),
        titleLarge = titleLarge.display(FontWeight.Bold, -0.01),
    )
}

/** Immer dunkel – das Neon-Design lebt vom dunklen Hintergrund. */
@Composable
fun KalorienTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NeonColors, typography = NeonTypography, content = content)
}
