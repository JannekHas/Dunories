package de.kalorienapp.ui.goal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.kalorienapp.R
import de.kalorienapp.domain.GoalSuccess
import de.kalorienapp.domain.model.GoalEndReason
import de.kalorienapp.ui.components.ConcentricRings
import de.kalorienapp.ui.components.GlowBackground
import de.kalorienapp.ui.components.RingSpec
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.theme.Neon
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Feste Entwurfsbreite der Teilen-Bilder. Die Vorschau skaliert diese Fläche nur (eigene Density),
 * damit Vorschau und exportiertes Bild in jedem Format gleich aussehen.
 */
internal val ShareDesignWidth = 360.dp

/** Alles, was ein Design zum Zeichnen braucht. */
private class ShareData(val s: GoalSuccess, val o: ShareOptions, val headline: String) {
    val reached get() = s.goal.endReason == GoalEndReason.REACHED
    val square get() = o.format == ShareFormat.SQUARE
    val story get() = o.format == ShareFormat.STORY
    fun has(field: ShareField) = o.shows(field) && (field != ShareField.ON_TARGET || s.onTargetRate != null)
}

/** Betrag der Veränderung ohne Vorzeichen („1,4“) – die Richtung steht als Wort daneben (Wunsch des Nutzers: kein Minus). */
internal fun GoalSuccess.amountText(): String = abs(changeKg).toInputString()

/** „abgenommen“ / „zugenommen“ / „gehalten“. */
@Composable
internal fun GoalSuccess.changeWord(): String = stringResource(
    when {
        changeKg < -0.05 -> R.string.change_lost
        changeKg > 0.05 -> R.string.change_gained
        else -> R.string.change_kept
    },
)

/** Ein teilbares Bild im gewählten [template], Format und Hintergrund (Größe: [ShareDesignWidth] × Format). */
@Composable
internal fun ShareCard(s: GoalSuccess, o: ShareOptions, template: ShareTemplate, photo: ImageBitmap?) {
    val reached = s.goal.endReason == GoalEndReason.REACHED
    val headline = o.headline.trim().ifEmpty {
        stringResource(if (reached) R.string.share_card_reached else R.string.share_card_date)
    }
    val d = ShareData(s, o.copy(template = template), headline)
    Box(Modifier.size(ShareDesignWidth, ShareDesignWidth / o.format.ratio)) {
        when {
            o.background == ShareBackground.TRANSPARENT -> Unit
            o.background == ShareBackground.PHOTO && photo != null -> {
                Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                // Abdunkeln, damit Schrift auf jedem Foto lesbar bleibt.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.75f)),
                        ),
                    ),
                )
            }
            else -> GlowBackground {}
        }
        when (template) {
            ShareTemplate.POSTER -> PosterTemplate(d)
            ShareTemplate.NUMBER -> NumberTemplate(d)
            ShareTemplate.CHART -> ChartTemplate(d)
            ShareTemplate.TILES -> TilesTemplate(d)
            ShareTemplate.BADGE -> BadgeTemplate(d)
            ShareTemplate.STICKER -> StickerTemplate(d)
        }
    }
}

// ---------- Designs ----------

/** Poster: die Zahlen groß und füllend, die Ringe nur als kleines Zeichen oben rechts. */
@Composable
private fun PosterTemplate(d: ShareData) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark()
            Spacer(Modifier.weight(1f))
            FullRings(d, Modifier.size(40.dp), stroke = 4.dp, gap = 2.dp)
        }
        if (!d.square) Spacer(Modifier.weight(0.5f))
        Headline(d.headline)
        if (d.has(ShareField.CHANGE)) {
            if (d.square) {
                // Im Quadrat Zahl und Wort nebeneinander – so bleibt Höhe für den Verlauf.
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountText(d.s, 52.sp, Modifier.weight(1f, fill = false))
                    Box(Modifier.padding(bottom = 8.dp)) { ChangeWord(d.s) }
                }
            } else {
                AmountText(d.s, 96.sp, Modifier.fillMaxWidth())
                ChangeWord(d.s)
            }
        }
        if (d.has(ShareField.CHART)) {
            WeightChart(
                d.s.weightPoints,
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
                labels = d.has(ShareField.WEIGHTS),
                strokeWidth = 4.dp,
            )
        } else {
            if (d.has(ShareField.WEIGHTS)) WeightsText(d.s)
            Spacer(Modifier.weight(1f))
        }
        FactChips(d)
    }
}

