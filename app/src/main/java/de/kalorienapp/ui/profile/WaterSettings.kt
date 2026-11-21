package de.kalorienapp.ui.profile

import de.kalorienapp.ui.components.SettingSwitchRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.AppSettings
import de.kalorienapp.ui.AppViewModelProvider

class WaterSettingsViewModel(private val settings: AppSettings) : ViewModel() {
    val enabled = settings.waterEnabled

    fun setEnabled(enabled: Boolean) = settings.setWaterEnabled(enabled)

    val proteinOnly = settings.proteinOnly

    fun setProteinOnly(enabled: Boolean) = settings.setProteinOnly(enabled)

    val weightEnabled = settings.weightEnabled

    fun setWeightEnabled(enabled: Boolean) = settings.setWeightEnabled(enabled)

    val bodyAnalysisEnabled = settings.bodyAnalysisEnabled

    fun setBodyAnalysisEnabled(enabled: Boolean) = settings.setBodyAnalysisEnabled(enabled)
}

/** „Körperanalyse“ (Bioimpedanz-Waage) – nur sichtbar, wenn Gewicht getrackt wird. */
@Composable
fun BodyAnalysisSettingSection(viewModel: WaterSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val weight by viewModel.weightEnabled.collectAsStateWithLifecycle()
    val enabled by viewModel.bodyAnalysisEnabled.collectAsStateWithLifecycle()
    if (!weight) return
    SettingSwitchRow(
        stringResource(R.string.body_analysis_enable),
        stringResource(R.string.body_analysis_enable_text),
        enabled,
        viewModel::setBodyAnalysisEnabled,
    )
}

/** „Gewicht tracken“ (standardmäßig an) – aus für alle, die mit Wiegen und Gewicht nichts zu tun haben wollen. */
@Composable
fun WeightTrackingSection(viewModel: WaterSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val enabled by viewModel.weightEnabled.collectAsStateWithLifecycle()
    SettingSwitchRow(stringResource(R.string.weight_enable), stringResource(R.string.weight_enable_text), enabled, viewModel::setWeightEnabled)
}

/** „Nur Eiweiß tracken“ (standardmäßig aus): Kalorien, KH und Fett verschwinden überall. */
@Composable
fun ProteinOnlySection(viewModel: WaterSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val enabled by viewModel.proteinOnly.collectAsStateWithLifecycle()
    SettingSwitchRow(stringResource(R.string.protein_only_enable), stringResource(R.string.protein_only_enable_text), enabled, viewModel::setProteinOnly)
}

/** Wasser an/aus (standardmäßig aus); Ziel stellt man auf der Wasser-Karte auf „Heute“ ein. */
@Composable
fun WaterSettingsSection(viewModel: WaterSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    SettingSwitchRow(stringResource(R.string.water_enable), stringResource(R.string.water_enable_text), enabled, viewModel::setEnabled)
}
