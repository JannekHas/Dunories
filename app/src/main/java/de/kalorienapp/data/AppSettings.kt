package de.kalorienapp.data

import android.content.Context
import de.kalorienapp.domain.FastingConfig
import de.kalorienapp.domain.FastingPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalTime

/** Einstellungen für Benachrichtigungen (klein, kein Teil des Profils → SharedPreferences statt Room). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _caloriesNotification = MutableStateFlow(prefs.getBoolean(KEY_CALORIES, false))
    /** Dauerhafte Benachrichtigung mit den verbleibenden kcal. */
    val caloriesNotification: StateFlow<Boolean> = _caloriesNotification.asStateFlow()

    private val _weightReminderDays = MutableStateFlow(
        prefs.getInt(KEY_WEIGHT_DAYS, DEFAULT_WEIGHT_DAYS).takeIf { it > 0 },
    )
    /** Erinnerung, wenn die letzte Messung so viele Tage alt ist; null = aus. */
    val weightReminderDays: StateFlow<Int?> = _weightReminderDays.asStateFlow()

    private val _weeklyReport = MutableStateFlow(prefs.getBoolean(KEY_WEEKLY, true))
    /** Montags automatisch eine KI-Wochenauswertung erstellen. */
    val weeklyReport: StateFlow<Boolean> = _weeklyReport.asStateFlow()

    private val _waterGoalMl = MutableStateFlow(prefs.getInt(KEY_WATER_GOAL, 0).takeIf { it > 0 })
    /** Eigenes Wasser-Tagesziel in ml; null = automatisch aus dem Gewicht. */
    val waterGoalMl: StateFlow<Int?> = _waterGoalMl.asStateFlow()

    fun setWaterGoalMl(ml: Int?) {
        prefs.edit().putInt(KEY_WATER_GOAL, ml ?: 0).apply()
        _waterGoalMl.value = ml
    }

    private val _mealReminders = MutableStateFlow(prefs.getBoolean(KEY_MEAL_REMINDERS, true))
    /** Erinnerung, wenn eine Hauptmahlzeit zur üblichen Zeit noch fehlt. */
    val mealReminders: StateFlow<Boolean> = _mealReminders.asStateFlow()

    fun setMealReminders(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEAL_REMINDERS, enabled).apply()
        _mealReminders.value = enabled
    }

    private val _fastingEnabled = MutableStateFlow(prefs.getBoolean(KEY_FASTING, false))
    /** Intervallfasten: Timer auf „Heute“, Benachrichtigungen, Auswertung. */
    val fastingEnabled: StateFlow<Boolean> = _fastingEnabled.asStateFlow()

    private val _fastingConfig = MutableStateFlow(readFastingConfig())
    val fastingConfig: StateFlow<FastingConfig> = _fastingConfig.asStateFlow()

    fun setFastingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FASTING, enabled).apply()
        _fastingEnabled.value = enabled
    }

    fun setFastingConfig(config: FastingConfig) {
        prefs.edit()
            .putString(KEY_FASTING_PLAN, config.plan.name)
            .putInt(KEY_FASTING_START, config.windowStart.toSecondOfDay() / 60)
            .putInt(KEY_FASTING_HOURS, config.customFastHours)
            .putString(KEY_FASTING_DAYS, config.fastDays.joinToString(",") { it.name })
            .apply()
        _fastingConfig.value = config
    }

    private fun readFastingConfig(): FastingConfig {
        val defaults = FastingConfig()
        return FastingConfig(
            plan = FastingPlan.entries.find { it.name == prefs.getString(KEY_FASTING_PLAN, null) } ?: defaults.plan,
            windowStart = prefs.getInt(KEY_FASTING_START, -1).takeIf { it in 0 until 24 * 60 }
                ?.let { LocalTime.of(it / 60, it % 60) } ?: defaults.windowStart,
            customFastHours = prefs.getInt(KEY_FASTING_HOURS, defaults.customFastHours),
            fastDays = prefs.getString(KEY_FASTING_DAYS, null)?.split(',')
                ?.mapNotNull { name -> DayOfWeek.entries.find { it.name == name } }?.toSet() ?: defaults.fastDays,
        )
    }

    fun setCaloriesNotification(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALORIES, enabled).apply()
        _caloriesNotification.value = enabled
    }

    fun setWeightReminderDays(days: Int?) {
        prefs.edit().putInt(KEY_WEIGHT_DAYS, days ?: 0).apply()
        _weightReminderDays.value = days
    }

    fun setWeeklyReport(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEEKLY, enabled).apply()
        _weeklyReport.value = enabled
    }

    private val _seenTips = MutableStateFlow(prefs.getStringSet(KEY_SEEN_TIPS, emptySet()).orEmpty().toSet())
    /** Namen der weggeklickten Tipps ([de.kalorienapp.ui.components.Tip]). */
    val seenTips: StateFlow<Set<String>> = _seenTips.asStateFlow()

    fun markTipSeen(name: String) {
        val updated = _seenTips.value + name
        prefs.edit().putStringSet(KEY_SEEN_TIPS, updated).apply()
        _seenTips.value = updated
    }

    /** Profil → „Tipps wieder anzeigen“. */
    fun resetTips() {
        prefs.edit().remove(KEY_SEEN_TIPS).apply()
        _seenTips.value = emptySet()
    }

    /** Ob schon einmal nach der Benachrichtigungs-Erlaubnis gefragt wurde (nicht bei jedem Start nerven). */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()

    companion object {
        const val DEFAULT_WEIGHT_DAYS = 3
        private const val KEY_CALORIES = "calories_notification"
        private const val KEY_WEIGHT_DAYS = "weight_reminder_days"
        private const val KEY_WEEKLY = "weekly_report"
        private const val KEY_WATER_GOAL = "water_goal_ml"
        private const val KEY_FASTING = "fasting_enabled"
        private const val KEY_MEAL_REMINDERS = "meal_reminders"
        private const val KEY_FASTING_PLAN = "fasting_plan"
        private const val KEY_FASTING_START = "fasting_window_start_min"
        private const val KEY_FASTING_HOURS = "fasting_custom_hours"
        private const val KEY_FASTING_DAYS = "fasting_days"
        private const val KEY_PERMISSION_ASKED = "notification_permission_asked"
        private const val KEY_SEEN_TIPS = "seen_tips"
    }
}
