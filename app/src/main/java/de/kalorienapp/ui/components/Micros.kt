package de.kalorienapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.Micro
import de.kalorienapp.domain.model.MicroUnit
import de.kalorienapp.domain.model.Sex
import de.kalorienapp.ui.theme.Neon
import kotlin.math.roundToInt

@get:androidx.annotation.StringRes
val Micro.labelRes: Int
    get() = when (this) {
        Micro.VITAMIN_A -> R.string.micro_vitamin_a
        Micro.VITAMIN_C -> R.string.micro_vitamin_c
        Micro.VITAMIN_D -> R.string.micro_vitamin_d
        Micro.VITAMIN_B12 -> R.string.micro_vitamin_b12
        Micro.FOLATE -> R.string.micro_folate
        Micro.CALCIUM -> R.string.micro_calcium
        Micro.IRON -> R.string.micro_iron
        Micro.MAGNESIUM -> R.string.micro_magnesium
        Micro.ZINC -> R.string.micro_zinc
        Micro.POTASSIUM -> R.string.micro_potassium
    }

@get:androidx.annotation.StringRes
val Micro.unitRes: Int
    get() = if (unit == MicroUnit.MICROGRAM) R.string.unit_ug else R.string.unit_mg

/** Menge mit sinnvollen Nachkommastellen (Vitamin D/B12 klein, Kalium groß). */
fun Micro.formatValue(value: Double): String = value.format(if (value < 10) 1 else 0)

/**
 * Vitamine/Mineralstoffe als Liste. Mit [sex] je Zeile ein Balken zum DGE-Tageswert.
 * Nur angegebene Werte; [showMissing] zeigt die übrigen mit „–“.
 */
@Composable
fun MicroList(values: Map<Micro, Double>, sex: Sex?, modifier: Modifier = Modifier, showMissing: Boolean = false) {
    val shown = Micro.entries.filter { it in values || showMissing }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        shown.forEach { micro ->
            val value = values[micro]
            val unit = stringResource(micro.unitRes)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(micro.labelRes), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.3f))
                Text(
                    value?.let { "${micro.formatValue(it)} $unit" } ?: "–",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                if (sex != null) {
                    val ratio = ((value ?: 0.0) / micro.reference(sex)).toFloat()
                    Box(
                        Modifier.padding(start = 10.dp).weight(1.2f).height(6.dp).clip(RoundedCornerShape(50)).background(Neon.Track),
                    ) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(ratio.coerceIn(0f, 1f))
                                .background(if (ratio >= 1f) Neon.Green else Neon.Cyan),
                        )
                    }
                    Text(
                        "${(ratio * 100).roundToInt()} %",
                        style = MaterialTheme.typography.labelSmall,
                        color = Neon.TextSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(44.dp),
                    )
                }
            }
        }
    }
}
