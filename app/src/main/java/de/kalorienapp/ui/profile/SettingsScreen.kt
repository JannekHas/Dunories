package de.kalorienapp.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.changelog.ChangelogDialog
import de.kalorienapp.ui.changelog.ChangelogUi
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.LocalBottomBarPadding
import de.kalorienapp.ui.fasting.FastingSettingsSection
import de.kalorienapp.ui.plan.MealPlanSettingsSection
import de.kalorienapp.ui.theme.Neon

/**
 * Einstellungen hinter dem Zahnrad im Profil (Neuordnung Nov. 2026, Variante A des Nutzers): sechs Kategorien,
 * jede öffnet eine eigene kurze Seite – statt einer langen Liste im Profil.
 */
enum class SettingsCategory(val titleRes: Int, val textRes: Int, val icon: ImageVector) {
    ACCOUNT(R.string.settings_cat_account, R.string.settings_cat_account_text, Icons.Filled.AccountCircle),
    AREAS(R.string.settings_cat_areas, R.string.settings_cat_areas_text, Icons.AutoMirrored.Filled.List),
    NOTIFICATIONS(R.string.settings_cat_notifications, R.string.settings_cat_notifications_text, Icons.Filled.Notifications),
    AI(R.string.settings_cat_ai, R.string.settings_cat_ai_text, Icons.Filled.Star),
    DATA(R.string.settings_cat_data, R.string.settings_cat_data_text, Icons.Filled.Lock),
    HELP(R.string.settings_cat_help, R.string.settings_cat_help_text, Icons.Filled.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (SettingsCategory) -> Unit) {
    SubPage(stringResource(R.string.settings_title), onBack) {
        GlassCard(Modifier.fillMaxWidth()) {
            SettingsCategory.entries.forEachIndexed { index, category ->
                if (index > 0) HorizontalDivider(color = Neon.TextSecondary.copy(alpha = 0.15f), modifier = Modifier.padding(horizontal = 16.dp))
                CategoryRow(category) { onOpen(category) }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: SettingsCategory, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon, contentDescription = null, tint = Neon.Cyan)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(category.titleRes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(category.textRes), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Neon.TextSecondary)
    }
}

/** Eine Kategorie der Einstellungen – die bisherigen Abschnitte, nur einzeln. */
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
    onOpenPlan: () -> Unit = {},
    onFeedback: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onPremium: () -> Unit = {},
    onYazio: () -> Unit = {},
) {
    SubPage(stringResource(category.titleRes), onBack) {
        when (category) {
            SettingsCategory.ACCOUNT -> AccountSection(onPrivacy = onPrivacy, onPremium = onPremium, part = AccountPart.ACCOUNT)
            SettingsCategory.AREAS -> {
                Text(stringResource(R.string.areas_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                SectionTitle(stringResource(R.string.areas_food))
                ProteinOnlySection()
                WaterSettingsSection()
                MealPlanSettingsSection(onOpenPlan)
                FastingSettingsSection()
                HorizontalDivider()
                SectionTitle(stringResource(R.string.areas_body))
                WeightTrackingSection()
                BodyAnalysisSettingSection()
                MeasuresSettingSection()
                HorizontalDivider()
                SectionTitle(stringResource(R.string.areas_social))
                GamificationSettingSection()
            }
            SettingsCategory.NOTIFICATIONS -> NotificationSettingsSection()
            SettingsCategory.AI -> {
                AccountSection(onPrivacy = onPrivacy, onPremium = onPremium, part = AccountPart.PREMIUM)
                Text(stringResource(R.string.settings_ai_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
            SettingsCategory.DATA -> {
                BackupSection()
                // Andere Apps haben keinen Export – die Serie kommt per Screenshot (Wunsch des Nutzers, Nov. 2026).
                OutlinedButton(onClick = onYazio, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.yazio_settings_entry))
                }
            }
            SettingsCategory.HELP -> {
                FeedbackEntry(onFeedback)
                var showChangelog by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { showChangelog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.changelog_open))
                }
                OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.privacy_title))
                }
                if (showChangelog) ChangelogDialog(ChangelogUi.VERSIONS.takeLast(1), onClose = { showChangelog = false })
            }
        }
    }
}

/** Unterseite mit Zurück-Pfeil und scrollbarem Inhalt (wie Datenschutz). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp + LocalBottomBarPadding.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
internal fun FeedbackEntry(onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_feedback), contentDescription = null, tint = Neon.Cyan)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(stringResource(R.string.feedback_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.feedback_entry_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            }
        }
    }
}
