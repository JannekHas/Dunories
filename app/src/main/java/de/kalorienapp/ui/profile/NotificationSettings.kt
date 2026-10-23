package de.kalorienapp.ui.profile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.AppSettings
import de.kalorienapp.notify.CalorieNotifier
import de.kalorienapp.notify.Notifications
import de.kalorienapp.notify.WorkScheduler
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.launch

class NotificationSettingsViewModel(
    private val settings: AppSettings,
    private val notifier: CalorieNotifier,
    private val scheduler: WorkScheduler,
) : ViewModel() {
    val calories = settings.caloriesNotification
    val weightDays = settings.weightReminderDays
    val weekly = settings.weeklyReport
    val mealReminders = settings.mealReminders

    fun setMealReminders(enabled: Boolean) {
        settings.setMealReminders(enabled)
        viewModelScope.launch { scheduler.scheduleMealReminder() }
    }

    fun setCalories(enabled: Boolean) {
        settings.setCaloriesNotification(enabled)
        viewModelScope.launch { notifier.refresh() }
    }

    fun setWeightDays(days: Int?) {
        settings.setWeightReminderDays(days)
        viewModelScope.launch { scheduler.scheduleWeightReminder() }
    }

    fun setWeekly(enabled: Boolean) {
        settings.setWeeklyReport(enabled)
        scheduler.scheduleWeekly()
    }

    /** Erlaubnis gerade erteilt → Kalorien-Benachrichtigung sofort anzeigen. */
    fun permissionGranted() {
        viewModelScope.launch { notifier.refresh() }
    }
}

/** Schalter für Kalorien-Benachrichtigung, Wiege-Erinnerung und automatische KI-Wochenauswertung. */
@Composable
fun NotificationSettingsSection(
    viewModel: NotificationSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val calories by viewModel.calories.collectAsStateWithLifecycle()
    val weightDays by viewModel.weightDays.collectAsStateWithLifecycle()
    val weekly by viewModel.weekly.collectAsStateWithLifecycle()
    val mealReminders by viewModel.mealReminders.collectAsStateWithLifecycle()
    var canPost by remember { mutableStateOf(Notifications.canPost(context)) }

    // Nach Rückkehr aus den System-Einstellungen neu prüfen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { canPost = Notifications.canPost(context) }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost = Notifications.canPost(context)
        if (granted) viewModel.permissionGranted()
    }
    fun ensurePermission() {
        if (!canPost && Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!canPost && (calories || weightDays != null || weekly)) {
            Column {
                Text(stringResource(R.string.notif_permission_missing), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Text(stringResource(R.string.notif_permission_open)) }
            }
        }
        SwitchRow(
            title = stringResource(R.string.notif_setting_calories),
            text = stringResource(R.string.notif_setting_calories_text),
            checked = calories,
            onChange = { viewModel.setCalories(it); if (it) ensurePermission() },
        )
        SwitchRow(
            title = stringResource(R.string.notif_setting_weigh),
            text = stringResource(R.string.notif_setting_weigh_text),
            checked = weightDays != null,
            onChange = { on ->
                viewModel.setWeightDays(if (on) AppSettings.DEFAULT_WEIGHT_DAYS else null)
                if (on) ensurePermission()
            },
        )
        weightDays?.let { days ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.notif_weigh_after), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                val colors = IconButtonDefaults.filledIconButtonColors(containerColor = Neon.SurfaceHighest, contentColor = Neon.TextPrimary)
                FilledIconButton(onClick = { viewModel.setWeightDays((days - 1).coerceAtLeast(1)) }, colors = colors, modifier = Modifier.size(32.dp)) {
                    Text(stringResource(R.string.minus), fontWeight = FontWeight.Bold)
                }
                Text(
                    pluralStringResource(R.plurals.days_count, days, days),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                FilledIconButton(onClick = { viewModel.setWeightDays((days + 1).coerceAtMost(14)) }, colors = colors, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
        SwitchRow(
            title = stringResource(R.string.notif_setting_meals),
            text = stringResource(R.string.notif_setting_meals_text),
            checked = mealReminders,
            onChange = { viewModel.setMealReminders(it); if (it) ensurePermission() },
        )
        SwitchRow(
            title = stringResource(R.string.notif_setting_report),
            text = stringResource(R.string.notif_setting_report_text),
            checked = weekly,
            onChange = { viewModel.setWeekly(it); if (it) ensurePermission() },
        )
    }
}

@Composable
private fun SwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.OnNeon),
        )
    }
}
