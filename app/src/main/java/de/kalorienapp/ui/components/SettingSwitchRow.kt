package de.kalorienapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.kalorienapp.ui.theme.Neon

/**
 * Einstellung mit Schalter: Titel, optional Erklärung, rechts der Schalter. Die ganze Zeile ist antippbar und
 * für TalkBack EIN Element („Wasser tracken, Schalter, aus“) – vorher las TalkBack nur „Schalter“ ohne Namen
 * (Barrierefreiheits-Check Nov. 2026).
 */
@Composable
fun SettingSwitchRow(
    title: String,
    text: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (text != null) {
                Text(text, style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.OnNeon),
        )
    }
}