/** Große Zahl: die Veränderung riesig in Regenbogenfarben. */
@Composable
private fun NumberTemplate(d: ShareData) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Headline(d.headline, textAlign = TextAlign.Center)
        BasicText(
            d.s.amountText(),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 1.1.em,
                textAlign = TextAlign.Center,
                brush = Brush.linearGradient(listOf(Neon.Cyan, Neon.Green, Neon.Yellow, Neon.Orange, Neon.Magenta)),
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = if (d.square) 110.sp else 140.sp, stepSize = 2.sp),
        )
        Text(
            stringResource(R.string.unit_kg) + " " + d.s.changeWord(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Neon.TextPrimary,
        )
        if (d.has(ShareField.WEIGHTS)) {
            Spacer(Modifier.height(8.dp))
            WeightsText(d.s)
        }
        Spacer(Modifier.height(18.dp))
        FactChips(d, center = true)
        Spacer(Modifier.weight(1f))
        Wordmark()
    }
}

/** Verlauf: der Gewichtsverlauf füllt das Bild, Start- und Endgewicht als Beschriftung. */
@Composable
private fun ChartTemplate(d: ShareData) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Wordmark()
        Spacer(Modifier.height(if (d.story) 32.dp else 4.dp))
        Headline(d.headline)
        if (d.has(ShareField.CHANGE)) {
            AmountText(d.s, if (d.square) 48.sp else 64.sp, Modifier.fillMaxWidth())
            ChangeWord(d.s)
        }
        WeightChart(
            d.s.weightPoints,
            Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
            labels = d.has(ShareField.WEIGHTS),
            strokeWidth = 4.dp,
        )
        FactText(d)
        if (d.story) Spacer(Modifier.height(24.dp))
    }
}

/** Kacheln: jede Kennzahl als kräftiger Farbblock, Werte zentriert und so groß wie möglich. */
@Composable
private fun TilesTemplate(d: ShareData) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(height = 20.dp)
            Spacer(Modifier.weight(1f))
            Headline(d.headline)
        }
        if (d.has(ShareField.CHANGE)) {
            Tile(Neon.Green, Modifier.fillMaxWidth().weight(1.4f), withKg(d.s.amountText()), d.s.changeWord(), maxSize = 96.sp)
        }
        if (d.has(ShareField.DURATION) || d.has(ShareField.ON_TARGET)) TileRow {
            if (d.has(ShareField.DURATION)) {
                Tile(
                    Neon.Cyan,
                    Modifier.weight(1f).fillMaxSize(),
                    AnnotatedString(d.s.days.toString()),
                    pluralStringResource(R.plurals.goal_celebrate_days_label, d.s.days),
                )
            }
            if (d.has(ShareField.ON_TARGET)) d.s.onTargetRate?.let { rate ->
                Tile(
                    Neon.Yellow,
                    Modifier.weight(1f).fillMaxSize(),
                    AnnotatedString("${(rate * 100).roundToInt()} %"),
                    stringResource(R.string.goal_celebrate_ontarget_label),
                )
            }
        }
        if (d.has(ShareField.CHART)) {
            Box(
                Modifier.fillMaxWidth().weight(1.3f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                WeightChart(d.s.weightPoints, Modifier.fillMaxSize(), labels = d.has(ShareField.WEIGHTS))
            }
        }
        if ((d.has(ShareField.WEIGHTS) && !d.has(ShareField.CHART)) || d.has(ShareField.DATES)) TileRow {
            if (d.has(ShareField.WEIGHTS) && !d.has(ShareField.CHART)) {
                Tile(
                    Neon.Magenta,
                    Modifier.weight(1f).fillMaxSize(),
                    withKg("${d.s.goal.startWeightKg.toInputString()} → ${d.s.endWeightKg.toInputString()}"),
                    stringResource(R.string.share_card_weights_label),
                )
            }
            if (d.has(ShareField.DATES)) {
                Tile(
                    Neon.Orange,
                    Modifier.weight(1f).fillMaxSize(),
                    AnnotatedString(rangeText(d.s)),
                    stringResource(R.string.share_card_range_label),
                    maxLines = 2,
                )
            }
        }
    }
}

