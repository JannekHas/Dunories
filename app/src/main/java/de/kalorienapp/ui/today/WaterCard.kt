package de.kalorienapp.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.domain.Water
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.theme.Neon
import kotlin.math.roundToInt

/** Getrunken / Ziel; [customGoal] = selbst gesetztes Ziel (sonst automatisch). */
data class WaterState(val ml: Int = 0, val goalMl: Int = 2500, val customGoal: Boolean = false)

private fun liters(ml: Int) = (ml / 1000.0).format(2).trimEnd('0').trimEnd(',')

/** Wasser auf „Heute“: Fortschritt, Glas/Flasche hinzufügen, letzten Eintrag zurücknehmen, Ziel ändern. */
@Composable
fun WaterCard(
    state: WaterState,
    onAdd: (Int) -> Unit,
    onUndo: () -> Unit,
    onGoal: (Int?) -> Unit,
) {
    var editGoal by remember { mutableStateOf(false) }
    if (editGoal) {
        WaterGoalDialog(state, onSave = { onGoal(it); editGoal = false }, onDismiss = { editGoal = false })
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Eine Zeile: Titel + Stand (antippen = Ziel ändern), zwei Schnellknöpfe, Rückgängig.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = stringResource(R.string.water_goal_title)) { editGoal = true },
                ) {
                    Icon(painterResource(R.drawable.ic_water_drop), contentDescription = null, tint = Neon.Blue, modifier = Modifier.size(20.dp))
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(stringResource(R.string.water_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.water_value, liters(state.ml), liters(state.goalMl)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.ml >= state.goalMl) Neon.Green else Neon.TextSecondary,
                        )
                    }
                }
                val padding = PaddingValues(horizontal = 10.dp)
                Button(
                    onClick = { onAdd(Water.GLASS_ML) },
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Blue, contentColor = Neon.OnNeon),
                    contentPadding = padding,
                    modifier = Modifier.height(36.dp),
                ) { Text(stringResource(R.string.water_glass), style = MaterialTheme.typography.labelMedium) }
                Button(
                    onClick = { onAdd(Water.BOTTLE_ML) },
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    contentPadding = padding,
                    modifier = Modifier.height(36.dp),
                ) { Text(stringResource(R.string.water_bottle), style = MaterialTheme.typography.labelMedium) }
                IconButton(onClick = onUndo, enabled = state.ml > 0, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.ic_undo), stringResource(R.string.water_undo), tint = Neon.TextSecondary)
                }
            }
            LinearProgressIndicator(
                progress = { (state.ml.toFloat() / state.goalMl).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = Neon.Blue,
                trackColor = Neon.Track,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun WaterGoalDialog(state: WaterState, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (state.customGoal) liters(state.goalMl) else "") }
    val liters = text.parseDecimal()?.takeIf { it in 0.5..8.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.water_goal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.water_goal_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                NumberField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.water_goal_label),
                    suffix = stringResource(R.string.unit_liter),
                    isError = text.isNotBlank() && liters == null,
                    errorText = stringResource(R.string.error_number),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(liters?.let { (it * 1000).roundToInt() }) },
                enabled = text.isBlank() || liters != null,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
