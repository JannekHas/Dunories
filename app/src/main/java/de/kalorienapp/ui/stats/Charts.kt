package de.kalorienapp.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.kalorienapp.domain.TrendPoint
import de.kalorienapp.domain.model.DayStats
import de.kalorienapp.domain.model.WeightEntry
import de.kalorienapp.ui.calendar.statusColor
import de.kalorienapp.ui.theme.Neon
import java.time.ZoneId

/** Balken je Tag (Farbe = Zielerreichung), gestrichelte Linie = Budget. */
@Composable
fun KcalBarChart(days: List<DayStats>, modifier: Modifier = Modifier) {
    Canvas(modifier.height(160.dp)) {
        if (days.isEmpty()) return@Canvas
        val maxValue = days.maxOf { maxOf(it.eaten.kcal, it.budgetKcal.toDouble()) }.coerceAtLeast(1.0) * 1.1
        val slot = size.width / days.size
        val barWidth = (slot * 0.65f).coerceAtMost(28.dp.toPx())
        days.forEachIndexed { index, day ->
            val x = slot * index + (slot - barWidth) / 2
            // Hintergrund-Spur
            drawRoundRect(
                Neon.Track, Offset(x, 0f), Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth / 2), alpha = 0.35f,
            )
            if (day.hasEntries) {
                val h = (day.eaten.kcal / maxValue * size.height).toFloat()
                drawRoundRect(
                    statusColor(day.status), Offset(x, size.height - h), Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
            val by = (size.height - day.budgetKcal / maxValue * size.height).toFloat()
            drawLine(
                Neon.TextPrimary, Offset(x - 2f, by), Offset(x + barWidth + 2f, by), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
            )
        }
    }
}

/** Gewichtsverlauf: Messungen als Punkte/Linie, [trend] als geglättete Linie (y-Achse automatisch). */
@Composable
fun WeightLineChart(entries: List<WeightEntry>, trend: List<TrendPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier.height(160.dp)) {
        if (entries.size < 2) return@Canvas
        val min = minOf(entries.minOf { it.weightKg }, trend.minOfOrNull { it.weightKg } ?: Double.MAX_VALUE)
        val max = maxOf(entries.maxOf { it.weightKg }, trend.maxOfOrNull { it.weightKg } ?: -Double.MAX_VALUE)
        val range = (max - min).coerceAtLeast(1.0)
        // x-Achse nach Messzeitpunkt – mehrere Messungen am selben Tag liegen nebeneinander.
        val first = entries.first().measuredAt.toEpochMilli()
        val span = (entries.last().measuredAt.toEpochMilli() - first).coerceAtLeast(1L).toFloat()
        val pad = 8.dp.toPx()
        fun point(e: WeightEntry) = Offset(
            pad + (e.measuredAt.toEpochMilli() - first) / span * (size.width - 2 * pad),
            pad + ((max - e.weightKg) / range).toFloat() * (size.height - 2 * pad),
        )
        val path = Path()
        entries.forEachIndexed { i, e ->
            val p = point(e)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, Neon.Cyan.copy(alpha = 0.45f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        entries.forEach { drawCircle(Neon.Cyan, 3.5.dp.toPx(), point(it)) }
        // Trend (Tagesmitte) darüber – kräftiger, damit er die Aussage trägt.
        if (trend.size >= 2) {
            val zone = ZoneId.systemDefault()
            val trendPath = Path()
            trend.forEachIndexed { i, t ->
                val millis = t.date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                val x = (pad + (millis - first) / span * (size.width - 2 * pad)).coerceIn(pad, size.width - pad)
                val y = pad + ((max - t.weightKg) / range).toFloat() * (size.height - 2 * pad)
                if (i == 0) trendPath.moveTo(x, y) else trendPath.lineTo(x, y)
            }
            drawPath(trendPath, Neon.Violet, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