/** Medaille: dünne Ringe als Abzeichen, Überschrift und Veränderung in der Mitte. */
@Composable
private fun BadgeTemplate(d: ShareData) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        FullRings(d, Modifier.fillMaxWidth(if (d.square) 0.78f else 0.9f), stroke = 7.dp, gap = 3.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.62f)) {
                Headline(d.headline, textAlign = TextAlign.Center)
                if (d.has(ShareField.CHANGE)) {
                    AmountText(d.s, 56.sp, Modifier.fillMaxWidth(), align = TextAlign.Center)
                    ChangeWord(d.s, small = true)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        FactText(d, center = true)
        Spacer(Modifier.weight(1f))
        Wordmark()
    }
}

/** Sticker: kleine Plakette in der Mitte – ideal mit transparentem Hintergrund oder eigenem Foto. */
@Composable
private fun StickerTemplate(d: ShareData) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FullRings(d, Modifier.size(64.dp), stroke = 5.dp, gap = 2.5.dp)
                Column {
                    Headline(d.headline)
                    Text(
                        withKg(d.s.amountText()),
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 1.1.em),
                        color = Neon.TextPrimary,
                        maxLines = 1,
                    )
                    ChangeWord(d.s, small = true)
                }
            }
            FactChips(d, center = true)
            Wordmark(height = 18.dp)
        }
    }
}

// ---------- Bausteine ----------

@Composable
private fun Wordmark(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    Image(
        painterResource(R.drawable.wordmark_dunories),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier.height(height),
    )
}

@Composable
private fun Headline(text: String, textAlign: TextAlign? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
        color = Neon.Cyan,
        fontWeight = FontWeight.ExtraBold,
        textAlign = textAlign,
    )
}

/** „1,4 kg“ mit kleinerer Einheit. */
@Composable
private fun withKg(value: String): AnnotatedString {
    val kg = stringResource(R.string.unit_kg)
    return buildAnnotatedString {
        append(value)
        withStyle(SpanStyle(fontSize = 0.42.em)) { append(" $kg") }
    }
}

/** Die Veränderung ohne Vorzeichen, so groß wie möglich (höchstens [maxSize]). */
@Composable
private fun AmountText(
    s: GoalSuccess,
    maxSize: TextUnit,
    modifier: Modifier,
    color: Color = Neon.TextPrimary,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        withKg(s.amountText()),
        modifier = modifier,
        style = MaterialTheme.typography.displayLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 1.05.em,
            color = color,
            textAlign = align,
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = maxSize, stepSize = 2.sp),
    )
}

/** „abgenommen“ in Grün (bzw. „zugenommen“ / „gehalten“). */
@Composable
private fun ChangeWord(s: GoalSuccess, small: Boolean = false) {
    Text(
        s.changeWord(),
        style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Neon.Green,
    )
}

@Composable
private fun WeightsText(s: GoalSuccess) {
    Text(
        stringResource(R.string.goal_celebrate_route, s.goal.startWeightKg.toInputString(), s.endWeightKg.toInputString()),
        style = MaterialTheme.typography.titleLarge,
        color = Neon.TextPrimary.copy(alpha = 0.9f),
    )
}

@Composable
private fun FactChips(d: ShareData, center: Boolean = false) {
    val duration = d.has(ShareField.DURATION)
    val onTarget = d.has(ShareField.ON_TARGET)
    val dates = d.has(ShareField.DATES)
    if (!duration && !onTarget && !dates) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (duration) ShareChip(pluralStringResource(R.plurals.share_card_days, d.s.days, d.s.days), Neon.Cyan)
        if (onTarget) d.s.onTargetRate?.let { ShareChip(stringResource(R.string.share_card_ontarget, (it * 100).roundToInt()), Neon.Yellow) }
        if (dates) ShareChip(rangeText(d.s), Neon.Violet)
    }
}

/** Dauer und Zeitraum als eine ruhige Textzeile. */
@Composable
private fun FactText(d: ShareData, center: Boolean = false) {
    val facts = listOfNotNull(
        if (d.has(ShareField.DURATION)) pluralStringResource(R.plurals.share_card_days, d.s.days, d.s.days) else null,
        if (d.has(ShareField.DATES)) rangeText(d.s) else null,
    )
    if (facts.isEmpty()) return
    Text(
        facts.joinToString("  ·  "),
        style = MaterialTheme.typography.titleMedium,
        color = Neon.TextPrimary.copy(alpha = 0.9f),
        textAlign = if (center) TextAlign.Center else null,
    )
}

