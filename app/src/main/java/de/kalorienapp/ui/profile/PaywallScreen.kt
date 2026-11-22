package de.kalorienapp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.account.AiTier
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.LocalBottomBarPadding
import de.kalorienapp.ui.theme.Neon

/**
 * Dunories Premium (Schritt 2, ohne Play Store): zeigt, was die KI kann, und je nach Stufe
 * „Mit Google weiter – 7 Tage gratis“ (Gast) oder „Kaufen bald über Google Play“ + Code einlösen (Konto).
 * Schlicht, ohne Druck – Barcode und Eintragen von Hand bleiben immer kostenlos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.aiStatus.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(Unit) { viewModel.refreshStatus() }

    val tier = status?.tier
    val trialDays = status?.trialDays?.takeIf { it > 0 } ?: 7

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp + LocalBottomBarPadding.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Neon.Yellow,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                when {
                    status?.premium == true -> stringResource(R.string.paywall_subtitle_premium)
                    tier == AiTier.GUEST -> stringResource(R.string.paywall_subtitle_guest, trialDays)
                    else -> stringResource(R.string.paywall_subtitle_free)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            status?.takeIf { it.premium }?.let {
                Text(
                    premiumStatusText(it),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Neon.Yellow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        stringResource(R.string.paywall_benefit_photo),
                        stringResource(R.string.paywall_benefit_label),
                        stringResource(R.string.paywall_benefit_describe),
                        stringResource(R.string.paywall_benefit_weekly),
                        stringResource(R.string.paywall_benefit_import),
                    ).forEach { benefit ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("✓", color = Neon.Green, fontWeight = FontWeight.Bold)
                            Text(benefit, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.paywall_always_free),
                style = MaterialTheme.typography.bodySmall,
                color = Neon.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                status?.premium == true -> Unit
                state.user == null -> {
                    Button(
                        onClick = { activity?.let(viewModel::startGoogle) },
                        enabled = busy == null,
                        colors = ButtonDefaults.buttonColors(containerColor = Neon.Yellow, contentColor = Neon.OnNeon),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.paywall_trial_button, trialDays)) }
                    Text(
                        stringResource(R.string.paywall_code_needs_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.paywall_buy_soon))
                    }
                    OutlinedButton(onClick = viewModel::openRedeem, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.paywall_have_code))
                    }
                }
            }
            busy?.let { Text(stringResource(it.textRes), style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally)) }
            if (busy == null) {
                message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally)) }
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.back))
            }
        }
    }
    AccountDialogs(viewModel, onPrivacy)
}
