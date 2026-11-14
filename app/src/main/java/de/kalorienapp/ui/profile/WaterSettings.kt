package de.kalorienapp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.AppSettings
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.theme.Neon

class WaterSettingsViewModel(private val settings: AppSettings) : ViewModel() {
    val enabled = settings.waterEnabled

    fun setEnabled(enabled: Boolean) = settings.setWaterEnabled(enabled)
}

/** Wasser an/aus (standardmäßig aus); Ziel stellt man auf der Wasser-Karte auf „Heute“ ein. */
@Composable
fun WaterSettingsSection(viewModel: WaterSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(R.string.water_enable), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.water_enable_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }
        Switch(
            checked = enabled,
            onCheckedChange = viewModel::setEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.OnNeon),
        )
    }
}