@Composable
private fun ShareChip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Neon.OnNeon,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun ColumnScope.TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Farbkachel: Wert zentriert und so groß, wie die Kachel es zulässt, Bezeichnung darunter. */
@Composable
private fun Tile(
    color: Color,
    modifier: Modifier,
    value: AnnotatedString,
    label: String,
    maxSize: TextUnit = 64.sp,
    maxLines: Int = 1,
) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(color).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BasicText(
                value,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 1.1.em,
                    color = Neon.OnNeon,
                    textAlign = TextAlign.Center,
                ),
                maxLines = maxLines,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = maxSize, stepSize = 1.sp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Neon.OnNeon.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun FullRings(
    d: ShareData,
    modifier: Modifier,
    stroke: Dp,
    gap: Dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val fill = if (d.reached) 1f else d.s.progress.toFloat()
    ConcentricRings(
        rings = listOf(
            RingSpec(fill, Neon.Rainbow),
            RingSpec(fill, listOf(Neon.Magenta)),
            RingSpec(fill, listOf(Neon.Yellow)),
            RingSpec(fill, listOf(Neon.Green)),
        ),
        modifier = modifier,
        strokeWidth = stroke,
        gap = gap,
        center = center,
    )
}

@Composable
internal fun rangeText(s: GoalSuccess): String {
    val start = s.goal.startDate
    val end = s.goal.endedDate ?: LocalDate.now()
    return if (start == end) start.formatGoalDate() else stringResource(R.string.share_card_range, start.formatGoalDate(), end.formatGoalDate())
}

/**
 * Gewichtsverlauf ohne Achsen: Linie mit weichem Verlauf darunter, Start cyan, Ende grün.
 * [labels] schreibt Start- und Endgewicht an die Punkte.
 */
@Composable
internal fun WeightChart(
    points: List<Pair<LocalDate, Double>>,
    modifier: Modifier,
    labels: Boolean = false,
    strokeWidth: Dp = 3.dp,
    lineColor: Color = Neon.Violet,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.titleSmall.copy(color = Neon.TextPrimary, fontWeight = FontWeight.Bold)
    val unit = stringResource(R.string.unit_kg)
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val first = points.first().first.toEpochDay()
        val spanDays = points.last().first.toEpochDay() - first
        val min = points.minOf { it.second }
        val range = (points.maxOf { it.second } - min).coerceAtLeast(0.5)
        val padX = 8.dp.toPx()
        // Bei flachen Diagrammen weniger Rand, sonst kippt die Linie.
        val padY = minOf(if (labels) 30.dp.toPx() else 8.dp.toPx(), size.height * 0.3f)
        val offsets = points.mapIndexed { i, (date, kg) ->
            val fx = if (spanDays == 0L) i / (points.size - 1f) else (date.toEpochDay() - first) / spanDays.toFloat()
            Offset(
                padX + fx * (size.width - 2 * padX),
                padY + ((1 - (kg - min) / range) * (size.height - 2 * padY)).toFloat(),
            )
        }
        val line = Path().apply { offsets.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        val area = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), Color.Transparent), startY = offsets.minOf { it.y }, endY = size.height),
        )
        drawPath(line, lineColor, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Neon.Cyan, strokeWidth.toPx() * 1.8f, offsets.first())
        drawCircle(Neon.Green, strokeWidth.toPx() * 2.1f, offsets.last())
        if (labels) {
            val gap = 8.dp.toPx()
            listOf(0, points.lastIndex).forEach { i ->
                val text = measurer.measure("${points[i].second.toInputString()} $unit", labelStyle)
                val o = offsets[i]
                val x = if (i == 0) o.x - padX / 2 else o.x - text.size.width + padX / 2
                val y = if (o.y < size.height / 2) o.y - text.size.height - gap else o.y + gap
                drawText(
                    text,
                    topLeft = Offset(
                        x.coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f)),
                        y.coerceIn(0f, (size.height - text.size.height).coerceAtLeast(0f)),
                    ),
                )
            }
        }
    }
}
