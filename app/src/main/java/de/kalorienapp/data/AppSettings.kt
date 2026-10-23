package de.kalorienapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        private const val KEY_PERMISSION_ASKED = "notification_permission_asked"
    }
}
